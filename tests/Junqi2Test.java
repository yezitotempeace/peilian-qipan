/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.JunqiAI;
import com.autogo.JunqiBoard;
import com.autogo.JunqiBoard.Pc;

import java.util.List;
import java.util.Random;

/**
 * 全新军棋规则引擎测试：布阵约束、地形、走法（公路/铁路/工兵拐弯/山界）、
 * 行营安全、战斗结算、暗棋亮子、胜负与和棋。
 */
public class Junqi2Test {

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

    static boolean can(JunqiBoard b, int side, int fr, int fc, int tr, int tc) {
        return b.isLegal(side, fr, fc, tr, tc);
    }

    public static void main(String[] args) {
        // ---------- ① 随机布阵的合法性 ----------
        for (int seed = 1; seed <= 30; seed++) {
            JunqiBoard b = new JunqiBoard(seed);
            int red = 0, black = 0;
            for (int r = 0; r < 12; r++) {
                for (int c = 0; c < 5; c++) {
                    Pc p = b.at(r, c);
                    if (p == null) continue;
                    if (p.side == JunqiBoard.RED) red++;
                    else black++;
                    // 行营开局必须为空
                    chk("行营开局为空 seed" + seed, false, b.isCamp(r, c));
                    // 军旗必须在大本营
                    if (p.type == JunqiBoard.JUNQI) chk("军旗在大本营 seed" + seed, true, b.isHq(r, c));
                    // 地雷在后两排
                    if (p.type == JunqiBoard.DILEI) {
                        boolean back2 = (p.side == JunqiBoard.RED) ? (r >= 10) : (r <= 1);
                        chk("地雷在后两排 seed" + seed, true, back2);
                    }
                    // 炸弹不在第一排
                    if (p.type == JunqiBoard.ZHADAN) {
                        boolean front = (p.side == JunqiBoard.RED) ? (r == 6) : (r == 5);
                        chk("炸弹不在第一排 seed" + seed, false, front);
                    }
                }
            }
            chk("每方 25 枚 seed" + seed, 25, red);
            chk("每方 25 枚 seed" + seed, 25, black);
        }

        // ---------- ② 地形 ----------
        JunqiBoard b = new JunqiBoard(7);
        chk("大本营(0,1)", true, b.isHq(0, 1));
        chk("大本营(11,3)", true, b.isHq(11, 3));
        chk("行营(3,2)", true, b.isCamp(3, 2));
        chk("行营(8,2)", true, b.isCamp(8, 2));
        chk("兵站(9,2)不是行营", false, b.isCamp(9, 2));
        chk("兵站(5,0)不是行营", false, b.isCamp(5, 0));
        // 铁路
        chk("铁路 第1行", true, b.isRail(1, 0) && b.isRail(1, 4));
        chk("铁路 第5行", true, b.isRail(5, 2));
        chk("铁路 第6行", true, b.isRail(6, 2));
        chk("铁路 第10行", true, b.isRail(10, 2));
        chk("铁路 左边线", true, b.isRail(0, 0) && b.isRail(11, 0));
        chk("铁路 右边线", true, b.isRail(0, 4) && b.isRail(11, 4));
        chk("铁路 中路靠前线", true, b.isRail(4, 2) && b.isRail(7, 2));
        chk("边线铁路贯通到第3行", true, b.isRail(3, 0));
        chk("边线铁路贯通到第8行", true, b.isRail(8, 0));
        chk("非铁路 中路深处", false, b.isRail(2, 2));

        // ---------- ③ 走法：公路一步 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 8, 1, JunqiBoard.RED, JunqiBoard.SILING);   // 红司令在普通兵站（非铁路）
        chk("公路：上", true, can(b, JunqiBoard.RED, 8, 1, 7, 1));
        chk("公路：下", true, can(b, JunqiBoard.RED, 8, 1, 9, 1));
        chk("公路：左", true, can(b, JunqiBoard.RED, 8, 1, 8, 0));
        chk("公路：右", true, can(b, JunqiBoard.RED, 8, 1, 8, 2));
        chk("公路：不能走两步", false, can(b, JunqiBoard.RED, 8, 1, 10, 1));
        chk("公路：不能斜走（非行营）", false, can(b, JunqiBoard.RED, 8, 1, 9, 0));

        // ---------- ④ 山界：中路两侧不能越线 ----------
        put(b, 5, 1, JunqiBoard.RED, JunqiBoard.SHIZHANG);
        chk("山界：第2列不能越线", false, can(b, JunqiBoard.RED, 5, 1, 6, 1));
        put(b, 5, 3, JunqiBoard.RED, JunqiBoard.SHIZHANG);
        chk("山界：第4列不能越线", false, can(b, JunqiBoard.RED, 5, 3, 6, 3));
        put(b, 6, 0, JunqiBoard.RED, JunqiBoard.SHIZHANG);
        chk("前线：第1列可以越线", true, can(b, JunqiBoard.RED, 6, 0, 5, 0));
        put(b, 6, 2, JunqiBoard.RED, JunqiBoard.TUANZHANG);
        chk("前线：第3列可以越线", true, can(b, JunqiBoard.RED, 6, 2, 5, 2));
        put(b, 6, 4, JunqiBoard.RED, JunqiBoard.SHIZHANG);
        chk("前线：第5列可以越线", true, can(b, JunqiBoard.RED, 6, 4, 5, 4));

        // ---------- ⑤ 铁路：直线滑行 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        // 第 1 行是铁路：放一个师长在 (1,0)，可以一路滑到 (1,4)
        put(b, 1, 0, JunqiBoard.RED, JunqiBoard.SHIZHANG);
        chk("铁路直滑 1 格", true, can(b, JunqiBoard.RED, 1, 0, 1, 1));
        chk("铁路直滑 3 格", true, can(b, JunqiBoard.RED, 1, 0, 1, 3));
        chk("铁路直滑到行尾", true, can(b, JunqiBoard.RED, 1, 0, 1, 4));
        chk("非工兵不能拐弯", false, can(b, JunqiBoard.RED, 1, 0, 2, 2));
        // 中路纵向铁路：第 4 行 → 第 7 行
        put(b, 4, 2, JunqiBoard.RED, JunqiBoard.TUANZHANG);
        chk("中路纵铁路向下", true, can(b, JunqiBoard.RED, 4, 2, 6, 2));

        // ---------- ⑥ 工兵：铁路可拐弯 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 1, 0, JunqiBoard.RED, JunqiBoard.GONGBING);
        chk("工兵铁路直走", true, can(b, JunqiBoard.RED, 1, 0, 1, 4));
        chk("工兵铁路拐弯（到左边线深处）", true, can(b, JunqiBoard.RED, 1, 0, 5, 0));
        chk("工兵铁路拐弯（到对角远处）", true, can(b, JunqiBoard.RED, 1, 0, 10, 4));

        // ---------- ⑦ 不可动的棋子 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 10, 2, JunqiBoard.RED, JunqiBoard.DILEI);
        put(b, 11, 2, JunqiBoard.RED, JunqiBoard.JUNQI);
        chk("地雷不能动", false, can(b, JunqiBoard.RED, 10, 2, 10, 1));
        chk("军旗不能动", false, can(b, JunqiBoard.RED, 11, 2, 10, 2));
        // 进大本营的棋子不能再动
        put(b, 11, 2, JunqiBoard.RED, JunqiBoard.SILING);
        chk("进大本营不能再动", false, can(b, JunqiBoard.RED, 11, 2, 10, 2));

        // ---------- ⑧ 行营安全：有子的行营不能被吃 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 3, 2, JunqiBoard.RED, JunqiBoard.PAIZHANG);      // 红排长进行营
        put(b, 2, 2, JunqiBoard.BLACK, JunqiBoard.SILING);      // 黑司令在上方
        chk("行营里的棋子不可被吃", false, can(b, JunqiBoard.BLACK, 2, 2, 3, 2));
        put(b, 3, 1, JunqiBoard.BLACK, JunqiBoard.SILING);
        chk("空行营可以进", true, can(b, JunqiBoard.BLACK, 3, 1, 4, 1));

        // ---------- ⑨ 战斗：高吃低 / 同级同归于尽 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 6, 2, JunqiBoard.RED, JunqiBoard.SILING);        // 司令(9)
        put(b, 5, 2, JunqiBoard.BLACK, JunqiBoard.LIANZHANG);   // 连长(3)
        chk("高吃低：能吃", true, can(b, JunqiBoard.RED, 6, 2, 5, 2));
        b.play(JunqiBoard.RED, 6, 2, 5, 2);
        chk("高吃低：司令活", true, b.at(5, 2) != null && b.at(5, 2).type == JunqiBoard.SILING);
        chk("高吃低：连长亡", true, b.at(6, 2) == null);

        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 6, 2, JunqiBoard.RED, JunqiBoard.LIANZHANG);
        put(b, 5, 2, JunqiBoard.BLACK, JunqiBoard.LIANZHANG);
        b.play(JunqiBoard.RED, 6, 2, 5, 2);
        chk("同级：攻击方亡", true, b.at(6, 2) == null);
        chk("同级：防守方亡", true, b.at(5, 2) == null);

        // ---------- ⑩ 炸弹与任何子同归于尽 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 6, 2, JunqiBoard.RED, JunqiBoard.ZHADAN);
        put(b, 5, 2, JunqiBoard.BLACK, JunqiBoard.SILING);
        b.play(JunqiBoard.RED, 6, 2, 5, 2);
        chk("炸弹碰司令：弹亡", true, b.at(6, 2) == null);
        chk("炸弹碰司令：司令亡", true, b.at(5, 2) == null);

        // ---------- ⑪ 地雷：非工兵撞雷雷保留，工兵挖雷 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 10, 2, JunqiBoard.RED, JunqiBoard.DILEI);
        put(b, 9, 2, JunqiBoard.BLACK, JunqiBoard.SILING);
        b.play(JunqiBoard.BLACK, 9, 2, 10, 2);
        chk("司令撞雷：司令亡", true, b.at(9, 2) == null);
        chk("司令撞雷：雷保留", true, b.at(10, 2) != null && b.at(10, 2).type == JunqiBoard.DILEI);

        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 10, 2, JunqiBoard.RED, JunqiBoard.DILEI);
        put(b, 9, 2, JunqiBoard.BLACK, JunqiBoard.GONGBING);
        b.play(JunqiBoard.BLACK, 9, 2, 10, 2);
        chk("工兵挖雷：雷消", true, b.at(10, 2) == null || b.at(10, 2).type != JunqiBoard.DILEI);
        chk("工兵挖雷：工兵活并占位", true, b.at(10, 2) != null && b.at(10, 2).type == JunqiBoard.GONGBING);

        // ---------- ⑫ 暗棋亮子：交战后双方身份公开 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 6, 2, JunqiBoard.RED, JunqiBoard.TUANZHANG);
        put(b, 7, 2, JunqiBoard.BLACK, JunqiBoard.PAIZHANG);
        chk("未交战：黑子盖着", false, b.at(7, 2).revealed);
        b.play(JunqiBoard.RED, 6, 2, 7, 2);
        chk("交战后：参战棋子亮出", true, b.at(7, 2) != null && b.at(7, 2).revealed);

        // ---------- ⑬ 胜负：扛军旗 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        put(b, 11, 2, JunqiBoard.BLACK, JunqiBoard.JUNQI);
        put(b, 10, 2, JunqiBoard.RED, JunqiBoard.GONGBING);
        chk("扛旗前未结束", false, b.gameOver());
        b.play(JunqiBoard.RED, 10, 2, 11, 2);
        chk("吃掉军旗 → 红胜", 1, b.result());

        // ---------- ⑭ 无棋可走判负 ----------
        b = new JunqiBoard(new int[2][0]);
        b.testStartBattle();
        // 黑方只剩一枚被围死的棋子
        put(b, 0, 0, JunqiBoard.BLACK, JunqiBoard.PAIZHANG);
        put(b, 0, 1, JunqiBoard.RED, JunqiBoard.SILING);
        put(b, 1, 0, JunqiBoard.RED, JunqiBoard.SILING);
        put(b, 11, 2, JunqiBoard.RED, JunqiBoard.JUNQI);
        // 轮到黑方：唯一棋子 (0,0) 四周全是红方/边界 → 无合法着法
        b.play(JunqiBoard.RED, 1, 0, 0, 0);   // 红司令吃掉黑排长，占了 (0,0)
        // 现在黑方没有棋子了 → 无合法着法
        chk("黑方无棋可走 → 红胜", 1, b.result());


        // ---------- ⑮ 翻棋模式：50 子随机扣铺、翻牌算一步、翻后可见 ----------
        JunqiBoard f = new JunqiBoard(31415, true);
        chk("翻棋：无布阵阶段", false, f.isSetupPhase());
        chk("翻棋：是翻棋模式", true, f.isFlipMode());
        int pieces = 0, down = 0, camps = 0;
        for (int r = 0; r < 12; r++) {
            for (int c = 0; c < 5; c++) {
                Pc p = f.at(r, c);
                if (p != null) {
                    pieces++;
                    if (p.faceDown) down++;
                    if (f.isCamp(r, c)) camps++;
                }
            }
        }
        chk("翻棋：50 枚棋子", 50, pieces);
        chk("翻棋：全部扣着", 50, down);
        chk("翻棋：行营留空", 0, camps);
        List<int[]> fm = f.legalMoves(JunqiBoard.RED);
        chk("翻棋：开局 50 个翻牌着法", 50, fm.size());
        boolean allFlip = true;
        for (int[] m : fm) allFlip &= (m[0] == m[2] && m[1] == m[3]);
        chk("翻棋：着法都是翻牌（起点=终点）", true, allFlip);

        // 翻开一枚 → 变明置、不能再翻、轮到对方
        int[] first = fm.get(0);
        int fr = first[0], fc = first[1];
        f.play(JunqiBoard.RED, fr, fc, fr, fc);
        chk("翻棋：翻开后明置", false, f.at(fr, fc).faceDown);
        chk("翻棋：翻开后亮身份", true, f.at(fr, fc).revealed);
        chk("翻棋：翻过不能重复翻", false, f.isLegal(JunqiBoard.RED, fr, fc, fr, fc));
        chk("翻棋：翻完轮到对方", JunqiBoard.BLACK, f.sideToMove());

        // 扣着的棋子不能直接移动（必须先翻）：扫所有着法，源格若是扣着的棋子则只能是翻牌
        boolean canMoveFaceDown = false;
        for (int[] m : f.legalMoves(JunqiBoard.RED)) {
            Pc src = f.at(m[0], m[1]);
            boolean isFlip = (m[0] == m[2] && m[1] == m[3]);
            if (src != null && src.faceDown && !isFlip) canMoveFaceDown = true;
        }
        chk("翻棋：扣着的棋子不能直接移动", false, canMoveFaceDown);

        // 翻棋里也可以吃子（明置棋子按军衔打）
        JunqiBoard f2 = new JunqiBoard(new int[2][0]);
        f2.testStartBattle();
        f2.testPut(6, 2, JunqiBoard.RED, JunqiBoard.GONGBING);
        f2.testPut(5, 2, JunqiBoard.BLACK, JunqiBoard.LIANZHANG);
        f2.play(JunqiBoard.RED, 6, 2, 5, 2);
        chk("翻棋战斗：工兵吃连长（工兵最小但这里只比大小）", false, f2.at(5, 2) == null);

        // 翻棋：扛军旗获胜
        JunqiBoard f3 = new JunqiBoard(new int[2][0]);
        f3.testStartBattle();
        f3.testPut(11, 2, JunqiBoard.BLACK, JunqiBoard.JUNQI);
        f3.testPut(10, 2, JunqiBoard.RED, JunqiBoard.SILING);
        f3.play(JunqiBoard.RED, 10, 2, 11, 2);
        chk("翻棋：扛旗获胜", 1, f3.result());

        // 翻棋：AI 能出招
        JunqiBoard f4 = new JunqiBoard(777, true);
        f4.testStartBattle();
        JunqiAI ai2 = new JunqiAI();
        ai2.setLevel(JunqiAI.STRONG);
        int[] am = ai2.genmove(f4);
        chk("翻棋：AI 能出招", true, am != null && f4.isLegal(JunqiBoard.BLACK, am[0], am[1], am[2], am[3]));

        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
