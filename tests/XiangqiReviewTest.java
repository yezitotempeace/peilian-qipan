import com.autogo.XiangqiAI;
import com.autogo.XiangqiBoard;
import com.autogo.XiangqiReview;

import java.util.ArrayList;
import java.util.List;

/** 象棋复盘的 JVM 断言（默认包，照项目其它测试的写法）。 */
public class XiangqiReviewTest {

    static int pass = 0;
    static int fail = 0;

    static void chk(String what, Object exp, Object act) {
        if (exp == null ? act == null : exp.equals(act)) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + exp + "，实际 " + act);
        }
    }

    static void chkNear(String what, double exp, double act) {
        if (Math.abs(exp - act) < 1e-9) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + exp + "，实际 " + act);
        }
    }

    static int[] mv(int fr, int fc, int tr, int tc) {
        return new int[]{fr, fc, tr, tc};
    }

    static boolean hasMove(List<int[]> list, int[] m) {
        for (int[] c : list) {
            if (c[0] == m[0] && c[1] == m[1] && c[2] == m[2] && c[3] == m[3]) {
                return true;
            }
        }
        return false;
    }

    /** 标准开局走四步：双方各提一步车。 */
    static List<int[]> opening4() {
        List<int[]> ms = new ArrayList<int[]>();
        ms.add(mv(9, 0, 8, 0));
        ms.add(mv(0, 0, 1, 0));
        ms.add(mv(8, 0, 7, 0));
        ms.add(mv(1, 0, 2, 0));
        return ms;
    }

    /** 一步杀：红车 (2,4) 将死黑将 (0,4)，(1,3)/(1,5) 两车封住九宫两翼。 */
    static XiangqiBoard mateBoard() {
        XiangqiBoard b = new XiangqiBoard(true);
        b.put(0, 4, XiangqiBoard.BLACK, XiangqiBoard.K);
        b.put(9, 4, XiangqiBoard.RED, XiangqiBoard.K);
        b.put(1, 3, XiangqiBoard.RED, XiangqiBoard.R);
        b.put(1, 5, XiangqiBoard.RED, XiangqiBoard.R);
        b.put(3, 4, XiangqiBoard.RED, XiangqiBoard.R);
        b.put(9, 0, XiangqiBoard.RED, XiangqiBoard.A);
        b.put(9, 8, XiangqiBoard.RED, XiangqiBoard.A);
        return b;
    }

    static void testWinrate() {
        chkNear("A1 0 分是五五开", 0.5, XiangqiReview.toWinrate(0));
        chk("A2 大优封顶 1.0", 1.0, XiangqiReview.toWinrate(1000000000));
        chk("A3 大劣封底 0.0", 0.0, XiangqiReview.toWinrate(-1000000000));
        double lo = XiangqiReview.toWinrate(300);
        double hi = XiangqiReview.toWinrate(900);
        chk("A4 分数越高胜率越高", Boolean.TRUE, hi > lo);
        chk("A5 反对称（+x 与 -x 相加为 1）", Boolean.TRUE,
                Math.abs(XiangqiReview.toWinrate(450) + XiangqiReview.toWinrate(-450) - 1.0) < 1e-9);
        chk("A6 K=600 时一个车（900 分）胜率约 0.95", Boolean.TRUE,
                XiangqiReview.toWinrate(900) > 0.93 && XiangqiReview.toWinrate(900) < 0.98);
    }

    static void testStructure() {
        XiangqiAI ai = new XiangqiAI();
        XiangqiReview.Review r = XiangqiReview.analyze(opening4(), ai, XiangqiAI.FAST);
        chk("B1 子数", 4, r.moveCount());
        chk("B2 wrToMove 长 n+1", 5, r.wrToMove.length);
        chk("B3 loss 长 n", 4, r.loss.length);
        chk("B4 bestMove 长 n+1", 5, r.bestMove.length);
        chk("B5 bestPoint 长 n+1", 5, r.bestPoint.length);
        boolean inRange = true;
        for (double w : r.wrToMove) {
            if (w < 0.0 || w > 1.0) {
                inRange = false;
            }
        }
        chk("B6 胜率都在 0..1", Boolean.TRUE, inRange);
        boolean nonNeg = true;
        for (double l : r.loss) {
            if (l < 0.0) {
                nonNeg = false;
            }
        }
        chk("B7 亏损都不为负", Boolean.TRUE, nonNeg);
        boolean formula = true;
        for (int i = 0; i < 4; i++) {
            double want = Math.max(0.0, r.wrToMove[i] - (1.0 - r.wrToMove[i + 1]));
            if (Math.abs(want - r.loss[i]) > 1e-9) {
                formula = false;
            }
        }
        chk("B8 亏损 = 上一手后亏掉的胜率", Boolean.TRUE, formula);
        boolean legal = true;
        XiangqiBoard b = new XiangqiBoard();
        for (int i = 0; i < 4; i++) {
            if (r.bestMove[i] == null || !hasMove(b.legalMoves(b.sideToMove()), r.bestMove[i])) {
                legal = false;
            }
            int[] m = opening4().get(i);
            b.play(m[0], m[1], m[2], m[3]);
        }
        chk("B9 每一步的推荐着法都合法", Boolean.TRUE, legal);
        chk("B10 终局位没有推荐着法", null, r.bestMove[4]);
        chk("B11 终局位推荐点是 -1", -1, r.bestPoint[4]);
        chk("B12 没有胜家", 0, r.winner);
        chk("B13 未终局说明", "对局进行中", r.endReason);
        String v = XiangqiReview.verdict(r);
        chk("B14 未完局的结论写「局面：」", Boolean.TRUE, v.startsWith("局面：对局进行中"));
        chk("B15 结论含走势", Boolean.TRUE, v.contains("｜"));
    }

    static void testMate() {
        XiangqiAI ai = new XiangqiAI();
        List<int[]> ms = new ArrayList<int[]>();
        ms.add(mv(3, 4, 2, 4));
        XiangqiReview.Review r = XiangqiReview.analyze(ms, mateBoard(), ai, XiangqiAI.NORMAL);
        chk("C1 杀棋后红胜", XiangqiBoard.RED, r.winner);
        chkNear("C2 终局位行棋方（黑）胜率 0", 0.0, r.wrToMove[1]);
        chk("C3 终局说明非空", Boolean.TRUE, r.endReason != null && r.endReason.length() > 0);
        String v = XiangqiReview.verdict(r);
        chk("C4 结论写明红胜", Boolean.TRUE, v.contains("红胜"));
        chk("C5 赢棋那一手不算大亏", Boolean.TRUE, r.loss[0] < XiangqiReview.BLUNDER_MIN);
        chk("C6 一手杀这种干净棋结论是「没有明显失误」", Boolean.TRUE, v.contains("全程没有明显失误"));
    }

    static void testIllegal() {
        XiangqiAI ai = new XiangqiAI();
        List<int[]> ms = opening4();
        ms.set(1, mv(0, 0, 5, 0)); // 车不能隔着兵飞过去
        String msg = "";
        try {
            XiangqiReview.analyze(ms, ai, XiangqiAI.FAST);
        } catch (IllegalArgumentException e) {
            msg = e.getMessage() == null ? "" : e.getMessage();
        }
        chk("D1 非法手顺抛异常", Boolean.TRUE, msg.contains("非法着法"));
        chk("D2 异常里写明第几手", Boolean.TRUE, msg.contains("第 2 手"));
    }

    static void testLevelRestore() {
        XiangqiAI ai = new XiangqiAI();
        ai.setLevel(XiangqiAI.STRONG);
        XiangqiReview.analyze(opening4(), ai, XiangqiAI.FAST);
        chk("E1 复盘后难度还原", XiangqiAI.STRONG, ai.level());
    }

    static void testDeterminism() {
        XiangqiAI a1 = new XiangqiAI();
        XiangqiAI a2 = new XiangqiAI();
        XiangqiReview.Review r1 = XiangqiReview.analyze(opening4(), a1, XiangqiAI.NORMAL);
        XiangqiReview.Review r2 = XiangqiReview.analyze(opening4(), a2, XiangqiAI.NORMAL);
        boolean same = r1.loss.length == r2.loss.length;
        for (int i = 0; same && i < r1.loss.length; i++) {
            if (Math.abs(r1.loss[i] - r2.loss[i]) > 1e-12) {
                same = false;
            }
        }
        chk("F1 同一盘棋两次复盘结果一致", Boolean.TRUE, same);
    }

    static void testEmpty() {
        XiangqiAI ai = new XiangqiAI();
        XiangqiReview.Review r = XiangqiReview.analyze(new ArrayList<int[]>(), ai, XiangqiAI.FAST);
        chk("G1 空棋谱 wr 长 1", 1, r.wrToMove.length);
        chk("G2 空棋谱 loss 长 0", 0, r.loss.length);
        chk("G3 空棋谱结论", Boolean.TRUE, XiangqiReview.verdict(r).contains("空棋谱"));
    }

    public static void main(String[] args) {
        testWinrate();
        testStructure();
        testMate();
        testIllegal();
        testLevelRestore();
        testDeterminism();
        testEmpty();
        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
