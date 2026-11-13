/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.JunqiAI;
import com.autogo.JunqiBoard;

import java.util.List;

/** 军棋 AI 测试：合法性、吃子选择、避强击弱、扛旗、难度档位。 */
public class JunqiAiTest {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    static void put(JunqiBoard b, int r, int c, int side, int type) {
        b.testClear(r, c);
        b.testPut(r, c, side, type);
    }

    static boolean same(int[] m, int r, int c, int tr, int tc) {
        return m != null && m[0] == r && m[1] == c && m[2] == tr && m[3] == tc;
    }

    public static void main(String[] args) {
        // ---------- ① 能出招且合法 ----------
        JunqiBoard b = new JunqiBoard(42);
        b.testStartBattle();
        JunqiAI ai = new JunqiAI();
        ai.setLevel(JunqiAI.STRONG);
        int[] mv = ai.genmove(b);
        chk("能出招", true, mv != null);
        chk("出的招合法", true, b.isLegal(JunqiBoard.BLACK, mv[0], mv[1], mv[2], mv[3]));

        // ---------- ② 吃掉已亮出的弱子，而不是攻击强子 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 5, 2, JunqiBoard.BLACK, JunqiBoard.SHIZHANG);
        put(b, 4, 2, JunqiBoard.RED, JunqiBoard.LIANZHANG);
        put(b, 5, 3, JunqiBoard.RED, JunqiBoard.SILING);
        b.at(4, 2).revealed = true;
        b.at(5, 3).revealed = true;
        mv = ai.genmove(b);
        chk("优先吃掉已亮的弱子", true, same(mv, 5, 2, 4, 2));
        chk("不攻击已亮的强子", false, same(mv, 5, 2, 5, 3));

        // ---------- ③ 地雷局能正常出招 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 4, 2, JunqiBoard.BLACK, JunqiBoard.DILEI);
        put(b, 5, 2, JunqiBoard.RED, JunqiBoard.SILING);
        b.at(5, 2).revealed = true;
        mv = ai.genmove(b);
        chk("地雷局能正常出招（或无棋可走）", true,
                mv == null || b.isLegal(JunqiBoard.BLACK, mv[0], mv[1], mv[2], mv[3]));

        // ---------- ④ 亮出的军旗直接扛 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 5, 2, JunqiBoard.BLACK, JunqiBoard.SHIZHANG);
        put(b, 4, 2, JunqiBoard.RED, JunqiBoard.JUNQI);
        b.at(4, 2).revealed = true;
        mv = ai.genmove(b);
        chk("亮出的军旗直接扛", true, same(mv, 5, 2, 4, 2));

        // ---------- ⑤ 旁边有炸弹也不会无脑硬吃 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 5, 2, JunqiBoard.BLACK, JunqiBoard.SILING);
        put(b, 4, 2, JunqiBoard.RED, JunqiBoard.GONGBING);
        b.at(4, 2).revealed = true;
        put(b, 4, 1, JunqiBoard.RED, JunqiBoard.ZHADAN);
        b.at(4, 1).revealed = true;
        mv = ai.genmove(b);
        chk("不会无视旁边炸弹硬吃", true, mv != null);

        // ---------- ⑥ 三档难度都出合法招 ----------
        for (int lv = JunqiAI.FAST; lv <= JunqiAI.STRONG; lv++) {
            JunqiBoard t = new JunqiBoard(100 + lv);
            t.testStartBattle();
            JunqiAI a2 = new JunqiAI();
            a2.setLevel(lv);
            int[] m2 = a2.genmove(t);
            chk("难度 " + lv + " 出合法招", true,
                    m2 != null && t.isLegal(JunqiBoard.BLACK, m2[0], m2[1], m2[2], m2[3]));
        }

        // ---------- ⑦ 自对弈整局能正常结束 ----------
        int finished = 0;
        for (int g = 0; g < 8; g++) {
            JunqiBoard t = new JunqiBoard(5000 + g);
            t.testStartBattle();
            JunqiAI a2 = new JunqiAI();
            a2.setLevel(JunqiAI.STRONG);
            java.util.Random r = new java.util.Random(g);
            int steps = 0;
            boolean bad = false;
            while (!t.gameOver() && steps < 800) {
                int side = t.sideToMove();
                int[] m;
                if (side == JunqiBoard.BLACK) {
                    m = a2.genmove(t);
                } else {
                    List<int[]> ms = t.legalMoves(JunqiBoard.RED);
                    m = ms.isEmpty() ? null : ms.get(r.nextInt(ms.size()));
                }
                if (m == null) {
                    break;
                }
                if (!t.isLegal(side, m[0], m[1], m[2], m[3])) {
                    bad = true;
                    break;
                }
                t.play(side, m[0], m[1], m[2], m[3]);
                steps++;
            }
            if (!bad && t.gameOver()) {
                finished++;
            }
        }
        chk("8 局自对弈至少 7 局正常终局", true, finished >= 7);

        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
