package com.autogo;

import android.view.View;
import android.view.ViewGroup;

public class JunqiProbe {

    static int pass, fail;

    public static void main(String[] args) throws Exception {
        JunqiActivity p = newPage();
        JunqiView v = (JunqiView) get(p, "boardView");
        v.fakeSize(500, 1200);   // 桩里没有真实 layout，自己给视图一个尺寸
        ViewGroup holder = contentOf(p);

        chk("新局默认暗棋", Boolean.TRUE, get(p, "dark"));
        chk("暗棋 hidden 全 true", JunqiBoard.COLS * JunqiBoard.ROWS, countTrue(hidden(v)));
        chk("状态条提到暗棋", true, text(p).contains("暗棋"));
        chk("水印在位", true, hasWatermark(holder));

        draw(v);
        chk("暗棋下 100 个圆角块（50 块棋子 × 边+面）", 100, android.graphics.Canvas.RECTS.size());
        chk("暗棋下不画兵种字", 0, android.graphics.Canvas.TEXTS.size());

        click(p, "暗棋 / 明棋");
        chk("切到明棋", Boolean.FALSE, get(p, "dark"));
        chk("明棋 hidden 全 false", 0, countTrue(hidden(v)));
        chk("状态条提到明棋", true, text(p).contains("明棋"));
        draw(v);
        chk("明棋下画 50 个兵种字", 50, android.graphics.Canvas.TEXTS.size());

        click(p, "暗棋 / 明棋");
        chk("切回暗棋 hidden 全 true", JunqiBoard.COLS * JunqiBoard.ROWS, countTrue(hidden(v)));

        // ⑥ 暗棋交战暴露：走到空格不暴露、吃子才暴露
        JunqiBoard cb = new JunqiBoard(true);   // 空盘（true = 不摆开局）
        cb.put(6, 0, JunqiBoard.RED, JunqiBoard.ENGINEER);    // 红工兵
        cb.put(7, 0, JunqiBoard.RED, JunqiBoard.PLATOON);     // 红排长（待命）
        cb.put(4, 0, JunqiBoard.BLACK, JunqiBoard.PLATOON);   // 黑排长（可吃）
        set(p, "board", cb);
        set(p, "startBoard", cb.copy());
        boolean[] rev = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
        rev[JunqiBoard.idx(6, 0)] = true;   // 这枚工兵此前交战暴露过，走掉后应复位
        set(p, "revealed", rev);
        set(p, "alive", false);   // 关掉 AI 回调，避免它把别的格子标成暴露
        call(p, "sync");
        call(p, "play", new Class<?>[]{int.class, int.class, int.class, int.class}, 6, 0, 5, 0); // 走到空格
        chk("暗棋：走到空格不暴露", false, rev[JunqiBoard.idx(5, 0)]);
        chk("暗棋：走掉的原格不暴露", false, rev[JunqiBoard.idx(6, 0)]);

        JunqiBoard cb2 = new JunqiBoard(true);
        cb2.put(6, 0, JunqiBoard.RED, JunqiBoard.ENGINEER);
        cb2.put(5, 0, JunqiBoard.BLACK, JunqiBoard.PLATOON);
        set(p, "board", cb2);
        set(p, "startBoard", cb2.copy());
        boolean[] rev2 = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
        rev2[JunqiBoard.idx(6, 0)] = true;
        set(p, "revealed", rev2);
        set(p, "alive", false);
        call(p, "sync");
        call(p, "play", new Class<?>[]{int.class, int.class, int.class, int.class}, 6, 0, 5, 0);
        chk("暗棋：吃子后目标格暴露", true, rev2[JunqiBoard.idx(5, 0)]);
        chk("暗棋：吃子后原格复位", false, rev2[JunqiBoard.idx(6, 0)]);

        // ⑦ 共用暴露逻辑：AI 走到空格不该暴露、吃子才暴露（原来电脑那一手是无条件暴露）
        JunqiBoard before3 = new JunqiBoard(true);   // 空盘：标准开局 (4,4) 可能已经有子，不能靠天吃饭
        before3.put(3, 3, JunqiBoard.RED, JunqiBoard.PLATOON);
        boolean[] r3 = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
        r3[JunqiBoard.idx(3, 3)] = true;   // 假装这枚子此前暴露过
        JunqiReveal.resolve(r3, before3, new int[]{JunqiBoard.idx(3, 3), JunqiBoard.idx(4, 4)});
        chk("电脑走到空格：目标格不暴露", false, r3[JunqiBoard.idx(4, 4)]);
        chk("电脑走掉的原格复位", false, r3[JunqiBoard.idx(3, 3)]);
        JunqiBoard before4 = new JunqiBoard();
        before4.put(5, 0, JunqiBoard.BLACK, JunqiBoard.PLATOON);
        boolean[] r4 = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
        JunqiReveal.resolve(r4, before4, new int[]{JunqiBoard.idx(6, 0), JunqiBoard.idx(5, 0)});
        chk("电脑吃子：目标格暴露", true, r4[JunqiBoard.idx(5, 0)]);

        // ⑦b 真回调：先用红方走一手（直接落盘，不走页面线程），再走「AI 一手落地」的真实路径。
        JunqiBoard real = new JunqiBoard();
        set(p, "board", real);
        set(p, "revealed", new boolean[JunqiBoard.COLS * JunqiBoard.ROWS]);
        set(p, "aiThinking", false);
        set(p, "alive", true);
        java.util.List<int[]> rm = real.legalMoves(JunqiBoard.RED);
        chk("真回调：标准开局红方有合法着", true, !rm.isEmpty());
        if (!rm.isEmpty()) {
            int[] h = rm.get(0);
            real.play(h[0] / JunqiBoard.COLS, h[0] % JunqiBoard.COLS,
                    h[1] / JunqiBoard.COLS, h[1] % JunqiBoard.COLS);
            int rr = -1, rc2 = -1, tr2 = -1, tc2 = -1;
            for (int[] m : real.legalMoves(JunqiBoard.BLACK)) {
                int r0 = m[0] / JunqiBoard.COLS, c0 = m[0] % JunqiBoard.COLS;
                int r1 = m[1] / JunqiBoard.COLS, c1 = m[1] % JunqiBoard.COLS;
                if (real.ownerAt(r1, c1) == JunqiBoard.EMPTY) { rr = r0; rc2 = c0; tr2 = r1; tc2 = c1; break; }
            }
            chk("真回调：找得到「走到空格」的一手", true, rr >= 0);
            if (rr >= 0) {
                call(p, "finishAiMove", new Class<?>[]{int[].class}, new int[]{rr, rc2, tr2, tc2});
                JunqiBoard cur = (JunqiBoard) get(p, "board");
                chk("真回调：AI 这手落下了（手数 2）", 2, cur.history().size());
                boolean[] rv6 = (boolean[]) get(p, "revealed");
                chk("真回调：AI 走到空格时落点没有暴露（无条件）", false,
                        rv6[JunqiBoard.idx(tr2, tc2)]);
            }
        }
        set(p, "alive", false);

        int before = ((Integer) get(p, "level")).intValue();
        click(p, "难度");
        int after = ((Integer) get(p, "level")).intValue();
        chk("难度从普通(1)循环到强(2)", 2, after);

        call(p, "resign");
        chk("认输弹窗出现", true, android.os.FakeLog.countPrefix("dialog.title:对局结束") >= 1);
        chk("认输文案", true, hasEvent("dialog.msg:", "你认输了"));

        // ⑨ 线程路径：让 AI 线程真的落一手（外面包 try/catch：万一前面某组把页面搞坏，
        // 这里抛异常也要显式报红，不能默默跳过 —— 第 105 轮的教训）
        try {
            // ⑨ 线程路径：让 AI 线程真的落一手（第 104 轮审计 中-1 的另一半：lambda 里的写必须有行为兜底）
            JunqiActivity pt = newPage();
            // 夹具目的：让 AI（黑）第一手只能走到**空格**（红子不在铁路那条直线上），
            // 这样「走到空格不暴露」这条断言在默认路径上就一定有区分力（第 105 轮审计 低-4）。
            JunqiBoard tb = new JunqiBoard(true);              // 空盘
            tb.put(2, 2, JunqiBoard.RED, JunqiBoard.PLATOON);  // 红：排长（不在铁路上）
            tb.put(6, 1, JunqiBoard.BLACK, JunqiBoard.ENGINEER); // 黑：工兵（铁路第 6 行）
            tb.put(11, 0, JunqiBoard.BLACK, JunqiBoard.FLAG);  // 黑：军旗（不能动；第 106 轮审计实测这里不是大本营，注释已改）
            chk("线程路径夹具：黑方有合法手", true, tb.legalMoves(JunqiBoard.BLACK).size() > 0);
            set(pt, "board", tb);
            set(pt, "startBoard", tb.copy());
            set(pt, "revealed", new boolean[JunqiBoard.COLS * JunqiBoard.ROWS]); // 暗棋：全隐藏
            set(pt, "alive", true);
            call(pt, "sync");
            int piecesBefore = countPieces(tb);
            int n0 = tb.history().size();
            call(pt, "play", new Class<?>[]{int.class, int.class, int.class, int.class}, 2, 2, 2, 1); // 红走一步 ⇒ 自动开 AI 线程
            boolean grew = false;
            for (int i = 0; i < 250 && !grew; i++) {
                android.app.Activity.drainUi();
                Thread.sleep(20);
                grew = tb.history().size() > n0 + 1;
            }
            chk("线程路径：AI 真的落了一手", true, grew);
            boolean[] tv = (boolean[]) get(pt, "revealed");
            chk("线程路径：AI 落地后「已暴露」最多只有 1 格（吃子才暴露）", true, countTrue(tv) <= 1);
            if (grew) {
                int[] last = tb.history().get(tb.history().size() - 1);
                chk("线程路径：AI 走掉的原格不暴露", false, tv[JunqiBoard.idx(last[0], last[1])]);
                if (countPieces(tb) == piecesBefore) {        // 没吃子
                    chk("线程路径：AI 走到空格时落点不暴露", false, tv[JunqiBoard.idx(last[2], last[3])]);
                } else {
                    chk("线程路径：AI 吃子时才把落点标成已暴露", true, tv[JunqiBoard.idx(last[2], last[3])]);
                }
            }

        } catch (RuntimeException ex) {
            chk("线程路径：不抛异常（" + ex.getClass().getSimpleName() + ": " + ex.getMessage() + "）", true, false);
        }

        // ⑩ 屏幕自适应：布局完成后再算；转屏按真机顺序（回调在前、layout 在后）
        JunqiActivity z = newPage();
        Object zScroll = get(z, "scroll");

        // 首帧初始宽要用「真实屏幕」夹具，否则 0×0 屏会把 4:11 和 5:12 都夹成最小宽 90，测不出区别（审计 110 低-2）
        JunqiActivity zs = new JunqiActivity();
        set(zs, "prefs", new android.content.SharedPreferences());
        android.content.res.Configuration zcfg = new android.content.res.Configuration();
        zcfg.screenWidthDp = 540;
        zcfg.screenHeightDp = 960;
        zcfg.smallestScreenWidthDp = 540;
        android.util.DisplayMetrics zdm = new android.util.DisplayMetrics();
        zdm.density = 2f;
        zdm.widthPixels = 1080;
        zdm.heightPixels = 1920;
        set(zs, "resources", new android.content.res.Resources(zcfg, zdm));
        zs.applicationInfo = new android.content.pm.ApplicationInfo();
        zs.applicationInfo.nativeLibraryDir = "/nonexistent";
        call(zs, "onCreate", new Class<?>[]{android.os.Bundle.class}, new android.os.Bundle());
        android.view.ViewGroup.LayoutParams zslp =
                (android.view.ViewGroup.LayoutParams) callInherited(get(zs, "boardView"), "getLayoutParams", new Class<?>[]{});
        chk("首帧初始棋盘宽 437（540×960dp/density2，5:12；4:11 会是 381）", 437, zslp.width);
        callInherited(get(z, "topChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 1080, 300);
        callInherited(get(z, "bottomChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 1080, 600);
        callInherited(zScroll, "fakeLayout", new Class<?>[]{int.class, int.class}, 1080, 1920);
        android.view.ViewGroup.LayoutParams zlp =
                (android.view.ViewGroup.LayoutParams) callInherited(get(z, "boardView"), "getLayoutParams", new Class<?>[]{});
        int zdp16 = ((Integer) call(z, "dp", new Class<?>[]{int.class}, 16)).intValue();
        int availW = 1080 - zdp16;
        int availH = 1920 - 300 - 600 - zdp16;
        int wantH = Math.min(availH, Math.round(availW * 12f / 5f));
        int wantW = Math.round(wantH * 5f / 12f);
        chk("自适应：首帧布局后棋盘高度 = min(可用高, 可用宽×12/5)", wantH, zlp.height);
        chk("自适应：首帧布局后棋盘宽度按 5:12 推出", wantW, zlp.width);

        // 真机顺序：先回调（旧尺寸），再 layout（新尺寸）
        call(z, "onConfigurationChanged", new Class<?>[]{android.content.res.Configuration.class}, new android.content.res.Configuration());
        callInherited(get(z, "topChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 1920, 200);
        callInherited(get(z, "bottomChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 1920, 400);
        callInherited(zScroll, "fakeLayout", new Class<?>[]{int.class, int.class}, 1920, 1080);
        zlp = (android.view.ViewGroup.LayoutParams) callInherited(get(z, "boardView"), "getLayoutParams", new Class<?>[]{});
        int lAvailW = 1920 - zdp16;
        int lAvailH = 1080 - 200 - 400 - zdp16;
        int lH = Math.min(lAvailH, Math.round(lAvailW * 12f / 5f));
        int lW = Math.round(lH * 5f / 12f);
        chk("自适应：转屏（回调在前、layout 在后）后按新尺寸重算", lH, zlp.height);
        chk("自适应：横屏宽度同样按 5:12", lW, zlp.width);

        // 极小屏横屏：minW 夹取不能把高度顶出可用空间
        callInherited(get(z, "topChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 640, 100);
        callInherited(get(z, "bottomChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 640, 150);
        callInherited(zScroll, "fakeLayout", new Class<?>[]{int.class, int.class}, 640, 360);
        zlp = (android.view.ViewGroup.LayoutParams) callInherited(get(z, "boardView"), "getLayoutParams", new Class<?>[]{});
        int sAvailH = 360 - 100 - 150 - zdp16;
        chk("自适应：小屏横屏棋盘不超出可用高度", true, zlp.height <= sAvailH);
        chk("自适应：小屏横屏宽度也按 5:12", Math.round(zlp.height * 5f / 12f), zlp.width);

        // 兜底：聚焦时再算一次；方法被删也要报红而不是崩
        try {
            call(z, "onWindowFocusChanged", new Class<?>[]{boolean.class}, Boolean.TRUE);
        } catch (NoSuchMethodException e) {
            chk("自适应：onWindowFocusChanged 兜底存在", true, false);
        }

        // 主题变化：原地重建不丢棋局/暗棋状态（旧实现 recreate 会 newGame）
        JunqiBoard zb = new JunqiBoard(true);
        zb.put(6, 0, JunqiBoard.RED, JunqiBoard.ENGINEER);
        set(z, "board", zb);
        set(z, "startBoard", zb.copy());
        set(z, "alive", false);
        call(z, "sync");
        call(z, "play", new Class<?>[]{int.class, int.class, int.class, int.class}, 6, 0, 5, 0);
        int zHist = ((JunqiBoard) get(z, "board")).history().size();
        int zRev = countTrue((boolean[]) get(z, "revealed"));
        set(z, "darkTheme", Boolean.valueOf(!((Boolean) get(z, "darkTheme")).booleanValue()));
        call(z, "onResume");
        chk("主题变化重建后棋局没丢", zHist, ((JunqiBoard) get(z, "board")).history().size());
        chk("主题变化重建后 revealed 表没被重置", zRev, countTrue((boolean[]) get(z, "revealed")));
        chk("主题变化走的是原地重建（没有 recreate 清局）", 0, ((Integer) get(z, "recreateCount")).intValue());

        // ⑪ 彩蛋：快档赢了也不显示；普通/强档赢了才显示；学习库所有档位都记
        for (int lv = JunqiAI.FAST; lv <= JunqiAI.STRONG; lv++) {
            JunqiActivity eg = newPage();
            set(eg, "level", Integer.valueOf(lv));
            JunqiBoard win = new JunqiBoard(true);
            win.put(1, 1, JunqiBoard.RED, JunqiBoard.COMMANDER);
            win.put(0, 1, JunqiBoard.BLACK, JunqiBoard.FLAG);
            win.play(1, 1, 0, 1);
            set(eg, "board", win);
            set(eg, "startBoard", win.copy());
            android.os.FakeLog.clear();
            call(eg, "finishGame");
            boolean egg = hasPrefix("dialog.view:");
            chk("难度 " + lv + " 赢了彩蛋显示=" + (lv != JunqiAI.FAST), true, egg == (lv != JunqiAI.FAST));
            chk("难度 " + lv + " 结束后写入学习库", true, hasEvent("dialog.msg:", "学习库"));
        }

        // ⑫ 认输是电脑赢：学习库记 WIN，不是 LOSS（第 112 轮审计 中-1）
        JunqiActivity rs = newPage();
        JunqiLearn l0 = (JunqiLearn) get(rs, "learn");
        int wins0 = l0.wins();
        int games0 = l0.games();
        android.os.FakeLog.clear();
        call(rs, "resign");
        JunqiLearn l1 = (JunqiLearn) get(rs, "learn");
        chk("认输后学习库局数 +1", games0 + 1, l1.games());
        chk("认输记成电脑赢（wins +1）", wins0 + 1, l1.wins());
        chk("认输弹窗仍写学习库", true, hasEvent("dialog.msg:", "学习库"));

        System.out.println("军棋页面探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) {
            System.exit(1);
        }
    }

    // ==== 环境 ====

    static JunqiActivity newPage() throws Exception {
        JunqiActivity a = new JunqiActivity();
        set(a, "prefs", new android.content.SharedPreferences());
        set(a, "resources", new android.content.res.Resources(new android.content.res.Configuration(), new android.util.DisplayMetrics()));
        a.applicationInfo = new android.content.pm.ApplicationInfo();
        a.applicationInfo.nativeLibraryDir = "/nonexistent";
        call(a, "onCreate", new Class<?>[]{android.os.Bundle.class}, new android.os.Bundle());
        ((JunqiView) get(a, "boardView")).setAnimationsEnabled(false);   // 探针只测静态绘制
        return a;
    }

    static ViewGroup contentOf(JunqiActivity p) throws Exception {
        return (ViewGroup) get(p, "content");
    }

    static boolean hasWatermark(ViewGroup root) throws Exception {
        if (root == null) {
            return false;
        }
        for (int i = 0; i < root.getChildCount(); i++) {
            View c = root.getChildAt(i);
            if (c instanceof android.widget.TextView) {
                String s = String.valueOf(((android.widget.TextView) c).getText());
                if (s.contains(Watermark.TEXT)) {
                    return true;
                }
            }
            if (c instanceof ViewGroup && hasWatermark((ViewGroup) c)) {
                return true;
            }
        }
        return false;
    }

    static void click(JunqiActivity p, String label) throws Exception {
        if (clickIn(contentOf(p), label)) {
            return;
        }
        throw new IllegalStateException("找不到按钮：" + label);
    }

    static boolean clickIn(ViewGroup g, String label) throws Exception {
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c instanceof android.widget.Button
                    && label.equals(String.valueOf(((android.widget.Button) c).getText()))) {
                callAny(c, "fakeClick");
                return true;
            }
            if (c instanceof ViewGroup && clickIn((ViewGroup) c, label)) {
                return true;
            }
        }
        return false;
    }

    static void draw(JunqiView v) throws Exception {
        android.graphics.Canvas.reset();
        java.lang.reflect.Method m = null;
        Class<?> c = v.getClass();
        while (c != null && m == null) {
            try {
                m = c.getDeclaredMethod("fakeOnDraw", android.graphics.Canvas.class);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        if (m == null) {
            throw new NoSuchMethodException("fakeOnDraw");
        }
        m.setAccessible(true);
        m.invoke(v, new android.graphics.Canvas());
    }

    static boolean[] hidden(JunqiView v) throws Exception {
        return (boolean[]) get(v, "hidden");
    }

    static int countTrue(boolean[] a) {
        int n = 0;
        for (boolean b : a) {
            if (b) {
                n++;
            }
        }
        return n;
    }

    static String text(JunqiActivity p) throws Exception {
        android.widget.TextView s = (android.widget.TextView) get(p, "status");
        return String.valueOf(s.getText());
    }

    static boolean hasPrefix(String prefix) {
        for (String e : android.os.FakeLog.EVENTS) {
            if (e.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasEvent(String prefix, String needle) {
        java.util.List<String> ev = android.os.FakeLog.EVENTS;
        for (String e : ev) {
            if (e.startsWith(prefix) && e.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    static int countPieces(JunqiBoard b) {
        int n = 0;
        for (int r = 0; r < JunqiBoard.ROWS; r++) {
            for (int c = 0; c < JunqiBoard.COLS; c++) {
                if (b.ownerAt(r, c) != JunqiBoard.EMPTY) {
                    n++;
                }
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

    // ==== 反射 ====

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

    static Object call(Object o, String name) throws Exception {
        return callAny(o, name);
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

    static Object callAny(Object o, String name) throws Exception {
        java.lang.reflect.Method m = null;
        Class<?> c = o.getClass();
        while (c != null && m == null) {
            try {
                m = c.getDeclaredMethod(name);
            } catch (NoSuchMethodException e) {
                c = c.getSuperclass();
            }
        }
        if (m == null) {
            throw new NoSuchMethodException(name);
        }
        m.setAccessible(true);
        return m.invoke(o);
    }
}
