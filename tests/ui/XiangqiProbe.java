package com.autogo;

import android.widget.TextView;

/** 象棋对局页桩测（第一份）：开局、走子、悔棋、难度、认输、全屏、空盘复盘。 */
public class XiangqiProbe {

    static int pass, fail;

    public static void main(String[] args) {
        try {
            run();
        } catch (Throwable t) {
            fail++;
            System.out.println("FAIL  探针异常：" + t);
        }
        System.out.println("象棋对局页探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    static void run() throws Exception {
        XiangqiActivity a = newPage();
        XiangqiBoard b = (XiangqiBoard) get(a, "board");
        chk("开局棋盘有子", true, b != null);
        chk("开局 0 手", 0, b.moveCount());
        chk("轮到红方", XiangqiBoard.RED, b.sideToMove());
        chk("状态条写了执红先走", true, status(a).contains("红"));

        // 人走一步：红兵 (6,0) → (5,0)
        callPlay(a, 6, 0, 5, 0);
        chk("走一手后手数 1", 1, b.moveCount());
        chk("轮到黑方", XiangqiBoard.BLACK, b.sideToMove());

        set(a, "aiThinking", false);   // 夹具：电脑那手不来了（alive 已关），先把思考标记放下
        call(a, "undo");
        b = (XiangqiBoard) get(a, "board");     // 悔棋是重放出来的新棋盘
        chk("悔棋回到 0 手", 0, b.moveCount());
        chk("悔棋后仍是标准开局（32 子）", 32, pieces(b));
        chk("悔棋后没被判终局", false, b.gameOver());
        call(a, "startReview");
        chk("空盘复盘没进「正在复盘」状态", false, ((Boolean) get(a, "reviewing")).booleanValue());
        chk("空盘复盘没打开复盘页", 0, lastStartedCount());

        chk("悔棋后轮到红方", XiangqiBoard.RED, b.sideToMove());

        // 非法步：红车 (9,0) 被自己的马挡住，走不出去
        int before = b.moveCount();
        callPlay(a, 9, 0, 9, 3);   // 车被自己的马挡住 ⇒ 非法
        chk("非法步不改手数", before, b.moveCount());

        call(a, "cycleLevel");
        chk("难度从普通升到强", XiangqiAI.STRONG, get(a, "level"));
        call(a, "cycleLevel");
        chk("难度再切回快", XiangqiAI.FAST, get(a, "level"));
        call(a, "cycleLevel");
        call(a, "cycleLevel");
        chk("四轮难度后又回到强", XiangqiAI.STRONG, get(a, "level"));

        boolean fs0 = ((Boolean) get(a, "fullscreen")).booleanValue();
        call(a, "toggleFullscreen");
        chk("全屏字段被翻过", true, ((Boolean) get(a, "fullscreen")).booleanValue() != fs0);
        call(a, "toggleFullscreen");
        chk("再切一次复原", fs0, ((Boolean) get(a, "fullscreen")).booleanValue());

        // 空盘点复盘：给提示、不开页
        android.os.FakeLog.clear(); // 去掉「次序耦合」：只看这次认输自己产生的日志
        call(a, "resign");
        chk("认输先弹确认框（清空后确实新弹了框）", true, anyEvent("dialog"));
        chk("确认框里没把棋盘判终局", false, ((XiangqiBoard) get(a, "board")).gameOver());

        // ⑳ 屏幕自适应：布局完成后再算；转屏按真机顺序（onConfigurationChanged 先到、layout 在后）
        XiangqiActivity z = newPage();
        Object zScroll = get(z, "scroll");
        callInherited(get(z, "topChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 300, 300);
        callInherited(get(z, "bottomChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 300, 600);
        callInherited(zScroll, "fakeLayout", new Class<?>[]{int.class, int.class}, 1080, 1920);   // 首帧 layout
        android.view.ViewGroup.LayoutParams zlp =
                (android.view.ViewGroup.LayoutParams) callInherited(get(z, "boardView"), "getLayoutParams", new Class<?>[]{});
        int zdp16 = ((Integer) call(z, "dp", new Class<?>[]{int.class}, 16)).intValue();
        int wantPortrait = Math.min(1080 - zdp16, 1920 - 300 - 600 - zdp16);
        chk("自适应：首帧布局后棋盘边长 = min(可用宽, 可用高)", wantPortrait, zlp.width);
        chk("自适应：竖屏棋盘是正方形", zlp.width, zlp.height);

        // 真机顺序：先 onConfigurationChanged（此时还是旧尺寸），然后才 layout 出新尺寸
        call(z, "onConfigurationChanged", new Class<?>[]{android.content.res.Configuration.class}, new android.content.res.Configuration());
        callInherited(get(z, "topChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 1920, 200);
        callInherited(get(z, "bottomChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 1920, 400);
        callInherited(zScroll, "fakeLayout", new Class<?>[]{int.class, int.class}, 1920, 1080);
        zlp = (android.view.ViewGroup.LayoutParams) callInherited(get(z, "boardView"), "getLayoutParams", new Class<?>[]{});
        int wantLandscape = Math.min(1920 - zdp16, 1080 - 200 - 400 - zdp16);
        chk("自适应：转屏（回调在前、layout 在后）后按新尺寸重算", wantLandscape, zlp.width);

        // 兜底：聚焦时再算一次；方法被删也要报红而不是崩
        try {
            call(z, "onWindowFocusChanged", new Class<?>[]{boolean.class}, Boolean.TRUE);
        } catch (NoSuchMethodException e) {
            chk("自适应：onWindowFocusChanged 兜底存在", true, false);
        }

        // 主题变化：原地重建不丢棋局（旧实现 recreate + onCreate newBoard 会把棋局清掉）
        callPlay(z, 6, 0, 5, 0);
        int zMoves = ((XiangqiBoard) get(z, "board")).moveCount();
        set(z, "dark", Boolean.valueOf(!((Boolean) get(z, "dark")).booleanValue()));
        call(z, "onResume");
        chk("主题变化重建后棋局没丢", zMoves, ((XiangqiBoard) get(z, "board")).moveCount());
        chk("主题变化重建后确实动过棋", true, zMoves > 0);
        chk("主题变化走的是原地重建（没有 recreate 清局）", 0, ((Integer) get(z, "recreateCount")).intValue());
    }

    static XiangqiActivity newPage() throws Exception {
        XiangqiActivity a = new XiangqiActivity();
        set(a, "prefs", new android.content.SharedPreferences());
        set(a, "resources", new android.content.res.Resources(new android.content.res.Configuration(), new android.util.DisplayMetrics()));
        call(a, "onCreate", new Class<?>[]{android.os.Bundle.class}, new android.os.Bundle());
        set(a, "alive", false);      // 关掉 AI 回调，避免线程干扰断言
        return a;
    }

    static int pieces(XiangqiBoard b) {
        int n = 0;
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                if (b.at(r, c) != 0) {
                    n++;
                }
            }
        }
        return n;
    }

    static String status(XiangqiActivity a) throws Exception {
        return String.valueOf(((TextView) get(a, "statusText")).getText());
    }

    static boolean anyEvent(String key) {
        for (String e : android.os.FakeLog.EVENTS) {
            if (e.contains(key)) {
                return true;
            }
        }
        return false;
    }

    static String lastToast(XiangqiActivity a) {
        java.util.List<String> ev = android.os.FakeLog.EVENTS;
        for (int i = ev.size() - 1; i >= 0; i--) {
            if (ev.get(i).startsWith("toast:")) {
                return ev.get(i);
            }
        }
        return null;
    }

    static int lastStartedCount() {
        int n = 0;
        for (String e : android.os.FakeLog.EVENTS) {
            if (e.startsWith("startActivity:")) {
                n++;
            }
        }
        return n;
    }

    static void chk(String what, Object exp, Object act) {
        if (String.valueOf(exp).equals(String.valueOf(act))) {
            pass++;
        } else {
            fail++;
            System.out.println("FAIL  " + what + "（期望 " + exp + "，实际 " + act + "）");
        }
    }

    static void callPlay(Object o, int a, int b, int c, int d) throws Exception {
        java.lang.reflect.Method m = o.getClass().getDeclaredMethod("play", int.class, int.class, int.class, int.class);
        m.setAccessible(true);
        m.invoke(o, a, b, c, d);
    }

    static void call(Object o, String name) throws Exception {
        java.lang.reflect.Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        m.invoke(o);
    }

    static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        java.lang.reflect.Method m = o.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(o, args);
    }

    /** 调父类里声明的方法（桩测的 View.fakeSize 在基类上）。 */
    static Object callInherited(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) {
            try {
                java.lang.reflect.Method m = c.getDeclaredMethod(name, types);
                m.setAccessible(true);
                return m.invoke(o, args);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        throw new NoSuchMethodException(name);
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

    static Object get(Object o, String n) throws Exception {
        return field(o.getClass(), n).get(o);
    }
}
