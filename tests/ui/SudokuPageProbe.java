/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.graphics.Canvas;
import android.os.Bundle;
import android.os.FakeLog;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.lang.reflect.Field;

/**
 * 数独页面探针：结构、屏幕适配（竖屏/横屏/各种尺寸）、绘制、行列宫冲突标红、
 * 笔记、提示、撤销、难度切换、存档。
 */
public class SudokuPageProbe {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    static SudokuActivity make(boolean landscape) {
        SudokuActivity a = new SudokuActivity();
        android.content.res.Configuration cfg = a.resources.getConfiguration();
        cfg.screenWidthDp = landscape ? 800 : 400;
        cfg.screenHeightDp = landscape ? 400 : 800;
        cfg.smallestScreenWidthDp = 400;
        cfg.orientation = landscape
                ? android.content.res.Configuration.ORIENTATION_LANDSCAPE
                : android.content.res.Configuration.ORIENTATION_PORTRAIT;
        a.onCreate(new Bundle());
        return a;
    }

    static <T> T field(Object o, String name) throws Exception {
        Field f = o.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return (T) f.get(o);
    }

    static Button buttonWith(View v, String text) {
        if (v instanceof Button && text.contentEquals(((Button) v).getText())) {
            return (Button) v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                Button r = buttonWith(g.getChildAt(i), text);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    static boolean hasText(View v, String s) {
        if (v instanceof TextView && String.valueOf(((TextView) v).getText()).contains(s)) {
            return true;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasText(g.getChildAt(i), s)) {
                    return true;
                }
            }
        }
        return false;
    }

    static LinearLayout bodyOf(LinearLayout root) {
        return (LinearLayout) root.getChildAt(1);
    }

    public static void main(String[] args) throws Exception {
        // ── ① 竖屏结构 ──
        SudokuActivity a = make(false);
        FrameLayout holder = (FrameLayout) a.content;
        chk("页面是「内容 + 水印」两层", 2, holder.getChildCount());
        LinearLayout root = (LinearLayout) holder.getChildAt(0);
        chk("内容根视图竖排", LinearLayout.VERTICAL, root.orientation);
        chk("有难度按钮（默认普通）", true, buttonWith(root, "难度 普通") != null);
        chk("有重开按钮", true, buttonWith(root, "重开") != null);
        chk("有擦除键", true, buttonWith(root, "擦") != null);
        chk("有笔记键", true, buttonWith(root, "笔记 关") != null);
        chk("有提示键", true, buttonWith(root, "提示") != null);
        chk("有撤销键", true, buttonWith(root, "撤销") != null);
        for (int d = 1; d <= 9; d++) {
            chk("有数字键 " + d, true, buttonWith(root, String.valueOf(d)) != null);
        }
        chk("竖屏：身体区竖排（棋盘在上、键盘在下）", LinearLayout.VERTICAL, bodyOf(root).orientation);

        // ── ② 横屏适配：身体区改左右排 ──
        SudokuActivity land = make(true);
        LinearLayout landRoot = (LinearLayout) ((FrameLayout) land.content).getChildAt(0);
        chk("横屏：身体区左右排（棋盘在左、键盘在右）", LinearLayout.HORIZONTAL, bodyOf(landRoot).orientation);

        // ── ③ 棋盘尺寸：永远取短边、居中，不溢出 ──
        SudokuView view = field(a, "view");
        SudokuGame game = field(a, "game");

        view.fakeSize(1080, 600);
        chk("宽屏：边长取短边 600", 600f, view.side());
        chk("宽屏：水平居中（左右各留 240）", 240f, view.originX());
        chk("宽屏：竖直不偏移", 0f, view.originY());

        view.fakeSize(400, 1200);
        chk("长方：边长取短边 400", 400f, view.side());
        chk("长方：竖直居中（上下各留 400）", 400f, view.originY());
        chk("长方：水平不偏移", 0f, view.originX());

        view.fakeSize(900, 900);
        chk("正方形：边长 900", 900f, view.side());
        chk("格宽 = 900/9", 100f, view.cellSize());
        int[] center = view.hitCell(450, 450);
        chk("点正中 = 第 5 行第 5 列", "4,4", center[0] + "," + center[1]);
        int[] corner = view.hitCell(5, 5);
        chk("点左上角 = 0,0", "0,0", corner[0] + "," + corner[1]);
        chk("点棋盘外返回 null", null, view.hitCell(-10, 100));
        chk("点棋盘右下外返回 null", null, view.hitCell(905, 500));

        // ── ④ 绘制：题面数字个数 = clues，网格线 20 条 ──
        view.fakeSize(900, 900);
        Canvas.reset();
        view.fakeOnDraw(new Canvas());
        chk("绘制的数字个数 = 题面提示数", game.clues(), Canvas.TEXTS.size());
        chk("网格线 20 条（细 12 + 粗 8）", 20, Canvas.LINES.size());

        // ── ⑤ 行列冲突：允许填，但标红（行为 A） ──
        int[] spot = findRowSpot(game);
        chk("找到同行两个空格和一个未用数字", true, spot != null);
        if (spot != null) {
            int r = spot[0], c1 = spot[1], c2 = spot[2], d = spot[3];
            view.select(r, c1);
            buttonWith(root, String.valueOf(d)).fakeClick();
            chk("第一格填 " + d + " 成功", d, game.at(r, c1));
            chk("第一格不冲突", false, game.conflictAt(r, c1));
            view.select(r, c2);
            buttonWith(root, String.valueOf(d)).fakeClick();
            chk("同行重复：仍然填进去（不拦）", d, game.at(r, c2));
            chk("同行重复：标红", true, game.conflictAt(r, c2));
            chk("冲突计数 >= 2", true, game.conflictCount() >= 2);
            chk("信息栏提示冲突", true, hasText(root, "冲突"));
            // 擦掉后冲突消失
            buttonWith(root, "擦").fakeClick();
            chk("擦除后该格空", 0, game.at(r, c2));
            chk("擦除后冲突没了", false, game.conflictAt(r, c2));
        }

        // ── ⑥ 笔记 ──
        SudokuActivity n = make(false);
        LinearLayout nRoot = (LinearLayout) ((FrameLayout) n.content).getChildAt(0);
        SudokuView nView = field(n, "view");
        SudokuGame nGame = field(n, "game");
        int[] e = firstEmpty(nGame);
        buttonWith(nRoot, "笔记 关").fakeClick();
        chk("笔记切换成开", true, buttonWith(nRoot, "笔记 开") != null);
        nView.select(e[0], e[1]);
        buttonWith(nRoot, "3").fakeClick();
        chk("笔记记下 3", true, (nGame.noteMask(e[0], e[1]) & (1 << 3)) != 0);
        chk("笔记模式不填数字", 0, nGame.at(e[0], e[1]));

        // ── ⑦ 提示与撤销 ──
        SudokuActivity h = make(false);
        LinearLayout hRoot = (LinearLayout) ((FrameLayout) h.content).getChildAt(0);
        SudokuGame hGame = field(h, "game");
        int before = hGame.remaining();
        buttonWith(hRoot, "提示").fakeClick();
        chk("提示填了一格", before - 1, hGame.remaining());
        chk("提示用了一次", 1, hGame.hintsUsed());
        buttonWith(hRoot, "撤销").fakeClick();
        chk("撤销后回到原样", before, hGame.remaining());

        // ── ⑧ 难度切换：普通 → 困难，题目确实更难（提示数更少） ──
        SudokuActivity d = make(false);
        LinearLayout dRoot = (LinearLayout) ((FrameLayout) d.content).getChildAt(0);
        SudokuGame dGame = field(d, "game");
        int cluesNormal = dGame.clues();
        buttonWith(dRoot, "难度 普通").fakeClick();
        chk("切到困难档", true, buttonWith(dRoot, "难度 困难") != null);
        chk("困难档难度值 = HARD", SudokuGame.HARD, dGame.difficulty());
        chk("困难档提示数 <= 26", true, dGame.clues() <= 26);
        chk("困难档比普通更空", true, dGame.clues() < cluesNormal);
        chk("困难档需要多次试探", true, SudokuGame.analyse(dGame.givenSnapshot()).guesses >= 2);
        chk("困难档唯一解", 1, SudokuGame.countSolutions(dGame.givenSnapshot(), 2));
        buttonWith(dRoot, "难度 困难").fakeClick();
        chk("再切回简单档", true, buttonWith(dRoot, "难度 简单") != null);

        // ── ⑨ 重开弹窗 ──
        FakeLog.clear();
        SudokuActivity rs = make(false);
        LinearLayout rsRoot = (LinearLayout) ((FrameLayout) rs.content).getChildAt(0);
        buttonWith(rsRoot, "重开").fakeClick();
        chk("重开弹出确认框", 1, FakeLog.countPrefix("dialog.title:重开一局"));

        // ── ⑩ 存档：填几格再开新页，进度还在 ──
        SudokuActivity s1 = make(false);
        SudokuGame g1 = field(s1, "game");
        SudokuView v1 = field(s1, "view");
        LinearLayout r1 = (LinearLayout) ((FrameLayout) s1.content).getChildAt(0);
        int[] spot2 = findRowSpot(g1);
        if (spot2 != null) {
            v1.select(spot2[0], spot2[1]);
            buttonWith(r1, String.valueOf(spot2[3])).fakeClick();
            s1.onPause();
            // 假 Context 的 SharedPreferences 是每个 Activity 一份；这里手动共用同一份，模拟真机重进页面
            SudokuActivity s2 = new SudokuActivity();
            s2.prefs = s1.prefs;
            s2.resources = s1.resources;
            s2.onCreate(new Bundle());
            SudokuGame g2 = field(s2, "game");
            chk("重开页面后题面一致", SudokuGame.encode(g1.givenSnapshot()), SudokuGame.encode(g2.givenSnapshot()));
            chk("重开页面后填的格还在", spot2[3], g2.at(spot2[0], spot2[1]));
        }

        System.out.println("数独页面探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    /**
     * 找同一行里两个空格 + 一个数字：这个数字放进去时行 / 列 / 宫都干净，
     * 这样两格都放完之后，「唯一的冲突」就是行内重复，断言才精确。
     */
    static int[] findRowSpot(SudokuGame g) {
        for (int r = 0; r < 9; r++) {
            for (int c1 = 0; c1 < 9; c1++) {
                if (g.at(r, c1) != 0) {
                    continue;
                }
                for (int c2 = c1 + 1; c2 < 9; c2++) {
                    if (g.at(r, c2) != 0) {
                        continue;
                    }
                    for (int d = 1; d <= 9; d++) {
                        if (cleanAt(g, r, c1, d) && cleanAt(g, r, c2, d)) {
                            return new int[]{r, c1, c2, d};
                        }
                    }
                }
            }
        }
        return null;
    }

    /** 在 (r,c) 放 d 是否与行 / 列 / 宫的已有数字都不重复。 */
    static boolean cleanAt(SudokuGame g, int r, int c, int d) {
        for (int k = 0; k < 9; k++) {
            if (g.at(r, k) == d || g.at(k, c) == d) {
                return false;
            }
        }
        for (int a = 0; a < 3; a++) {
            for (int b = 0; b < 3; b++) {
                if (g.at((r / 3) * 3 + a, (c / 3) * 3 + b) == d) {
                    return false;
                }
            }
        }
        return true;
    }

    static int[] firstEmpty(SudokuGame g) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                if (g.at(r, c) == 0) {
                    return new int[]{r, c};
                }
            }
        }
        return new int[]{0, 0};
    }
}
