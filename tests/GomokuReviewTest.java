import com.autogo.GomokuBoard;
import com.autogo.GomokuEngine;
import com.autogo.GomokuReview;
import com.autogo.LocalGomokuAI;

import java.util.ArrayList;
import java.util.List;

/**
 * 五子棋复盘（GomokuReview）的 JVM 断言。纯逻辑，不需要 Android。
 *
 * 口径（与 GomokuReview 的注释一致）：
 *   - wrToMove[i]：下第 i 手之前「该走的人」的胜率
 *   - loss[i]：第 i 手本身的亏损（loss[0] 恒为 NaN）
 *   - bestMove[i]：下第 i 手之前电脑推荐的点
 */
public class GomokuReviewTest {

    // 难度档：0 = 快，1 = 普通，2 = 强（见 GomokuEngine 的注释）
    private static final int FAST = 0;
    private static final int NORMAL = 1;
    private static final int STRONG = 2;

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    private static void check(boolean cond, String what) {
        if (cond) {
            passed++;
        } else {
            failures.add(what);
            System.out.println("  FAIL: " + what);
        }
    }

    private static void checkEq(Object actual, Object expected, String what) {
        boolean ok = (actual == null) ? (expected == null) : actual.equals(expected);
        if (!ok) {
            System.out.println("  FAIL: " + what + "（期望 " + expected + "，实际 " + actual + "）");
            failures.add(what);
        } else {
            passed++;
        }
    }

    private static void checkNear(double actual, double expected, double tol, String what) {
        boolean ok = Math.abs(actual - expected) <= tol || (Double.isNaN(actual) && Double.isNaN(expected));
        if (!ok) {
            System.out.println("  FAIL: " + what + "（期望 " + expected + "±" + tol + "，实际 " + actual + "）");
            failures.add(what);
        } else {
            passed++;
        }
    }

    // ---------- 夹具 ----------

    /** 按脚本给分的假引擎：第 i 个值 = 「已经下了 i 手」时该走一方的评估分。 */
    private static final class Script implements GomokuEngine {
        final double[] scores;
        final int[] recs;
        final List<Integer> levelCalls = new ArrayList<>();
        final List<Boolean> flagSeen = new ArrayList<>();
        int level = NORMAL;

        Script(double[] scores, int[] recs) {
            this.scores = scores;
            this.recs = recs;
        }

        @Override public String name() { return "脚本引擎"; }

        @Override public void setLevel(int l) { level = l; levelCalls.add(l); }

        @Override public int level() { return level; }

        @Override public double evaluate(GomokuBoard board, int color) {
            flagSeen.add(board.forbiddenForBlack);
            int i = board.moves.size();
            return (i < scores.length) ? scores[i] : 0;
        }

        @Override public int genmove(GomokuBoard board, int color) {
            flagSeen.add(board.forbiddenForBlack);
            int i = board.moves.size();
            return (i < recs.length) ? recs[i] : -1;
        }
    }

    private static List<int[]> moves(int... spec) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i + 1 < spec.length; i += 2) out.add(new int[]{spec[i], spec[i + 1]});
        return out;
    }

    /** 一局 9 手：黑在 14 行连五（黑胜），白在 0 行连四（未成五）。 */
    private static List<int[]> nineMoveBlackWin(int size) {
        return moves(
                GomokuBoard.BLACK, 14 * size + 0, GomokuBoard.WHITE, 0 * size + 0,
                GomokuBoard.BLACK, 14 * size + 1, GomokuBoard.WHITE, 0 * size + 1,
                GomokuBoard.BLACK, 14 * size + 2, GomokuBoard.WHITE, 0 * size + 2,
                GomokuBoard.BLACK, 14 * size + 3, GomokuBoard.WHITE, 0 * size + 3,
                GomokuBoard.BLACK, 14 * size + 4);
    }

    private static double wr(double score) {
        return GomokuReview.toWinrate(score);
    }

    // ---------- toWinrate ----------

    private static void testToWinrate() {
        check(Double.isNaN(GomokuReview.toWinrate(Double.NaN)), "NaN 分 -> NaN 胜率");
        checkEq(GomokuReview.toWinrate(0), 0.5, "均势 -> 50%");
        checkEq(GomokuReview.toWinrate(LocalGomokuAI.WIN / 2), 1.0, "必胜分 -> 100%");
        checkEq(GomokuReview.toWinrate(-LocalGomokuAI.WIN / 2), 0.0, "必败分 -> 0%");
        checkEq(GomokuReview.toWinrate(LocalGomokuAI.WIN), 1.0, "超过必胜分 -> 100%");
        check(GomokuReview.toWinrate(3000) > 0.5, "正分 -> 高于 50%");
        check(GomokuReview.toWinrate(-3000) < 0.5, "负分 -> 低于 50%");
        check(GomokuReview.toWinrate(3000) < GomokuReview.toWinrate(9000), "分越高胜率越高（单调）");
        check(GomokuReview.toWinrate(0.5) > 0.5 && GomokuReview.toWinrate(0.5) < 0.51, "小正分 -> 略高于 50%");
    }

    // ---------- 空 / 单手的边角 ----------

    private static void testEmpty() {
        Script s = new Script(new double[]{123}, new int[]{0});
        GomokuReview.Review r = GomokuReview.analyze(new ArrayList<int[]>(), 15, false, s, FAST);
        checkEq(r.moveCount(), 0, "空手顺：0 手");
        checkEq(r.wrToMove.length, 1, "空手顺：数组长度 1");
        checkEq(r.bestMove.length, 1, "空手顺：bestMove 长度 1");
        checkEq(r.loss.length, 0, "空手顺：loss 长度 0");
        checkEq(r.firstColor, GomokuBoard.BLACK, "空手顺：先手按黑算");
        checkEq(GomokuReview.verdict(r), "没有可复盘的手数。", "空手顺：结论措辞");
        check(r.topLosses(3).isEmpty(), "空手顺：失误排行空");
    }

    private static void testNullMoves() {
        Script s = new Script(new double[]{}, new int[]{});
        GomokuReview.Review r = GomokuReview.analyze(null, 15, false, s, FAST);
        checkEq(r.moveCount(), 0, "手顺为 null：当空局处理");
        check(r.moves != null, "手顺为 null：moves 不是 null");
    }

    // ---------- 逐手数组的形状与算法 ----------

    private static void testArrays() {
        int size = 15;
        List<int[]> mv = nineMoveBlackWin(size);
        // 第 0..9 手的「该走一方」评估分：偶数手黑走给正分、奇数手白走给负分 ⇒ 黑一直略优
        double[] scores = {3000, -3000, 3000, -3000, 3000, -3000, 3000, -3000, 3000, 0};
        int[] recs = {size * 7 + 7, size * 7 + 7, size * 7 + 7, size * 7 + 7, size * 7 + 7,
                size * 7 + 7, size * 7 + 7, size * 7 + 7, size * 7 + 7, -1};
        Script s = new Script(scores, recs);
        GomokuReview.Review r = GomokuReview.analyze(mv, size, false, s, FAST);

        checkEq(r.moveCount(), 9, "9 手棋：手数");
        checkEq(r.wrToMove.length, 10, "9 手棋：wrToMove 长度 10");
        checkEq(r.loss.length, 9, "9 手棋：loss 长度 9（一手一格）");
        checkEq(r.bestMove.length, 10, "9 手棋：bestMove 长度 10");
        checkEq(r.firstColor, GomokuBoard.BLACK, "9 手棋：先手黑");
        checkEq(r.winner, GomokuBoard.BLACK, "9 手棋：黑胜");
        check(r.endReason.contains("五连"), "9 手棋：终局原因写明五连");

        checkNear(r.wrToMove[0], wr(3000), 1e-12, "wrToMove[0] = 脚本分换算");
        checkNear(r.wrToMove[1], wr(-3000), 1e-12, "wrToMove[1] = 脚本分换算（轮到白）");
        checkNear(r.wrToMove[2], wr(3000), 1e-12, "wrToMove[2] = 脚本分换算（又轮到黑）");

        // loss[i] = max(0, 走之前该走的胜率 - 走之后该走的胜率)，loss[0] 对应第 1 手
        for (int i = 0; i < 9; i++) {
            double before = r.wrToMove[i];
            double after = 1.0 - r.wrToMove[i + 1];
            double expect = Math.max(0.0, before - after);
            checkNear(r.loss[i], expect, 1e-12, "loss[" + i + "] = 第 " + (i + 1) + " 手 走前-走后");
        }
        // 脚本的评估在两边之间是自洽的（黑 +3000 与 白 -3000 说的是同一件事）⇒ 不该记成亏损
        checkNear(r.loss[0], 0.0, 1e-15, "两边评估自洽时第 1 手不亏");

        // 走子方走完之后反而变好（亏损为负）时要截断到 0，不能记成负数
        List<int[]> mv2 = moves(GomokuBoard.BLACK, 0 * size + 0, GomokuBoard.WHITE, 0 * size + 1);
        Script s2 = new Script(new double[]{-3000, 3000, 0}, new int[]{0, 0, -1});
        GomokuReview.Review r2 = GomokuReview.analyze(mv2, size, false, s2, FAST);
        check(r2.wrToMove[1] > r2.wrToMove[0] - 1e-12,
                "被让先的一方走完反而变好（脚本分从 -3000 到 +3000 的对侧）");
        checkNear(r2.loss[0], 0.0, 1e-15, "走子方变好时亏损截断到 0（不记负数）");

        checkEq(r.bestMove[0], GomokuBoard.pointToName(size, size * 7 + 7), "bestMove[0] = 脚本推荐点");
        checkEq(r.bestPoint[0], size * 7 + 7, "bestPoint[0] = 脚本推荐点");
        checkEq(r.bestMove[9], "", "终局后没有推荐点（空串）");
        checkEq(r.bestPoint[9], -1, "终局后没有推荐点（-1）");
        checkEq(r.bestMove[1], GomokuBoard.pointToName(size, size * 7 + 7), "bestMove[1] 也来自脚本");

        // 视角换算：黑视角第 1 格 = wr[0]；第 2 格（轮到白）= 1 - wr[1]
        double[] forBlack = r.winratesFor(GomokuBoard.BLACK);
        checkNear(forBlack[0], r.wrToMove[0], 1e-12, "黑视角第 0 格 = wrToMove[0]");
        checkNear(forBlack[1], 1.0 - r.wrToMove[1], 1e-12, "黑视角第 1 格 = 1 - wrToMove[1]");
        double[] forWhite = r.winratesFor(GomokuBoard.WHITE);
        checkNear(forWhite[0], 1.0 - r.wrToMove[0], 1e-12, "白视角第 0 格 = 1 - wrToMove[0]");
        checkNear(forWhite[1], r.wrToMove[1], 1e-12, "白视角第 1 格 = wrToMove[1]");
        checkNear(forBlack[0] + forWhite[0], 1.0, 1e-12, "两边视角互补");

        // 结果数组与传入的手顺一致
        checkEq(r.moves.get(0)[0], GomokuBoard.BLACK, "复盘手顺第 1 手颜色");
        checkEq(r.moves.get(8)[1], 14 * size + 4, "复盘手顺最后一手点");
    }

    private static void testMoveListNotShared() {
        int size = 15;
        List<int[]> mv = nineMoveBlackWin(size);
        Script s = new Script(new double[20], new int[20]);
        GomokuReview.Review r = GomokuReview.analyze(mv, size, false, s, FAST);
        mv.get(0)[0] = GomokuBoard.WHITE; // 外部改手顺
        checkEq(r.moves.get(0)[0], GomokuBoard.BLACK, "复盘内部手顺是拷贝（外部改动不影响）");
    }

    // ---------- 结论措辞 ----------

    private static void testVerdictBlunder() {
        int size = 15;
        // 只有两手：黑先走（脚本说黑有 10000 分的优势），黑走完白方视角仍 +10000 ⇒ 黑亏掉一大块
        List<int[]> mv = moves(GomokuBoard.BLACK, 0 * size + 0, GomokuBoard.WHITE, 0 * size + 1);
        double[] scores = {10000, 10000, 0};
        int[] recs = {size * 7 + 7, size * 7 + 7, -1};
        Script s = new Script(scores, recs);
        GomokuReview.Review r = GomokuReview.analyze(mv, size, false, s, FAST);
        String v = GomokuReview.verdict(r);
        check(v.contains("黑第 1 手"), "结论写明是谁的第几手：" + v);
        check(v.contains(GomokuBoard.pointToName(size, 0)), "结论写明实际下在哪：" + v);
        check(v.contains("亏 32%"), "结论写明亏了多少（32%）：" + v);
        check(v.contains("（应下 " + GomokuBoard.pointToName(size, size * 7 + 7) + "）"), "结论写明应下哪：" + v);
        check(!v.contains("全程没有明显失误"), "有失误时不写「全程没有明显失误」");
        checkEq(r.topLosses(1).get(0), 0, "失误排行第一 = 第 1 手（0 基下标 0）");
    }

    private static void testVerdictNoBlunder() {
        int size = 15;
        double[] scores = {3000, -3000, 3000, -3000, 3000, -3000, 3000, -3000, 3000, 0};
        int[] recs = new int[10];
        Script s = new Script(scores, recs);
        GomokuReview.Review r = GomokuReview.analyze(nineMoveBlackWin(size), size, false, s, FAST);
        String v = GomokuReview.verdict(r);
        check(v.contains("终局：黑胜"), "9 手黑胜：结论以「终局：黑胜」开头：" + v);
        check(v.contains("全程没有明显失误"), "亏损都很小：写「全程没有明显失误」：" + v);
        check(v.contains("从头领先到尾"), "黑每一手都 >= 50%：写「从头领先到尾」：" + v);
    }

    private static void testVerdictTookLeadLater() {
        int size = 15;
        // 黑前两手落后（负分），第 3 手起一直领先
        double[] scores = {-3000, 3000, 3000, -3000, 3000, -3000, 3000, -3000, 3000, 0};
        int[] recs = new int[10];
        Script s = new Script(scores, recs);
        GomokuReview.Review r = GomokuReview.analyze(nineMoveBlackWin(size), size, false, s, FAST);
        String v = GomokuReview.verdict(r);
        check(v.contains("从第 2 手起再未落后"), "黑第 2 手之后才领先：写明从第 2 手起：" + v);
    }

    private static void testVerdictLastMoveDecided() {
        int size = 15;
        // 黑全程落后，只有终局那一格（= 1.0）才领先 ⇒ 写「胜负到最后才见分晓」
        double[] scores = {-3000, 3000, -3000, 3000, -3000, 3000, -3000, 3000, -3000, 0};
        int[] recs = new int[10];
        Script s = new Script(scores, recs);
        GomokuReview.Review r = GomokuReview.analyze(nineMoveBlackWin(size), size, false, s, FAST);
        String v = GomokuReview.verdict(r);
        check(v.contains("胜负到最后才见分晓"), "只有终局那格领先：写「胜负到最后才见分晓」：" + v);
    }

    private static void testVerdictDraw() {
        // 9x9 下满：黑落在 (r + 2c) % 4 ∈ {0,1} 的点上，白落在其余点（任何方向都连不出 5 颗）
        int size = 9;
        List<Integer> blackPts = new ArrayList<>();
        List<Integer> whitePts = new ArrayList<>();
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int v = (r + 2 * c) % 4;
                ((v == 0 || v == 1) ? blackPts : whitePts).add(r * size + c);
            }
        }
        checkEq(blackPts.size(), size * size / 2 + 1, "下满夹具：黑子比白子多一个");
        checkEq(whitePts.size(), size * size / 2, "下满夹具：白子数");
        List<int[]> mv = new ArrayList<>();
        for (int i = 0; i < whitePts.size(); i++) {
            mv.add(new int[]{GomokuBoard.BLACK, blackPts.get(i)});
            mv.add(new int[]{GomokuBoard.WHITE, whitePts.get(i)});
        }
        mv.add(new int[]{GomokuBoard.BLACK, blackPts.get(blackPts.size() - 1)});
        Script s = new Script(new double[26], new int[26]);
        GomokuReview.Review r = GomokuReview.analyze(mv, size, false, s, FAST);
        String v = GomokuReview.verdict(r);
        check(v.contains("和棋"), "下满盘：结论写和棋：" + v);
        checkNear(r.wrToMove[r.moveCount()], 0.5, 1e-12, "下满盘：终局那一格胜率 = 50%");
        check(!v.contains("从头领先到尾"), "和棋不写谁领先到尾：" + v);
    }

    // ---------- 非法手顺 / 难度还原 ----------

    private static void testIllegalMove() {
        int size = 15;
        List<int[]> mv = moves(GomokuBoard.BLACK, 0, GomokuBoard.WHITE, 0); // 白下在已占点
        Script s = new Script(new double[4], new int[4]);
        boolean threw = false;
        String msg = "";
        try {
            GomokuReview.analyze(mv, size, false, s, FAST);
        } catch (RuntimeException e) {
            threw = true;
            msg = String.valueOf(e.getMessage());
        }
        check(threw, "非法手顺（重复落子）：抛异常");
        check(msg.contains("非法落子"), "异常信息写明非法落子：" + msg);
        check(msg.contains("第 2 手"), "异常信息写明第几手：" + msg);
    }

    /**
     * 历史里有「开禁手就非法」的手（当时开关关着走出来的）：开禁手复盘必须照样能算，不重判旧手。
     * 夹具：黑 7,4 7,5 7,6 / 4,7 5,7 6,7，最后一手 7,7 同时造出两个四（四四）。
     */
    private static void testHistoryWithForbiddenHand() {
        int size = 15;
        List<int[]> mv = moves(
                GomokuBoard.BLACK, 7 * size + 4, GomokuBoard.WHITE, 0 * size + 0,
                GomokuBoard.BLACK, 7 * size + 5, GomokuBoard.WHITE, 0 * size + 2,
                GomokuBoard.BLACK, 7 * size + 6, GomokuBoard.WHITE, 0 * size + 4,
                GomokuBoard.BLACK, 4 * size + 7, GomokuBoard.WHITE, 0 * size + 6,
                GomokuBoard.BLACK, 5 * size + 7, GomokuBoard.WHITE, 0 * size + 8,
                GomokuBoard.BLACK, 6 * size + 7, GomokuBoard.WHITE, 0 * size + 10,
                GomokuBoard.BLACK, 7 * size + 7);
        // 夹具自检：自由规则重放成功；末手在这个局面对黑是禁手（四四）——replay 本身不重判历史，
        // 所以这里单独把开关拧开问一次「这一手算不算禁手」。
        GomokuBoard free = GomokuBoard.replay(mv, size, false);
        checkEq(free.moves.size(), 13, "夹具：自由规则重放 13 手成功");
        GomokuBoard b12 = GomokuBoard.replay(new ArrayList<int[]>(mv.subList(0, 12)), size, false);
        b12.forbiddenForBlack = true;
        check(b12.isForbidden(GomokuBoard.BLACK, 7, 7), "夹具：末手（四四）对黑是禁手");
        checkEq(b12.forbiddenKind(7, 7), "四四", "夹具：禁手种类是四四");
        // 开禁手复盘：不抛、手数/数组长度对、结论开着禁手
        Script s = new Script(new double[20], new int[20]);
        GomokuReview.Review r = null;
        String err = "";
        try {
            r = GomokuReview.analyze(mv, size, true, s, FAST);
        } catch (RuntimeException e) {
            err = String.valueOf(e.getMessage());
        }
        check(r != null, "开禁手复盘：不抛异常（" + err + "）");
        if (r != null) {
            checkEq(r.moves.size(), 13, "开禁手复盘：手顺 13 手");
            checkEq(r.wrToMove.length, 14, "开禁手复盘：胜率数组 n+1");
            checkEq(r.loss.length, 13, "开禁手复盘：亏损数组 n");
            checkEq(r.forbiddenForBlack, true, "开禁手复盘：结论里禁手是开着的");
            boolean allOn = !s.flagSeen.isEmpty();
            for (Boolean b : s.flagSeen) allOn &= b;
            check(allOn, "开禁手复盘：引擎每次评估时禁手都是开着的（" + s.flagSeen.size() + " 次）");
        }
        // 关禁手复盘：同一份手顺也照算，结论里禁手是关的
        Script s2 = new Script(new double[20], new int[20]);
        GomokuReview.Review r2 = GomokuReview.analyze(mv, size, false, s2, FAST);
        checkEq(r2.forbiddenForBlack, false, "关禁手复盘：结论里禁手是关着的");
        boolean allOff = !s2.flagSeen.isEmpty();
        for (Boolean b : s2.flagSeen) allOff &= !b;
        check(allOff, "关禁手复盘：引擎每次评估时禁手都是关着的（" + s2.flagSeen.size() + " 次）");
    }

    private static void testNullEngine() {
        boolean threw = false;
        try {
            GomokuReview.analyze(new ArrayList<int[]>(), 15, false, null, FAST);
        } catch (IllegalArgumentException e) {
            threw = true;
        }
        check(threw, "引擎为 null：抛 IllegalArgumentException");
    }

    private static void testLevelRestored() {
        int size = 15;
        Script s = new Script(new double[20], new int[20]);
        s.level = STRONG;
        GomokuReview.analyze(nineMoveBlackWin(size), size, false, s, FAST);
        checkEq(s.level(), STRONG, "复盘结束后难度还原成调用前的档");
        checkEq(s.levelCalls.get(0), FAST, "复盘开始时先把难度切到复盘档");
        checkEq(s.levelCalls.get(s.levelCalls.size() - 1), STRONG, "复盘结束时切回原档");
    }

    // ---------- 和内置 AI 联动（真引擎、自对弈整局）----------

    private static void testSelfPlayWithBuiltinAI() {
        int size = 15;
        LocalGomokuAI ai = new LocalGomokuAI();
        GomokuBoard board = new GomokuBoard(size, false);
        int color = GomokuBoard.BLACK;
        for (int i = 0; i < 60 && !board.gameOver; i++) {
            int p = ai.genmove(board, color);
            if (p < 0) break;
            if (!board.isLegal(color, p)) {
                check(false, "自对弈第 " + (i + 1) + " 手非法：" + GomokuBoard.pointToName(size, p));
                return;
            }
            board.play(color, p);
            color = GomokuBoard.opponent(color);
        }
        check(board.moves.size() >= 10, "自对弈至少下了 10 手");

        GomokuReview.Review a = GomokuReview.analyze(board.moves, size, false, ai, FAST);
        GomokuReview.Review b = GomokuReview.analyze(board.moves, size, false, ai, FAST);
        int n = board.moves.size();
        checkEq(a.wrToMove.length, n + 1, "自对弈复盘：数组长度 = 手数 + 1");
        checkEq(a.bestMove.length, n + 1, "自对弈复盘：bestMove 长度");
        checkEq(a.loss.length, n, "自对弈复盘：loss 长度 = 手数");
        boolean wrOk = true, lossOk = true, bestOk = true, sameOk = true;
        for (int i = 0; i <= n; i++) {
            double w = a.wrToMove[i];
            if (!Double.isNaN(w) && (w < 0 || w > 1)) wrOk = false;
            if (i < n && (Double.isNaN(a.loss[i]) || a.loss[i] < 0)) lossOk = false;
            if (i < n) {
                String bm = a.bestMove[i];
                if (bm == null || bm.isEmpty()) bestOk = false;
                else {
                    int pp = a.bestPoint[i];
                    if (pp < 0 || pp >= size * size) bestOk = false;
                }
            }
            if (Double.compare(a.wrToMove[i], b.wrToMove[i]) != 0) sameOk = false;
        }
        check(wrOk, "自对弈复盘：每格胜率都在 0~1 或 NaN");
        check(lossOk, "自对弈复盘：每手亏损都 >= 0");
        check(bestOk, "自对弈复盘：每手（除终局）都有合法推荐点");
        check(sameOk, "自对弈复盘：同局面重算结果一致（无随机数）");
        check(!GomokuReview.verdict(a).isEmpty(), "自对弈复盘：结论非空");
        check(GomokuReview.verdict(a).contains("终局"), "自对弈复盘：结论含「终局」：" + GomokuReview.verdict(a));
    }

    private static void testEvaluateWiring() {
        // 终局局面：胜方 +WIN、败方 -WIN（顺带验证 evaluate 的接线）
        int size = 9;
        LocalGomokuAI ai = new LocalGomokuAI();
        GomokuBoard b = new GomokuBoard(size, false);
        for (int i = 0; i < 5; i++) {
            b.play(GomokuBoard.BLACK, 0 * size + i);
            if (b.gameOver) break;
            if (i < 4) b.play(GomokuBoard.WHITE, 8 * size + i);
        }
        check(b.gameOver, "五连夹具：棋局已结束");
        checkEq(ai.evaluate(b, GomokuBoard.BLACK), LocalGomokuAI.WIN, "胜方评估 = +WIN");
        checkEq(ai.evaluate(b, GomokuBoard.WHITE), -LocalGomokuAI.WIN, "败方评估 = -WIN");
        checkEq(GomokuReview.toWinrate(ai.evaluate(b, GomokuBoard.BLACK)), 1.0, "胜方胜率 100%");
    }

    public static void main(String[] args) {
        System.out.println("五子棋复盘（GomokuReview）");
        testToWinrate();
        testEmpty();
        testNullMoves();
        testArrays();
        testMoveListNotShared();
        testVerdictBlunder();
        testVerdictNoBlunder();
        testVerdictTookLeadLater();
        testVerdictLastMoveDecided();
        testVerdictDraw();
        testIllegalMove();
        testNullEngine();
        testHistoryWithForbiddenHand();
        testLevelRestored();
        testSelfPlayWithBuiltinAI();
        testEvaluateWiring();
        System.out.println("通过 " + passed + " 项，失败 " + failures.size() + " 项");
        if (!failures.isEmpty()) System.exit(1);
    }
}
