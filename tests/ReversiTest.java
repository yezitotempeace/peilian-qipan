/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.ReversiAi;
import com.autogo.ReversiBoard;

import java.util.List;

/** 黑白棋规则测试。 */
public class ReversiTest {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    static String pts(List<int[]> ms) {
        StringBuilder sb = new StringBuilder();
        for (int[] m : ms) {
            sb.append(m[0]).append(m[1]).append(" ");
        }
        return sb.toString().trim();
    }

    public static void main(String[] a) {
        ReversiBoard b = new ReversiBoard();
        chk("开局黑先", ReversiBoard.BLACK, b.sideToMove());
        chk("初始 4 子", 4, b.count(ReversiBoard.BLACK) + b.count(ReversiBoard.WHITE));
        chk("初始各 2 子", 2, b.count(ReversiBoard.BLACK));
        chk("初始合法点", "23 32 45 54", pts(b.legalMoves(ReversiBoard.BLACK)));

        // 黑下 (3,2)，夹翻 (3,3)
        chk("黑 (3,2) 合法", true, b.isLegal(ReversiBoard.BLACK, 3, 2));
        b.play(ReversiBoard.BLACK, 3, 2);
        chk("落子后轮白", ReversiBoard.WHITE, b.sideToMove());
        chk("(3,2) 变黑", ReversiBoard.BLACK, b.at(3, 2));
        chk("(3,3) 被翻成黑", ReversiBoard.BLACK, b.at(3, 3));
        chk("黑 4 子", 4, b.count(ReversiBoard.BLACK));

        // 不能下在已有子上 / 不能下不夹子的空格
        chk("已有子不能下", false, b.isLegal(ReversiBoard.WHITE, 3, 3));
        chk("不夹子的空格非法", false, b.isLegal(ReversiBoard.WHITE, 0, 0));

        // 白方回应
        List<int[]> wm = b.legalMoves(ReversiBoard.WHITE);
        chk("白有合法点", true, !wm.isEmpty());
        int[] w0 = wm.get(0);
        b.play(ReversiBoard.WHITE, w0[0], w0[1]);
        chk("白下完轮黑", ReversiBoard.BLACK, b.sideToMove());

        // 翻子计数：黑再下一手，翻子数应 > 0
        List<int[]> bm = b.legalMoves(ReversiBoard.BLACK);
        if (!bm.isEmpty()) {
            int before = b.count(ReversiBoard.BLACK);
            b.play(ReversiBoard.BLACK, bm.get(0)[0], bm.get(0)[1]);
            chk("下子后黑子数增加", true, b.count(ReversiBoard.BLACK) > before);
        }

        // 弃权：构造一方无棋可走的局面，验证轮次跳过与结束
        ReversiBoard p = new ReversiBoard();
        p.testSetState(ReversiBoard.BLACK, false);
        // 清空并摆一个「白无棋可走」的残局：黑占四角与大部分，白只剩中间被围死
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                p.testPut(r, c, ReversiBoard.EMPTY);
            }
        }
        p.testPut(0, 0, ReversiBoard.BLACK);
        p.testPut(0, 1, ReversiBoard.BLACK);
        p.testPut(1, 0, ReversiBoard.BLACK);
        p.testPut(1, 1, ReversiBoard.WHITE);
        p.testPut(7, 7, ReversiBoard.BLACK);
        // 白只有 (1,1)，四周都被黑围住且无处可翻 ⇒ 白无棋
        p.testSetState(ReversiBoard.BLACK, false);
        chk("残局白无棋", 0, p.legalMoves(ReversiBoard.WHITE).size());
        // 黑有棋（翻 (1,1)）
        chk("残局黑有棋", true, p.legalMoves(ReversiBoard.BLACK).size() > 0);

        // 终局判定：棋盘下满
        ReversiBoard f = new ReversiBoard();
        f.testSetState(ReversiBoard.BLACK, false);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                f.testPut(r, c, ReversiBoard.BLACK);
            }
        }
        f.testPut(0, 0, ReversiBoard.WHITE);
        f.testPut(0, 1, ReversiBoard.WHITE);
        f.testSetState(ReversiBoard.BLACK, true);
        chk("满盘 64 子", 64, f.count(ReversiBoard.BLACK) + f.count(ReversiBoard.WHITE));
        chk("黑 62 白 2 则黑胜", ReversiBoard.BLACK, f.winner());
        // 平局：32 对 32
        f.testSetState(ReversiBoard.BLACK, true);
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                f.testPut(r, c, (r + c) % 2 == 0 ? ReversiBoard.BLACK : ReversiBoard.WHITE);
            }
        }
        chk("32 对 32 判平", 0, f.winner());

        // 自对弈不崩、能终局
        int finished = 0;
        for (int g = 0; g < 10; g++) {
            ReversiBoard t = new ReversiBoard();
            ReversiAi ai = new ReversiAi();
            ai.setLevel(ReversiAi.STRONG);
            java.util.Random rnd = new java.util.Random(g);
            int steps = 0;
            while (!t.isGameOver() && steps < 200) {
                int side = t.sideToMove();
                int[] m = ai.genmove(t);
                if (m == null) {
                    break;
                }
                t.play(side, m[0], m[1]);
                steps++;
            }
            if (t.isGameOver()) {
                finished++;
            }
        }
        chk("10 局自对弈全部终局", 10, finished);

        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
