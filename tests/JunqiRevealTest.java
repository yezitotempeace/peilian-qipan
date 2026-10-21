/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

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

        // 5. 战斗兵种 / 攻方 / 胜方可读（供界面把「被吃的对方兵种」翻出来）
        JunqiBoard b5 = new JunqiBoard(true);
        b5.put(5, 0, JunqiBoard.RED, JunqiBoard.COMMANDER);
        b5.put(4, 0, JunqiBoard.BLACK, JunqiBoard.PLATOON);
        boolean m5 = b5.play(5, 0, 4, 0);
        chk("红司令吃黑排长能落子", true, m5);
        chk("被吃对方兵种=排长", "排长", JunqiBoard.typeName(b5.lastDefType()));
        chk("进攻方=红", JunqiBoard.RED, b5.lastAttacker());
        chk("胜方=红", JunqiBoard.RED, b5.lastResult());

        JunqiBoard b6 = new JunqiBoard(true);
        b6.put(5, 0, JunqiBoard.RED, JunqiBoard.ENGINEER);
        b6.put(4, 0, JunqiBoard.BLACK, JunqiBoard.MINE);
        b6.play(5, 0, 4, 0);
        chk("工兵挖雷：胜方=红", JunqiBoard.RED, b6.lastResult());
        chk("工兵挖雷：对方兵种=地雷", "地雷", JunqiBoard.typeName(b6.lastDefType()));

        JunqiBoard b7 = new JunqiBoard(true);
        b7.put(5, 0, JunqiBoard.RED, JunqiBoard.COMMANDER);
        b7.put(4, 0, JunqiBoard.BLACK, JunqiBoard.COMMANDER);
        b7.play(5, 0, 4, 0);
        chk("同级同归于尽：胜方=0", 0, b7.lastResult());
        chk("同级同归于尽：对方兵种=司令", "司令", JunqiBoard.typeName(b7.lastDefType()));

        JunqiBoard b8 = new JunqiBoard(true);
        b8.put(5, 0, JunqiBoard.RED, JunqiBoard.COMMANDER);
        b8.play(5, 0, 4, 0);
        chk("走到空格：对方兵种=空（不揭示）", JunqiBoard.EMPTY, b8.lastDefType());

        System.out.println("军棋暴露规则测试：通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
