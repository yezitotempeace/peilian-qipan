/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import com.autogo.Board;
import com.autogo.WinEgg;

/** 隐藏彩蛋的开关：只在最强档给（快档、普通档都不给）；只看「人赢了」。 */
public class WinEggTest {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    public static void main(String[] args) {
        chk("彩蛋号码", "707380438", WinEgg.CODE);
        chk("快档不显示", false, WinEgg.eggAllowed(0, 2));
        chk("普通档不显示", false, WinEgg.eggAllowed(1, 2));
        chk("强档显示", true, WinEgg.eggAllowed(2, 2));
        chk("越界档位不显示", false, WinEgg.eggAllowed(9, 2));

        chk("围棋：人执黑 B+R 算赢", true, WinEgg.goHumanWon("B+R", Board.BLACK));
        chk("围棋：人执白 B+R 算输", false, WinEgg.goHumanWon("B+R", Board.WHITE));
        chk("围棋：黑胜文案", true, WinEgg.goHumanWon("黑胜 3.5 目", Board.BLACK));
        chk("围棋：空结果不算赢", false, WinEgg.goHumanWon("", Board.BLACK));
        chk("围棋：和棋文案不算赢", false, WinEgg.goHumanWon("和棋", Board.BLACK));
        chk("人执黑认输，赢家是白", "W+R", WinEgg.resignResult(Board.BLACK));
        chk("人执白认输，赢家是黑", "B+R", WinEgg.resignResult(Board.WHITE));
        chk("认输结果不判人赢", false, WinEgg.goHumanWon(WinEgg.resignResult(Board.BLACK), Board.BLACK));
        chk("围棋快档 = 0", 0, WinEgg.goLevel("fast"));
        chk("围棋普通档 = 1", 1, WinEgg.goLevel("normal"));
        chk("围棋强档 = 2", 2, WinEgg.goLevel("strong"));
        chk("围棋未知档按普通", 1, WinEgg.goLevel("whatever"));
        chk("围棋快档彩蛋不给", false, WinEgg.eggAllowed(WinEgg.goLevel("fast"), 2));
        chk("围棋普通档彩蛋不给", false, WinEgg.eggAllowed(WinEgg.goLevel("normal"), 2));
        chk("围棋强档彩蛋给", true, WinEgg.eggAllowed(WinEgg.goLevel("strong"), 2));

        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
