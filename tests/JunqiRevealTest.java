import com.autogo.JunqiBoard;
import com.autogo.JunqiReveal;

public class JunqiRevealTest {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    static boolean[] none() {
        return new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
    }

    public static void main(String[] args) {
        // 1. 交战 ⇒ 目标格暴露
        JunqiBoard b1 = new JunqiBoard(true);
        b1.put(5, 0, JunqiBoard.RED, JunqiBoard.COMMANDER);
        b1.put(4, 0, JunqiBoard.BLACK, JunqiBoard.PLATOON);
        boolean[] r1 = none();
        JunqiReveal.resolve(r1, b1, new int[]{JunqiBoard.idx(5, 0), JunqiBoard.idx(4, 0)});
        chk("交战过的格子暴露", true, r1[JunqiBoard.idx(4, 0)]);
        chk("走掉的格子复位", false, r1[JunqiBoard.idx(5, 0)]);

        // 2. 走到空格 ⇒ 不暴露
        JunqiBoard b2 = new JunqiBoard(true);
        b2.put(5, 0, JunqiBoard.RED, JunqiBoard.COMMANDER);
        boolean[] r2 = none();
        JunqiReveal.resolve(r2, b2, new int[]{JunqiBoard.idx(5, 0), JunqiBoard.idx(4, 0)});
        chk("走到空格不暴露", false, r2[JunqiBoard.idx(4, 0)]);

        // 3. 明棋：有子的格子全暴露
        JunqiBoard b3 = new JunqiBoard();
        b3.setupStandard(new java.util.Random(3));
        boolean[] r3 = none();
        JunqiReveal.revealAll(r3, b3);
        int n = 0;
        for (boolean x : r3) {
            if (x) {
                n++;
            }
        }
        chk("明棋暴露 50 个格子", 50, n);
        boolean allMatch = true;
        for (int r = 0; r < JunqiBoard.ROWS; r++) {
            for (int c = 0; c < JunqiBoard.COLS; c++) {
                boolean has = b3.ownerAt(r, c) != JunqiBoard.EMPTY;
                if (r3[JunqiBoard.idx(r, c)] != has) {
                    allMatch = false;
                }
            }
        }
        chk("明棋：每格「有子 == 已暴露」", true, allMatch);

        // 4. 越界/空数组不炸
        JunqiReveal.resolve(null, b1, new int[]{0, 1});
        JunqiReveal.resolve(none(), null, null);
        chk("空参数不炸", true, true);

        System.out.println("军棋暴露规则测试：通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
