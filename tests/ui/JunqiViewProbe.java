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

    /** 与 JunqiView.geometry 同一套公式（方格棋盘：5 列 × 12 行，格子是正方形）。 */
    static float[] geom(int w, int h) {
        float pad = Math.max(8f, Math.min(w, h) * 0.03f);
        float cell = Math.min((w - 2 * pad) / JunqiBoard.COLS, (h - 2 * pad) / JunqiBoard.ROWS);
        return new float[]{(w - cell * JunqiBoard.COLS) / 2f, (h - cell * JunqiBoard.ROWS) / 2f, cell};
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
        chk("标准开局 50 块棋子（每块 2 个圆角矩形：边+面）+ 10 个行营圈", "100/10", Canvas.RECTS.size() + "/" + Canvas.CIRCLES.size());

        // 铁路：只有 1/5/6/10 四行横线 + 第 0/4 列两条竖线；行营圆必须是 0.47 格（露在棋子外）
        float cellR = geom(500, 1000)[2];
        float oyR = geom(500, 1000)[1];
        int railLines = 0, railH = 0;
        java.util.TreeSet<Long> railRows = new java.util.TreeSet<Long>();
        for (float[] ln : Canvas.LINES) {
            if (ln[4] == (float) 0x996D4C33) {
                railLines++;
                if (Math.abs(ln[1] - ln[3]) < 0.01f) {
                    railH++;
                    railRows.add(Long.valueOf(Math.round((ln[1] - oyR) / cellR - 0.5f)));
                }
            }
        }
        chk("铁路横线只有 1/5/6/10 四行", 4, railH);
        chk("铁路横线所在行", "[1, 5, 6, 10]", railRows.toString());
        chk("铁路线共 6 条（4 横 + 2 竖）", 6, railLines);
        int campR = 0;
        for (float[] c : Canvas.CIRCLES) {
            if (Math.abs(c[2] - cellR * 0.47f) < 0.01f) {
                campR++;
            }
        }
        chk("行营圆半径 = 0.47 格（露在棋子外）", 10, campR);

        boolean[] hidden = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
        for (int i = 0; i < hidden.length; i++) {
            hidden[i] = std.ownerAt(JunqiBoard.rowOf(i), JunqiBoard.colOf(i)) != JunqiBoard.EMPTY;
        }
        v.setHidden(hidden);
        draw(v);
        chk("暗棋：所有子都盖住，没有文字", 0, Canvas.TEXTS.size());
        chk("暗棋：背面圆 50 个 + 行营 10 个", 60, Canvas.CIRCLES.size());
        chk("暗棋：棋子仍是 50 块（2 个圆角矩形/块）", 100, Canvas.RECTS.size());

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
        t.onTouchEvent(MotionEvent.down(g[0] + 2.5f * g[2], g[1] + 5.5f * g[2]));   // 第 5 行第 2 列格心
        chk("点格子中心命中 (5,2)", 5, got[0]);
        chk("列也对", 2, got[1]);
        got[0] = -1;
        t.onTouchEvent(MotionEvent.down(g[0] + 0.15f * g[2], g[1] + 0.15f * g[2]));  // 左上角格内
        chk("点格子左上角仍在 (0,0)", 0, got[0]);
        chk("列也是 0", 0, got[1]);
        got[0] = -1;
        t.onTouchEvent(MotionEvent.down(g[0] - g[2] * 0.2f, g[1] + 5 * g[2]));
        chk("点棋盘外不落子", -1, got[0]);
        got[0] = -1;
        t.setInputEnabled(false);
        t.onTouchEvent(MotionEvent.down(g[0] + 2.5f * g[2], g[1] + 5.5f * g[2]));
        chk("关掉输入后不回调", -1, got[0]);

        // 棋子块不能压到相邻格：宽 ≤ 0.9 格、高 ≤ 0.76 格
        Canvas.reset();
        v.fakeOnDraw(new Canvas());
        float cell = geom(500, 1000)[2];
        boolean fits = true;
        for (android.graphics.RectF rf : Canvas.RECTS) {
            if (rf.right - rf.left > cell * 0.9f || rf.bottom - rf.top > cell * 0.76f) {
                fits = false;
            }
        }
        chk("棋子块不超出一格（与相邻棋子不重叠）", true, fits);

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
