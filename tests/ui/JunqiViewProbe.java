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

    /** 与 JunqiView.geometry 同一套公式：列距 unit、行距 0.6 unit、整盘 4.8×8.4 unit。 */
    static float[] geom(int w, int h) {
        float unit = Math.min(w / 4.8f, h / 8.4f);
        float cw = unit, ch = unit * 0.6f;
        float ox = (w - 4.8f * cw) / 2f + 0.4f * cw;
        float oy = (h - 8.4f * cw) / 2f + 0.5f * ch;
        return new float[]{ox, oy, cw, ch};
    }

    static float cyOf(float[] g, int row) {
        return g[1] + row * g[3] + (row >= 6 ? 2f * g[3] : 0f);
    }

    public static void main(String[] args) {
        JunqiView empty = view(500, 1000);
        empty.setBoard(new JunqiBoard(true));
        draw(empty);
        chk("空盘只画行营10/大本营4/山界2/前线3 = 19 个标记字，不写 60 个兵站", 19, Canvas.TEXTS.size());
        chk("空盘画 20 个行营圆（填+描边）+ 4 个山界圆 = 24", 24, Canvas.CIRCLES.size());
        chk("空盘画 50 个兵站/大本营（兵站双描边）", 96, Canvas.RECTS.size());

        JunqiBoard std = new JunqiBoard();
        std.setupStandard(new Random(7));
        JunqiView v = view(500, 1000);
        v.setBoard(std);
        draw(v);
        chk("标准开局至少 50 个兵种字", true, Canvas.TEXTS.size() >= 50);
        chk("标准开局不再画 60 个「兵站」字（总数 < 65）", true, Canvas.TEXTS.size() < 65);
        chk("标准开局 96 站点框 + 100 棋子框 / 24 圆（行营+山界）", "196/24", Canvas.RECTS.size() + "/" + Canvas.CIRCLES.size());

        // 铁路：4 条横 + 2 条竖 + 中线 1 小段；线是黑底白虚线，从站点后面穿过
        float[] g0 = geom(500, 1000);
        float cw0 = g0[2], ch0 = g0[3], oy0 = g0[1];
        int railLines = 0, railH = 0;
        java.util.TreeSet<Long> railRows = new java.util.TreeSet<Long>();
        for (float[] ln : Canvas.LINES) {
            if (ln[4] == (float) 0xFF161616) {
                railLines++;
                if (Math.abs(ln[1] - ln[3]) < 0.01f) {
                    railH++;
                    int best = 0;
                    float bd = Float.MAX_VALUE;
                    for (int rr = 0; rr < 12; rr++) {
                        float d = Math.abs(ln[1] - cyOf(g0, rr));
                        if (d < bd) { bd = d; best = rr; }
                    }
                    railRows.add(Long.valueOf(best));
                }
            }
        }
        chk("铁路横线只有 1/5/6/10 四行", 4, railH);
        chk("铁路横线所在行", "[1, 5, 6, 10]", railRows.toString());
        chk("铁路共 7 段（4 横 + 2 竖 + 中线 1 小段）", 7, railLines);
        int campR = 0;
        for (float[] c : Canvas.CIRCLES) {
            if (Math.abs(c[2] - cw0 * 0.20f) < 0.01f) {
                campR++;
            }
        }
        chk("行营圆半径 = 0.20 列距（填充+描边各 10）", 20, campR);

        boolean[] hidden = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
        for (int i = 0; i < hidden.length; i++) {
            hidden[i] = std.ownerAt(JunqiBoard.rowOf(i), JunqiBoard.colOf(i)) != JunqiBoard.EMPTY;
        }
        v.setHidden(hidden);
        draw(v);
        chk("暗棋：不再画 50 个兵种字，只剩站点标记", true, Canvas.TEXTS.size() <= 15 && !Canvas.TEXTS.contains("司令"));
        chk("暗棋：背面圆 50 + 行营 20 + 山界 4 = 74", 74, Canvas.CIRCLES.size());
        chk("暗棋：站点框 96 + 棋子框 100", 196, Canvas.RECTS.size());

        v.setHidden(null);
        v.setSelected(5, 2);
        v.setLegalPoints(new int[]{JunqiBoard.idx(4, 2), JunqiBoard.idx(6, 2)});
        v.setLastMove(5, 2, 4, 2);
        draw(v);
        chk("24 圆 + 2 可走点 = 26（上一手/选中是站点框）", 26, Canvas.CIRCLES.size());
        chk("选中后仍不画 60 个「兵站」字", true, Canvas.TEXTS.size() < 65);

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
        t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2], g[1] + 5 * g[3]));   // 第 5 行第 2 列站点心
        chk("点格子中心命中 (5,2)", 5, got[0]);
        chk("列也对", 2, got[1]);
        got[0] = -1;
        t.onTouchEvent(MotionEvent.down(g[0], g[1]));  // (0,0) 站点心
        chk("点 (0,0) 站点心命中", 0, got[0]);
        chk("列也是 0", 0, got[1]);
        got[0] = -1;
        t.onTouchEvent(MotionEvent.down(g[0] - g[2] * 0.6f, g[1] + 5 * g[3]));
        chk("点棋盘外不落子", -1, got[0]);
        // 下半场（人执红 6..11 行）必须点得中：纵坐标要扫 12 行，不能按 row*ch 反算
        for (int rr : new int[]{6, 9, 11}) {
            got[0] = -1;
            t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2], cyOf(g, rr)));
            chk("下半场第 " + rr + " 行点得中", rr, got[0]);
        }
        got[0] = -1;
        t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2], (cyOf(g, 5) + cyOf(g, 6)) / 2f));
        chk("中线空带（山界之间）不落子", -1, got[0]);
        got[0] = -1;
        t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2], cyOf(g, 2) + g[3] * 0.47f));
        chk("纵坐标 ±0.47 行距仍命中第 2 行", 2, got[0]);
        got[0] = -1;
        t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2], cyOf(g, 2) + g[3] * 0.49f));
        chk("纵坐标 ±0.49 行距不落子", -1, got[0]);
        got[0] = -1;
        t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2] + g[2] * 0.47f, cyOf(g, 3)));
        chk("横坐标 ±0.47 列距仍命中第 2 列", 2, got[1]);
        got[0] = -1;
        t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2] + g[2] * 0.49f, cyOf(g, 3)));
        chk("横坐标 ±0.49 列距不落子", -1, got[0]);
        got[0] = -1;
        t.setInputEnabled(false);
        t.onTouchEvent(MotionEvent.down(g[0] + 2 * g[2], g[1] + 5 * g[3]));
        chk("关掉输入后不回调", -1, got[0]);

        // 棋子块/站点框不能超出各自单位：站点宽 0.4 unit、高 0.2 unit，棋子 ≤ 0.376×0.188
        Canvas.reset();
        v.fakeOnDraw(new Canvas());
        float cwF = geom(500, 1000)[2];
        int bad = 0, hqWide = 0, rings = 0;
        for (android.graphics.RectF rf : Canvas.RECTS) {
            float rw = rf.right - rf.left, rh = rf.bottom - rf.top;
            if (Math.abs(rw - cwF * 0.60f) < 0.02f && Math.abs(rh - cwF * 0.20f) < 0.02f) {
                hqWide++;
            }
            if (rw > cwF * 0.48f && rh > cwF * 0.27f) {
                rings++;
            }
            // 棋子/兵站：宽 ≤0.4、高 ≤0.2；大本营：宽 0.6、高 0.2
            if (rw <= cwF * 0.45f && (rw > cwF * 0.401f || rh > cwF * 0.201f)) {
                bad++;
            }
        }
        chk("棋子/兵站不超 0.4×0.2 unit", 0, bad);
        chk("大本营 4 个 0.6×0.2 unit（比兵站宽一半）", 4, hqWide);
        chk("选中/上一手 3 个标记框", 3, rings);

        JunqiView sw = view(500, 1000);
        sw.setBoard(std);
        sw.setBoard(new JunqiBoard(true));
        draw(sw);
        chk("换盘后仍不画 60 个「兵站」字", true, Canvas.TEXTS.size() < 65);

        System.out.println("军棋棋盘探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }
}
