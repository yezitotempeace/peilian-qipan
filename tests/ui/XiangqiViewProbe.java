package com.autogo;

import android.graphics.Canvas;

/** 象棋棋盘视图的桩测探针（先打印实测计数，再逐条钉断）。 */
public class XiangqiViewProbe {
    static int pass = 0, fail = 0;

    static void chk(String what, Object exp, Object act) {
        if (String.valueOf(exp).equals(String.valueOf(act))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what + "（期望 " + exp + "，实际 " + act + "）");
        }
    }

    static void ok(String what, boolean c) {
        if (c) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL " + what);
        }
    }

    static XiangqiView make() {
        XiangqiView v = new XiangqiView(new android.content.Context());
        v.setPalette(com.autogo.Palette.of(false));
        v.fakeSize(900, 1000);
        return v;
    }

    public static void main(String[] a) {
        // 1) 标准开局：线/圆/文字数量
        XiangqiView v = make();
        v.setBoard(new XiangqiBoard());
        Canvas.reset();
        v.fakeOnDraw(new android.graphics.Canvas());
        chk("标准局：线数 = 10 横 + 16 竖段 + 4 九宫 + 4 立体边", 34, Canvas.LINES.size());
        chk("标准局：圆数 = 32 子 × 4（阴影/棋身/圈/高光）", 128, Canvas.CIRCLES.size());
        chk("标准局：文字数 = 2 河界 + 9 列标 + 10 行号 + 32 子", 53, Canvas.TEXTS.size());
        chk("标准局：32 次平移（每个子一次）", 32, Canvas.translateCount);
        java.util.List<String> tx = Canvas.TEXTS;
        ok("列标是 A..I（不含 J）", tx.contains("A") && tx.contains("I") && !tx.contains("J"));
        ok("行号含 1 与 10", tx.contains("1") && tx.contains("10"));
        ok("有「楚  河」", tx.contains("楚  河"));
        ok("有「漢  界」", tx.contains("漢  界"));
        ok("红方七种字都出现", tx.contains("帅") && tx.contains("仕") && tx.contains("相")
                && tx.contains("马") && tx.contains("车") && tx.contains("炮") && tx.contains("兵"));
        ok("黑方七种字都出现", tx.contains("将") && tx.contains("士") && tx.contains("象")
                && tx.contains("马") && tx.contains("车") && tx.contains("炮") && tx.contains("卒"));

        // 2) 空盘
        XiangqiView e = make();
        e.setBoard(new XiangqiBoard(true));
        Canvas.reset();
        e.fakeOnDraw(new android.graphics.Canvas());
        chk("空盘：没有棋子圆", 0, Canvas.CIRCLES.size());
        chk("空盘：线仍是 34", 34, Canvas.LINES.size());
        chk("空盘：文字只剩 2 河界 + 9 + 10", 21, Canvas.TEXTS.size());

        // 3) 选中 + 可走点 + 上一手
        XiangqiView s = make();
        XiangqiBoard b = new XiangqiBoard();
        s.setBoard(b);
        s.setSelected(9, 0);            // 红车
        s.setLegalPoints(new int[]{8 * XiangqiBoard.COLS + 0, 7 * XiangqiBoard.COLS + 0});
        s.setLastMove(9 * XiangqiBoard.COLS + 0, 7 * XiangqiBoard.COLS + 0);
        Canvas.reset();
        s.fakeOnDraw(new android.graphics.Canvas());
        chk("选中/可走点/上一手：圆 = 128 + 2 上一手 + 1 选中 + 2 可走点", 133, Canvas.CIRCLES.size());
        chk("这几样不改变线数", 34, Canvas.LINES.size());
        s.setBoard(new XiangqiBoard(true));
        Canvas.reset();
        s.fakeOnDraw(new android.graphics.Canvas());
        chk("换棋局后清掉选中与上一手（空盘只剩 0 圆）", 0, Canvas.CIRCLES.size());

        // 4) 触摸命中与落点回读
        final int[] got = {-1, -1};
        XiangqiView t = make();
        t.setBoard(new XiangqiBoard());
        t.setOnIntersectionTap(new XiangqiView.OnIntersectionTap() {
            public void onTap(int row, int col) {
                got[0] = row;
                got[1] = col;
            }
        });
        t.fakeOnSizeChanged(900, 1000, 0, 0);
        Canvas.reset();
        t.fakeOnDraw(new android.graphics.Canvas());                 // 让 recompute 生效
        float padX = getF(t, "padX"), padY = getF(t, "padY"), sp = getF(t, "spacing");
        ok("几何：间距为正、左右对称", sp > 0f && Math.abs(900f - (padX * 2f + 8f * sp)) < 0.01f);
        chk("几何：上下对称", true, Math.abs(1000f - (padY * 2f + 9f * sp)) < 0.01f);
        t.onTouchEvent(touch(padX + 3 * sp, padY + 7 * sp));
        chk("点 (7,3) 收到 (7,3)", "7,3", got[0] + "," + got[1]);
        got[0] = -1;
        t.onTouchEvent(touch(padX + 3 * sp + sp * 0.30f, padY + 7 * sp));
        chk("偏 0.30 格仍算同一交点", "7,3", got[0] + "," + got[1]);
        got[0] = -1;
        t.onTouchEvent(touch(padX + 3 * sp + sp * 0.5f, padY + 7 * sp + sp * 0.5f));
        chk("格子正中（离交点最远处）不触发回调", "-1", String.valueOf(got[0]));
        got[0] = -1;
        t.setInputEnabled(false);
        t.onTouchEvent(touch(padX + 3 * sp, padY + 7 * sp));
        chk("输入关掉后不回调", "-1", String.valueOf(got[0]));

        System.out.println("象棋棋盘视图探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    static float getF(Object o, String name) {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return ((Number) f.get(o)).floatValue();
            } catch (NoSuchFieldException ex) {
                c = c.getSuperclass();
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        throw new RuntimeException("找不到字段 " + name);
    }

    static android.view.MotionEvent touch(float x, float y) {
        return android.view.MotionEvent.down(x, y);
    }
}
