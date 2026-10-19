#!/usr/bin/env bash
# 装机之前能做的全部自动化验证。
#
#   BoardTest    —— 围棋规则引擎 / SGF / 分析解析（纯逻辑，秒级）
#   GtpTest      —— 对着一个真的 KataGo 进程跑完整 GTP 链路：握手、多行响应、
#                   错误响应、超时、kata-genmove_analyze 解析
#   GomokuTest   —— 五子棋规则：落子 / 连线 / 禁手（长连、四四、三三）/ 终局 / 拷贝
#   GomokuAITest —— 五子棋内置 AI：难度档、只读契约、成五/堵五/堵活三、
#                   禁手不吃子、评估对称性、三档自对弈合法性
#   GomokuReviewTest —— 五子棋复盘：胜率换算、每手亏损（含截断）、推荐点、
#                       结论措辞、非法手顺报错、难度还原、自对弈整盘
#   RapfiGomokuTest —— 五子棋外部强引擎 Rapfi 的适配层：对着真的 pbrain-rapfi 进程
#                       跑 Gomocup 协议（坐标映射、成五/堵五、只读契约、进程重启）
#
#   Driver95     —— 五子棋界面层桩测：假 android 类 + 真 GomokuActivity，覆盖棋盘路数、
#                   我执黑/白、悔棋轮次、复盘入口、终局设置留存、外部引擎按路数取舍
#
# Board/Sgf/Analysis/GtpEngine 都只用标准库、不碰 Android 类，所以能在 JVM 上直接验证。
set -euo pipefail

PROJ="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-arm64}"
OUT="${TEST_BUILD_DIR:-/tmp/autogo-jvmtest}"
KATAGO="${KATAGO_BIN:-/usr/local/bin/katago}"
MODEL="${KATAGO_MODEL:-/sdcard/改/陪练棋盘/档案/autogo/models/kata1-b10c128-s1141046784-d204142634.txt.gz}"
# 真实棋谱（桌面版自对弈产出），用来端到端验证 SGF 读取
REAL_SGF="${REAL_SGF:-/sdcard/改/陪练棋盘/档案/autogo/games_19/game_001.sgf}"

export PATH="$JAVA_HOME/bin:$PATH"
export LANG=C.UTF-8 LC_ALL=C.UTF-8

rm -rf "$OUT"
mkdir -p "$OUT/src" "$OUT/classes"
# 源码路径含中文，javac 会报 Invalid filename，先拷到 ASCII 目录
cp "$PROJ/src/com/autogo/Board.java" \
   "$PROJ/src/com/autogo/Sgf.java" \
   "$PROJ/src/com/autogo/Analysis.java" \
   "$PROJ/src/com/autogo/GtpEngine.java" \
   "$PROJ/src/com/autogo/LayoutMath.java" \
   "$PROJ/src/com/autogo/GameReview.java" \
   "$PROJ/src/com/autogo/Theme.java" \
   "$PROJ/src/com/autogo/Palette.java" \
   "$PROJ/src/com/autogo/GomokuBoard.java" \
   "$PROJ/src/com/autogo/GomokuEngine.java" \
   "$PROJ/src/com/autogo/LocalGomokuAI.java" \
   "$PROJ/src/com/autogo/GomokuReview.java" \
   "$PROJ/src/com/autogo/XiangqiBoard.java" \
   "$PROJ/src/com/autogo/XiangqiAI.java" \
   "$PROJ/src/com/autogo/XiangqiReview.java" \
   "$PROJ/src/com/autogo/JunqiBoard.java" \
   "$PROJ/src/com/autogo/JunqiAI.java" "$PROJ/src/com/autogo/JunqiLearn.java" "$PROJ/src/com/autogo/JunqiReveal.java" \
   "$PROJ/src/com/autogo/GomokuLearn.java" \
   "$PROJ/src/com/autogo/AnimMath.java" \
   "$PROJ/src/com/autogo/Game2048.java" \
   "$PROJ/src/com/autogo/RapfiGomokuEngine.java" \
   "$PROJ/tests/BoardTest.java" \
   "$PROJ/tests/GtpTest.java" \
   "$PROJ/tests/GomokuTest.java" \
   "$PROJ/tests/GomokuAITest.java" \
   "$PROJ/tests/GomokuReviewTest.java" \
   "$PROJ/tests/GomokuLearnTest.java" \
   "$PROJ/tests/XiangqiTest.java" \
   "$PROJ/tests/XiangqiAITest.java" \
   "$PROJ/tests/XiangqiReviewTest.java" \
   "$PROJ/tests/AnimMathTest.java" \
   "$PROJ/tests/JunqiTest.java" "$PROJ/tests/JunqiAITest.java" "$PROJ/tests/JunqiRevealTest.java" \
   "$PROJ/tests/JunqiStrengthTest.java" "$PROJ/tests/JunqiLearnTest.java" \
   "$PROJ/tests/Game2048Test.java" \
   "$PROJ/tests/XiangqiTrendTest.java" \
   "$PROJ/tests/RapfiGomokuTest.java" \
   "$OUT/src/"
cp "$PROJ/tests/fixture_analyze.txt" "$OUT/"

javac -encoding UTF-8 -nowarn -d "$OUT/classes" "$OUT"/src/*.java

echo "=========== 1. 规则引擎 / SGF / 分析解析 ==========="
java -cp "$OUT/classes" BoardTest "$OUT/fixture_analyze.txt" "$REAL_SGF"

echo
echo "=========== 2. 五子棋 / 象棋 / 军棋：规则 / 内置 AI / 复盘 / 学习（纯逻辑） ==========="
java -cp "$OUT/classes" GomokuTest
java -cp "$OUT/classes" GomokuAITest
java -cp "$OUT/classes" GomokuReviewTest
java -cp "$OUT/classes" GomokuLearnTest
java -cp "$OUT/classes" XiangqiTest
java -cp "$OUT/classes" XiangqiAITest
java -cp "$OUT/classes" XiangqiReviewTest
java -cp "$OUT/classes" AnimMathTest
java -cp "$OUT/classes" JunqiAITest
java -cp "$OUT/classes" JunqiStrengthTest  # 三档难度强度回归（固定种子，慢约 15 秒）
java -cp "$OUT/classes" JunqiLearnTest
java -cp "$OUT/classes" JunqiRevealTest
java -cp "$OUT/classes" JunqiTest  # 军棋规则（5×12 / 铁路 / 行营 / 战斗 / 胜负）
java -cp "$OUT/classes" Game2048Test  # 2048 规则（4×4 / 合并一次 / 生成 / 胜负）
java -cp "$OUT/classes" XiangqiTrendTest  # 象棋复盘走势文案 / 未终局前缀

echo
echo "=========== 3. 五子棋外部强引擎 Rapfi（适配层，跑宿主版 pbrain-rapfi） ==========="
# 项目目录在 /sdcard 上，那儿既不能加可执行位又是 noexec —— 和 App 一样先拷到可执行的临时目录
RAPFI_SRC="$PROJ/tools/rapfi/pbrain-rapfi-linux-aarch64"
RAPFI_BIN="$OUT/rapfi/pbrain-rapfi"
mkdir -p "$OUT/rapfi"
if [ -f "$RAPFI_SRC" ]; then
  cp "$RAPFI_SRC" "$RAPFI_BIN"
  chmod +x "$RAPFI_BIN"
fi
if [ -x "$RAPFI_BIN" ]; then
  java -cp "$OUT/classes" RapfiGomokuTest "$RAPFI_BIN"
  rc=$?
  if [ $rc -ne 0 ]; then
    echo "RapfiGomokuTest 退出码 $rc —— 这不算通过"
    exit $rc
  fi
else
  echo "错误：$RAPFI_BIN 不可执行 —— 外部强引擎的用例没跑，这不算通过"
  exit 1
fi

echo
echo
echo "=========== 4. 界面层桩测（假 android 类 + 真界面源码） ==========="
# 容器里没有真机（pm/am/logcat 不可用、android.jar 是桩），界面层只能在 JVM 上用假 android 类驱动。
# 这一节编的界面源码全是真类：MenuActivity（启动选择页）、GomokuActivity（对局页）、
# GomokuView（棋盘绘制与触摸）、GomokuReviewActivity（复盘页）、WinrateBarView / WinrateChartView。
# 假类只提供 android.* 的空壳（含 android.graphics.* 与 android.view.MotionEvent）；
# v96 那版用的两个替身（tests/ui/sub/）在 v97 已经删掉 —— 现在画和复盘页也是真代码在跑。
# 四个探针各自独立：
#   MenuProbe（项数以运行输出为准）：启动选择页（两个按钮各自跳哪一页、标题/提示/版本文案、主题档落地与配色、
#                      系统深浅色变化后自己重判重铺、回到前台重盯点与离开注销、版本读不到时兜底）
#   Driver95（项数以运行输出为准）：棋盘 13/15/19 路、我执黑/白、悔棋轮次、复盘入口、终局设置留存、非 15 路强档走内置 AI
#   ViewProbe（项数以运行输出为准）：几何换算（棋盘尺寸/间距/圆心）、触摸命中（含 0.6 格容差与全屏按钮优先级）、
#                      手顺与推荐点开关、路数变化后重算几何，外加**绘制层**（网格 2n 条线、坐标 2n 个文字
#                      且列跳过 I、9 个星位、石身+边+最后一手标记、五连赢线 30+1 条且长 4×spacing、
#                      手顺数字、推荐点提示圈 —— 自查发现（先前的审计报告没提过）的「只测几何不测绘制」缺口）
#   ReviewProbe（项数以运行输出为准）：复盘页读 Intent（长度校验）、失误排行、选中同步与夹取、黑白视角换算、
#                      终局文字推导、onPause/onResume 不崩与定时刷新、点行跳步（手顺/失误/终局）、
#                      高亮行错位（第 54 轮审计 低-1 的回归钉桩）
# 已知盲区（第 52 轮审计 低-2）：Driver95 用 sun.misc.Unsafe.allocateInstance 造 GomokuActivity，
#   不走构造器 ⇒ 字段声明处的默认值（如 `private int size = 15;`）不生效，由 Driver95.setDefaults 手工镜像。
#   把源码里的声明默认值改错，这一节不会变红（ViewProbe / ReviewProbe / MenuProbe 用 new 造对象，不受这条限制）。
ANDROID_JAR="${ANDROID_JAR:-/root/android/sdk/platforms/android-34/android.jar}"
if [ ! -f "$ANDROID_JAR" ]; then
  echo "错误：找不到 android.jar（$ANDROID_JAR）—— 第 4 节要拿它编界面层，没它跑不了，这不算通过"
  echo "      可用 ANDROID_JAR=/path/to/android.jar 覆盖。"
  exit 1
fi
if [ -d "$PROJ/tests/ui/sub" ]; then
  echo "错误：$PROJ/tests/ui/sub 还在 —— v97 起界面层桩测用真类，替身目录必须删掉（否则悄悄退回替身）"
  exit 1
fi
mkdir -p "$OUT/ui/src" "$OUT/ui/fake"
cp "$PROJ/src/com/autogo/MenuActivity.java" "$PROJ/src/com/autogo/GomokuActivity.java" \
   "$PROJ/src/com/autogo/Watermark.java" "$PROJ/src/com/autogo/WinEgg.java" "$PROJ/src/com/autogo/BoardArt.java" \
   "$PROJ/src/com/autogo/XiangqiView.java" "$PROJ/src/com/autogo/XiangqiActivity.java" \
   "$PROJ/src/com/autogo/XiangqiReviewActivity.java" \
   "$PROJ/src/com/autogo/JunqiView.java" "$PROJ/src/com/autogo/JunqiActivity.java" \
   "$PROJ/src/com/autogo/Game2048View.java" "$PROJ/src/com/autogo/Game2048Activity.java" \
   "$PROJ/src/com/autogo/GomokuView.java" \
   "$PROJ/src/com/autogo/GomokuReviewActivity.java" "$PROJ/src/com/autogo/WinrateChartView.java" \
   "$PROJ/src/com/autogo/WinrateBarView.java" "$PROJ/src/com/autogo/UiTheme.java" \
   "$PROJ/src/com/autogo/Screen.java" "$PROJ/src/com/autogo/GomokuLearn.java" "$PROJ/src/com/autogo/Changelog.java" "$PROJ/src/com/autogo/AnimMath.java" "$OUT/ui/src/"
cp -r "$PROJ/tests/ui/fake/." "$OUT/ui/fake/"
FAKE_N=$(find "$OUT/ui/fake" -name '*.java' | wc -l)
if [ "$FAKE_N" -lt 37 ]; then
  echo "错误：假 android 类只有 $FAKE_N 个（应 ≥ 37）—— 界面层桩测的基础设施不完整，这不算通过"
  exit 1
fi
javac -encoding UTF-8 -nowarn -d "$OUT/ui/fakecls" $(find "$OUT/ui/fake" -name '*.java')
# 接线桩：MenuActivity 里的 Intent 目标 MainActivity.class 需要一个能解析的类。
# 围棋对局页（1800+ 行、拖着一堆只在真机上存在的 API）编不进桩测，这里只放一个空壳 ——
# 探针断的是「点了按钮跳到哪个类名」，不测那一页的行为。
mkdir -p "$OUT/ui/stub"
cp "$PROJ/tests/ui/stub/com/autogo/MainActivity.java" "$OUT/ui/stub/"
javac -encoding UTF-8 -nowarn -d "$OUT/ui/stubcls" -cp "$OUT/ui/fakecls:$ANDROID_JAR" "$OUT/ui/stub/MainActivity.java"
javac -encoding UTF-8 -nowarn -d "$OUT/ui/realcls" -cp "$OUT/ui/fakecls:$OUT/ui/stubcls:$OUT/classes:$ANDROID_JAR" "$OUT"/ui/src/*.java
javac -encoding UTF-8 -nowarn -d "$OUT/ui/testcls" -cp "$OUT/ui/fakecls:$OUT/ui/realcls:$OUT/ui/stubcls:$OUT/classes:$ANDROID_JAR" \
  "$PROJ/tests/ui/Driver95.java" "$PROJ/tests/ui/ViewProbe.java" "$PROJ/tests/ui/ReviewProbe.java" \
  "$PROJ/tests/ui/MenuProbe.java" "$PROJ/tests/ui/XiangqiViewProbe.java" "$PROJ/tests/ui/JunqiViewProbe.java" "$PROJ/tests/ui/JunqiProbe.java" \
  "$PROJ/tests/ui/XiangqiReviewProbe.java" "$PROJ/tests/ui/XiangqiProbe.java" \
  "$PROJ/tests/ui/WatermarkProbe.java" "$PROJ/tests/ui/WinEggTest.java" "$PROJ/tests/ui/Game2048ViewProbe.java" "$PROJ/tests/ui/Game2048PageProbe.java"
for t in Driver95 ViewProbe ReviewProbe MenuProbe XiangqiViewProbe JunqiViewProbe JunqiProbe XiangqiReviewProbe XiangqiProbe WatermarkProbe WinEggTest Game2048ViewProbe Game2048PageProbe; do
  java -cp "$OUT/ui/fakecls:$OUT/ui/realcls:$OUT/ui/stubcls:$OUT/ui/testcls:$OUT/classes" com.autogo.$t
  rc=$?
  if [ $rc -ne 0 ]; then
    echo "$t 退出码 $rc —— 界面层桩测没过，这不算通过"
    exit $rc
  fi
done

# 军棋页必须走 JunqiReveal（规则只留一份，别再内联重写）
REVEAL_CALLS=$(python3 - "$PROJ/src/com/autogo/JunqiActivity.java" <<'PYEOF'
import re, sys
s = open(sys.argv[1], encoding="utf-8").read()
s = re.sub(r"/\*.*?\*/", "", s, flags=re.S)
s = re.sub(r"//[^\n]*", "", s)
print(len(re.findall(r"JunqiReveal\s*\.\s*resolve\s*\(", s)))
PYEOF
)
if [ "$REVEAL_CALLS" != "2" ]; then
  echo "错误：JunqiActivity 里 JunqiReveal.resolve 的调用数是 $REVEAL_CALLS，期望 2（人走 + 电脑走各一次）"
  exit 1
fi
echo "军棋暴露规则接线：JunqiReveal.resolve 调用 $REVEAL_CALLS 处 ✓"
# 除了 JunqiReveal，页面/任何源文件里都不要再自己给 revealed[...] 赋值（第 101 轮审计的「双绕过」演示；
# 第 103 轮审计指出旧正则只认常量下标，`revealed[JunqiBoard.idx(...)] = true` 漏网 ⇒ 改成语法级脚本，能配对嵌套 []、也查别名）
if ! python3 tools/checks/check_inline_reveal.py src/com/autogo; then
  exit 1
fi

echo "=========== 5. GTP 客户端（需要真引擎） ==========="
if [ -x "$KATAGO" ] && [ -f "$MODEL" ]; then
  java -cp "$OUT/classes" GtpTest "$KATAGO" "$MODEL" "$OUT/gtptest.cfg"
  rc=$?
  if [ $rc -ne 0 ]; then
    echo "GtpTest 退出码 $rc（2 = 它自己判定引擎/权重路径不对、根本没跑）—— 这不算通过"
    echo

exit $rc
  fi
else
  echo "错误：KATAGO_BIN=$KATAGO 或 KATAGO_MODEL=$MODEL 不可用 —— 真引擎用例没跑，这不算通过"
  exit 1
fi

echo "=========== 6. 检测力温度计（故意把守卫次序对调）==========="
# 只跑套件发现不了「断言失去区分力」：温度计在自己的 /tmp 副本里把两道守卫对调，
# 要求恰好 Driver95 红 2 条（100/0 → 98/2）。它不改活树，也不计入上面的项数。
if ! bash "$PROJ/tools/checks/guard_order_thermometer.sh"; then
  echo "温度计失败：断言可能已失去区分力（详见上面输出）" >&2
  exit 1
fi

# ── 2048 没有彩蛋：源码里不许出现 WinEgg（用户明确要求）──
if grep -n "WinEgg" "$PROJ/src/com/autogo/Game2048.java" "$PROJ/src/com/autogo/Game2048View.java" "$PROJ/src/com/autogo/Game2048Activity.java" >/dev/null 2>&1; then
  echo "错误：2048 源码里出现了 WinEgg —— 这个游戏明确没有彩蛋" >&2
  exit 1
fi
echo "2048 彩蛋检查：没有接 WinEgg ✓"

# ── 更新日志同步：CHANGELOG.md 的最新版本必须与 App 内「公告」的最新版本一致 ──
V1=$(grep -m1 "^## v" "$PROJ/CHANGELOG.md" | sed 's/^## //')
V2=$(grep -m1 'new Entry("' "$PROJ/src/com/autogo/Changelog.java" | sed 's/.*new Entry("\([^"]*\)".*/\1/')
if [ "$V1" != "$V2" ]; then
  echo "错误：更新日志不同步 —— CHANGELOG.md 最新是「$V1」，App 内公告最新是「$V2」" >&2
  exit 1
fi
echo "更新日志同步：$V1 ✓"
python3 - "$PROJ" "$V1" <<'PYEOF' || exit 1
import re, sys
proj, ver = sys.argv[1], sys.argv[2]
java = open(proj + "/src/com/autogo/Changelog.java", encoding="utf-8").read()
def entries(src):
    out = []
    for mm in re.finditer(r'new Entry\("(v[0-9]+)",', src):
        st = mm.end()
        nxt = src.find('new Entry("', st)
        en = nxt if nxt != -1 else src.find('};', st)
        out.append((mm.group(1), re.findall(r'"([^"]*)"', src[st:en])))   # 含每条公告的最后一句
    return out
allents = entries(java)
if not allents:
    print("守卫失败：Changelog.java 里找不到 new Entry(\"vNN\" …)"); sys.exit(1)
md = open(proj + "/CHANGELOG.md", encoding="utf-8").read()
norm = lambda t: re.sub(r'[\s*`]+', '', t)

# 第 90 轮 低-①：先把 HTML 注释整段剥掉 —— 注释里的内容渲染后看不见，既不该计覆盖也不该计顺序
scan = re.sub(r'<!--.*?-->', '', md, flags=re.S)
scan = re.sub(r'```.*?```', '', scan, flags=re.S)      # 围栏代码块里的「## vNN」不算标题（第 92 轮 低-①）
scan = re.sub(r'<pre>.*?</pre>', '', scan, flags=re.S)  # 同理

# 段标题：只认以 vNN 开头（严格词界，## v124x 不算）的「## 」标题
VT = r'v(\d+)(?![\w])'
cover, stand = set(), []
for hm in re.finditer(r'^## (.+)$', scan, flags=re.M):
    head = hm.group(1).strip()
    if not re.match(r'^v\d+(?![\w])', head):
        continue
    toks = [int(x) for x in re.findall(VT, head)]
    cover.update(toks)
    if len(toks) == 1:
        stand.append(toks[0])

# 正文核对（最新版逐句）
secs = {}
for chunk in re.split(r'^## ', scan, flags=re.M)[1:]:
    hd = chunk.split("\n", 1)[0].strip()
    hm = re.match(r'^(v\d+)(?![\w])', hd)
    if hm and hm.group(1) not in secs:
        secs[hm.group(1)] = chunk
if ver not in secs:
    print("守卫失败：CHANGELOG.md 里找不到 " + ver + " 段落"); sys.exit(1)
total = 0
for vv, lines in allents:          # 每条公告都逐句核对（第 92 轮 低-②）
    if vv not in secs:
        print("守卫失败：CHANGELOG.md 里找不到 " + vv + " 段落"); sys.exit(1)
    b = norm(secs[vv])
    for ln in lines:
        if norm(ln) and norm(ln) not in b:
            print("守卫失败：CHANGELOG.md 的 " + vv + " 段落缺少公告里的句子：" + ln); sys.exit(1)
    total += len(lines)
print("更新日志正文同步：" + str(len(allents)) + " 条公告、共 " + str(total) + " 句逐句核对 ✓")

mv = re.search(r'当前版本 \*\*(v\d+)\*\*（versionCode (\d+) / versionName ([^）]*)）', md)
vv = open(proj + "/VERSION", encoding="utf-8").read().strip()
if not mv:
    print("守卫失败：CHANGELOG.md 里找不到『当前版本 **vN**（versionCode N / versionName …）』这一行（第 3 行附近；发布后应由 build.sh 同步为 v" + vv + "）"); sys.exit(1)
if mv.group(1) != "v" + vv:
    print("守卫失败：CHANGELOG.md 的「当前版本」是 " + mv.group(1) + "，而 VERSION=" + vv + "（发布时应由 build.sh 同步）"); sys.exit(1)
if mv.group(2) != vv:
    print("守卫失败：CHANGELOG.md 的「当前版本」行里 versionCode=" + mv.group(2) + "，与 VERSION=" + vv + " 不符"); sys.exit(1)
print("更新日志当前版本：" + mv.group(1) + " ✓（与 VERSION 一致）")

# ── 公告与更新日志的结构守卫（第 87 / 89 / 90 轮审计）──
srcj = open(proj + "/src/com/autogo/Changelog.java", encoding="utf-8").read()
jv = re.findall(r'new Entry\("(v[0-9]+)"', srcj)
if not jv:
    print("守卫失败：Changelog.java 里找不到 new Entry(\"vNN\" …)"); sys.exit(1)
if len(set(jv)) != len(jv):
    print("守卫失败：Changelog.java 里有重复版本：" + ",".join(jv)); sys.exit(1)
jn = [int(v[1:]) for v in jv]
for a, c in zip(jn, jn[1:]):
    if a <= c:
        print("守卫失败：公告不是严格倒序（v" + str(a) + " 排在 v" + str(c) + " 之前）"); sys.exit(1)

# ① 覆盖：每条公告都要在段标题里出现（范围/合并段按其中出现的版本号展开）
if len(set(stand)) != len(stand):
    print("守卫失败：CHANGELOG.md 里有重复的独立段：" + ",".join("v%d" % x for x in stand)); sys.exit(1)
missing = [v for v in jv if int(v[1:]) not in set(stand)]
if missing:
    print("守卫失败：公告缺独立段标题：" + ",".join(missing) + "（每条公告都要有自己的一节「## vNN」；范围/合辑标题只能用于非公告的历史段，且不能被 <!-- --> 注释掉）"); sys.exit(1)

# ② 顺序：只比「独立段」（标题里只有一个版本号）与公告列表的先后 —— 这样①②互不遮蔽
seq = [v for v in stand if v in set(jn)]
expect = [v for v in jn if v in set(seq)]
if seq != expect:
    print("守卫失败：独立段顺序与 Changelog.java 不一致（md=" + ",".join("v%d" % x for x in seq) + " java=" + ",".join("v%d" % x for x in expect) + "）"); sys.exit(1)

# ③「md 第一段是最新版」由上面 bash 预检（V1==V2）保证，这里不再重复（第 90 轮：它被完全遮蔽）

# ④ 不超前
if cover and max(cover) > jn[0]:
    print("守卫失败：CHANGELOG.md 里出现比公告最新版更新的段落（v" + str(max(cover)) + " > v" + str(jn[0]) + "）"); sys.exit(1)

print("更新日志结构：公告 " + str(len(jv)) + " 条，独立段 " + str(len(stand)) + " 段（" + ("== 公告数 ✓" if len(stand) == len(jv) else "≠ 公告数 ✗") + "）；段标题共 " + str(len(cover)) + " 个版本号")
PYEOF

echo
# ---- 水印接线守卫：九个页面都必须挂右下角水印 ----
WM_MISS=0
for f in MenuActivity MainActivity ReviewActivity GomokuActivity GomokuReviewActivity XiangqiActivity XiangqiReviewActivity JunqiActivity Game2048Activity; do
    if ! python3 -c "import re,sys; s=open(sys.argv[1],encoding='utf-8').read(); s=re.sub(r'/\\*.*?\\*/','',s,flags=re.S); s=re.sub(r'//[^\n]*','',s); sys.exit(0 if re.search(r'^\\s*Watermark\\.wrap\\(',s,re.M) else 1)" "$PROJ/src/com/autogo/$f.java"; then
        echo "守卫失败：$f.java 没有调用 Watermark.wrap（水印缺失）"
        WM_MISS=1
    fi
done
if [ "$WM_MISS" -ne 0 ]; then exit 1; fi
echo "水印接线：九个页面都挂了右下角水印 ✓"

echo "套件结束：全部通过（含第 6 节温度计）"
