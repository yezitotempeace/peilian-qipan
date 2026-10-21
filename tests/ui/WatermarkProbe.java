/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** 水印样式断言：字号 11sp、颜色 textDim、透明度 0.6（改这三样必须变红）。 */
public class WatermarkProbe {

    static int pass, fail;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            fail++;
            System.out.println("FAIL  探针异常：" + t);
        }
        System.out.println("水印样式探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    static void run() throws Exception {
        MenuActivity a = new MenuActivity();
        set(a, "prefs", new android.content.SharedPreferences());
        set(a, "resources", new android.content.res.Resources(new android.content.res.Configuration(), new android.util.DisplayMetrics()));
        call(a, "onCreate", new Class<?>[]{android.os.Bundle.class}, new android.os.Bundle());
        View content = (View) get(a, "content");
        TextView wm = findText(content, Watermark.TEXT);
        chk("页面里找得到水印文字", true, wm != null);
        if (wm == null) {
            return;
        }
        chk("水印字号 11sp", 11.0f, get(wm, "lastTextSize"));
        chk("水印透明度 0.6", 0.6f, get(wm, "alpha"));
        Palette p = (Palette) get(a, "palette");
        chk("水印颜色是主题 textDim", p.textDim, get(wm, "lastTextColor"));
        android.widget.FrameLayout.LayoutParams flp =
                (android.widget.FrameLayout.LayoutParams) callGet(wm, "getLayoutParams");
        boolean padOk = flp != null && flp.topMargin > 0 && flp.topMargin == flp.leftMargin
                && flp.topMargin == flp.rightMargin && flp.topMargin == flp.bottomMargin;
        chk("水印四边留了同样的边距（不为 0）", true, padOk);
    }

    static TextView findText(View v, String text) {
        if (v instanceof TextView) {
            String t = String.valueOf(((TextView) v).getText());
            if (t.equals(text)) {
                return (TextView) v;
            }
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView r = findText(g.getChildAt(i), text);
                if (r != null) {
                    return r;
                }
            }
        }
        return null;
    }

    static void chk(String what, Object exp, Object act) {
        if (String.valueOf(exp).equals(String.valueOf(act))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL  " + what + "（期望 " + exp + "，实际 " + act + "）");
        }
    }

    static void call(Object o, String n, Class<?>[] t, Object... args) throws Exception {
        java.lang.reflect.Method m = o.getClass().getDeclaredMethod(n, t);
        m.setAccessible(true);
        m.invoke(o, args);
    }

    static java.lang.reflect.Field field(Class<?> c, String n) throws Exception {
        while (c != null) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(n);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchFieldException(n);
    }

    static void set(Object o, String n, Object v) throws Exception {
        field(o.getClass(), n).set(o, v);
    }

    static Object callGet(Object o, String n) throws Exception {
        java.lang.reflect.Method m = o.getClass().getMethod(n);
        m.setAccessible(true);
        return m.invoke(o);
    }

    static Object get(Object o, String n) throws Exception {
        return field(o.getClass(), n).get(o);
    }
}
