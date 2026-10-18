import com.autogo.GomokuBoard;
import com.autogo.GomokuEngine;
import com.autogo.LocalGomokuAI;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * LocalGomokuAI 的行为测试（JVM 直跑，不需要 Android）。
 *
 * 关注三件事：
 *  1) 契约：难度档、只读棋局、终局不出手、同一局面同手（确定性）；
 *  2) 棋力底线：能成五就成五、对方成五必堵、对方活三必应、少见的四四/长连点不落；
 *  3) 极端路径：三档难度自对弈整局不抛异常、不出非法手、不出 -1。
 */
public class GomokuAITest {

    private static int passed = 0;
    private static final List<String> failures = new ArrayList<>();

    // ---------- 工具 ----------

    private static void check(boolean cond, String what) {
        if (cond) {
            passed++;
        } else {
            failures.add(what);
            System.out.println("  FAIL: " + what);
        }
    }

    private static void checkEq(Object actual, Object expected, String what) {
        boolean ok = (actual == null) ? expected == null : actual.equals(expected);
        if (!ok) {
            failures.add(what + "（期望 " + expected + "，实际 " + actual + "）");
            System.out.println("  FAIL: " + what + "（期望 " + expected + "，实际 " + actual + "）");
        } else {
            passed++;
        }
    }

    /** 直接摆子，跳过规则校验（构造测试局面）。pts 形如 "7,3 7,4"（row,col）。 */
    private static void place(GomokuBoard b, int color, String pts) {
        for (String tok : pts.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            String[] rc = tok.split(",");
            b.grid[b.idx(Integer.parseInt(rc[0].trim()), Integer.parseInt(rc[1].trim()))] = color;
        }
    }

    /** 逐手 play（会更新 gameOver / winner / 手顺）；pts 形如 "7,3 7,4"。 */
    private static GomokuBoard playAll(int color, String pts) {
        GomokuBoard b = new GomokuBoard(15, false);
        for (String tok : pts.trim().split("\\s+")) {
            if (tok.isEmpty()) continue;
            String[] rc = tok.split(",");
            b.play(color, b.idx(Integer.parseInt(rc[0].trim()), Integer.parseInt(rc[1].trim())));
        }
        return b;
    }

    private static GomokuBoard board(String blackPts, String whitePts) {
        GomokuBoard b = new GomokuBoard(15, false);
        if (blackPts != null) place(b, GomokuBoard.BLACK, blackPts);
        if (whitePts != null) place(b, GomokuBoard.WHITE, whitePts);
        return b;
    }

    /** 落子后这一串是不是成五（用于验证 AI 的落点确实是「成五点」）。 */
    private static boolean makesFive(GomokuBoard b, int color, int point) {
        int[] g = b.grid.clone();
        g[point] = color;
        return GomokuBoard.runLengthOn(g, b.size, color, b.row(point), b.col(point)) >= 5;
    }

    /** 对方落在这点上会不会成五（用于验证 AI 的落点确实是「堵点」）。 */
    private static boolean blocksFive(GomokuBoard b, int color, int point) {
        return makesFive(b, GomokuBoard.opponent(color), point);
    }

    // ---------- A. 契约 ----------

    private static void testContract() {
        LocalGomokuAI ai = new LocalGomokuAI();
        checkEq(ai.level(), LocalGomokuAI.NORMAL, "默认难度是普通");
        ai.setLevel(9);
        checkEq(ai.level(), LocalGomokuAI.NORMAL, "越界难度（9）回到普通");
        ai.setLevel(-3);
        checkEq(ai.level(), LocalGomokuAI.NORMAL, "越界难度（-3）回到普通");
        ai.setLevel(LocalGomokuAI.STRONG);
        checkEq(ai.level(), LocalGomokuAI.STRONG, "设成强档就一直是强档");
        check(!ai.name().isEmpty(), "引擎名不是空串");

        GomokuBoard empty = new GomokuBoard(15, false);
        checkEq(ai.genmove(empty, GomokuBoard.BLACK), empty.idx(7, 7), "空盘第一手走天元");
        checkEq(ai.genmove(empty, 7), -1, "颜色不合法（7）返回 -1");

        GomokuBoard over = playAll(GomokuBoard.BLACK, "7,3 7,4 7,5 7,6 7,7");
        check(over.gameOver, "（前置）五连局面已终局");
        checkEq(ai.genmove(over, GomokuBoard.WHITE), -1, "终局后不再出手（-1）");

        GomokuBoard full = new GomokuBoard(5, false);
        for (int i = 0; i < full.grid.length; i++) full.grid[i] = GomokuBoard.BLACK;
        checkEq(ai.genmove(full, GomokuBoard.WHITE), -1, "下满的棋盘返回 -1");

        // 只读：genmove 不许动传入的棋局。
        GomokuBoard mid = board("7,7 8,8 6,6", "7,8 8,9");
        int[] snapshot = mid.grid.clone();
        int movesBefore = mid.moves.size();
        ai.setLevel(LocalGomokuAI.NORMAL);
        int mv = ai.genmove(mid, GomokuBoard.BLACK);
        check(Arrays.equals(snapshot, mid.grid), "genmove 不改动传入棋盘");
        checkEq(mid.moves.size(), movesBefore, "genmove 不往手顺里加东西");
        check(mid.isLegal(GomokuBoard.BLACK, mv), "genmove 返回的是合法点");
        check(mid.winner == GomokuBoard.EMPTY && !mid.gameOver, "genmove 不改 win / gameOver 状态");

        // 确定性：同一局面、同一档，两次一样；两个实例也一样。
        LocalGomokuAI ai2 = new LocalGomokuAI();
        ai2.setLevel(LocalGomokuAI.NORMAL);
        checkEq(ai.genmove(mid, GomokuBoard.BLACK), mv, "同一实例两次同手");
        checkEq(ai2.genmove(mid, GomokuBoard.BLACK), mv, "两个实例同手（无随机数）");
    }

    // ---------- B. 能不能赢 / 该不该堵 ----------

    private static void testWinNow() {
        LocalGomokuAI ai = new LocalGomokuAI();
        // 黑四连、两头空：AI 执黑必须马上成五。
        GomokuBoard b = board("7,3 7,4 7,5 7,6", null);
        int mv = ai.genmove(b, GomokuBoard.BLACK);
        check(mv >= 0, "能成五时有落子");
        check(makesFive(b, GomokuBoard.BLACK, mv), "能成五时就成五（落点确实成五）");
        check(b.isLegal(GomokuBoard.BLACK, mv), "成五的落点是合法点");
    }

    private static void testWinBeatsBlock() {
        LocalGomokuAI ai = new LocalGomokuAI();
        // 双方都四连：自己有把握赢，就别先去堵。
        GomokuBoard b = board("7,3 7,4 7,5 7,6", "9,3 9,4 9,5 9,6");
        int mv = ai.genmove(b, GomokuBoard.BLACK);
        check(makesFive(b, GomokuBoard.BLACK, mv),
                "能赢就先赢（而不是先堵对方的四）");
    }

    private static void testBlockFive() {
        LocalGomokuAI ai = new LocalGomokuAI();
        GomokuBoard b = board(null, "7,3 7,4 7,5 7,6");
        int mv = ai.genmove(b, GomokuBoard.BLACK);
        check(blocksFive(b, GomokuBoard.BLACK, mv), "对方四连时必须堵在成五点");
    }

    private static void testBlockOpenThree() {
        LocalGomokuAI ai = new LocalGomokuAI();
        ai.setLevel(LocalGomokuAI.NORMAL);
        // 白活三（两头空），黑必须先应，否则白下一手活四必杀。
        GomokuBoard b = board("0,0", "7,5 7,6 7,7");
        int mv = ai.genmove(b, GomokuBoard.BLACK);
        check(mv == b.idx(7, 4) || mv == b.idx(7, 8),
                "对方活三时堵在两头（实际 " + b.row(mv) + "," + b.col(mv) + "）");
    }

    private static void testAttackOpenThree() {
        LocalGomokuAI ai = new LocalGomokuAI();
        ai.setLevel(LocalGomokuAI.NORMAL);
        // 自己活三、对方只有零散子：应该去做活四 / 冲四（而不是乱走）。
        GomokuBoard b = board("7,5 7,6 7,7", "0,0 0,1");
        int mv = ai.genmove(b, GomokuBoard.BLACK);
        int[] g = b.grid.clone();
        g[mv] = GomokuBoard.BLACK;
        int run = GomokuBoard.runLengthOn(g, b.size, GomokuBoard.BLACK, b.row(mv), b.col(mv));
        check(run >= 4, "自己活三时做出四（实际连成 " + run + "）");
    }

    // ---------- C. 禁手 ----------

    private static void testForbidden() {
        LocalGomokuAI ai = new LocalGomokuAI();
        // 三三禁手：落 (7,7) 同时造出横竖两个活三。
        GomokuBoard b = new GomokuBoard(15, true);
        place(b, GomokuBoard.BLACK, "7,5 7,6 5,7 6,7");
        check(!b.isLegal(GomokuBoard.BLACK, b.idx(7, 7)), "（前置）(7,7) 对黑是三三禁手点");
        int mv = ai.genmove(b, GomokuBoard.BLACK);
        check(mv != b.idx(7, 7), "黑棋禁手点不落（三三点）");
        check(b.isLegal(GomokuBoard.BLACK, mv), "黑棋落点合法（过禁手校验）");

        // 黑四连但左端被白堵住、右边隔一格又有黑子：唯一「能连成 5+」的点 (7,7)
        // 会连出 6 个（长连禁手）⇒ 成五这条捷径必须让位给禁手校验。
        GomokuBoard o = new GomokuBoard(15, true);
        place(o, GomokuBoard.BLACK, "7,3 7,4 7,5 7,6 7,8");
        place(o, GomokuBoard.WHITE, "7,2");
        check(!o.isLegal(GomokuBoard.BLACK, o.idx(7, 7)), "（前置）(7,7) 对黑是长连禁手点");
        int om = ai.genmove(o, GomokuBoard.BLACK);
        check(om != o.idx(7, 7), "黑棋不落长连点");
        check(o.isLegal(GomokuBoard.BLACK, om), "黑棋不为成五而破禁手（落点合法）");

        // 同样的形状换成白棋：白没有禁手 ⇒ 白照样能落 (7,7)。
        GomokuBoard w = new GomokuBoard(15, true);
        place(w, GomokuBoard.WHITE, "7,5 7,6 5,7 6,7");
        check(w.isLegal(GomokuBoard.WHITE, w.idx(7, 7)), "（前置）白在 (7,7) 合法");
        LocalGomokuAI ai0 = new LocalGomokuAI();
        ai0.setLevel(LocalGomokuAI.FAST);
        int wm = ai0.genmove(w, GomokuBoard.WHITE);
        check(w.isLegal(GomokuBoard.WHITE, wm), "白棋落点合法（白无禁手）");
    }

    // ---------- D. 评估 ----------

    private static void testEvaluate() {
        LocalGomokuAI ai = new LocalGomokuAI();
        GomokuBoard b = board("7,3 7,4 7,5", "9,3 9,4 9,5");
        GomokuBoard b2 = board("7,3 7,4 7,5", "9,3 9,4 9,5");
        check(Math.abs(ai.evaluate(b, GomokuBoard.BLACK) - ai.evaluate(b2, GomokuBoard.BLACK)) < 1e-9,
                "同形局面评估一致");
        check(Math.abs(ai.evaluate(b, GomokuBoard.BLACK) + ai.evaluate(b, GomokuBoard.WHITE)) < 1e-9,
                "两边视角互为相反数");

        GomokuBoard lead = board("7,3 7,4 7,5", "0,0");
        check(ai.evaluate(lead, GomokuBoard.BLACK) > 0, "领先的一方评估为正");
        check(ai.evaluate(lead, GomokuBoard.WHITE) < 0, "落后的一方评估为负");

        GomokuBoard even = board("7,3 7,4 7,5", "7,11 7,10 7,9");
        check(Math.abs(ai.evaluate(even, GomokuBoard.BLACK)) < 1e-9, "镜像局面评估为 0");

        GomokuBoard over = playAll(GomokuBoard.BLACK, "7,3 7,4 7,5 7,6 7,7");
        checkEq(ai.evaluate(over, GomokuBoard.BLACK), LocalGomokuAI.WIN, "终局赢家评估 = +WIN");
        checkEq(ai.evaluate(over, GomokuBoard.WHITE), -LocalGomokuAI.WIN, "终局输家评估 = -WIN");
        checkEq(ai.evaluate(over, 9), 0d, "颜色不合法（9）评估 0");
    }

    // ---------- E. 自对弈 ----------

    /** 一局自对弈；返回 {非法手数, -1 次数, 落子数}。任何异常都会冒出来让测试失败。 */
    private static int[] selfPlay(int level, boolean forbid, int size, int maxMoves) {
        GomokuBoard b = new GomokuBoard(size, forbid);
        LocalGomokuAI ai = new LocalGomokuAI();
        ai.setLevel(level);
        int illegal = 0, minusOne = 0, moves = 0;
        int color = GomokuBoard.BLACK;
        while (!b.gameOver && moves < maxMoves) {
            int p = ai.genmove(b, color);
            if (p < 0) {
                minusOne++;
                break;
            }
            if (!b.isLegal(color, p)) {
                illegal++;
                break;
            }
            b.play(color, p);
            moves++;
            color = GomokuBoard.opponent(color);
        }
        return new int[]{illegal, minusOne, moves};
    }

    private static void testSelfPlay() {
        int[] fast = selfPlay(LocalGomokuAI.FAST, false, 15, 225);
        checkEq(fast[0], 0, "快档自对弈：没有非法手");
        checkEq(fast[1], 0, "快档自对弈：没有无处可下的情况");
        check(fast[2] > 20, "快档自对弈：真的下起来了（" + fast[2] + " 手）");

        int[] forbid = selfPlay(LocalGomokuAI.FAST, true, 15, 225);
        checkEq(forbid[0], 0, "快档 + 黑棋禁手自对弈：没有非法手（含禁手）");
        checkEq(forbid[1], 0, "快档 + 黑棋禁手自对弈：没有无处可下的情况");

        int[] normal = selfPlay(LocalGomokuAI.NORMAL, false, 9, 40);
        checkEq(normal[0], 0, "普通档 9 路短局：没有非法手");
        checkEq(normal[1], 0, "普通档 9 路短局：没有无处可下的情况");

        // 强档只验「能给出合法手」，整局自对弈太慢。
        LocalGomokuAI strong = new LocalGomokuAI();
        strong.setLevel(LocalGomokuAI.STRONG);
        GomokuBoard mid = board("7,7 6,8 8,6", "7,8 8,7");
        int mv = strong.genmove(mid, GomokuBoard.BLACK);
        check(mid.isLegal(GomokuBoard.BLACK, mv), "强档在残局也能给出合法手");
    }

    public static void main(String[] args) {
        testContract();
        testWinNow();
        testWinBeatsBlock();
        testBlockFive();
        testBlockOpenThree();
        testAttackOpenThree();
        testForbidden();
        testEvaluate();
        testSelfPlay();

        System.out.println();
        System.out.println("通过 " + passed + " 项，失败 " + failures.size() + " 项");
        if (!failures.isEmpty()) {
            System.out.println("失败清单：");
            for (String f : failures) System.out.println("  - " + f);
            System.exit(1);
        }
    }
}
