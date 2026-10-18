#!/usr/bin/env bash
# 构建 APK（不用 Gradle：单模块、无外部依赖，直接用 aapt2/javac/d8/apksigner 走一遍）。
set -euo pipefail

# 任何一步失败都归一化成 rc=1 并给出自家文案（第 77 轮审计 低-1：sed/aapt2 等会透传工具自身 rc）
MANIFEST=""
MANIFEST_BAK=""
on_fail() {
  rc=$?
  if [ -n "$MANIFEST_BAK" ] && [ -f "$MANIFEST_BAK" ]; then
    if mv -f "$MANIFEST_BAK" "$MANIFEST"; then
      echo "已把 AndroidManifest.xml 回退到构建前的版本（第 87 轮审计 低-①）" >&2
    else
      echo "警告：AndroidManifest.xml 回退失败，清单里可能残留本次的版本号（第 88 轮审计 低-③）" >&2
    fi
  fi
  echo "构建失败：上一条命令失败（rc=$rc）⇒ 见上面的报错" >&2
  exit 1
}
trap on_fail ERR

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_SDK:-/root/android/sdk}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"
OUT="${BUILD_DIR:-/tmp/appbuild}"
# 密钥库必须放在**不会被清空**的地方。
# 早先它是 $OUT/debug.keystore，而 $OUT 在第 2 步开头就 rm -rf —— 于是
# "if [ ! -f $KS ]" 每次构建都成立，每次重新生成一把新密钥。
# 结果每个 APK 签名都不同，Android 拒绝用不同签名的包覆盖已安装的应用，
# 表现就是「更新装不上」。这是真正的根因，和版本号无关。
KS="$PROJ/autogo-debug.keystore"
APK_NAME="autogo.apk"

export PATH="$JAVA_HOME/bin:$PATH"

# 本机是 aarch64，而 Google build-tools 里的 aapt2/zipalign 是 x86_64 二进制，直接跑会报
# "cannot execute: required file not found"。Ubuntu 的 android-sdk-build-tools 提供 aarch64
# 原生版，所以逐个探测：SDK 里的能跑就用 SDK 的，否则回退到系统包。
pick() {
  local name="$1" sdk="$2" probe="$3" sys
  if [ -x "$sdk" ] && "$sdk" $probe >/dev/null 2>&1; then echo "$sdk"; return; fi
  sys="$(command -v "$name" || true)"
  echo "$sys"
}
AAPT2="$(pick aapt2 "$BT/aapt2" version)"
ZIPALIGN="$(pick zipalign "$BT/zipalign" -h)"
D8="$(pick d8 "$BT/d8" --help)"
APKSIGNER="$(pick apksigner "$BT/apksigner" --version)"

for pair in "aapt2=$AAPT2" "zipalign=$ZIPALIGN" "d8=$D8" "apksigner=$APKSIGNER"; do
  [ -n "${pair#*=}" ] || { echo "找不到可用的 ${pair%%=*}"; exit 1; }
  echo "使用 ${pair%%=*}: ${pair#*=}"
done
[ -f "$PLATFORM" ] || { echo "缺少 android.jar: $PLATFORM"; exit 1; }

echo "== 1/7 生成图标 =="
python3 "$PROJ/make_icons.py"

echo "== 2/7 准备目录 =="
rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/stage"

# 版本号：从 VERSION 文件读，自增后写回，同时改写 AndroidManifest.xml。
# aapt2 link 的 --version-code 参数不会覆盖 manifest 里的值，所以必须直接改文件。
# versionCode 每次构建 +1，让 Android 能识别「这是新版本」并提示覆盖安装。
# versionName 用「主版本.日期.序号」格式，用户一眼能看出是哪次构建。
VERSION_FILE="$PROJ/VERSION"
# 以 manifest 里现有的 versionCode 为下限：万一 VERSION 文件被回滚或丢失，
# 也不至于打出比已安装版本更低的号（那会让系统认为「新包是旧的」）。
FLOOR=$(sed -n 's/.*android:versionCode="\([0-9]*\)".*/\1/p' "$PROJ/AndroidManifest.xml" | head -1)
[ -n "$FLOOR" ] || FLOOR=0
VCODE=$(cat "$VERSION_FILE" 2>/dev/null || echo "0")
[ "$VCODE" -gt "$FLOOR" ] 2>/dev/null || VCODE="$FLOOR"
VCODE=$((VCODE + 1))
# 需要「原样重打某个版本」时（复现/审计重建），用 BUILD_VCODE=<号> 钉住，
# 不再自增；号必须 >= 清单现有值，否则会打出比已装版本更低的号。
if [ -n "${BUILD_VCODE:-}" ]; then
  if [ "$BUILD_VCODE" -lt "$FLOOR" ] 2>/dev/null; then
    echo "构建失败：BUILD_VCODE=$BUILD_VCODE 小于清单里的 versionCode=$FLOOR" >&2
    exit 1
  fi
  VCODE="$BUILD_VCODE"
  echo "（按 BUILD_VCODE 钉住版本：$VCODE）"
fi
# 本地日期。用 date -u 的话，澳门这边 0~8 点的构建会显示成昨天。
VDATE=$(date +%Y%m%d)
VNAME="1.${VDATE}.${VCODE}"
# VERSION 文件在最后一步「构建成功」时才写（见文件末尾）：
# 中途失败就不会把号推进，下一轮由 manifest 的 FLOOR 兜底，不会打出更低的号。
# 用 sed 直接改 AndroidManifest.xml 里的 versionCode 和 versionName
MANIFEST="$PROJ/AndroidManifest.xml"
MANIFEST_BAK="$PROJ/AndroidManifest.xml.prev"
cp -p "$MANIFEST" "$MANIFEST_BAK"
sed -i 's/android:versionCode="[0-9]*"/android:versionCode="'"$VCODE"'"/' "$PROJ/AndroidManifest.xml"
sed -i 's/android:versionName="[^"]*"/android:versionName="'"$VNAME"'"/' "$PROJ/AndroidManifest.xml"
echo "版本: versionCode=$VCODE versionName=$VNAME"

echo "== 3/7 aapt2 compile =="
"$AAPT2" compile --dir "$PROJ/res" -o "$OUT/res.zip"

echo "== 4/7 aapt2 link =="
"$AAPT2" link \
  -o "$OUT/base.apk" \
  -I "$PLATFORM" \
  --manifest "$PROJ/AndroidManifest.xml" \
  -A "$PROJ/assets" \
  --java "$OUT/gen" \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --version-code "$VCODE" --version-name "$VNAME" \
  "$OUT/res.zip"

echo "== 5/7 javac =="
# 项目路径含中文（/sdcard/改/...），javac 会报 "Invalid filename"，
# 所以把源码拷到纯 ASCII 的临时目录再编译。
rm -rf "$OUT/src"
mkdir -p "$OUT/src"
cp -r "$PROJ/src/." "$OUT/src/"
export LANG=C.UTF-8 LC_ALL=C.UTF-8
mapfile -t SRCS < <(find "$OUT/src" "$OUT/gen" -name '*.java')
# android.jar 里没有 java.lang.invoke.LambdaMetafactory（它是编译器专用桩），
# 不加 core-lambda-stubs.jar 的话，任何 lambda 都会报
# "cannot find symbol: method metafactory"。d8 后面再做真正的脱糖。
LAMBDA_STUBS="$BT/core-lambda-stubs.jar"
BCP="$PLATFORM"
[ -f "$LAMBDA_STUBS" ] && BCP="$PLATFORM:$LAMBDA_STUBS"
# 注意：不能写成 "javac ... | grep ... || true"，那样 javac 的失败会被 grep 的
# 退出码盖掉，错误被吞、照样出包。这里显式取 javac 的退出码。
# javac 段临时摘掉 ERR trap：ERR trap 不受 set +e 影响，会抢在下面的日志提示之前退出（第 78 轮审计 低-中）
trap - ERR
set +e
javac -J-Dfile.encoding=UTF-8 -encoding UTF-8 -source 8 -target 8 -nowarn \
  -bootclasspath "$BCP" -classpath "$BCP" \
  -d "$OUT/classes" "${SRCS[@]}" > "$OUT/javac.log" 2>&1
JAVAC_RC=$?
set -e
grep -vE 'bootstrap class path|source value 8|target value 8|deprecat' "$OUT/javac.log" || true
if [ "$JAVAC_RC" -ne 0 ]; then
  echo "javac 失败（完整日志 $OUT/javac.log）"
  exit 1
fi
trap on_fail ERR

echo "== 6/7 d8 =="
mapfile -t CLASSES < <(find "$OUT/classes" -name '*.class')
"$D8" --release --lib "$PLATFORM" --min-api 26 --output "$OUT/dex" "${CLASSES[@]}"

# 权重资产校验：三个模型必须在 assets/ 里且大小对得上。
# 少一个就会产出「小包」：真机上对应档位直接不可用（我踩过一次：assets 少了两个大模型，包从 122 MB 掉到 9.7 MB）。
for spec in "model_b6c96.txt.gz:4967720:88c7c3da8c4b57fa3b537f07662210d0" "model_b10c128.txt.gz:14466254:61421f1b09789c3263939cd442dc23b8" "model_b18c384nbt.bin.gz:97898094:efc406f90990b1fb96034f426e9965fe"; do
  f="${spec%%:*}"; rest="${spec#*:}"; want="${rest%%:*}"; wantmd5="${rest##*:}"
  if [ ! -f "$PROJ/assets/$f" ]; then
    echo "构建失败：assets/$f 不存在（少了权重，包会变成小包）" >&2
    exit 1
  fi
  got=$(stat -c %s "$PROJ/assets/$f")
  if [ "$got" != "$want" ]; then
    echo "构建失败：assets/$f 大小 $got ≠ 期望 $want（模型被截断或换过？确认后更新本校验）" >&2
    exit 1
  fi
  gotmd5=$(md5sum "$PROJ/assets/$f" | cut -d' ' -f1)
  if [ "$gotmd5" != "$wantmd5" ]; then
    echo "构建失败：assets/$f 内容对不上（md5 $gotmd5 ≠ 期望 $wantmd5；大小一样但换了模型？）" >&2
    exit 1
  fi
done
echo "权重资产校验：三个模型都在（约 5 MB / 14 MB / 93 MB）"

echo "== 7/7 打包 / 对齐 / 签名 =="
# 失败一律退出码 1（文案区分类别）；任何失败都要把上一版产物放回去（不只校验器那一条路 —— 第 75 轮审计 低-1）
BUILD_OK=0
restore_prev() {
  if [ "$BUILD_OK" -ne 1 ] && [ -f "$PROJ/$APK_NAME.prev" ]; then
    rm -f "$PROJ/$APK_NAME" "$PROJ/$APK_NAME.idsig"
    mv -f "$PROJ/$APK_NAME.prev" "$PROJ/$APK_NAME"
    [ -f "$PROJ/$APK_NAME.idsig.prev" ] && mv -f "$PROJ/$APK_NAME.idsig.prev" "$PROJ/$APK_NAME.idsig"
    echo "已回退到上一版产物：$PROJ/$APK_NAME" >&2
  fi
}
trap restore_prev EXIT

# 打包前先把上一版产物挪到 .prev：apksigner 直接写项目树，校验失败时不能把上一版好包一起删掉
# （第 74 轮审计 低-3）。成功出包后再删掉 .prev。
[ -f "$PROJ/$APK_NAME" ] && mv -f "$PROJ/$APK_NAME" "$PROJ/$APK_NAME.prev"
[ -f "$PROJ/$APK_NAME.idsig" ] && mv -f "$PROJ/$APK_NAME.idsig" "$PROJ/$APK_NAME.idsig.prev"
cp "$OUT/dex/classes.dex" "$OUT/stage/classes.dex"
mkdir -p "$OUT/stage/lib/arm64-v8a"
cp "$PROJ/jniLibs/arm64-v8a/libkatago.so" "$OUT/stage/lib/arm64-v8a/libkatago.so"
# 可选：五子棋的外部强引擎 Rapfi（有就装进去；没有也照样能构建，App 里会退回内置 AI）
EXTRA_LIBS=""
if [ -f "$PROJ/jniLibs/arm64-v8a/librapfi.so" ]; then
  cp "$PROJ/jniLibs/arm64-v8a/librapfi.so" "$OUT/stage/lib/arm64-v8a/librapfi.so"
  EXTRA_LIBS="lib/arm64-v8a/librapfi.so"
fi

cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/stage" && zip -q -X "$OUT/unsigned.apk" classes.dex lib/arm64-v8a/libkatago.so $EXTRA_LIBS )

"$ZIPALIGN" -f -p 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=AutoGo Debug,O=Android,C=US" >/dev/null 2>&1
fi

if ! "$APKSIGNER" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$PROJ/$APK_NAME" "$OUT/aligned.apk"; then
  echo "构建失败：签名失败（keystore/密码不对，或 apksigner 报错）⇒ 上一版产物已回退，见上面的报错。" >&2
  exit 1
fi

"$APKSIGNER" verify --print-certs "$PROJ/$APK_NAME" | head -3

# 校验打出来的包真的带这个版本号。
# 没有这一步的话，前面任何一步静默失败（比如改源码的脚本报错、编译没跑到）
# 都会把「清单说 36、包里还是 35」的旧包复制出去，而且看不出来 —— 真踩过一次。
ACTUAL_VCODE=$("$AAPT2" dump badging "$PROJ/$APK_NAME" 2>/dev/null \
  | sed -n "s/.*versionCode='\([0-9]*\)'.*/\1/p" | head -1)
if [ "$ACTUAL_VCODE" != "$VCODE" ]; then
  echo "构建校验失败：期望 versionCode=$VCODE，包内是 '${ACTUAL_VCODE:-读不到}'" >&2
  exit 1
fi
# 校验清单里声明的每个 activity 类都在 dex 里有类定义。
# 第 71 轮审计：只校验 versionCode 时，源码里删掉一个 activity 类（如 MenuActivity.java）
# 仍会 rc=0 打包成功，而包里 launchable 指向不存在的类。
# 第 72 轮审计又拆出第一版的三个洞：单引号属性值一条都不解析（还谎报数量）、
# 用 grep 字符串判「类存在」（源码里留同名字符串就能骗过）、合法的裸名/别名被误报。
# ⇒ v118 交给 tools/checks/check_activities.py：aapt2 dump xmltree 解析清单
#   （跨行 / 单双引号 / 裸名 / activity-alias 的 targetActivity）+ 直接解析 dex 的 class_defs 表。
set +e
python3 "$PROJ/tools/checks/check_activities.py" "$PROJ/$APK_NAME" "$AAPT2"
CHECK_RC=$?
set -e
if [ "$CHECK_RC" -ne 0 ]; then
  rm -f "$PROJ/$APK_NAME" "$PROJ/$APK_NAME.idsig"
  case "$CHECK_RC" in
    3) echo "构建失败：包内 dex 解析失败（包可能已损坏）⇒ 已删除这个不合格的包，修好后重跑。" >&2;;
    2) echo "构建失败：校验器自身出错（用法或 aapt2 问题）⇒ 见上面的报错。" >&2;;
    *) echo "构建失败：清单里有组件类不在包里 ⇒ 已删除这个不合格的包，修好后重跑。" >&2;;
  esac
  if [ -f "$PROJ/$APK_NAME.prev" ]; then
    mv -f "$PROJ/$APK_NAME.prev" "$PROJ/$APK_NAME"
    [ -f "$PROJ/$APK_NAME.idsig.prev" ] && mv -f "$PROJ/$APK_NAME.idsig.prev" "$PROJ/$APK_NAME.idsig"
    echo "已回退到上一版产物：$PROJ/$APK_NAME" >&2
  fi
  exit 1
fi
# 到这里才算构建成功，才推进 VERSION 文件
# CHANGELOG「当前版本」行同步：必须在 rm .prev / 写 VERSION 之前（第 86 轮审计 中：同步失败不回滚）
if [ -f "$PROJ/CHANGELOG.md" ]; then
  python3 - "$PROJ/CHANGELOG.md" "$VCODE" "$VNAME" <<'PYEOF'
import re, sys
p, v = sys.argv[1], sys.argv[2]
s = open(p, encoding="utf-8").read()
vn = sys.argv[3]
s2, n = re.subn(r'当前版本 \*\*v\d+\*\*（versionCode \d+ / versionName [^）]*）',
                '当前版本 **v' + v + '**（versionCode ' + v + ' / versionName ' + vn + '）', s, count=1)
if n == 0:
    s2, n = re.subn(r'当前版本 \*\*v\d+\*\*（[^）]*）',
                    '当前版本 **v' + v + '**（versionCode ' + v + ' / versionName ' + vn + '）', s, count=1)
if n != 1:
    print("构建失败：CHANGELOG.md 里找不到「当前版本 **vN**」行，无法同步"
          "（改了那一行的格式就会这样；修好格式再构建，否则版本号与更新日志会对不上）", file=sys.stderr)
    sys.exit(1)
else:
    open(p, "w", encoding="utf-8").write(s2)
    print("已把 CHANGELOG.md 的「当前版本」行同步为 v" + v)
PYEOF
fi
rm -f "$PROJ/$APK_NAME.prev" "$PROJ/$APK_NAME.idsig.prev"
rm -f "$MANIFEST_BAK"
BUILD_OK=1
echo "$VCODE" > "$VERSION_FILE"
# 版本推进时同步更新日志的「当前版本」行（否则守卫在出货树上会红 —— 第 83 轮审计中）
echo "校验通过：versionCode=$VCODE（清单与包内一致，签名与上次相同）"
ls -la "$PROJ/$APK_NAME"
echo "OK -> $PROJ/$APK_NAME"
