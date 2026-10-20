package com.autogo;

import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.reflect.Field;

/** 2048 页面：入口文案、滑动得分、重开。 */
public class Game2048PageProbe {

    static int pass, fail;
    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) { pass++; }
        else { fail++; System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got); }
    }

    static <T extends View> T find(View v, Class<T> type) {
        if (type.isInstance(v)) return type.cast(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                T r = find(g.getChildAt(i), type);
                if (r != null) return r;
            }
        }
        return null;
    }

    static boolean hasText(View v, String s) {
        if (v instanceof TextView && String.valueOf(((TextView) v).getText()).contains(s)) return true;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) if (hasText(g.getChildAt(i), s)) return true;
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        Game2048Activity a = new Game2048Activity();
        a.onCreate(new Bundle());
        FrameLayout holder = (FrameLayout) a.content;
        LinearLayout root = (LinearLayout) holder.getChildAt(0);
        chk("页面标题是 2048", true, hasText(root, "2048"));
        chk("提示写明滑动规则", true, hasText(root, "滑动合并相同数字"));
        chk("得分从 0 开始", true, hasText(root, "得分 0"));

        Game2048 g = new Game2048(1);
        g.load(new int[]{2, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, 0);
        Field gf = Game2048Activity.class.getDeclaredField("game");
        gf.setAccessible(true);
        gf.set(a, g);
        Game2048View view = find(root, Game2048View.class);
        view.setGame(g);
        view.onTouchEvent(MotionEvent.down(500, 300));
        view.onTouchEvent(MotionEvent.up(100, 300));
        Field sf = Game2048Activity.class.getDeclaredField("scoreView");
        sf.setAccessible(true);
        chk("左滑合并后得分 4", "得分 4", String.valueOf(((TextView) sf.get(a)).getText()));

        Button restart = null;
        for (int i = 0; i < root.getChildCount() && restart == null; i++) {
            View child = root.getChildAt(i);
            if (child instanceof LinearLayout) {
                for (int j = 0; j < ((LinearLayout) child).getChildCount(); j++) {
                    View b = ((LinearLayout) child).getChildAt(j);
                    if (b instanceof Button && "重开".contentEquals(((Button) b).getText())) {
                        restart = (Button) b;
                        break;
                    }
                }
            }
        }
        if (restart == null) throw new AssertionError("找不到重开按钮");
        restart.fakeClick();
        chk("重开后得分归零", "得分 0", String.valueOf(((TextView) sf.get(a)).getText()));

        System.out.println("2048 页面探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) System.exit(1);
    }
}
