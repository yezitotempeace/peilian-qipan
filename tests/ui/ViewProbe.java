package com.autogo;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * 真 GomokuView 的触摸/几何桩测：假 android 类 + 真源码。
 * 覆盖：交点吸附与容差、越界、输入开关、换路数后几何重算、全屏返回按钮的点击判定。
 */
public class ViewProbe {
    static int pass = 0, fail = 0;
    static int tapped = Integer.MIN_VALUE;
    static int exits = 0;

    static void chk(String what, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            pass++;
            System.out.println("PASS  " + what + " = " + actual);
        } else {
            fail++;
            System.out.println("FAIL  " + what + "（期望 " + expected + "，实际 " + actual + "）");
        }
    }

    static android.content.Context ctx() {
        android.content.Context c = new android.content.Context();
        c.resources = new android.content.res.Resources(new android.content.res.Configuration(),
                new android.util.DisplayMetrics());
        c.resources.getDisplayMetrics().density = 2f;
        return c;
    }

    static GomokuView make(android.content.Context c, int n, int w, int h) {
        GomokuView v = new GomokuView(c);
        v.setOnIntersectionTap(new GomokuView.OnIntersectionTap() {
            public void onTap(int point) { tapped = point; }
        });
        v.setOnExitFullscreen(new Runnable() {
            public void run() { exits++; }
        });
        v.setBoard(new GomokuBoard(n, false));
        v.fakeSize(w, h);
        v.fakeOnSizeChanged(w, h, 0, 0);
        Canvas.RECTS.clear();
        v.fakeOnDraw(new Canvas());
        return v;
    }

    static void tap(GomokuView v, float x, float y) {
        tapped = -1;
        v.onTouchEvent(MotionEvent.down(x, y));
    }

    static void tapAt(GomokuView v, LayoutMath.BoardGeometry g, int row, int col) {
        tap(v, g.padX + col * g.spacing, g.padY + row * g.spacing);
    }


    static float fld(GomokuView v, String name) throws Exception {
        java.lang.reflect.Field f = GomokuView.class.getDeclaredField(name);
        f.setAccessible(true);
        return ((Number) f.get(v)).floatValue();
    }

    static boolean hasRadius(java.util.List<float[]> cs, float want) {
        for (float[] c : cs) if (Math.abs(c[2] - want) <= want * 0.05f + 0.01f) return true;
        return false;
    }

    static boolean near(float a, float b, float tol) { return Math.abs(a - b) <= tol; }

    static boolean hasLineOfLength(java.util.List<float[]> ls, float want, float tol) {
        for (float[] l : ls) {
            float len = (float) Math.hypot(l[2] - l[0], l[3] - l[1]);
            if (Math.abs(len - want) <= tol) return true;
        }
        return false;
    }

    /** 单字母坐标文字按顺序拼起来（列标签）。 */
    static String letterLabels(java.util.List<String> ts) {
        StringBuilder sb = new StringBuilder();
        for (String t : ts) if (t.length() == 1 && t.charAt(0) >= 'A' && t.charAt(0) <= 'Z') sb.append(t);
        return sb.toString();
    }

    /** 所有圆的半径都等于 want。 */
    static boolean allRadius(java.util.List<float[]> cs, float want) {
        if (cs.isEmpty()) return false;
        for (float[] c : cs) if (Math.abs(c[2] - want) > 0.01f) return false;
        return true;
    }

    /** 有一个圆心在 (x, y)、半径为 want 的圆。 */
    static boolean hasCircleAt(java.util.List<float[]> cs, float x, float y, float want) {
        for (float[] c : cs) {
            if (Math.abs(c[0] - x) < 0.5f && Math.abs(c[1] - y) < 0.5f && Math.abs(c[2] - want) < 0.01f) return true;
        }
        return false;
    }

    static int countText(java.util.List<String> ts, String s) {
        int k = 0;
        for (String t : ts) if (t.equals(s)) k++;
        return k;
    }

    /** 绘制层：网格 / 坐标 / 星位 / 石身与边 / 最后一手标记 / 赢线 / 手顺数字 / 推荐点（自查发现：真 GomokuView 原先只测了几何与触摸，没测绘制）。 */
    static void testDraw() throws Exception {
        android.content.Context c = ctx();
        final int n = 15;
        final int BW = 900, BH = 1200;
        GomokuView v = make(c, n, BW, BH);
        float spacing = fld(v, "spacing");
        float sr = fld(v, "stoneRadius");
        Canvas.reset();
        v.fakeOnDraw(new Canvas());
        chk("绘制①：空盘网格线 2×15 条 + 立体边 4 条", 34, Canvas.LINES.size());
        chk("绘制①：空盘坐标文字 2×15 个", 30, Canvas.TEXTS.size());
        chk("绘制①：空盘只有 9 个星位小圆", 9, Canvas.CIRCLES.size());
        chk("绘制①：列坐标跳过 I（第 8 列是 H）", 1, countText(Canvas.TEXTS, "H"));
        chk("绘制①：15 个列标签正好是 A..H + J..P（跳过 I）", "ABCDEFGHJKLMNOP", letterLabels(Canvas.TEXTS));
        chk("绘制①：坐标里没有字母 I", 0, countText(Canvas.TEXTS, "I"));
        chk("绘制①：星位小圆半径 = max(2, 0.075×spacing)", true, allRadius(Canvas.CIRCLES, Math.max(2f, spacing * 0.075f)));
        chk("绘制①：行坐标从下往上（15 与 1 各一个）", 2, countText(Canvas.TEXTS, "15") + countText(Canvas.TEXTS, "1"));

        GomokuBoard b = new GomokuBoard(n, false);
        int p = b.idx(7, 7);
        b.play(GomokuBoard.BLACK, p);
        v.setBoard(b);
        v.setLastMove(p);
        Canvas.reset();
        v.fakeOnDraw(new Canvas());
        chk("绘制②：一颗子 = 星位 9 + 阴影 + 石身 + 边 + 高光 + 最后一手标记 = 14 个圆", 14, Canvas.CIRCLES.size());
        chk("绘制②：石身半径与几何 stoneRadius 一致", true, hasRadius(Canvas.CIRCLES, sr));
        chk("绘制②：最后一手有 0.34×stoneRadius 的标记圈", true, hasRadius(Canvas.CIRCLES, sr * 0.34f));
        float px = fld(v, "padX"), py = fld(v, "padY");
        chk("绘制②：最后一手标记圈圆心就在最后一手那个交点上",
                true, hasCircleAt(Canvas.CIRCLES, px + 7 * spacing, py + 7 * spacing, sr * 0.34f));

        GomokuBoard w = new GomokuBoard(n, false);
        for (int col = 3; col <= 7; col++) w.play(GomokuBoard.BLACK, w.idx(7, col));
        v.setBoard(w);
        v.setLastMove(w.idx(7, 7));
        v.setWinLine(new int[]{w.idx(7, 3), w.idx(7, 4), w.idx(7, 5), w.idx(7, 6), w.idx(7, 7)});
        Canvas.reset();
        v.fakeOnDraw(new Canvas());
        chk("绘制③：五连多画 1 条赢线（网格 30 + 立体边 4 + 赢线 1）", 35, Canvas.LINES.size());
        chk("绘制③：赢线长度 = 4×spacing（穿过五颗子）", true, hasLineOfLength(Canvas.LINES, spacing * 4f, spacing * 0.2f));
        chk("绘制③：五连时最后一手仍有标记圈", true, hasRadius(Canvas.CIRCLES, sr * 0.34f));

        v.setShowNumbers(true);
        Canvas.reset();
        v.fakeOnDraw(new Canvas());
        chk("绘制④：开手顺后每颗子多一个数字（2×15 + 5）", 35, Canvas.TEXTS.size());
        v.setShowNumbers(false);

        v.setHighlight(new int[]{w.idx(9, 9)});
        Canvas.reset();
        v.fakeOnDraw(new Canvas());
        chk("绘制⑤：推荐点画 0.92×stoneRadius 的提示圈", true, hasRadius(Canvas.CIRCLES, sr * 0.92f));
        v.setHighlight(null);
    }

    public static void main(String[] args) {
        final int W = 900, H = 1200;
        android.content.Context c = ctx();
        LayoutMath.BoardGeometry g15 = LayoutMath.boardGeometry(W, H, 15, 0f);
        GomokuView v = make(c, 15, W, H);

        tapAt(v, g15, 7, 7);
        chk("15 路：点正中交点 (7,7)", 112, tapped);
        tapAt(v, g15, 0, 0);
        chk("15 路：点左上交点 (0,0)", 0, tapped);
        tapAt(v, g15, 14, 14);
        chk("15 路：点右下交点 (14,14)", 224, tapped);

        tap(v, g15.padX + 7 * g15.spacing + g15.spacing * 0.4f, g15.padY + 7 * g15.spacing);
        chk("偏离 0.4 格：仍吸附到最近交点", 112, tapped);
        tap(v, g15.padX + 7 * g15.spacing + g15.spacing * 0.5f, g15.padY + 7 * g15.spacing + g15.spacing * 0.5f);
        chk("点在格子正中（离最近交点 0.707 格）：超过容差不落子", -1, tapped);
        tap(v, g15.padX - 3 * g15.spacing, g15.padY + 7 * g15.spacing);
        chk("棋盘左外侧：不落子", -1, tapped);

        tapped = -1;
        v.onTouchEvent(MotionEvent.up(g15.padX + 7 * g15.spacing, g15.padY + 7 * g15.spacing));
        chk("ACTION_UP：不落子（只在按下时判定）", -1, tapped);

        v.setInputEnabled(false);
        tapAt(v, g15, 7, 7);
        chk("inputEnabled=false：不落子", -1, tapped);
        v.setInputEnabled(true);

        v.setBoard(null);
        tapAt(v, g15, 7, 7);
        chk("board=null：不落子且不抛异常", -1, tapped);

        GomokuView v2 = make(c, 15, W, H);
        v2.setOnIntersectionTap(null);
        v2.setBoard(new GomokuBoard(15, false));
        tapAt(v2, g15, 7, 7);
        chk("tapListener=null：不落子且不抛异常", -1, tapped);

        // 换路数：几何必须先清零再重算（否则会按旧间距算错点号）
        GomokuView v3 = make(c, 15, W, H);
        v3.setBoard(new GomokuBoard(19, false));
        tapAt(v3, g15, 7, 7);
        chk("换 19 路但几何未重算：按旧间距不乱落子", -1, tapped);
        v3.fakeOnSizeChanged(W, H, 0, 0);
        Canvas.RECTS.clear();
        v3.fakeOnDraw(new Canvas());
        LayoutMath.BoardGeometry g19 = LayoutMath.boardGeometry(W, H, 19, 0f);
        tapAt(v3, g19, 0, 0);
        chk("19 路重算后：点 (0,0)", 0, tapped);
        tapAt(v3, g19, 18, 18);
        chk("19 路重算后：点 (18,18)", 360, tapped);

        GomokuView v4 = make(c, 13, W, H);
        LayoutMath.BoardGeometry g13 = LayoutMath.boardGeometry(W, H, 13, 0f);
        tapAt(v4, g13, 12, 12);
        chk("13 路：点 (12,12)", 168, tapped);

        int clicks0 = View.performClicks;
        tapAt(v4, g13, 6, 6);
        tapAt(v4, g13, 6, 7);
        chk("每次命中都触发一次 performClick", 2, View.performClicks - clicks0);

        // 全屏返回按钮：先判按钮、且不受 inputEnabled 影响
        exits = 0;
        GomokuView v5 = make(c, 15, W, H);
        tap(v5, 46.8f, 46.8f);
        chk("非全屏：左上角不触发退出全屏", 0, exits);

        v5.setFullscreen(true);
        v5.fakeOnSizeChanged(W, H, 0, 0);
        Canvas.RECTS.clear();
        v5.fakeOnDraw(new Canvas());
        chk("全屏时画出了返回按钮（drawRoundRect 一次）", 1, Canvas.RECTS.size());
        RectF btn = Canvas.RECTS.isEmpty() ? new RectF(46.8f, 46.8f, 296.4f, 150.8f) : Canvas.RECTS.get(0);
        tap(v5, btn.centerX(), btn.centerY());
        chk("全屏：点按钮中心退出全屏", 1, exits);
        tap(v5, btn.centerX(), btn.bottom + btn.height() * 2f);
        chk("全屏：点按钮下方（超出容差）不退出", 1, exits);
        v5.setInputEnabled(false);
        tap(v5, btn.centerX(), btn.centerY());
        chk("全屏：inputEnabled=false 也点得动返回按钮", 2, exits);
        v5.setInputEnabled(true);
        LayoutMath.BoardGeometry g15fs = LayoutMath.boardGeometry(W, H, 15,
                btn.height() + 2f * 10f);
        tapAt(v5, g15fs, 7, 7);
        chk("全屏：棋盘下移后点 (7,7) 仍命中", 112, tapped);

        GomokuView v6 = make(c, 15, W, H);
        v6.setShowNumbers(true);
        chk("setShowNumbers(true) 往返", Boolean.TRUE, v6.getShowNumbers());
        v6.setShowNumbers(false);
        chk("setShowNumbers(false) 往返", Boolean.FALSE, v6.getShowNumbers());
        GomokuBoard b6 = new GomokuBoard(15, false);
        b6.play(GomokuBoard.BLACK, b6.idx(7, 7));
        b6.play(GomokuBoard.WHITE, b6.idx(7, 8));
        v6.setBoard(b6);
        v6.setLastMove(b6.idx(7, 8));
        v6.setHighlight(new int[]{b6.idx(5, 5)});
        v6.setWinLine(new int[]{b6.idx(7, 7), b6.idx(7, 8)});
        v6.setShowNumbers(true);
        v6.setPalette(Palette.of(true));
        v6.fakeOnSizeChanged(W, H, 0, 0);
        boolean drew = true;
        try {
            Canvas.RECTS.clear();
            v6.fakeOnDraw(new Canvas());
        } catch (RuntimeException e) {
            drew = false;
        }
        chk("有子/高亮/获胜线/手顺/深色配色下重绘不抛异常", Boolean.TRUE, drew);
        chk("深色配色下重绘也画过棋子（translate 调用）", Boolean.TRUE, Canvas.translateCount > 0);

        // onMeasure：非全屏取正方形（较短边），全屏铺满；横屏规格专门用来区分 min(w,h) 与 w
        GomokuView v7 = make(c, 15, W, H);
        int specPortW = View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY);   // 900
        int specPortH = View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY);   // 1200
        int specLandW = View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY);   // 1200
        int specLandH = View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY);   // 900
        v7.fakeOnMeasure(specPortW, specPortH);
        chk("非全屏 onMeasure（竖屏 900x1200）：取较短边成正方形", "900x900", v7.measuredW + "x" + v7.measuredH);
        v7.fakeOnMeasure(specLandW, specLandH);
        chk("非全屏 onMeasure（横屏 1200x900）：仍取较短边（不是直接用量宽）", "900x900", v7.measuredW + "x" + v7.measuredH);
        v7.setFullscreen(true);
        v7.fakeOnMeasure(specPortW, specPortH);
        chk("全屏 onMeasure（竖屏 900x1200）：铺满可用区域", "900x1200", v7.measuredW + "x" + v7.measuredH);
        v7.fakeOnMeasure(specLandW, specLandH);
        chk("全屏 onMeasure（横屏 1200x900）：铺满可用区域", "1200x900", v7.measuredW + "x" + v7.measuredH);

        try { testDraw(); } catch (Exception e) { chk("绘制组不该抛异常", "无异常", e.toString()); }

        System.out.println("界面层探针（真 GomokuView）：PASS " + pass + " / FAIL " + fail);
        System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
