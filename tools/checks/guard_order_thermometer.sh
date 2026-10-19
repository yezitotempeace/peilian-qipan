#!/usr/bin/env bash
# 检测力温度计（第 71 轮审计 (c) 建议固化）：
# 把 src/com/autogo/GomokuActivity.java 里 startReview() 的两道守卫【次序对调】，
# 期望 tests/ui/Driver95.java 恰好红 2 条（⑮b 的两条），基线 70/0。
# 只碰 /tmp 下的副本，绝不改项目文件。
#
# 用法： bash tools/checks/guard_order_thermometer.sh
# 依赖： ANDROID_JAR（默认 /root/android/sdk/platforms/android-34/android.jar）、java/javac 17
set -u
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ANDROID_JAR="${ANDROID_JAR:-/root/android/sdk/platforms/android-34/android.jar}"
JAVA_HOME_DIR="${JAVA_HOME_DIR:-/usr/lib/jvm/java-17-openjdk-arm64}"
JAVAC="$JAVA_HOME_DIR/bin/javac"; JAVA="$JAVA_HOME_DIR/bin/java"
W="${TMPDIR:-/tmp}/thermometer_$$"

[ -x "$JAVAC" ] || { echo "错误：找不到 javac（$JAVAC）" >&2; exit 1; }
[ -f "$ANDROID_JAR" ] || { echo "错误：找不到 android.jar（$ANDROID_JAR）" >&2; exit 1; }

build_and_run() {   # $1=源码根（含 src/ tests/） $2=工作目录
  local R="$1" W="$2"
  rm -rf "$W"; mkdir -p "$W" || return 1
  "$JAVAC" -encoding UTF-8 -nowarn -d "$W/fakecls" $(find "$R/tests/ui/fake" -name '*.java') || return 1
  "$JAVAC" -encoding UTF-8 -nowarn -cp "$W/fakecls" -d "$W/basecls" \
    "$R/src/com/autogo/Board.java" "$R/src/com/autogo/Palette.java" "$R/src/com/autogo/Theme.java" "$R/src/com/autogo/LayoutMath.java" \
    "$R/src/com/autogo/Screen.java" "$R/src/com/autogo/GomokuBoard.java" "$R/src/com/autogo/GomokuEngine.java" \
    "$R/src/com/autogo/GomokuReview.java" "$R/src/com/autogo/LocalGomokuAI.java" "$R/src/com/autogo/RapfiGomokuEngine.java" \
    "$R/src/com/autogo/GomokuLearn.java" "$R/src/com/autogo/AnimMath.java" "$R/src/com/autogo/XiangqiBoard.java" \
    "$R/src/com/autogo/XiangqiAI.java" "$R/src/com/autogo/XiangqiReview.java" \
    "$R/src/com/autogo/JunqiBoard.java" "$R/src/com/autogo/JunqiAI.java" "$R/src/com/autogo/JunqiLearn.java" "$R/src/com/autogo/JunqiReveal.java" "$R/src/com/autogo/Game2048.java" || return 1
  "$JAVAC" -encoding UTF-8 -nowarn -cp "$W/fakecls:$W/basecls:$ANDROID_JAR" -d "$W/stubcls" $(find "$R/tests/ui/stub" -name '*.java') || return 1
  "$JAVAC" -encoding UTF-8 -nowarn -cp "$W/fakecls:$W/basecls:$W/stubcls:$ANDROID_JAR" -d "$W/realcls" \
    "$R/src/com/autogo/GomokuActivity.java" "$R/src/com/autogo/GomokuView.java" "$R/src/com/autogo/GomokuReviewActivity.java" \
    "$R/src/com/autogo/WinrateChartView.java" "$R/src/com/autogo/WinrateBarView.java" "$R/src/com/autogo/UiTheme.java" \
    "$R/src/com/autogo/MenuActivity.java" "$R/src/com/autogo/Changelog.java" \
    "$R/src/com/autogo/Watermark.java" "$R/src/com/autogo/WinEgg.java" "$R/src/com/autogo/BoardArt.java" \
    "$R/src/com/autogo/XiangqiView.java" "$R/src/com/autogo/XiangqiActivity.java" \
    "$R/src/com/autogo/XiangqiReviewActivity.java" \
    "$R/src/com/autogo/JunqiView.java" "$R/src/com/autogo/JunqiActivity.java" \
    "$R/src/com/autogo/Game2048View.java" "$R/src/com/autogo/Game2048Activity.java" || return 1
  "$JAVAC" -encoding UTF-8 -nowarn -cp "$W/fakecls:$W/basecls:$W/realcls:$W/stubcls:$ANDROID_JAR" -d "$W/tcls" \
    "$R/tests/ui/Driver95.java" || return 1
  LANG=C.UTF-8 LC_ALL=C.UTF-8 "$JAVA" -Xmx256m -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 \
    -cp "$W/fakecls:$W/basecls:$W/realcls:$W/stubcls:$W/tcls" com.autogo.Driver95 2>/dev/null | tail -1
}

mkdir -p "$W" || exit 1
trap 'rm -rf "$W"' EXIT   # 异常出口也清掉临时目录（第 75 轮审计 提示-2）
cp -a "$ROOT/src" "$W/src" && cp -a "$ROOT/tests" "$W/tests" || { echo "错误：拷贝失败" >&2; exit 1; }

echo "== 基线 =="
BASE=$(build_and_run "$W" "$W/base") || { echo "错误：基线跑不起来" >&2; exit 1; }
echo "   $BASE"
# 期望值只写一处（第 72 轮审计：写两处时只改一处会打出「应为 68/2，实际是 68/2」的自相矛盾日志）
EXP_BASE="PASS 121 / FAIL 0"
EXP_MUT="PASS 119 / FAIL 2"
case "$BASE" in
  *"$EXP_BASE") ;;
  *) echo "⚠ 基线首跑是「$BASE」（期望 $EXP_BASE）—— Driver95 有约 4% 的低频抖动（第 102 轮审计实测），重跑一次确认" >&2
     BASE=$(build_and_run "$W" "$W/base") || { echo "错误：基线重跑跑不起来" >&2; exit 1; }
     echo "   基线重跑：$BASE" >&2
     case "$BASE" in
       *"$EXP_BASE") echo "✅ 基线重跑通过（首跑为抖动）" >&2;;
       *) echo "❌ 基线两次都不是 $EXP_BASE —— 说明测试或源码已变，请先更新温度计" >&2; exit 1;;
     esac;;
esac

echo "== 变异体：两道守卫次序对调 =="
python3 - "$W/src/com/autogo/GomokuActivity.java" <<'PY' || { RC=$?; [ "$RC" = 2 ] && { echo "❌ 守卫锚点变换失败（内层 rc=2）—— 源码已变，请先更新温度计" >&2; exit 2; }; exit 1; }
import sys
p = sys.argv[1]; s = open(p, encoding="utf-8").read()
ai = '''        if (aiThinking) {
            // 电脑正在想：先让它下完。执白新局等第一手时也是空盘，但这时该说的是这一句
            // （次序不能反 —— 第 65 轮审计 G6 点出的老缺口）。
            toast("等电脑下完这一手再复盘。");
            return;
        }
'''
empty = '''        if (board.moves.isEmpty()) {
            // 空盘上没有可复盘的手顺（新局后立刻点「复盘」）：直接提示，别开一个空页面
            toast("还没有落子，没法复盘。");
            return;
        }
'''
if s.count(ai) != 1 or s.count(empty) != 1:
    print("❌ 守卫锚点找不到（源码改了？请更新温度计）", file=sys.stderr); sys.exit(2)
s = s.replace(ai, "@@AI@@").replace(empty, "@@EMPTY@@").replace("@@AI@@", empty).replace("@@EMPTY@@", ai)
open(p, "w", encoding="utf-8").write(s)
PY
MUT=$(build_and_run "$W" "$W/mut") || { echo "错误：变异体跑不起来" >&2; exit 1; }
echo "   $MUT"
case "$MUT" in
  *"$EXP_MUT") echo "✅ 温度计正常：次序对调被抓住（$EXP_MUT），说明这两条断言今天有牙。"; rm -rf "$W"; exit 0;;
  *)
     echo "⚠ 首跑变异体是「$MUT」（期望 $EXP_MUT）—— 可能是 Driver95 的低频抖动（第 102 轮审计实测约 4%），重跑一次确认" >&2
     MUT=$(build_and_run "$W" "$W/mut") || { echo "错误：变异体重跑跑不起来" >&2; exit 1; }
     echo "   变异体重跑：$MUT" >&2
     case "$MUT" in
       *"$EXP_MUT") echo "✅ 温度计正常（首跑为抖动，重跑 $EXP_MUT）"; rm -rf "$W"; exit 0;;
     esac
     echo "❌ 温度计失灵：两次都不是 $EXP_MUT，最后是「$MUT」—— 断言可能已经失去区分力。" >&2; rm -rf "$W"; exit 1;;
esac
