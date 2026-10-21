/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.content.Intent;
import android.widget.TextView;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/** v98 候选：真 GomokuReviewActivity 的桩测（假 android 类 + Unsafe 之外的普通 new）。 */
public class ReviewProbe {

    static int pass = 0, fail = 0;


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


    /** 沿继承链找字段（假 Activity 的 content 声明在父类上）。 */
    static Object getUp(Object o, String name) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(o);
            } catch (NoSuchFieldException e) {
                // 继续往父类找
            }
        }
        throw new NoSuchFieldException(name);
    }

    static void chk(String what, boolean ok) {
        if (ok) { pass++; System.out.println("PASS  " + what); }
        else { fail++; System.out.println("FAIL  " + what); }
    }

    static void chkEq(String what, Object exp, Object act) {
        boolean ok = (exp == null) ? (act == null) : exp.equals(act);
        chk(what + "（期望 " + exp + "，实际 " + act + "）", ok);
    }

    // ——— 反射工具 ———
    static Object get(Object o, String f) throws Exception {
        Field fd = GomokuReviewActivity.class.getDeclaredField(f);
        fd.setAccessible(true);
        return fd.get(o);
    }

    static int gi(Object o, String f) throws Exception { return ((Number) get(o, f)).intValue(); }
    static boolean gb(Object o, String f) throws Exception { return (Boolean) get(o, f); }
    static String gs(Object o, String f) throws Exception { return (String) get(o, f); }
    static int len(Object arr) { return arr == null ? -1 : Array.getLength(arr); }
    static int listSize(Object l) { return l == null ? -1 : ((List<?>) l).size(); }

    /** 取列表里第 i 个元素（探针里到处要拿「第几行」）。 */
    static Object rowAt(Object l, int i) { return ((List<?>) l).get(i); }

    /** 真的点一下那一行（假 View 记下了 setOnClickListener 注册的回调）。 */
    static void clickRow(Object row) { ((android.view.View) row).fakeClick(); }

    /** 被标了底色的手顺行下标（逗号分隔；没有就是空串）。 */
    static String highlightRows(Object a) throws Exception {
        List<?> rs = (List<?>) get(a, "rows");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rs.size(); i++) {
            android.view.View row = (android.view.View) rs.get(i);
            if (row.bgColor != 0) {
                if (sb.length() > 0) sb.append(",");
                sb.append(i);
            }
        }
        return sb.toString();
    }

    /** 直接把选中位置拨回 0（开局前）。 */
    static void selectZero(GomokuReviewActivity a) throws Exception {
        java.lang.reflect.Method m = GomokuReviewActivity.class.getDeclaredMethod("select", int.class);
        m.setAccessible(true);
        m.invoke(a, 0);
    }

    /** 胜率条当前显示的标签（真 WinrateBarView 的私有字段）。 */
    /** 读任意对象（含父类）的私有字段 —— 上面那个 get() 只认 GomokuReviewActivity 自己的字段。 */
    static Object getF(Object o, String f) throws Exception {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try { Field fd = k.getDeclaredField(f); fd.setAccessible(true); return fd.get(o); } catch (NoSuchFieldException e) { }
        }
        throw new NoSuchFieldException(f + "（" + o.getClass().getName() + " 及其父类都没有）");
    }

    static double barValue(Object a) throws Exception {
        Object b = get(a, "bar");
        return ((Double) getF(b, "blackWinrate")).doubleValue();
    }

    static int chartSelected(Object a) throws Exception {
        Object c = get(a, "chart");
        return ((Integer) getF(c, "selected")).intValue();
    }

    static boolean near(double x, double y) throws Exception { return Math.abs(x - y) < 1e-9; }

    static String barLabel(GomokuReviewActivity a) throws Exception {
        Object bar = get(a, "bar");
        java.lang.reflect.Field fd = bar.getClass().getDeclaredField("label");
        fd.setAccessible(true);
        return (String) fd.get(bar);
    }
    static String text(Object o, String f) throws Exception { return ((TextView) get(o, f)).getText().toString(); }

    static void callInt(Object o, String m, int v) throws Exception {
        Method md = GomokuReviewActivity.class.getDeclaredMethod(m, int.class);
        md.setAccessible(true);
        md.invoke(o, v);
    }

    static int callColorAt(Object o, int i) throws Exception {
        Method md = GomokuReviewActivity.class.getDeclaredMethod("colorAt", int.class);
        md.setAccessible(true);
        return ((Number) md.invoke(o, i)).intValue();
    }

    @SuppressWarnings("unchecked")
    static void callOnCreate(Object o) throws Exception {
        Method md = GomokuReviewActivity.class.getDeclaredMethod("onCreate", android.os.Bundle.class);
        md.setAccessible(true);
        md.invoke(o, new android.os.Bundle());
    }

    static void callNoArg(Object o, String m) throws Exception {
        Method md = GomokuReviewActivity.class.getDeclaredMethod(m);
        md.setAccessible(true);
        md.invoke(o);
    }

    // ——— 夹具 ———
    static final int N = 9;

    static Intent base() {
        return intentFor(19, false, GomokuBoard.BLACK, N, true, GomokuBoard.BLACK, "黑五连", "黑胜（测试）");
    }

    static Intent intentFor(int size, boolean forbid, int first, int n, boolean goodArrays,
                            int winner, String end, String verdict) {
        Intent it = new Intent();
        it.putExtra(GomokuReviewActivity.EXTRA_SIZE, size);
        it.putExtra(GomokuReviewActivity.EXTRA_FORBID, forbid);
        it.putExtra(GomokuReviewActivity.EXTRA_FIRST, first);
        int[] mv = new int[2 * n];
        for (int i = 0; i < n; i++) {
            mv[2 * i] = (i % 2 == 0) ? first : GomokuBoard.opponent(first);
            mv[2 * i + 1] = i * size + (i % size);
        }
        it.putExtra(GomokuReviewActivity.EXTRA_MOVES, mv);
        int wl = goodArrays ? n + 1 : n;   // 坏夹具：wr 故意短一格
        double[] wr = new double[wl];
        double[] ls = new double[n];
        String[] bm = new String[n + 1];
        int[] bp = new int[n + 1];
        for (int i = 0; i < wl; i++) wr[i] = 0.5;
        for (int i = 0; i < n; i++) { ls[i] = 0.01; bm[i] = ""; bp[i] = -1; }
        bm[n] = "";
        bp[n] = -1;
        it.putExtra(GomokuReviewActivity.EXTRA_WR, wr);
        it.putExtra(GomokuReviewActivity.EXTRA_LOSS, ls);
        it.putExtra(GomokuReviewActivity.EXTRA_BEST, bm);
        it.putExtra(GomokuReviewActivity.EXTRA_BESTPT, bp);
        if (verdict != null) it.putExtra(GomokuReviewActivity.EXTRA_VERDICT, verdict);
        if (end != null) it.putExtra(GomokuReviewActivity.EXTRA_END, end);
        it.putExtra(GomokuReviewActivity.EXTRA_WINNER, winner);
        return it;
    }

    static GomokuReviewActivity make(Intent it) throws Exception {
        GomokuReviewActivity a = new GomokuReviewActivity();
        a.intent = it;
        callOnCreate(a);
        return a;
    }

    public static void main(String[] args) throws Exception {
        // ① 正常数据全部收下
        GomokuReviewActivity a = make(base());
        chk("⑰ 复盘页挂着「穗安」水印", hasWatermark((android.view.View) getUp(a, "content")));
        chkEq("① 路数收下", 19, gi(a, "size"));
        chkEq("① 禁手开关收下", false, gb(a, "forbid"));
        chkEq("① 首手颜色收下", GomokuBoard.BLACK, gi(a, "firstColor"));
        chkEq("① 手顺条数", N, listSize(get(a, "moves")));
        chkEq("① wrToMove 长度 n+1", N + 1, len(get(a, "wrToMove")));
        chkEq("① loss 长度 n", N, len(get(a, "loss")));
        chkEq("① bestMove 长度 n+1", N + 1, len(get(a, "bestMove")));
        chkEq("① bestPoint 长度 n+1", N + 1, len(get(a, "bestPoint")));
        chkEq("① 结论文字收下", "黑胜（测试）", gs(a, "verdictLine"));
        chkEq("① 终局文字收下", "黑五连", gs(a, "endText"));
        chk("① 胜率条建起来了", get(a, "bar") != null);
        chk("① 曲线建起来了", get(a, "chart") != null);
        chkEq("① 手顺行 = n + 终局行", N + 1, listSize(get(a, "rows")));
        chkEq("① onCreate 末尾选中终局行", N, gi(a, "sel"));
        chk("① 位置文字含「黑棋胜率」", text(a, "positionText").contains("黑棋胜率"));
        chkEq("① 无大失误时不列失误行", 0, listSize(get(a, "blunders")));

        // ② 大失误进「失误排行」（loss[2] = 0.30 ⇒ 第 3 手）
        Intent big = base();
        double[] ls = (double[]) get(a, "loss");
        double[] ls2 = new double[ls.length];
        for (int i = 0; i < ls.length; i++) ls2[i] = 0.01;
        ls2[2] = 0.30;
        big.putExtra(GomokuReviewActivity.EXTRA_LOSS, ls2);
        GomokuReviewActivity b = make(big);
        chkEq("② 一条大失误 ⇒ 一行排行", 1, listSize(get(b, "blunders")));
        chk("② 排行里写明是第 3 手", ((TextView) ((List<?>) get(b, "blunders")).get(0)).getText().toString().contains("第 3 手"));

        // ③ 预计算数组长度不匹配 ⇒ 只收基础信息，且不再读「终局文字」
        GomokuReviewActivity c = make(intentFor(19, false, GomokuBoard.BLACK, N, false, GomokuBoard.BLACK, "黑五连", "黑胜"));
        chkEq("③ 长度不匹配：手顺仍收下", N, listSize(get(c, "moves")));
        chkEq("③ 长度不匹配：结论文字仍收下", "黑胜", gs(c, "verdictLine"));
        chk("③ 长度不匹配：预计算数组不收", get(c, "wrToMove") == null && get(c, "loss") == null
                && get(c, "bestMove") == null && get(c, "bestPoint") == null);
        chkEq("③ 提前 return 后不再读终局文字", "", gs(c, "endText"));

        // ④ 手顺数组奇数长度 ⇒ 尾部半个忽略
        Intent odd = base();
        int[] mv = new int[2 * N + 1];
        for (int i = 0; i < 2 * N; i++) mv[i] = i;
        mv[2 * N] = 7;
        odd.putExtra(GomokuReviewActivity.EXTRA_MOVES, mv);
        chkEq("④ 奇数长度手顺 ⇒ 整手才计数", N, listSize(get(make(odd), "moves")));

        // ⑤ 空 Intent（对局页什么都没带）⇒ 预计算数组缺失，提前 return，连终局文字都不推导
        GomokuReviewActivity d = make(new Intent());
        chkEq("⑤ 空 Intent：路数回落 15", 15, gi(d, "size"));
        chkEq("⑤ 空 Intent：没有手顺", 0, listSize(get(d, "moves")));
        chkEq("⑤ 空 Intent：数组缺失时终局文字留空", "", gs(d, "endText"));
        chk("⑤ 空 Intent：预计算数组为空", get(d, "wrToMove") == null);
        chkEq("⑤ 空 Intent：只有终局行", 1, listSize(get(d, "rows")));

        // ⑤b 空棋谱但数组齐全（n = 0 ⇒ wr/bm/bp 长度 1、loss 长度 0）⇒ 走「（空棋谱）」分支
        GomokuReviewActivity e = make(intentFor(15, false, GomokuBoard.BLACK, 0, true, GomokuBoard.EMPTY, null, null));
        chkEq("⑤b 空棋谱：数组长度 1 被收下", 1, len(get(e, "wrToMove")));
        chkEq("⑤b 空棋谱：终局文字", "（空棋谱）", gs(e, "endText"));
        chkEq("⑤b 空棋谱：只有终局行", 1, listSize(get(e, "rows")));
        chk("⑤b 空棋谱时信息行写「空棋谱」而不是「开局」", text(e, "positionText").contains("空棋谱"));

        // ⑥ 没有「终局文字」时按 winner 推导
        chkEq("⑥ 黑胜推导", "黑胜", gs(make(intentFor(15, false, GomokuBoard.BLACK, N, true, GomokuBoard.BLACK, null, null)), "endText"));
        chkEq("⑥ 白胜推导", "白胜", gs(make(intentFor(15, false, GomokuBoard.BLACK, N, true, GomokuBoard.WHITE, null, null)), "endText"));
        chkEq("⑥ 和棋推导", "和棋（下满未分胜负）",
                gs(make(intentFor(15, false, GomokuBoard.BLACK, N, true, GomokuBoard.EMPTY, null, null)), "endText"));

        // ⑦ select 的夹取与文案
        callInt(a, "select", 999);
        chkEq("⑦ select 超出 ⇒ 夹到终局行", N, gi(a, "sel"));
        callInt(a, "select", -5);
        chkEq("⑦ select 小于 0 ⇒ 夹到 0", 0, gi(a, "sel"));
        callInt(a, "select", 3);
        chkEq("⑦ select 3 ⇒ sel 跟着走", 3, gi(a, "sel"));
        chk("⑦ 位置文字含「第 3 手」", text(a, "positionText").contains("第 3 手"));
        callInt(a, "select", N);
        chk("⑦ 回到终局行的位置文字含「第 9 手」", text(a, "positionText").contains("第 9 手"));

        // ⑧ 手顺颜色按首手颜色交替
        GomokuReviewActivity w = make(intentFor(15, false, GomokuBoard.WHITE, N, true, GomokuBoard.WHITE, "白胜", null));
        chkEq("⑧ 白先：第 0 手是白", GomokuBoard.WHITE, callColorAt(w, 0));
        chkEq("⑧ 白先：第 1 手是黑", GomokuBoard.BLACK, callColorAt(w, 1));
        chkEq("⑧ 黑先：第 0 手是黑", GomokuBoard.BLACK, callColorAt(a, 0));
        chkEq("⑧ 黑先：第 1 手是白", GomokuBoard.WHITE, callColorAt(a, 1));

        // ⑨ onPause / onResume 不崩，resume 后仍停在原来那一手
        int before = android.os.Handler.POSTED.size();
        callNoArg(a, "onPause");
        chk("⑨ onPause 不抛异常", true);
        callNoArg(a, "onResume");
        chkEq("⑨ onResume 后选中的一手不变", N, gi(a, "sel"));
        chk("⑨ onResume 注册了定时刷新（postDelayed）", android.os.Handler.POSTED.size() > before);

        // ⑩ 黑白视角换算（wrToMove 是「轮走方」的胜率，黑视角要按轮次翻转）
        Intent pers = base();
        double[] wr2 = new double[N + 1];
        for (int i = 0; i < wr2.length; i++) wr2[i] = 0.5;
        wr2[1] = 0.8;
        pers.putExtra(GomokuReviewActivity.EXTRA_WR, wr2);
        GomokuReviewActivity p = make(pers);
        callInt(p, "select", 1);
        chk("⑩ 黑先时第 1 手轮到白 ⇒ 黑视角 = 20%", text(p, "positionText").contains("20%"));
        Intent persW = intentFor(15, false, GomokuBoard.WHITE, N, true, GomokuBoard.WHITE, "白胜", null);
        double[] wr3 = new double[N + 1];
        for (int i = 0; i < wr3.length; i++) wr3[i] = 0.5;
        wr3[1] = 0.8;
        persW.putExtra(GomokuReviewActivity.EXTRA_WR, wr3);
        GomokuReviewActivity pw = make(persW);
        callInt(pw, "select", 1);
        chk("⑩ 白先时第 1 手轮到黑 ⇒ 黑视角 = 80%", text(pw, "positionText").contains("80%"));

        // ⑪ 点行跳步：失误行 / 手顺行 / 终局行各自跳到哪一步（此前只直接调过 select()，没人「点」过行）
        GomokuReviewActivity cb = make(big);   // big：loss[2] = 0.30 ⇒ 失误排行 1 行（第 3 手）
        chkEq("⑪ 一进来停在终局", N, gi(cb, "sel"));
        Object bl = get(cb, "blunders");
        chkEq("⑪ 失误排行 1 行", 1, listSize(bl));
        clickRow(rowAt(bl, 0));
        chkEq("⑪ 点失误行 ⇒ 跳到那一手（第 3 手）", 3, gi(cb, "sel"));
        chk("⑪ 点失误行后位置文字写明第 3 手", text(cb, "positionText").contains("第 3 手，"));
        chkEq("⑪ 点失误行后胜率条标签是「第 3 手走完」", "第 3 手走完", barLabel(cb));
        Object rowList = get(cb, "rows");
        clickRow(rowAt(rowList, 1));
        chkEq("⑪ 点手顺第 2 行 ⇒ 选中第 2 手", 2, gi(cb, "sel"));
        chk("⑪ 点手顺行后位置文字写明第 2 手", text(cb, "positionText").contains("第 2 手，"));
        clickRow(rowAt(rowList, N));
        chkEq("⑪ 点终局行 ⇒ 选中终局", N, gi(cb, "sel"));
        chk("⑪ 点终局行后位置文字写明第 9 手", text(cb, "positionText").contains("第 9 手"));
        chkEq("⑪ 点终局行后胜率条标签带「：终局」", "第 9 手走完：终局", barLabel(cb));
        GomokuReviewActivity db = make(base());
        chkEq("⑪ 没有失误时排行里没有行可点", 0, listSize(get(db, "blunders")));

        // ⑫ 高亮行：选了哪一步，就该有且只有对应那一行被标底色（第 54 轮审计 低-1）
        GomokuReviewActivity eb = make(big);
        chkEq("⑫ 一进来（终局档）只高亮终局行", "9", highlightRows(eb));
        clickRow(rowAt(get(eb, "rows"), 0));
        chkEq("⑫ 点第 1 手行后高亮第 1 手行", "0", highlightRows(eb));
        clickRow(rowAt(get(eb, "rows"), 2));
        chkEq("⑫ 点第 3 手行后高亮第 3 手行", "2", highlightRows(eb));
        clickRow(rowAt(get(eb, "rows"), N));
        chkEq("⑫ 点终局行后高亮终局行", "9", highlightRows(eb));
        GomokuReviewActivity fb = make(big);
        selectZero(fb);
        chkEq("⑫ 回到开局前时没有任何行被高亮", "", highlightRows(fb));

        // ⑫b 中段档（第 5 / 第 6 / 最后一手）：只在个别档上正确的改法必须被抓（第 55 轮审计的 m3_selective_le3 骗过了旧断言）
        GomokuReviewActivity gb = make(big);
        Object gRows = get(gb, "rows");
        clickRow(rowAt(gRows, 4));                 // 手顺第 5 行 = 第 5 手
        chkEq("⑫b 点手顺第 5 行 ⇒ 选中第 5 手", 5, gi(gb, "sel"));
        chkEq("⑫b 第 5 手档只高亮第 5 手那一行（行号 4）", "4", highlightRows(gb));
        chkEq("⑫b 第 5 手档胜率条标签", "第 5 手走完", barLabel(gb));
        clickRow(rowAt(gRows, 5));                 // 手顺第 6 行 = 第 6 手
        chkEq("⑫b 点手顺第 6 行 ⇒ 选中第 6 手", 6, gi(gb, "sel"));
        chkEq("⑫b 第 6 手档只高亮第 6 手那一行（行号 5）", "5", highlightRows(gb));
        chkEq("⑫b 第 6 手档胜率条标签", "第 6 手走完", barLabel(gb));
        clickRow(rowAt(gRows, 8));                 // 手顺最后一行 = 第 9 手 ⇒ 终局档
        chkEq("⑫b 点手顺最后一行 ⇒ 选中终局", 9, gi(gb, "sel"));
        chkEq("⑫b 最后一手档高亮终局行（行号 9）", "9", highlightRows(gb));
        chkEq("⑫b 最后一手档胜率条标签", "第 9 手走完：终局", barLabel(gb));

        // ⑬ 高亮用的颜色也要钉住（第 55 轮审计提示 2：改成任何非 0 颜色都能蒙过去）
        Object cf = make(base());
        Object palF = get(cf, "palette");
        int surf = ((Integer) palF.getClass().getField("surface").get(palF)).intValue();
        Object rowsF = get(cf, "rows");
        chkEq("⑬ 高亮的终局行用的就是主题 surface 色", surf, ((android.view.View) rowAt(rowsF, 9)).bgColor);
        chkEq("⑬ 没被选中的行不涂底色", 0, ((android.view.View) rowAt(rowsF, 3)).bgColor);

        // ⑭ 胜率条的数值与曲线的选中下标（第 60 轮审计 (c)：此前只钉了文案，数值和下标的改法蒙得过去）
        // 注意口径：胜率条读的是「黑棋胜率」，第 i 格轮到白走时要取 1 - wrToMove[i]（第 0 格是开局前）。
        Intent itV = intentFor(15, false, GomokuBoard.BLACK, 9, true, GomokuBoard.BLACK, "黑五连", "黑胜（测试）");
        final double[] wrV = {0.60, 0.68, 0.53, 0.77, 0.44, 0.35, 0.85, 0.58, 0.41, 0.93};
        itV.putExtra(GomokuReviewActivity.EXTRA_WR, wrV);
        Object ch = make(itV);
        Object rowsV = get(ch, "rows");
        chkEq("⑭ 一进来胜率条 = 终局那一格的黑胜率（轮白 ⇒ 1 - wr）", true, near(barValue(ch), 1.0 - wrV[9]));
        chkEq("⑭ 一进来曲线选中的就是终局那一格", 9, chartSelected(ch));
        clickRow(rowAt(rowsV, 1));          // 手顺第 2 行 = 第 2 手 ⇒ 选中 2
        chkEq("⑭ 点第 2 手行后胜率条 = 第 2 格的黑胜率", true, near(barValue(ch), wrV[2]));
        chkEq("⑭ 点第 2 手行后曲线选中下标 = 2", 2, chartSelected(ch));
        clickRow(rowAt(rowsV, 4));          // 手顺第 5 行 = 第 5 手（中间档）
        chkEq("⑭ 点第 5 手行后胜率条 = 第 5 格的黑胜率（轮白 ⇒ 1 - wr）", true, near(barValue(ch), 1.0 - wrV[5]));
        chkEq("⑭ 点第 5 手行后曲线选中下标 = 5", 5, chartSelected(ch));
        clickRow(rowAt(rowsV, 6));          // 手顺第 7 行 = 第 7 手（中间档）
        chkEq("⑭ 点第 7 手行后胜率条 = 第 7 格的黑胜率（轮白 ⇒ 1 - wr）", true, near(barValue(ch), 1.0 - wrV[7]));
        chkEq("⑭ 点第 7 手行后曲线选中下标 = 7", 7, chartSelected(ch));

        // ⑮ 全档位循环：0..n 每一档的曲线下标 + 胜率条数值（第 62/63 轮：抽查密度不够会漏掉「只在被抽查处写对」的改法）
        int badSel = -1, badBarAt = -1;
        for (int k = 0; k <= N; k++) {
            callInt(ch, "select", k);
            if (chartSelected(ch) != k && badSel < 0) badSel = k;
            double want = (k % 2 == 0) ? wrV[k] : 1.0 - wrV[k];
            if (!near(barValue(ch), want) && badBarAt < 0) badBarAt = k;
        }
        chkEq("⑮ 0..9 每一档的曲线选中下标都对（-1 = 没有错的档）", -1, badSel);
        chkEq("⑮ 0..9 每一档的胜率条数值都对（-1 = 没有错的档）", -1, badBarAt);
        boolean alias = false;
        for (int i = 0; i < wrV.length && !alias; i++) {
            // 一个数自己是自己的补数（0.5）也算撞值 —— 第 65 轮审计 提示-1
            if (Math.abs(2.0 * wrV[i] - 1.0) < 1e-9) { alias = true; break; }
            for (int j = 0; j < wrV.length; j++)
                if (i != j && (Math.abs(wrV[i] - wrV[j]) < 1e-9 || Math.abs(1.0 - wrV[i] - wrV[j]) < 1e-9)) { alias = true; break; }
        }
        chkEq("⑮ 夹具自身没有撞值（第 i 档 ≠ 第 j 档、≠ 第 j 档的补数，自己也不等于自己的补数）", false, alias);
        selectZero((GomokuReviewActivity) ch);
        chkEq("⑭ 回到开局前：胜率条 = 开局那格", true, near(barValue(ch), wrV[0]));
        chkEq("⑭ 回到开局前：曲线选中下标 = 0", 0, chartSelected(ch));

        // ⑯ 复盘页必须有棋盘，且跟着选中手数走（真机反馈：复盘没有棋盘，看不到哪手在哪里）
        callInt(ch, "select", N);   // 回到终局档（上一组 ⑭ 结尾把选择挪到「开局前」了）
        Object rbv = get(ch, "boardView");
        chkEq("⑯ 复盘页有棋盘视图", true, rbv != null);
        chkEq("⑯ 一进来（选到终局）棋盘上摆了 n 手", 9,
                (getF(rbv, "board") == null ? -1 : ((java.util.List) getF(getF(rbv, "board"), "moves")).size()));
        chkEq("⑯ 棋盘开着「手顺数字」（才看得出哪手在哪里）", true, getF(rbv, "showNumbers"));
        selectZero((GomokuReviewActivity) ch);
        chkEq("⑯ 选到「开局前」棋盘是空的", 0,
                (getF(rbv, "board") == null ? -1 : ((java.util.List) getF(getF(rbv, "board"), "moves")).size()));
        callInt(ch, "select", 3);
        chkEq("⑯ 点第 3 手后棋盘正好摆了 3 手", 3,
                (getF(rbv, "board") == null ? -1 : ((java.util.List) getF(getF(rbv, "board"), "moves")).size()));


        System.out.println();
        System.out.println("复盘页探针：PASS " + pass + " / FAIL " + fail);
        if (fail > 0) System.exit(1);
    }
}
