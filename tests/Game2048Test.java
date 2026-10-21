/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

import com.autogo.Game2048;

/** 2048 纯逻辑：滑动、合并一次、得分、生成、胜负。 */
public class Game2048Test {

    static int pass, fail;
    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) { pass++; }
        else { fail++; System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got); }
    }

    static int[] board(int... v) {
        int[] a = new int[16];
        System.arraycopy(v, 0, a, 0, Math.min(v.length, 16));
        return a;
    }

    static int[] row0(Game2048 g) { return new int[]{g.at(0,0),g.at(0,1),g.at(0,2),g.at(0,3)}; }
    static int nonZero(Game2048 g) {
        int n = 0; for (int v : g.snapshot()) if (v != 0) n++; return n;
    }

    public static void main(String[] args) {
        Game2048 fresh = new Game2048(1);
        chk("开局 2 个非空格", 2, nonZero(fresh));
        boolean twoOrFour = true;
        for (int v : fresh.snapshot()) if (v != 0 && v != 2 && v != 4) twoOrFour = false;
        chk("开局只生成 2/4", true, twoOrFour);

        Game2048 g = new Game2048(7);
        g.load(board(2,2,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        chk("左移 2+2 动了吗", true, g.move(Game2048.LEFT));
        chk("左移合并成 4", 4, g.at(0,0));
        chk("合并得分 4", 4, g.score());
        chk("合并后生成 1 个新子（共 2 个）", 2, nonZero(g));

        Game2048 four = new Game2048(8);
        four.load(board(2,2,2,2, 0,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        four.move(Game2048.LEFT);
        chk("2 2 2 2 左移 = 4 4", "4/4", four.at(0,0) + "/" + four.at(0,1));
        chk("2 2 2 2 得分 8", 8, four.score());
        chk("只合并一次（不出现 8）", false, four.at(0,0) == 8 || four.at(0,1) == 8);

        Game2048 once = new Game2048(21);
        once.load(board(2,2,4,0, 0,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        once.move(Game2048.LEFT);
        chk("2 2 4 左移：第一格 4", 4, once.at(0,0));
        chk("2 2 4 左移：第二格 4（2+2 后的 4 不再和原 4 合并）", 4, once.at(0,1));
        chk("2 2 4 左移：没有一次并成 8", false, once.at(0,0) == 8);
        chk("2 2 4 左移：得分 4", 4, once.score());

        java.util.List<Game2048.MoveAnim> anims = once.lastAnims();
        chk("2 2 4 左移产生 3 条动画记录", 3, anims.size());
        int mergedTo0 = 0;
        for (Game2048.MoveAnim a : anims) {
            if (a.to == 0 && a.merged) mergedTo0++;
        }
        chk("两个 2 都合并到第 0 格（2 条 merged）", 2, mergedTo0);

        Game2048 twice = new Game2048(22);
        twice.load(board(4,4,8,8, 0,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        twice.move(Game2048.LEFT);
        chk("4 4 8 8 左移 = 8 16", "8/16", twice.at(0,0) + "/" + twice.at(0,1));
        chk("4 4 8 8 得分 24", 24, twice.score());

        Game2048 gap = new Game2048(23);
        gap.load(board(0,2,0,2, 0,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        gap.move(Game2048.LEFT);
        chk("0 2 0 2 左移合并成 4", 4, gap.at(0,0));
        chk("0 2 0 2 得分 4", 4, gap.score());

        Game2048 gap2 = new Game2048(24);
        gap2.load(board(2,0,2,4, 0,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        gap2.move(Game2048.LEFT);
        chk("2 0 2 4 左移 = 4 4", "4/4", gap2.at(0,0) + "/" + gap2.at(0,1));
        chk("2 0 2 4 得分 4", 4, gap2.score());

        Game2048 invalid = new Game2048(25);
        invalid.load(board(2,4,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        chk("已靠左的 2 4 再左移：没动", false, invalid.move(Game2048.LEFT));
        chk("无效移动不生成新子", 2, nonZero(invalid));
        chk("无效移动不加分", 0, invalid.score());

        Game2048 noMerge = new Game2048(9);
        noMerge.load(board(2,4,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        noMerge.move(Game2048.LEFT);
        chk("2 4 不合并，仍在原位", "2/4", noMerge.at(0,0) + "/" + noMerge.at(0,1));
        chk("不合并得分 0", 0, noMerge.score());

        Game2048 right = new Game2048(10);
        right.load(board(2,2,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        right.move(Game2048.RIGHT);
        chk("右移合并到最右", 4, right.at(0,3));
        chk("右移得分 4", 4, right.score());

        Game2048 up = new Game2048(11);
        up.load(board(2,0,0,0, 2,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        up.move(Game2048.UP);
        chk("上移合并", 4, up.at(0,0));
        Game2048 down = new Game2048(12);
        down.load(board(2,0,0,0, 2,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        down.move(Game2048.DOWN);
        chk("下移合并到最下", 4, down.at(3,0));

        Game2048 stuck = new Game2048(13);
        stuck.load(board(2,4,2,4, 4,2,4,2, 2,4,2,4, 4,2,4,2), 0);
        chk("无空格且无相邻同值：结束", true, stuck.over());
        chk("结束后 move 返回 false", false, stuck.move(Game2048.LEFT));
        chk("结束后不再生成新子", 16, nonZero(stuck));

        Game2048 mergeable = new Game2048(14);
        mergeable.load(board(2,4,2,4, 4,2,4,2, 2,4,2,4, 4,2,4,4), 0);
        chk("有相邻同值：未结束", false, mergeable.over());
        chk("canMove 为 true", true, mergeable.canMove());

        Game2048 win = new Game2048(15);
        win.load(board(1024,1024,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0), 0);
        win.move(Game2048.LEFT);
        chk("1024+1024 = 2048", 2048, win.at(0,0));
        chk("达到 2048 判赢", true, win.won());
        chk("赢了还能继续", true, win.canMove());

        Game2048 snap = new Game2048(16);
        snap.load(board(8,0,0,0, 0,0,0,0, 0,0,0,0, 0,0,0,0), 123);
        int[] s = snap.snapshot();
        chk("snapshot 分数", 123, snap.score());
        chk("snapshot 首格", 8, s[0]);
        Game2048 copy = new Game2048(17);
        copy.load(s, 123);
        chk("load 后首格", 8, copy.at(0,0));
        chk("load 后分数", 123, copy.score());

        System.out.println("2048 测试：通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
