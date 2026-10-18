import com.autogo.JunqiAI;
import com.autogo.JunqiBoard;

public class JunqiAITest {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    static boolean sameMove(int[] a, int[] b) {
        return a != null && b != null && a[0] == b[0] && a[1] == b[1] && a[2] == b[2] && a[3] == b[3];
    }

    static boolean has(java.util.List<int[]> ms, int fr, int fc, int tr, int tc) {
        int from = JunqiBoard.idx(fr, fc);
        int to = JunqiBoard.idx(tr, tc);
        for (int[] m : ms) {
            if (m[0] == from && m[1] == to) {
                return true;
            }
        }
        return false;
    }

    /** 红：司令 (5,0)、排长 (5,1)；黑：一枚暗子 (2,0)、工兵 (2,1)。 */
    static JunqiBoard base(int blackType) {
        JunqiBoard b = new JunqiBoard(true);
        b.put(5, 0, JunqiBoard.RED, JunqiBoard.COMMANDER);
        b.put(5, 1, JunqiBoard.RED, JunqiBoard.PLATOON);
        b.put(2, 0, JunqiBoard.BLACK, blackType);
        b.put(2, 1, JunqiBoard.BLACK, JunqiBoard.ENGINEER);
        return b;
    }

    public static void main(String[] args) {
        JunqiAI ai = new JunqiAI();
        chk("默认难度是普通", JunqiAI.NORMAL, ai.level());
        ai.setLevel(9);
        chk("越界难度回到普通", JunqiAI.NORMAL, ai.level());
        ai.setLevel(JunqiAI.FAST);
        chk("可以设成快", JunqiAI.FAST, ai.level());

        // A2 出的手必须合法
        JunqiBoard b = base(JunqiBoard.ARMY);
        int[] mv = new JunqiAI().genmove(b, JunqiBoard.RED, null);
        chk("有棋可走时给出一手", true, mv != null);
        chk("给出的手合法", true, mv != null && has(b.legalMoves(JunqiBoard.RED), mv[0], mv[1], mv[2], mv[3]));

        // A3 同一局面两次同样的手
        int[] again = new JunqiAI().genmove(b, JunqiBoard.RED, null);
        chk("同局面两份 AI 出同一手", true,
                mv != null && again != null && mv[0] == again[0] && mv[1] == again[1]
                        && mv[2] == again[2] && mv[3] == again[3]);

        // A4 不偷看：把未暴露的黑子换成别的兵种，走法不变
        int[] strong = new JunqiAI().genmove(base(JunqiBoard.COMMANDER), JunqiBoard.RED, null);
        int[] weak = new JunqiAI().genmove(base(JunqiBoard.ENGINEER), JunqiBoard.RED, null);
        chk("暗子换成司令或工兵，AI 走法一样（不偷看）", true,
                strong != null && weak != null && strong[0] == weak[0] && strong[1] == weak[1]
                        && strong[2] == weak[2] && strong[3] == weak[3]);

        // A5 masked：只盖未暴露的敌子
        JunqiBoard b2 = base(JunqiBoard.ARMY);
        boolean[] rev = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
        rev[JunqiBoard.idx(2, 1)] = true; // 只暴露 (2,1) 的工兵
        JunqiBoard view = JunqiAI.masked(b2, JunqiBoard.RED, rev);
        chk("自己的司令没被盖", JunqiBoard.COMMANDER, view.at(5, 0));
        chk("未暴露的黑子被盖成排长", JunqiAI.UNKNOWN, view.at(2, 0));
        chk("已暴露的黑子保留兵种", JunqiBoard.ENGINEER, view.at(2, 1));
        chk("原棋盘没被改动", JunqiBoard.ARMY, b2.at(2, 0));

        // A6 终局不出手
        JunqiBoard over = new JunqiBoard(true);
        over.put(1, 1, JunqiBoard.RED, JunqiBoard.COMMANDER);
        over.put(0, 1, JunqiBoard.BLACK, JunqiBoard.FLAG);
        over.play(1, 1, 0, 1);
        chk("夺旗后终局", true, over.gameOver());
        chk("终局不出手", null, new JunqiAI().genmove(over, JunqiBoard.RED, null));

        // A7 评估符号
        chk("红方子力占优时评估为正", true, JunqiAI.evaluate(base(JunqiBoard.PLATOON), JunqiBoard.RED) > 0);
        chk("黑方视角相反为负", true, JunqiAI.evaluate(base(JunqiBoard.PLATOON), JunqiBoard.BLACK) < 0);

        // A8 三档难度必须真不一样：普通档贪吃「送到嘴边的排长」，强档会算到被司令反吃
        JunqiBoard trap = new JunqiBoard(true);
        trap.put(6, 1, JunqiBoard.RED, JunqiBoard.COMPANY);    // 我方连长(200)
        trap.put(7, 3, JunqiBoard.RED, JunqiBoard.ENGINEER);   // 我方工兵(120)：放在非铁路格，给一个安全走法
        trap.put(6, 2, JunqiBoard.BLACK, JunqiBoard.PLATOON);  // 黑排长(150)：看起来白吃
        trap.put(5, 2, JunqiBoard.BLACK, JunqiBoard.COMMANDER);// 黑司令(900)：吃完就能反吃
        boolean[] allRevealed = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
        java.util.Arrays.fill(allRevealed, true);
        int[] normalMv = new JunqiAI(JunqiAI.NORMAL).genmove(trap, JunqiBoard.RED, allRevealed);
        chk("普通档会贪吃送到嘴边的子", true,
                normalMv != null && normalMv[0] == 6 && normalMv[1] == 1 && normalMv[2] == 6 && normalMv[3] == 2);
        int[] strongMv = new JunqiAI(JunqiAI.STRONG).genmove(trap, JunqiBoard.RED, allRevealed);
        chk("强档不吃会被反吃的子", false,
                strongMv != null && strongMv[0] == 6 && strongMv[1] == 1 && strongMv[2] == 6 && strongMv[3] == 2);
        chk("强档给的仍是合法手", true,
                strongMv != null && has(trap.legalMoves(JunqiBoard.RED), strongMv[0], strongMv[1], strongMv[2], strongMv[3]));

        // A9 快档有真随机：同种子可复现；不同种子会走出不同的手（不是换个名字的普通档）
        JunqiBoard openBoard = new JunqiBoard(true);
        openBoard.put(6, 2, JunqiBoard.RED, JunqiBoard.COMPANY);
        openBoard.put(0, 0, JunqiBoard.BLACK, JunqiBoard.FLAG);
        JunqiAI fastA = new JunqiAI(JunqiAI.FAST);
        fastA.setSeed(42);
        JunqiAI fastB = new JunqiAI(JunqiAI.FAST);
        fastB.setSeed(42);
        int[] fast1 = fastA.genmove(openBoard, JunqiBoard.RED, null);
        int[] fast2 = fastB.genmove(openBoard, JunqiBoard.RED, null);
        chk("快档同一随机种子可复现", true, sameMove(fast1, fast2));
        java.util.Set<String> fastMoves = new java.util.HashSet<String>();
        for (int seed = 1; seed <= 20; seed++) {
            JunqiAI f = new JunqiAI(JunqiAI.FAST);
            f.setSeed(seed);
            int[] m = f.genmove(openBoard, JunqiBoard.RED, null);
            fastMoves.add(m[0] + "," + m[1] + "," + m[2] + "," + m[3]);
        }
        chk("快档不同种子会走出不同的手", true, fastMoves.size() > 1);

        // A10 强档也不偷看：未暴露的黑子换成司令/工兵，强档走法不变
        int[] strongA = new JunqiAI(JunqiAI.STRONG).genmove(base(JunqiBoard.COMMANDER), JunqiBoard.RED, null);
        int[] strongB = new JunqiAI(JunqiAI.STRONG).genmove(base(JunqiBoard.ENGINEER), JunqiBoard.RED, null);
        chk("强档：暗子换兵种走法不变（不偷看）", true, sameMove(strongA, strongB));

        System.out.println("军棋 AI 测试：通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
