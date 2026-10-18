package com.autogo;

import android.content.Intent;
import android.widget.TextView;

/** 象棋复盘页桩测（第一份）：读参、长度门槛、选中夹取、视角换算、失误文字。 */
public class XiangqiReviewProbe {

    static int pass, fail;

    public static void main(String[] args) throws Exception {
        // ① 4 手棋：红 兵(6,0)->(5,0)、黑 卒(3,0)->(4,0)、红 马(9,1)->(7,2)、黑 马(0,1)->(2,2)
        int[] mv = {6, 0, 5, 0, 3, 0, 4, 0, 9, 1, 7, 2, 0, 1, 2, 2};
        double[] wr = {0.60, 0.80, 0.55, 0.70, 1.0};
        double[] loss = {0.12, 0.30, 0.05, 0.02};
        String[] best = {"H2-H3", "B8-B7", "C1-E2", "H9-G7", ""};
        int[] bestpt = {1, 2, 3, 4, 5};

        XiangqiReviewActivity a = open(intent(mv, wr, loss, best, bestpt, "对局进行中"));
        chk("读入 4 手", 4, get(a, "n"));
        chk("预计算数组被采纳", true, get(a, "wr") != null);
        chk("初始选中最后一手", 4, get(a, "sel"));
        chk("终局态条标签", "终局", label(a));
        chk("位置文字写第 4 手", true, position(a).contains("第 4 手"));

        callInt(a, "select", 1);
        chk("选中第 1 手", 1, get(a, "sel"));
        chk("条标签写第 1 手走完", "第 1 手走完", label(a));
        chk("轮到黑走（奇偶）", true, position(a).contains("轮黑走"));
        chk("黑方 0.8 折算成红方 20%", true, position(a).contains("20%"));
        chk("上一手亏损百分比", true, position(a).contains("上一手亏 12%"));

        XiangqiView xv = (XiangqiView) get(a, "boardView");
        callInt(a, "select", 1);
        chk("推荐点下发到棋盘（第 1 手位置）", 2, get(xv, "hint"));
        callInt(a, "select", 99);
        chk("终局不画推荐点", -1, get(xv, "hint"));
        chk("下标夹到 4", 4, get(a, "sel"));
        callInt(a, "select", -3);
        chk("负下标夹到 0", 0, get(a, "sel"));
        chk("开局标签", "开局前（红先）", label(a));
        callInt(a, "select", 2);
        chk("曲线选中的就是第 2 手", 2, get(get(a, "chart"), "selected"));
        com.autogo.Palette pal2 = (com.autogo.Palette) get(a, "palette");
        java.util.List<android.view.View> rows2 =
                (java.util.List<android.view.View>) get(a, "moveRows");
        chk("第 2 手那一行高亮成 surface 色", pal2.surface, rows2.get(2).bgColor);
        chk("其它行没有高亮（第 1 手）", 0, rows2.get(1).bgColor);

        XiangqiReviewActivity b = null;
        try {
            b = open(intent(mv, new double[]{0.5, 0.5, 0.5, 0.5}, loss, best, bestpt, null));
        } catch (Throwable ex) {
            fail++;
            System.out.println("FAIL  长度不对时不该崩（实际 " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + "）");
        }
        if (b != null) {
            chk("长度不对 ⇒ 预计算数组不采纳", true, get(b, "wr") == null);
            chk("长度不对仍能画（胜率回退 50%）", true, position(b).contains("50%"));
        }

        XiangqiReviewActivity c = open(new Intent());
        chk("空 Intent ⇒ 0 手", 0, get(c, "n"));
        chk("空棋谱位置文字写开局", true, position(c).contains("开局"));

        System.out.println("象棋复盘页探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    static Intent intent(int[] mv, double[] wr, double[] loss, String[] best, int[] bestpt, String end) {
        Intent it = new Intent();
        it.putExtra(XiangqiReviewActivity.EXTRA_MOVES, mv);
        it.putExtra(XiangqiReviewActivity.EXTRA_WR, wr);
        it.putExtra(XiangqiReviewActivity.EXTRA_LOSS, loss);
        it.putExtra(XiangqiReviewActivity.EXTRA_BEST, best);
        it.putExtra(XiangqiReviewActivity.EXTRA_BESTPT, bestpt);
        it.putExtra(XiangqiReviewActivity.EXTRA_VERDICT, "红方从头领先到尾");
        if (end != null) {
            it.putExtra(XiangqiReviewActivity.EXTRA_END, end);
        }
        return it;
    }

    static XiangqiReviewActivity open(Intent it) throws Exception {
        XiangqiReviewActivity a = new XiangqiReviewActivity();
        set(a, "prefs", new android.content.SharedPreferences());
        set(a, "resources", new android.content.res.Resources(new android.content.res.Configuration(), new android.util.DisplayMetrics()));
        set(a, "intent", it);
        call(a, "onCreate", new Class<?>[]{android.os.Bundle.class}, new android.os.Bundle());
        return a;
    }

    static String label(XiangqiReviewActivity a) throws Exception {
        WinrateBarView bar = (WinrateBarView) get(a, "bar");
        return String.valueOf(get(bar, "label"));
    }

    static String position(XiangqiReviewActivity a) throws Exception {
        return String.valueOf(((TextView) get(a, "positionText")).getText());
    }

    static void chk(String what, Object exp, Object act) {
        if (String.valueOf(exp).equals(String.valueOf(act))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL  " + what + "（期望 " + exp + "，实际 " + act + "）");
        }
    }

    static java.lang.reflect.Field field(Class<?> c, String name) throws Exception {
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    static void set(Object o, String name, Object v) throws Exception {
        field(o.getClass(), name).set(o, v);
    }

    static Object get(Object o, String name) throws Exception {
        return field(o.getClass(), name).get(o);
    }

    static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method m = o.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(o, args);
    }

    static Object callInt(Object o, String name, int v) throws Exception {
        java.lang.reflect.Method m = o.getClass().getDeclaredMethod(name, int.class);
        m.setAccessible(true);
        return m.invoke(o, v);
    }
}
