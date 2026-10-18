package com.autogo;

import android.graphics.Canvas;
import android.view.MotionEvent;
import java.util.Random;

/** 军棋棋盘绘制与触摸的桩测（假 android 类 + 真 JunqiView）。 */
public class JunqiViewProbe {

    static int pass, fail;

    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got);
        }
    }

    static JunqiView view(int w, int h) {
        JunqiView v = new JunqiView(new android.content.Context());
        v.fakeSize(w, h);
        return v;
    }

    static void draw(JunqiView v) {
        Canvas.reset();
        v.fakeOnDraw(new Canvas());
    }

    /** 与 JunqiView.geometry 同一套公式，用来点准格子中心。 */
    static float[] geom(int w, int h) {
        float pad = Math.max(6f, Math.min(w, h) * 0.02f);
        float sp = Math.min((w - 2 * pad) / 4f, (h - 2 * pad) / 11f);
        return new float[]{(w - sp * 4) / 2f, (h - sp * 11) / 2f, sp};
    }

    public static void main(String[] args) {
        JunqiView empty = view(500, 1000);
        empty.setBoard(new JunqiBoard(true));
        draw(empty);
        chk("空盘没有棋子文字", 0, Canvas.TEXTS.size());
        chk("空盘画 10 个行营圈", 10, Canvas.CIRCLES.size());
        chk("空盘没有棋子块", 0, Canvas.RECTS.size());

        JunqiBoard std = new JunqiBoard();
        std.setupStandard(new Random(7));
        JunqiView v = view(500, 1000);
        v.setBoard(std);
        draw(v);
        chk("标准开局 50 个兵种文字", 50, Canvas.TEXTS.size());
        chk("标准开局 50 块棋子 + 10 个行营圈", "50/10", Canvas.RECTS.size() + "/" + Canvas.CIRCLES.size());

        boolean[] hidden = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
        for (int i = 0; i < hidden.length; i++) {
            hidden[i] = std.ownerAt(JunqiBoard.rowOf(i), JunqiBoard.colOf(i)) != JunqiBoard.EMPTY;
        }
        v.setHidden(hidden);
        draw(v);
        chk("暗棋：所有子都盖住，没有文字", 0, Canvas.TEXTS.size());
        chk("暗棋：背面圆 50 个 + 行营 10 个", 60, Canvas.CIRCLES.size());
        chk("暗棋：棋子块仍是 50 块", 50, Canvas.RECTS.size());

        v.setHidden(null);
        v.setSelected(5, 2);
        v.setLegalPoints(new int[]{JunqiBoard.idx(4, 2), JunqiBoard.idx(6, 2)});
        v.setLastMove(5, 2, 4, 2);
        draw(v);
        chk("选中圈 + 2 可走点 + 2 上一手 = 15 个圆", 15, Canvas.CIRCLES.size());
        chk("文字仍是 50 个", 50, Canvas.TEXTS.size());

        JunqiView t = view(500, 1000);
        t.setBoard(std);
        final int[] got = new int[]{-1, -1};
        t.setOnCellTap(new JunqiView.OnCellTap() {
            public void onTap(int row, int col) {
                got[0] = row;
                got[1] = col;
            }
        });
        float[] g = geom(500, 1000);
        t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2], g[1] + 5 * g[2]));
        chk("点棋盘中心命中 (5,2)", 5, got[0]);
        chk("列也对", 2, got[1]);
        got[0] = -1;
        t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2] + g[2] * 0.45f, g[1] + 5 * g[2] + g[2] * 0.45f));
        chk("斜偏 0.45 格（离最近交点 0.64 格）不落子", -1, got[0]);
        got[0] = -1;
        t.setInputEnabled(false);
        t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2], g[1] + 5 * g[2]));
        chk("关掉输入后不回调", -1, got[0]);

        JunqiView sw = view(500, 1000);
        sw.setBoard(std);
        sw.setBoard(new JunqiBoard(true));
        draw(sw);
        chk("换盘后按空盘画", 0, Canvas.TEXTS.size());

        System.out.println("军棋棋盘探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }
}
