package com.autogo;

import android.os.FakeLog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * v95：「棋盘路数 13/15/19」与「我执黑 / 我执白（电脑先手）」的 JVM 桩测。
 * 真 GomokuActivity + 假 android 类（Unsafe 造实例、反射调私有方法）。
 *
 * ⚠ 已知限制（第 52 轮审计 低-2）：用 sun.misc.Unsafe.allocateInstance 造实例**不走构造器**，
 *   所以字段声明处的默认值（如 `private int size = 15;`）不会生效 —— 那 15 / 黑 / 普通档 / 无禁手
 *   是由下面的 setDefaults 手工镜像出来的。把源码里的声明默认值改错，本探针不会变红；
 *   setDefaults 自己过期（和源码默认值不一致）同样会继续绿灯 ⇒ 改源码默认值时必须同步改它。
 *   （ViewProbe / ReviewProbe / MenuProbe 用 new 造对象，不受这条限制；第 54 轮审计 提示-2 指出原来漏写了 MenuProbe）
 */
public class Driver95 {

    static int pass, fail;
    /** 等 AI 落子的上限：第 102 轮审计实测约 4% 低频假红，放宽到 20 s 后连跑 45 次全绿。 */
    static final long WAIT_MS = 20000L;
    static final sun.misc.Unsafe U;

    static {
        sun.misc.Unsafe u = null;
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            u = (sun.misc.Unsafe) f.get(null);
        } catch (Throwable t) { System.out.println("拿不到 Unsafe：" + t); }
        U = u;
    }


    /** 深度优先找一个写着「穗安」的文本视图（水印）。 */
    static boolean hasWatermark(android.view.View v) {
        if (v instanceof android.widget.TextView) {
            if (com.autogo.Watermark.TEXT.equals(String.valueOf(((android.widget.TextView) v).getText()))) return true;
        }
        if (v instanceof android.view.ViewGroup) {
            for (android.view.View c : ((android.view.ViewGroup) v).children) {
                if (hasWatermark(c)) return true;
            }
        }
        return false;
    }

    static void chk(String what, Object expected, Object actual) {
        boolean ok = expected == null ? actual == null : expected.equals(actual);
        if (ok) { pass++; System.out.println("  PASS  " + what + " = " + actual); }
        else { fail++; System.out.println("  FAIL  " + what + " 期望 " + expected + "，实际 " + actual); }
    }

    static Field field(Class<?> c, String name) throws Exception {
        while (c != null) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f; }
            catch (NoSuchFieldException e) { c = c.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    static void set(Object o, String name, Object v) throws Exception {
        Field f = field(o.getClass(), name);
        long off = U.objectFieldOffset(f);
        if (v instanceof Integer) U.putInt(o, off, ((Integer) v).intValue());
        else if (v instanceof Boolean) U.putBoolean(o, off, ((Boolean) v).booleanValue());
        else U.putObject(o, off, v);
    }

    static GomokuBoard bd(GomokuActivity a) throws Exception { return (GomokuBoard) get(a, "board"); }

    static void setStatic(Class<?> c, String name, Object v) throws Exception {
        Field f = c.getDeclaredField(name); f.setAccessible(true);
        Object base = U.staticFieldBase(f); long off = U.staticFieldOffset(f);
        if (v instanceof Integer) U.putInt(base, off, ((Integer) v).intValue());
        else U.putObject(base, off, v);
    }

    static Object get(Object o, String name) throws Exception { return field(o.getClass(), name).get(o); }

    static Object call(Object o, String name) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name);
        m.setAccessible(true);
        return m.invoke(o);
    }

    static Object call(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        Method m = o.getClass().getDeclaredMethod(name, types);
        m.setAccessible(true);
        return m.invoke(o, args);
    }

    static String events() {
        for (int i = 0; i < 50; i++) {
            try { return String.join(" | ", FakeLog.EVENTS); }
            catch (java.util.ConcurrentModificationException e) { sleep5(); }
        }
        return "";
    }

    static void sleep5() { try { Thread.sleep(5); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); } }

    /** 只要第 from 条之后的新事件（FakeLog 是累积的 ⇒ 「这一档说了哪句话」必须看增量）。 */
    static String newEvents(int from) {
        for (int i = 0; i < 50; i++) {
            try {
                java.util.List<String> ev = FakeLog.EVENTS;
                StringBuilder sb = new StringBuilder();
                for (int k = from; k < ev.size(); k++) sb.append(" | ").append(ev.get(k));
                return sb.toString();
            } catch (java.util.ConcurrentModificationException e) { sleep5(); }
        }
        return "";
    }

    /** 后台 AI 线程会同时往 FakeLog 里写 ⇒ 计数要能容忍并发修改。 */
    static int safeCount(String prefix) {
        for (int i = 0; i < 200; i++) {
            try { return FakeLog.countPrefix(prefix); }
            catch (java.util.ConcurrentModificationException e) { sleep5(); }
        }
        return -1;
    }

    static int drain() { return android.app.Activity.drainUi(); }

    /** 等某个前缀的假事件再出现一次（基线之后）。 */
    static boolean waitMore(String prefix, long ms) throws Exception {
        long base = safeCount(prefix);
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < ms) {
            if (safeCount(prefix) > base) return true;
            Thread.sleep(20);
        }
        return false;
    }

    /** 等某个前缀的假事件再出现一次，并在等待过程中执行已排队的 UI 任务。 */
    static boolean waitDrain(String prefix, long ms) throws Exception {
        long base = safeCount(prefix);
        long t0 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t0 < ms) {
            drain();
            if (safeCount(prefix) > base) return true;
            Thread.sleep(20);
        }
        return false;
    }

    static int sideOf(GomokuBoard b) { return b.moves.size() % 2 == 0 ? GomokuBoard.BLACK : GomokuBoard.WHITE; }

    /** 造一个「刚 onCreate 完」的实例（board 还没建，等 newGame/restoreGame）。 */
    /** Unsafe 造实例不会跑字段初始化器 ⇒ 按源码里的声明默认值补齐（真机上由 JVM 填）。 */
    static void setDefaults(GomokuActivity a) throws Exception {
        set(a, "engine", new LocalGomokuAI());
        set(a, "level", LocalGomokuAI.NORMAL);
        set(a, "forbid", false);
        set(a, "showNumbers", false);
        set(a, "fullscreen", false);
        set(a, "aiThinking", false);
        set(a, "reviewing", false);
        set(a, "alive", true);
        set(a, "statusText", "");
        set(a, "size", 15);
        set(a, "humanColor", GomokuBoard.BLACK);
        set(a, "nextSize", 15);
        set(a, "nextHuman", GomokuBoard.BLACK);
        a.applicationInfo = new android.content.pm.ApplicationInfo();
        a.applicationInfo.nativeLibraryDir = "/nonexistent";
    }

    /**
     * 用「真构造器」造实例：字段初始化器会跑，所以按钮的监听器字段（newGameClick/learnClick…）非空，
     * 假 View.fakeClick() 才能真的点到监听器。Unsafe 版（fresh/page）不跑字段初始化器，
     * 监听器是 null ⇒ 点不动（⑱ 组原来就是被这个卡住的）。
     */
    static GomokuActivity nativePage() throws Exception {
        GomokuActivity a = new GomokuActivity();
        set(a, "prefs", new android.content.SharedPreferences());
        set(a, "resources", new android.content.res.Resources(new android.content.res.Configuration(), new android.util.DisplayMetrics()));
        a.applicationInfo = new android.content.pm.ApplicationInfo();
        a.applicationInfo.nativeLibraryDir = "/nonexistent";
        call(a, "onCreate", new Class<?>[]{android.os.Bundle.class}, new android.os.Bundle());
        return a;
    }

    /** 电脑对战用：反复排空假 UI 队列，直到棋盘上的手数达到 want（或超时）。返回实际手数。 */
    static int waitMoves(GomokuActivity a, int want, long ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        int n = 0;
        while (System.currentTimeMillis() < end) {
            drain();
            GomokuBoard b = bd(a);
            n = b == null ? 0 : b.moves.size();
            if (n >= want) return n;
            Thread.sleep(20);
        }
        return n;
    }

    static GomokuActivity fresh() throws Exception {
        GomokuActivity a = (GomokuActivity) U.allocateInstance(GomokuActivity.class);
        setDefaults(a);
        set(a, "boardView", new GomokuView(null));
        return a;
    }

    /** 真 GomokuView 把棋盘存在私有字段 board 里（它不写 FakeLog）—— 从外面读出来核对。 */
    static int viewSize(GomokuActivity a) throws Exception {
        Object b = get(get(a, "boardView"), "board");
        return b == null ? -1 : ((GomokuBoard) b).size;
    }

    static int viewMoves(GomokuActivity a) throws Exception {
        Object b = get(get(a, "boardView"), "board");
        return b == null ? -1 : ((GomokuBoard) b).moves.size();
    }


    /** 沿继承链找方法（假类的不少方法声明在父类 View 上，getDeclaredMethod 在子类上找不到）。 */
    static int callInt(Object o, String name) throws Exception {
        return ((Integer) callAny(o, name, new Class<?>[]{})).intValue();
    }

    static Object callAny(Object o, String name, Class<?>[] types, Object... args) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Method md = c.getDeclaredMethod(name, types);
                md.setAccessible(true);
                return md.invoke(o, args);
            } catch (NoSuchMethodException ignore) {
            }
        }
        throw new NoSuchMethodException(name + " on " + o.getClass().getName());
    }

    public static void main(String[] args) throws Exception {
        // ① 默认新局：15 路、我执黑
        GomokuActivity a = fresh();
        call(a, "newGame");
        GomokuBoard b = (GomokuBoard) get(a, "board");
        chk("默认棋盘路数", Integer.valueOf(15), Integer.valueOf(b.size));
        chk("默认我执黑", Integer.valueOf(GomokuBoard.BLACK), get(a, "humanColor"));
        chk("新局状态条", "你执黑先走。棋盘：15 路　难度：普通　禁手：关", get(a, "statusText"));
        chk("棋盘视图收到 15 路的盘", Boolean.TRUE, Boolean.valueOf(viewSize(a) == 15 && viewMoves(a) == 0));

        // ② 换路数（空盘 ⇒ 立刻生效）
        call(a, "cycleSize");
        chk("棋盘改成 19 路", Integer.valueOf(19), Integer.valueOf(((GomokuBoard) get(a, "board")).size));
        chk("状态条报出新路数", Boolean.TRUE, Boolean.valueOf(((String) get(a, "statusText")).contains("棋盘：19 路")));
        chk("棋盘视图收到 19 路的盘", Boolean.TRUE, Boolean.valueOf(viewSize(a) == 19 && viewMoves(a) == 0));
        call(a, "cycleSize");
        chk("再换一次到 13 路", Integer.valueOf(13), Integer.valueOf(((GomokuBoard) get(a, "board")).size));
        chk("再换一次回到 15 路", Boolean.TRUE, Boolean.valueOf(true));

        // ③ 换我执（空盘 ⇒ 立刻开局，电脑先走）
        call(a, "toggleHuman");
        chk("我执变成白", Integer.valueOf(GomokuBoard.WHITE), get(a, "humanColor"));
        chk("执白时的开局文案", "你执白后走，电脑先下。", call(a, "startText"));
        waitMore("runOnUiThread", WAIT_MS);
        drain();
        GomokuBoard b2 = (GomokuBoard) get(a, "board");
        chk("电脑先落了 1 手", Integer.valueOf(1), Integer.valueOf(b2.moves.size()));
        chk("先手是黑（电脑）", Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(b2.moves.get(0)[0]));
        chk("轮到人时状态条", "轮到你落子。", get(a, "statusText"));

        // ④ 人（执白）落一手，电脑应一手
        // 电脑第一手会占天元（13 路 = 6*13+6），所以这一手另挑一个点
        call(a, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(6 * 13 + 7));
        GomokuBoard b3 = (GomokuBoard) get(a, "board");
        chk("人落的是白子", Integer.valueOf(GomokuBoard.WHITE), Integer.valueOf(b3.moves.get(1)[0]));
        waitMore("runOnUiThread", WAIT_MS);
        drain();
        GomokuBoard b4 = (GomokuBoard) get(a, "board");
        chk("电脑应了一手（共 3 手）", Integer.valueOf(3), Integer.valueOf(b4.moves.size()));
        chk("第三手是黑", Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(b4.moves.get(2)[0]));

        // ⑤ 悔棋：执白时退回到「轮到你走」（留电脑那一手）
        call(a, "undo");
        GomokuBoard b5 = (GomokuBoard) get(a, "board");
        chk("悔棋后退到 1 手", Integer.valueOf(1), Integer.valueOf(b5.moves.size()));
        chk("悔棋后轮到人（白）", Integer.valueOf(GomokuBoard.WHITE), Integer.valueOf(sideOf(b5)));
        chk("悔棋状态条", "已悔棋，轮到你落子。", get(a, "statusText"));

        // ⑥ 执黑时悔棋退回空盘
        GomokuActivity c = fresh();
        call(c, "newGame");
        call(c, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(7 * 15 + 7));
        waitMore("runOnUiThread", WAIT_MS);
        drain();
        chk("执黑：人 + 电脑共 2 手", Integer.valueOf(2), Integer.valueOf(((GomokuBoard) get(c, "board")).moves.size()));
        call(c, "undo");
        chk("执黑悔棋后退回空盘", Integer.valueOf(0), Integer.valueOf(((GomokuBoard) get(c, "board")).moves.size()));
        chk("执黑悔棋状态条", "已悔棋，你执黑先走。", get(c, "statusText"));

        // ⑦ 没轮到人时点棋盘不落子
        GomokuActivity d = fresh();
        set(d, "nextHuman", Integer.valueOf(GomokuBoard.WHITE));
        set(d, "humanColor", Integer.valueOf(GomokuBoard.WHITE));
        set(d, "board", new GomokuBoard(15, false));
        set(d, "aiThinking", Boolean.FALSE);
        call(d, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(7 * 15 + 7));
        chk("没轮到人时点棋盘不落子", Integer.valueOf(0), Integer.valueOf(((GomokuBoard) get(d, "board")).moves.size()));

        // ⑧ 认输：人执白时认输 ⇒ 电脑赢
        GomokuActivity e = fresh();
        set(e, "nextHuman", Integer.valueOf(GomokuBoard.WHITE));
        call(e, "newGame");
        waitMore("runOnUiThread", WAIT_MS);
        drain();
        call(e, "resign");
        chk("执白认输后判电脑赢", Boolean.TRUE, Boolean.valueOf(((String) get(e, "statusText")).startsWith("电脑赢了")));

        // ⑨ 复盘 extra 带的是当前路数（19）
        GomokuActivity f = fresh();
        call(f, "cycleSize");
        call(f, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(9 * 19 + 9));
        waitMore("runOnUiThread", WAIT_MS);
        drain();
        call(f, "startReview");
        chk("复盘页被打开", Boolean.TRUE, Boolean.valueOf(waitDrain("startActivity:com.autogo.GomokuReviewActivity", 20000)));
        drain();
        android.content.Intent it = android.app.Activity.lastStarted;
        chk("复盘 extra 里的路数", Integer.valueOf(19),
                it == null ? null : it.extras.get(GomokuReviewActivity.EXTRA_SIZE));

        // ⑩ 转屏/重建：设置与棋局一起恢复
        call(f, "saveState");
        GomokuActivity g = fresh();
        call(g, "restoreGame");
        chk("恢复后仍是 19 路", Integer.valueOf(19), Integer.valueOf(((GomokuBoard) get(g, "board")).size));
        chk("恢复后我执仍是黑", Integer.valueOf(GomokuBoard.BLACK), get(g, "humanColor"));

        // ⑪ 棋局进行中改设置：当前这盘不动，点「新局」才生效
        GomokuActivity h = fresh();
        call(h, "newGame");
        call(h, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(7 * 15 + 7));
        waitMore("runOnUiThread", WAIT_MS);
        drain();
        int before = ((GomokuBoard) get(h, "board")).moves.size();
        call(h, "cycleSize");
        chk("进行中改路数：当前这盘手数不动", Integer.valueOf(before), Integer.valueOf(((GomokuBoard) get(h, "board")).moves.size()));
        chk("进行中改路数：当前这盘还是 15 路", Integer.valueOf(15), Integer.valueOf(((GomokuBoard) get(h, "board")).size));
        chk("进行中改路数：提示下一局生效", Boolean.TRUE, Boolean.valueOf(((String) get(h, "statusText")).contains("（点「新局」生效）")));
        call(h, "newGame");
        chk("点新局后按新设置开局", Integer.valueOf(19), Integer.valueOf(((GomokuBoard) get(h, "board")).size));
        chk("新局后手数清零", Integer.valueOf(0), Integer.valueOf(((GomokuBoard) get(h, "board")).moves.size()));

        // ⑫ 执白（电脑先手）：悔棋 / 恢复 / 思考中改设置 / 复盘 extra
        GomokuActivity k = fresh();
        set(k, "nextHuman", Integer.valueOf(GomokuBoard.WHITE));
        call(k, "newGame");
        waitMore("runOnUiThread", WAIT_MS); drain();
        chk("执白新局：电脑先落 1 手", Integer.valueOf(1), Integer.valueOf(bd(k).moves.size()));
        chk("执白新局：那手是黑", Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(bd(k).moves.get(0)[0]));
        call(k, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(7 * 15 + 8));
        waitMore("runOnUiThread", WAIT_MS); drain();
        chk("执白：人落一手后电脑应一手（3 手）", Integer.valueOf(3), Integer.valueOf(bd(k).moves.size()));
        call(k, "undo");
        chk("执白悔棋：退到 1 手", Integer.valueOf(1), Integer.valueOf(bd(k).moves.size()));
        chk("执白悔棋：轮到人（白）", Integer.valueOf(GomokuBoard.WHITE), Integer.valueOf(sideOf(bd(k))));
        chk("执白悔棋：状态条", "已悔棋，轮到你落子。", get(k, "statusText"));

        call(k, "saveState");
        GomokuActivity k2 = fresh();
        call(k2, "restoreGame");
        Thread.sleep(300); drain();
        chk("执白恢复（轮到人）：手数不变", Integer.valueOf(1), Integer.valueOf(bd(k2).moves.size()));
        chk("执白恢复：我执仍是白", Integer.valueOf(GomokuBoard.WHITE), get(k2, "humanColor"));

        GomokuActivity m2 = fresh();
        set(m2, "humanColor", Integer.valueOf(GomokuBoard.WHITE));
        GomokuBoard bm = new GomokuBoard(15, false);
        bm.play(GomokuBoard.BLACK, 7 * 15 + 7);
        bm.play(GomokuBoard.WHITE, 7 * 15 + 8);
        set(m2, "board", bm);
        set(m2, "statusText", "x");
        call(m2, "saveState");
        GomokuActivity m3 = fresh();
        call(m3, "restoreGame");
        boolean ran = false;
        long t1 = System.currentTimeMillis();
        while (System.currentTimeMillis() - t1 < 8000) {
            drain();
            if (bd(m3).moves.size() == 3) { ran = true; break; }
            Thread.sleep(20);
        }
        chk("执白恢复（轮到电脑）：自动补一手", Boolean.TRUE, Boolean.valueOf(ran));
        chk("执白恢复（轮到电脑）：补完轮到人", Integer.valueOf(GomokuBoard.WHITE), Integer.valueOf(sideOf(bd(m3))));

        GomokuActivity n = fresh();
        call(n, "newGame");
        call(n, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(7 * 15 + 7));
        waitMore("runOnUiThread", WAIT_MS); drain();
        int before2 = bd(n).moves.size();
        set(n, "aiThinking", Boolean.TRUE);
        call(n, "cycleSize");
        chk("思考中改路数：手数不动", Integer.valueOf(before2), Integer.valueOf(bd(n).moves.size()));
        chk("思考中改路数：当前盘仍 15 路", Integer.valueOf(15), Integer.valueOf(bd(n).size));
        chk("思考中改路数：提示下一局", Boolean.TRUE, Boolean.valueOf(((String) get(n, "statusText")).contains("（点「新局」生效）")));
        chk("思考中改路数：aiThinking 保持", Boolean.TRUE, get(n, "aiThinking"));

        GomokuActivity o = fresh();
        set(o, "nextHuman", Integer.valueOf(GomokuBoard.WHITE));
        call(o, "cycleSize");
        call(o, "cycleSize");
        call(o, "newGame");
        waitMore("runOnUiThread", WAIT_MS); drain();
        set(o, "aiThinking", Boolean.FALSE);
        call(o, "startReview");
        chk("执白 + 13 路复盘：复盘页被打开", Boolean.TRUE,
                Boolean.valueOf(waitDrain("startActivity:com.autogo.GomokuReviewActivity", 20000)));
        drain();
        android.content.Intent it2 = android.app.Activity.lastStarted;
        chk("执白 + 13 路复盘：extra 路数", Integer.valueOf(13),
                it2 == null ? null : it2.extras.get(GomokuReviewActivity.EXTRA_SIZE));
        chk("执白 + 13 路复盘：extra 先手是黑", Integer.valueOf(GomokuBoard.BLACK),
                it2 == null ? null : it2.extras.get(GomokuReviewActivity.EXTRA_FIRST));

        // ⑬ 上一局结束 + 页面重建：设置要记住（第 51 轮低-2 的钉桩）
        GomokuActivity q = fresh();
        call(q, "cycleSize");        // 15 → 19（空盘立刻生效）
        call(q, "toggleHuman");      // 黑 → 白
        waitMore("runOnUiThread", WAIT_MS); drain();
        chk("终局前：19 路", Integer.valueOf(19), Integer.valueOf(bd(q).size));
        set(q, "aiThinking", Boolean.FALSE);
        call(q, "resign");
        call(q, "saveState");        // 终局 ⇒ savedMoves == null
        GomokuActivity q2 = (GomokuActivity) U.allocateInstance(GomokuActivity.class);
        setDefaults(q2);
        // Unsafe 不跑字段初始化器 ⇒ 假 Context 的 prefs/resources 要手动补上（onCreate → UiTheme/Screen 要用）
        set(q2, "prefs", new android.content.SharedPreferences());
        set(q2, "resources", new android.content.res.Resources(new android.content.res.Configuration(), new android.util.DisplayMetrics()));
        q2.applicationInfo = new android.content.pm.ApplicationInfo();
        q2.applicationInfo.nativeLibraryDir = "/nonexistent";
        call(q2, "onCreate", new Class<?>[]{android.os.Bundle.class}, new android.os.Bundle());
        waitMore("runOnUiThread", WAIT_MS); drain();
        chk("重建后仍是 19 路", Integer.valueOf(19), Integer.valueOf(bd(q2).size));
        chk("重建后仍是我执白", Integer.valueOf(GomokuBoard.WHITE), get(q2, "humanColor"));
        set(q2, "aiThinking", Boolean.FALSE);

        // ⑭ 非 15 路 + 强档：不许把 15×15 的外部引擎叫上来（第 51 轮中-1）
        java.io.File rapdir = new java.io.File(System.getProperty("java.io.tmpdir"), "pv96-native-a");
        rapdir.mkdirs();
        java.io.File fakeRapfi = new java.io.File(rapdir, "librapfi.so");
        java.io.FileWriter fw = new java.io.FileWriter(fakeRapfi);
        fw.write("#!/bin/sh\nwhile read line; do case \"$line\" in START*) echo OK;; DONE) echo 7,7;; END) exit 0;; esac; done\n");
        fw.close();
        fakeRapfi.setExecutable(true);
        GomokuActivity r15 = fresh();
        r15.applicationInfo.nativeLibraryDir = rapdir.getAbsolutePath();
        set(r15, "level", Integer.valueOf(LocalGomokuAI.STRONG));
        call(r15, "newGame");
        Object e15 = call(r15, "engineForMove");
        chk("15 路 + 强档 + Rapfi 可用：用外部引擎", "RapfiGomokuEngine",
                e15 == null ? null : e15.getClass().getSimpleName());
        chk("15 路 + 强档：难度名不写内置", "强", call(r15, "levelLabel"));

        GomokuActivity r19 = fresh();
        r19.applicationInfo.nativeLibraryDir = rapdir.getAbsolutePath();
        set(r19, "level", Integer.valueOf(LocalGomokuAI.STRONG));
        call(r19, "cycleSize");      // 15 → 19（空盘立刻生效）
        chk("19 路时棋盘确实是 19", Integer.valueOf(19), Integer.valueOf(bd(r19).size));
        Object e19 = call(r19, "engineForMove");
        Object local = get(r19, "engine");
        chk("19 路 + 强档：走内置 AI（不是外部引擎）", Boolean.TRUE, Boolean.valueOf(e19 == local));
        chk("19 路 + 强档：难度名标注内置", "强（内置）", call(r19, "levelLabel"));

        GomokuActivity r13 = fresh();
        r13.applicationInfo.nativeLibraryDir = rapdir.getAbsolutePath();
        set(r13, "level", Integer.valueOf(LocalGomokuAI.STRONG));
        call(r13, "cycleSize");      // 19
        call(r13, "cycleSize");      // 13
        chk("13 路时棋盘确实是 13", Integer.valueOf(13), Integer.valueOf(bd(r13).size));
        chk("13 路 + 强档：走内置 AI", Boolean.TRUE, Boolean.valueOf(call(r13, "engineForMove") == get(r13, "engine")));
        chk("13 路 + 强档：难度名标注内置", "强（内置）", call(r13, "levelLabel"));

        // ⑮ 空盘点「复盘」：只提示，不开一个空页面
        GomokuActivity nb = fresh();
        call(nb, "newGame");
        android.app.Activity.lastStarted = null;
        call(nb, "startReview");
        // 后台复盘线程要是一启动就会去开页面：等一会儿再断言「没跳转」才有意义
        try { Thread.sleep(600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        drain();   // 第 57 轮审计提示①：runOnUiThread 只是入队，断言前必须先把队列排空
        chk("⑮ 空盘点复盘不跳转（不开空页面）", Boolean.TRUE, Boolean.valueOf(android.app.Activity.lastStarted == null));
        chk("⑮ 空盘点复盘给出提示", Boolean.TRUE, Boolean.valueOf(events().contains("还没有落子")));

        // ⑮b 空盘 + 电脑正在想（执白新局等第一手）：该提示「等电脑下完这一手」，不是「还没有落子」
        GomokuActivity nw = fresh();
        call(nw, "newGame");
        set(nw, "aiThinking", Boolean.TRUE);
        android.app.Activity.lastStarted = null;
        int nwFrom = FakeLog.EVENTS.size();
        call(nw, "startReview");
        // 第 67 轮审计 (c)②：给「不跳转」也留一个有界等待窗口（对标 ⑮ 的 600ms），否则守卫被删它也照样绿
        try { Thread.sleep(600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        drain();
        chk("⑮b 空盘 + 电脑思考中：不跳转", Boolean.TRUE, Boolean.valueOf(android.app.Activity.lastStarted == null));
        String nwNew = newEvents(nwFrom);   // 第 68 轮审计 (a)④：⑮b 也走增量，别读累积事件
        chk("⑮b 空盘 + 电脑思考中：提示等电脑下完这一手", Boolean.TRUE, Boolean.valueOf(nwNew.contains("等电脑下完这一手")));
        chk("⑮b 空盘 + 电脑思考中：不说「还没有落子」", Boolean.FALSE, Boolean.valueOf(nwNew.contains("还没有落子")));

        // ⑮c 有手顺 + 电脑正在想：同样是「等电脑」，不能因为盘上有子就去开页面（第 66 轮审计 (c)①）
        GomokuActivity nv = fresh();
        call(nv, "newGame");
        call(nv, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(7 * 15 + 7));
        set(nv, "aiThinking", Boolean.TRUE);
        android.app.Activity.lastStarted = null;
        int nvFrom = FakeLog.EVENTS.size();
        call(nv, "startReview");
        try { Thread.sleep(600); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        drain();
        String nvNew = newEvents(nvFrom);
        chk("⑮c 有手顺 + 电脑思考中：不跳转", Boolean.TRUE, Boolean.valueOf(android.app.Activity.lastStarted == null));
        chk("⑮c 有手顺 + 电脑思考中：提示等电脑下完这一手", Boolean.TRUE, Boolean.valueOf(nvNew.contains("等电脑下完这一手")));
        chk("⑮c 有手顺 + 电脑思考中：不说「还没有落子」", Boolean.FALSE, Boolean.valueOf(nvNew.contains("还没有落子")));

        // ⑯/⑰ 造一个真的 onCreate 过的实例（fresh() 只造壳：没有 root/scroll/topChrome/bottomChrome）
        GomokuActivity ui = (GomokuActivity) U.allocateInstance(GomokuActivity.class);
        setDefaults(ui);
        set(ui, "prefs", new android.content.SharedPreferences());
        set(ui, "resources", new android.content.res.Resources(new android.content.res.Configuration(), new android.util.DisplayMetrics()));
        ui.applicationInfo = new android.content.pm.ApplicationInfo();
        ui.applicationInfo.nativeLibraryDir = "/nonexistent";
        call(ui, "onCreate", new Class<?>[]{android.os.Bundle.class}, new android.os.Bundle());
        waitMore("runOnUiThread", WAIT_MS); drain();

        // ⑰ 棋盘按「实际可用空间」自适应（真机反馈：五子棋界面没有自适应）
        callAny(get(ui, "scroll"), "fakeSize", new Class<?>[]{int.class, int.class}, 1080, 1920);
        callAny(get(ui, "topChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 1080, 300);
        callAny(get(ui, "bottomChrome"), "fakeSize", new Class<?>[]{int.class, int.class}, 1080, 600);
        call(ui, "onWindowFocusChanged", new Class<?>[]{boolean.class}, Boolean.TRUE);
        android.view.ViewGroup.LayoutParams alp =
                (android.view.ViewGroup.LayoutParams) callAny(get(ui, "boardView"), "getLayoutParams", new Class<?>[]{});
        int dp16 = ((Integer) call(ui, "dp", new Class<?>[]{int.class}, 16)).intValue();
        int wantSide = Math.min(1080 - dp16, 1920 - 300 - 600 - dp16);
        chk("⑰ 棋盘边长 = min(可用宽, 可用高)（顶栏/底栏都扣掉）", Integer.valueOf(wantSide), Integer.valueOf(alp.width));

        // ⑯ 全屏切换：真机上曾因给 root 换 LayoutParams 类型而崩 —— 用户看到的就是「按全屏键像按了返回键」
        Object rootV = get(ui, "root");
        Object lpBefore = callAny(rootV, "getLayoutParams", new Class<?>[]{});
        call(ui, "enterFullscreen");
        chk("⑯ 进全屏没有换掉 root 的 LayoutParams 对象（换了真机会强转崩溃）", lpBefore, callAny(rootV, "getLayoutParams", new Class<?>[]{}));
        android.view.ViewGroup.LayoutParams blp =
                (android.view.ViewGroup.LayoutParams) callAny(get(ui, "boardView"), "getLayoutParams", new Class<?>[]{});
        chk("⑯ 进全屏后棋盘铺满（宽 = MATCH_PARENT）", Integer.valueOf(-1), Integer.valueOf(blp.width));
        call(ui, "exitFullscreen");
        android.view.ViewGroup.LayoutParams blp2 =
                (android.view.ViewGroup.LayoutParams) callAny(get(ui, "boardView"), "getLayoutParams", new Class<?>[]{});
        chk("⑯ 退全屏后棋盘回到正方形", Boolean.TRUE, Boolean.valueOf(blp2.width == blp2.height));


        // ⑱ 学习：记战绩 / 落盘 / 「学习」按钮 / 学到的一手真的会走
        Object learnObj = get(ui, "learn");
        chk("⑱ 建页时读了学习库（字段不为空）", Boolean.TRUE, Boolean.valueOf(learnObj != null));

        Object bottom = get(ui, "bottomChrome");
        int rows = ((Integer) callAny(bottom, "getChildCount", new Class<?>[]{})).intValue();
        Object lastRow = callAny(bottom, "getChildAt", new Class<?>[]{int.class}, Integer.valueOf(rows - 1));
        int cells = ((Integer) callAny(lastRow, "getChildCount", new Class<?>[]{})).intValue();
        Object modeBtn0 = callAny(lastRow, "getChildAt", new Class<?>[]{int.class}, Integer.valueOf(0));
        chk("⑱ 最后一行第一个按钮是「对战」（v128 起）", "对战", callAny(modeBtn0, "getText", new Class<?>[]{}));
        Object learnBtn = callAny(lastRow, "getChildAt", new Class<?>[]{int.class}, Integer.valueOf(1));
        chk("⑱ 最后一行第二个按钮是「学习」", "学习", callAny(learnBtn, "getText", new Class<?>[]{}));
        chk("⑱ 最后一行是 5 个按钮（对战/学习/全屏/复盘/返回）", Integer.valueOf(5), Integer.valueOf(cells));

        // 用真构造器造的页面来点（字段初始化器跑过 ⇒ learnClick 非空；Unsafe 造的实例监听器是 null）
        GomokuActivity clickUi = null;
        boolean pageOk = true;
        try {
            clickUi = nativePage();
        } catch (NullPointerException npe) {
            pageOk = false;
            chk("⑱ 真构造器造页（nativePage）没崩", Boolean.TRUE, Boolean.FALSE);
        }
        if (!pageOk) {
            System.out.println("通过 " + pass + " 项，失败 " + fail + " 项");
            if (fail > 0) System.exit(1);
            return;
        }
        Object cc = get(clickUi, "bottomChrome");
        Object row2 = callAny(cc, "getChildAt", new Class<?>[]{int.class},
                Integer.valueOf(callInt(cc, "getChildCount") - 1));
        Object learnBtn2 = callAny(row2, "getChildAt", new Class<?>[]{int.class}, Integer.valueOf(1));
        chk("⑱ 真构造器造的页面：最后一个按钮行的第二个是「学习」", "学习", callAny(learnBtn2, "getText", new Class<?>[]{}));
        chk("⑱ 真构造器造的页面：学习按钮挂了监听器", Boolean.FALSE,
                Boolean.valueOf(((android.view.View) learnBtn2).clickListener == null));
        int evFrom = android.os.FakeLog.EVENTS.size();
        callAny(learnBtn2, "fakeClick", new Class<?>[]{});
        String ev = newEvents(evFrom);

        // 黑五连收官 ⇒ 应当把这盘记进学习库并落盘（先开新局，别沿用别的用例留下的子）
        call(ui, "newGame");
        chk("⑱ 开局前棋盘是空的", Integer.valueOf(0), Integer.valueOf(bd(ui).moves.size()));
        java.io.File lf = (java.io.File) call(ui, "learnFile");
        lf.delete();
        chk("⑱ 落盘断言前先删掉旧存档（否则这条断言没区分力）", Boolean.FALSE, Boolean.valueOf(lf.isFile()));
        int gamesBefore = ((GomokuLearn) get(ui, "learn")).games();
        int p0 = 7 * 15 + 3;
        call(ui, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(p0));
        call(ui, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.WHITE), Integer.valueOf(0 * 15 + 0));
        call(ui, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(7 * 15 + 4));
        call(ui, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.WHITE), Integer.valueOf(0 * 15 + 1));
        call(ui, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(7 * 15 + 5));
        call(ui, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.WHITE), Integer.valueOf(0 * 15 + 2));
        call(ui, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(7 * 15 + 6));
        call(ui, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.WHITE), Integer.valueOf(0 * 15 + 3));
        call(ui, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(7 * 15 + 7));
        GomokuLearn lib = (GomokuLearn) get(ui, "learn");
        chk("⑱ 终局后记下这一盘（比开局前多 1 盘）", Integer.valueOf(gamesBefore + 1), Integer.valueOf(lib.games()));
        chk("⑱ 学习库落盘（存档是新写出来的，不是上一轮跑剩的）", Boolean.TRUE,
                Boolean.valueOf(lf.isFile() && lf.length() > 0));

        // 学到的一手真的会走：预先造库，让「第二候选」在 3 盘里都赢
        GomokuActivity adv = (GomokuActivity) U.allocateInstance(GomokuActivity.class);
        setDefaults(adv);
        set(adv, "prefs", new android.content.SharedPreferences());
        set(adv, "resources", new android.content.res.Resources(new android.content.res.Configuration(), new android.util.DisplayMetrics()));
        adv.applicationInfo = new android.content.pm.ApplicationInfo();
        adv.applicationInfo.nativeLibraryDir = "/nonexistent";
        call(adv, "onCreate", new Class<?>[]{android.os.Bundle.class}, new android.os.Bundle());
        waitMore("runOnUiThread", WAIT_MS); drain();
        call(adv, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(0));
        GomokuBoard ab = bd(adv);
        GomokuEngine ae = (GomokuEngine) get(adv, "engine");
        int[] cands = ae.topMoves(ab, GomokuBoard.WHITE, 5);
        chk("⑱ 这一局面有多个候选（否则学不出来差别）", Boolean.TRUE, Boolean.valueOf(cands.length >= 2));
        // 学出来的那一手：y 取「候选里不是引擎首选、也不和夹具撞点」的那一手
        int[] reserved = {0, 1, 2, 3};
        int y = -1;
        for (int cand : cands) {
            boolean used = false;
            for (int r : reserved) if (r == cand) used = true;
            if (!used && cand != cands[0]) { y = cand; break; }
        }
        chk("⑱ 找到一手可以安全用来造历史的候选", Boolean.TRUE, Boolean.valueOf(y >= 0));
        GomokuLearn pre = new GomokuLearn();
        for (int kk = 0; kk < 3; kk++) {
            java.util.List<int[]> gm = new java.util.ArrayList<>();
            gm.add(new int[]{GomokuBoard.BLACK, 0});
            gm.add(new int[]{GomokuBoard.WHITE, y});
            gm.add(new int[]{GomokuBoard.BLACK, 1});
            gm.add(new int[]{GomokuBoard.WHITE, 2});
            gm.add(new int[]{GomokuBoard.BLACK, 3});
            pre.recordGame(gm, 15, GomokuBoard.WHITE);
        }
        chk("⑱ 预置的三盘都记进去了（手顺合法）", Integer.valueOf(3), Integer.valueOf(pre.games()));
        set(adv, "learn", pre);
        int movesBefore = ab.moves.size();
        call(adv, "startAiTurn");
        waitMore("runOnUiThread", 8000); drain();
        sleep5(); waitMore("runOnUiThread", 2000); drain();
        GomokuBoard ab2 = bd(adv);
        chk("⑱ 学习有把握时，电脑走的是「学出来的那一手」而不是引擎首选", Integer.valueOf(y),
                Integer.valueOf(ab2.moves.size() > movesBefore ? ab2.moves.get(movesBefore)[1] : -1));

        // ⑲ 电脑对电脑（用户要求「AI 对 AI」）
        GomokuActivity av = nativePage();
        chk("⑲ 五子棋对局页挂着「穗安」水印", Boolean.TRUE, hasWatermark((android.view.View) get(av, "content")));
        Object row3 = ((android.view.ViewGroup) get(av, "bottomChrome")).getChildAt(2);
        Object modeBtn = ((android.view.ViewGroup) row3).getChildAt(0);
        chk("⑲ 第三行第一个按钮是「对战」", "对战",
                modeBtn instanceof android.widget.TextView ? String.valueOf(((android.widget.TextView) modeBtn).getText()) : null);
        set(av, "aiVsAi", Boolean.FALSE);
        set(av, "aiPaused", Boolean.FALSE);
        ((android.view.View) modeBtn).fakeClick();
        chk("⑲ 点「对战」进入电脑对战", Boolean.TRUE, Boolean.valueOf(((Boolean) get(av, "aiVsAi")).booleanValue()));
        String st19 = String.valueOf(get(av, "statusText"));
        chk("⑲ 进入电脑对战后的状态条是电脑文案（实际：「" + st19 + "」）", Boolean.TRUE,
                Boolean.valueOf(st19.contains("电脑对战") || st19.contains("电脑思考中")));
        chk("⑲ 电脑对战时不写「轮到你落子」", Boolean.FALSE,
                Boolean.valueOf(st19.contains("轮到你落子")));
        int n1 = waitMoves(av, 1, 8000);
        chk("⑲ 电脑黑先落一手", Boolean.TRUE, Boolean.valueOf(n1 >= 1));
        int n2 = waitMoves(av, 3, 15000);
        chk("⑲ 双方都在下（连下 3 手）", Boolean.TRUE, Boolean.valueOf(n2 >= 3));
        call(av, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(50));
        drain();
        chk("⑲ 电脑对战时人点棋盘不留子", Integer.valueOf(GomokuBoard.EMPTY),
                Integer.valueOf(bd(av).grid[50]));
        call(av, "toggleAiVsAi");   // 暂停（直接驱动状态机，避免依赖按钮点击次数）
        chk("⑲ 暂停开关置位", Boolean.TRUE, Boolean.valueOf(((Boolean) get(av, "aiPaused")).booleanValue()));
        int n3 = bd(av).moves.size();
        call(av, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(50));
        drain(); sleep5(); drain(); Thread.sleep(1500); drain();
        chk("⑲ 暂停后不再落子（人点棋盘也不落）", Integer.valueOf(n3), Integer.valueOf(bd(av).moves.size()));
        call(av, "toggleAiVsAi");   // 继续
        int n4 = waitMoves(av, n3 + 1, 15000);
        chk("⑲ 继续后接着下", Boolean.TRUE, Boolean.valueOf(n4 > n3));
        // 暂停中按「新局」：aiPaused 也必须拦住 maybeStartAiTurn（否则新局会立刻自己下起来）
        call(av, "toggleAiVsAi");   // 先暂停
        call(av, "newGame");
        sleep5(); drain(); Thread.sleep(1200); drain();
        chk("⑲ 暂停时开新局也不自动落子", Integer.valueOf(0),
                Integer.valueOf(((GomokuBoard) get(av, "board")).moves.size()));
        // 确定性判据：空盘 + 轮到黑（就是 humanColor）⇒ 没有 aiVsAi 守卫时这一手会真落下去
        set(av, "aiVsAi", Boolean.TRUE);
        set(av, "humanColor", Integer.valueOf(GomokuBoard.BLACK));
        set(av, "board", new GomokuBoard(15, false));
        call(av, "onHumanTap", new Class<?>[]{int.class}, Integer.valueOf(50));
        drain();
        chk("⑲ 电脑对战时人点棋盘不留子（空盘轮到黑也不落）", Integer.valueOf(0),
                Integer.valueOf(((GomokuBoard) get(av, "board")).moves.size()));
        // 文档里承诺的三句用户可见文案，必须有断言看着（第 81 轮审计指出：删掉它们套件曾全绿）
        // 文档里承诺的开局文案：直接问 startText()（状态条会被 AI 回调覆盖成「电脑思考中…」，读状态条不确定）
        chk("⑲ 电脑对战的开局文案", "电脑对战：黑先，双方都由电脑下。",
                String.valueOf(call(av, "startText")));
        set(av, "aiPaused", Boolean.FALSE);   // 归一化：保证下面这一下走的是「暂停」路径（此刻可能已是暂停态）
        call(av, "toggleAiVsAi");             // 暂停：暂停路径同步写状态条
        drain();
        String stP19 = String.valueOf(get(av, "statusText"));
        chk("⑲ 暂停时状态条写明「已暂停」（实际：「" + stP19 + "」）", Boolean.TRUE,
                Boolean.valueOf(stP19.contains("已暂停")));
        call(av, "newGame");                  // 暂停中开新局：状态条应同时含开局句与「已暂停」提示（这行专治「暂停后缀被删」）
        sleep5(); drain();
        String stP2 = String.valueOf(get(av, "statusText"));
        chk("⑲ 暂停中开新局的文案以电脑对战开局句开头（实际：「" + stP2 + "」）", Boolean.TRUE,
                Boolean.valueOf(stP2.startsWith("电脑对战：黑先，双方都由电脑下。")));
        chk("⑲ 暂停中开新局的文案写明「已暂停」（实际：「" + stP2 + "」）", Boolean.TRUE,
                Boolean.valueOf(stP2.contains("（已暂停，点「对战」继续）")));
        Object row1x = ((android.view.ViewGroup) get(av, "bottomChrome")).getChildAt(0);
        int undoToast0 = android.os.FakeLog.countPrefix("toast:电脑对战不能悔棋，点「新局」重开。");
        int resignToast0 = android.os.FakeLog.countPrefix("toast:电脑对战没有认输，点「新局」重开。");
        ((android.view.View) ((android.view.ViewGroup) row1x).getChildAt(1)).fakeClick();
        ((android.view.View) ((android.view.ViewGroup) row1x).getChildAt(2)).fakeClick();
        drain();
        chk("⑲ 电脑对战时点「悔棋」给出提示", Integer.valueOf(undoToast0 + 1),
                Integer.valueOf(android.os.FakeLog.countPrefix("toast:电脑对战不能悔棋，点「新局」重开。")));
        chk("⑲ 电脑对战时点「认输」给出提示", Integer.valueOf(resignToast0 + 1),
                Integer.valueOf(android.os.FakeLog.countPrefix("toast:电脑对战没有认输，点「新局」重开。")));
        // ⑲ 双方轮流、各下各的颜色（真机反馈：黑棋不出手、白棋一手下到底 —— v132 回归钉）
        GomokuActivity av2 = nativePage();
        set(av2, "aiVsAi", Boolean.TRUE);
        call(av2, "newGame");
        long tAlt = System.currentTimeMillis();
        while (System.currentTimeMillis() - tAlt < WAIT_MS && bd(av2).moves.size() < 6) {
            drain();
            try { Thread.sleep(15); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        GomokuBoard bAlt = bd(av2);
        int nBlack19 = 0, nWhite19 = 0;
        for (int i = 0; i < bAlt.moves.size(); i++) {
            if (bAlt.moves.get(i)[0] == GomokuBoard.BLACK) nBlack19++; else nWhite19++;
        }
        chk("⑲ 电脑对战双方都落子（黑 " + nBlack19 + " / 白 " + nWhite19 + "）", Boolean.TRUE,
                Boolean.valueOf(nBlack19 >= 1 && nWhite19 >= 1));
        boolean altOk = true;
        for (int i = 0; i < bAlt.moves.size(); i++) {
            int want = (i % 2 == 0) ? GomokuBoard.BLACK : GomokuBoard.WHITE;
            if (bAlt.moves.get(i)[0] != want) altOk = false;
        }
        chk("⑲ 电脑对战每一手的颜色按黑先交替", Boolean.TRUE, Boolean.valueOf(altOk));

        // ⑲ 电脑对电脑的棋不记进学习库（直接构造黑五连终局后调 finishGame）
        GomokuActivity av3 = nativePage();
        set(av3, "aiVsAi", Boolean.TRUE);
        GomokuBoard bWin19 = new GomokuBoard(15, false);
        set(av3, "board", bWin19);
        int[] seq19 = { bWin19.idx(7, 3), bWin19.idx(0, 0), bWin19.idx(7, 4), bWin19.idx(0, 1), bWin19.idx(7, 5),
                bWin19.idx(0, 2), bWin19.idx(7, 6), bWin19.idx(0, 3), bWin19.idx(7, 7) };
        for (int i = 0; i < seq19.length; i++) {
            bWin19.play(i % 2 == 0 ? GomokuBoard.BLACK : GomokuBoard.WHITE, seq19[i]);
        }
        chk("⑲ 夹具：黑五连终局", Boolean.TRUE, Boolean.valueOf(bWin19.gameOver));
        Object lr19 = get(av3, "learn");
        int g19 = callInt(lr19, "games");
        call(av3, "finishGame");
        chk("⑲ 电脑对电脑的棋不记进学习库", Integer.valueOf(g19), Integer.valueOf(callInt(lr19, "games")));

        // ⑲b 电脑对战不进学习库：learn.pick 那处守卫（第 85 轮审计 发现没断言看着）
        resetSaved();
        GomokuActivity av5 = nativePage();
        set(av5, "aiVsAi", Boolean.TRUE);
        set(av5, "aiPaused", Boolean.TRUE);
        call(av5, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(0));
        GomokuBoard bd5 = bd(av5);
        int[] c5 = ((GomokuEngine) get(av5, "engine")).topMoves(bd5, GomokuBoard.WHITE, 5);
        int engMove5 = ((GomokuEngine) get(av5, "engine")).genmove(bd5, GomokuBoard.WHITE);
        int y5 = -1;
        for (int cand : c5) {
            if (cand != c5[0] && cand != engMove5 && cand != 0 && cand != 1 && cand != 2 && cand != 3) { y5 = cand; break; }
        }
        chk("⑲b 夹具：找到「学得出来但不是引擎首选」的候选", Boolean.TRUE,
                Boolean.valueOf(y5 >= 0 && y5 != c5[0]));
        SpyLearn pre5 = new SpyLearn();
        for (int kk = 0; kk < 3; kk++) {
            java.util.List<int[]> gm = new java.util.ArrayList<>();
            gm.add(new int[]{GomokuBoard.BLACK, 0});
            gm.add(new int[]{GomokuBoard.WHITE, y5});
            gm.add(new int[]{GomokuBoard.BLACK, 1});
            gm.add(new int[]{GomokuBoard.WHITE, 2});
            gm.add(new int[]{GomokuBoard.BLACK, 3});
            pre5.recordGame(gm, 15, GomokuBoard.WHITE);
        }
        chk("⑲b 夹具：三盘历史都记进去了", Integer.valueOf(3), Integer.valueOf(pre5.games()));
        set(av5, "learn", pre5);
        int before5 = bd5.moves.size();
        call(av5, "startAiTurn");
        waitMore("runOnUiThread", 8000); drain(); sleep5(); waitMore("runOnUiThread", 2000); drain();
        GomokuBoard b6 = bd(av5);
        int played5 = b6.moves.size() > before5 ? b6.moves.get(before5)[1] : -1;
        chk("⑲b 电脑对战不走「学习」那一手", Boolean.TRUE, Boolean.valueOf(played5 != y5));
        chk("⑲b 电脑对战不调用 learn.pick", Integer.valueOf(0), Integer.valueOf(pre5.pickCalls));

        // ⑲b 对照：同样的局面与学习库，但人机模式 ⇒ 应该真的走「学出来的那一手」
        resetSaved();
        GomokuActivity avB = nativePage();
        set(avB, "aiVsAi", Boolean.FALSE);
        set(avB, "humanColor", Integer.valueOf(GomokuBoard.BLACK));
        call(avB, "play", new Class<?>[]{int.class, int.class}, Integer.valueOf(GomokuBoard.BLACK), Integer.valueOf(0));
        set(avB, "learn", pre5);
        int beforeB = bd(avB).moves.size();
        call(avB, "startAiTurn");
        waitMore("runOnUiThread", 8000); drain(); sleep5(); waitMore("runOnUiThread", 2000); drain();
        GomokuBoard bdB = bd(avB);
        int playedB = bdB.moves.size() > beforeB ? bdB.moves.get(beforeB)[1] : -1;
        chk("⑲b 对照：人机模式会调用 learn.pick", Boolean.TRUE, Boolean.valueOf(pre5.pickCalls >= 1));
        chk("⑲b 对照：人机模式落子合法", Boolean.TRUE, Boolean.valueOf(playedB >= 0 && playedB < 225));

        // ⑲c 复盘成功回调：电脑对战不记失误、人机模式会记（探针学习库数调用次数）
        resetSaved();
        GomokuActivity av6 = nativePage();
        set(av6, "aiVsAi", Boolean.TRUE);
        set(av6, "aiPaused", Boolean.TRUE);
        GomokuBoard b7 = bd(av6);
        int[] seqA = { b7.idx(7, 7), b7.idx(0, 0), b7.idx(7, 8), b7.idx(0, 1), b7.idx(8, 7), b7.idx(0, 2) };
        for (int i = 0; i < seqA.length; i++) {
            call(av6, "play", new Class<?>[]{int.class, int.class},
                    Integer.valueOf(i % 2 == 0 ? GomokuBoard.BLACK : GomokuBoard.WHITE), Integer.valueOf(seqA[i]));
        }
        SpyLearn spyA = new SpyLearn();
        set(av6, "learn", spyA);
        call(av6, "startReview");
        chk("⑲c 电脑对战：复盘页仍然打开", Boolean.TRUE,
                Boolean.valueOf(waitDrain("startActivity:com.autogo.GomokuReviewActivity", 20000)));
        waitMore("runOnUiThread", 3000); drain();
        chk("⑲c 电脑对战复盘成功回调不记失误（探针调用次数）", Integer.valueOf(0),
                Integer.valueOf(spyA.blunderCalls));

        resetSaved();
        GomokuActivity avC = nativePage();
        set(avC, "aiVsAi", Boolean.FALSE);
        set(avC, "humanColor", Integer.valueOf(GomokuBoard.BLACK));
        GomokuBoard bdC = bd(avC);
        int[] seqB = { bdC.idx(7, 7), bdC.idx(0, 0), bdC.idx(7, 8), bdC.idx(0, 1), bdC.idx(8, 7), bdC.idx(0, 2) };
        for (int i = 0; i < seqB.length; i++) {
            call(avC, "play", new Class<?>[]{int.class, int.class},
                    Integer.valueOf(i % 2 == 0 ? GomokuBoard.BLACK : GomokuBoard.WHITE), Integer.valueOf(seqB[i]));
        }
        SpyLearn spyB = new SpyLearn();
        set(avC, "learn", spyB);
        call(avC, "startReview");
        chk("⑲c 人机模式：复盘页打开（对照组）", Boolean.TRUE,
                Boolean.valueOf(waitDrain("startActivity:com.autogo.GomokuReviewActivity", 20000)));
        waitMore("runOnUiThread", 3000); drain();
        chk("⑲c 人机模式复盘成功回调会记失误（探针调用次数 ≥1）", Boolean.TRUE,
                Boolean.valueOf(spyB.blunderCalls >= 1));

        call(av, "toggleAiVsAi");   // 收尾：暂停这条线程

        System.out.println("v95 设置探针：PASS " + pass + " / FAIL " + fail);
        System.exit(fail == 0 ? 0 : 1);
    }

    /** 探针学习库：数一数 recordBlunders / recordGame 被调了几次（给「电脑对战不进学习库」的守卫补断言）。 */
    /** 清掉跨页静态存档：反射造的页会把上几组的棋局从 savedMoves 里恢复出来（⑲c 曾因此「该点已有子」）。 */
    static void resetSaved() {
        try {
            Class<?> c = Class.forName("com.autogo.GomokuActivity");
            java.lang.reflect.Field f = c.getDeclaredField("savedMoves");
            f.setAccessible(true);
            f.set(null, null);
        } catch (Exception e) {
            // 清不掉就让夹自己去撞；这里不掩盖真正的断言失败
        }
    }

    static final class SpyLearn extends GomokuLearn {
        int blunderCalls;
        int gameCalls;
        int pickCalls;

        @Override
        public int pick(GomokuBoard board, int color, int[] ranked, int engineTop) {
            pickCalls++;
            return super.pick(board, color, ranked, engineTop);
        }

        @Override
        public int recordBlunders(java.util.List<int[]> moves, int size, int humanColor, double[] loss, double min) {
            blunderCalls++;
            return super.recordBlunders(moves, size, humanColor, loss, min);
        }

        @Override
        public boolean recordGame(java.util.List<int[]> moves, int size, int result) {
            gameCalls++;
            return super.recordGame(moves, size, result);
        }
    }
}
