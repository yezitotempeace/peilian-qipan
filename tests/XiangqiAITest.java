import com.autogo.XiangqiAI;
import com.autogo.XiangqiBoard;

/** 象棋 AI 的 JVM 断言（不依赖 Android）。 */
public class XiangqiAITest {
    static int checks = 0, fails = 0;

    static void eq(String what, Object exp, Object act) {
        checks++;
        if (!String.valueOf(exp).equals(String.valueOf(act))) {
            fails++;
            System.out.println("FAIL " + what + "（期望 " + exp + "，实际 " + act + "）");
        }
    }

    static void ok(String what, boolean c) {
        checks++;
        if (!c) {
            fails++;
            System.out.println("FAIL " + what);
        }
    }

    static XiangqiBoard board() {
        XiangqiBoard b = new XiangqiBoard(true);
        b.put(9, 4, XiangqiBoard.RED, XiangqiBoard.K);
        b.put(0, 4, XiangqiBoard.BLACK, XiangqiBoard.K);
        b.put(5, 4, XiangqiBoard.RED, XiangqiBoard.P);
        return b;
    }

    static boolean sameMove(int[] a, int[] b) {
        return a != null && b != null && a.length == 4 && b.length == 4
                && a[0] == b[0] && a[1] == b[1] && a[2] == b[2] && a[3] == b[3];
    }

    public static void main(String[] args) {
        XiangqiAI ai = new XiangqiAI();
        eq("默认难度是普通", XiangqiAI.NORMAL, ai.level());
        ai.setLevel(9);
        eq("越界难度回到普通", XiangqiAI.NORMAL, ai.level());
        ai.setLevel(-3);
        eq("负数难度回到普通", XiangqiAI.NORMAL, ai.level());

        // 吃子：黑车白送，红方 AI 必须吃掉
        XiangqiBoard b = board();
        b.put(4, 0, XiangqiBoard.BLACK, XiangqiBoard.R);
        b.put(4, 4, XiangqiBoard.RED, XiangqiBoard.R);
        ai.setLevel(XiangqiAI.NORMAL);
        int[] mv = ai.genmove(b);
        ok("能吃到白送的车", mv != null && mv[2] == 4 && mv[3] == 0);

        // 确定性 + 只读
        XiangqiBoard b2 = board();
        b2.put(4, 0, XiangqiBoard.BLACK, XiangqiBoard.R);
        b2.put(4, 4, XiangqiBoard.RED, XiangqiBoard.R);
        int[] mv2 = ai.genmove(b2);
        ok("两次给同一手（确定性）", sameMove(mv, mv2));
        eq("AI 不动棋盘：手数", 0, b2.moveCount());
        eq("AI 不动棋盘：行棋方", XiangqiBoard.RED, b2.sideToMove());
        ok("AI 不动棋盘：白送的车还在", b2.at(4, 0) != 0);

        // 一步杀：红双车，黑将困在底线
        XiangqiBoard mate = new XiangqiBoard(true);
        mate.put(0, 4, XiangqiBoard.BLACK, XiangqiBoard.K);
        mate.put(0, 0, XiangqiBoard.RED, XiangqiBoard.R);
        mate.put(1, 0, XiangqiBoard.RED, XiangqiBoard.R);
        mate.put(9, 4, XiangqiBoard.RED, XiangqiBoard.K);
        mate.put(5, 4, XiangqiBoard.RED, XiangqiBoard.P);
        int[] m = ai.genmove(mate);
        ok("一步杀时有招可下", m != null);
        if (m != null) {
            ok("下的就是杀棋（走完黑方被将死）", mate.play(m[0], m[1], m[2], m[3]) && mate.isCheckmate(XiangqiBoard.BLACK));
        }

        // 评估：子力多的一方为正、两边互为相反数
        XiangqiBoard ev = board();
        ev.put(4, 0, XiangqiBoard.RED, XiangqiBoard.R);
        int redScore = ai.evaluate(ev, XiangqiBoard.RED);
        int blackScore = ai.evaluate(ev, XiangqiBoard.BLACK);
        ok("多一个红车的红方评估为正", redScore > 0);
        eq("两边评估互为相反数", -redScore, blackScore);

        // 终局 / 无棋可走
        XiangqiBoard over = new XiangqiBoard(true);
        over.put(0, 4, XiangqiBoard.BLACK, XiangqiBoard.K);
        over.put(0, 0, XiangqiBoard.RED, XiangqiBoard.R);
        over.put(1, 0, XiangqiBoard.RED, XiangqiBoard.R);
        over.put(9, 4, XiangqiBoard.RED, XiangqiBoard.K);
        over.put(5, 4, XiangqiBoard.RED, XiangqiBoard.P);
        over.play(1, 0, 1, 1);   // 交给黑方（已被将死）
        ok("终局时不再出手", ai.genmove(over) == null);
        ok("空棋局返回 null", ai.genmove(new XiangqiBoard(true)) == null);

        // 三档难度都能给出一手合法着法
        for (int lv = XiangqiAI.FAST; lv <= XiangqiAI.STRONG; lv++) {
            ai.setLevel(lv);
            XiangqiBoard normal = new XiangqiBoard();
            int[] g = ai.genmove(normal);
            boolean legal = g != null && normal.isLegal(g[0], g[1], g[2], g[3]);
            ok("难度 " + lv + " 给的是合法着法", legal);
        }

        System.out.println("通过 " + (checks - fails) + " 项，失败 " + fails + " 项");
        if (fails > 0) {
            System.exit(1);
        }
    }
}
