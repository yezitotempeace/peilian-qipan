package com.autogo;

import com.autogo.Board;
import com.autogo.WinEgg;

/** 隐藏彩蛋的开关：只给普通/强档，快档不给；只看「人赢了」。 */
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
        chk("快档不显示", false, WinEgg.eggAllowed(0, 0));
        chk("普通档显示", true, WinEgg.eggAllowed(1, 0));
        chk("强档显示", true, WinEgg.eggAllowed(2, 0));
        chk("越界档位（默认普通）显示", true, WinEgg.eggAllowed(9, 0));

        chk("围棋：人执黑 B+R 算赢", true, WinEgg.goHumanWon("B+R", Board.BLACK));
        chk("围棋：人执白 B+R 算输", false, WinEgg.goHumanWon("B+R", Board.WHITE));
        chk("围棋：黑胜文案", true, WinEgg.goHumanWon("黑胜 3.5 目", Board.BLACK));
        chk("围棋：空结果不算赢", false, WinEgg.goHumanWon("", Board.BLACK));
        chk("围棋：和棋文案不算赢", false, WinEgg.goHumanWon("和棋", Board.BLACK));
        chk("人执黑认输，赢家是白", "W+R", WinEgg.resignResult(Board.BLACK));
        chk("人执白认输，赢家是黑", "B+R", WinEgg.resignResult(Board.WHITE));
        chk("认输结果不判人赢", false, WinEgg.goHumanWon(WinEgg.resignResult(Board.BLACK), Board.BLACK));

        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
