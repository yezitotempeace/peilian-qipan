package com.autogo;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 启动选择页（真 MenuActivity）的界面层桩测。
 *
 * 这一页此前是**零自动化覆盖**：它是 App 的入口，点错就整页跳错。
 * 这里用 tests/ui/fake 的假 android 类把真 MenuActivity 跑起来，钉住：
 *   分流的两个按钮各自跳到哪一页、页面上写了什么、主题档怎么落地、
 *   从系统配置变化与回到前台两条路径上的主题重判、版本号兜底。
 */
public class MenuProbe {

    static int passed = 0, failed = 0;

    static void check(boolean ok, String what) {
        if (ok) { passed++; System.out.println("PASS: " + what); }
        else { failed++; System.out.println("FAIL: " + what); }
    }

    static void checkEq(Object want, Object got, String what) {
        boolean ok = want == null ? got == null : want.equals(got);
        if (ok) { passed++; System.out.println("PASS: " + what); }
        else { failed++; System.out.println("FAIL: " + what + "（期望 " + want + "，实际 " + got + "）"); }
    }

    /** 反射调 protected 生命周期方法（假 Activity 把它们声明成 protected，包外调不到）。 */
    static void lifecycle(MenuActivity a, String name, Class<?>[] sig, Object... args) {
        try {
            java.lang.reflect.Method m = android.app.Activity.class.getDeclaredMethod(name, sig);
            m.setAccessible(true);
            m.invoke(a, args);
        } catch (Exception e) {
            throw new RuntimeException("调 " + name + " 失败：" + e, e);
        }
    }

    static MenuActivity create(int themeMode) {
        Handler.POSTED.clear();
        Handler.removed = 0;
        Activity.lastStarted = null;
        MenuActivity a = new MenuActivity();
        a.prefs.ints.put(UiTheme.KEY_MODE, themeMode);
        lifecycle(a, "onCreate", new Class<?>[]{Bundle.class}, new Bundle());
        return a;
    }

    static String textOf(View v) {
        return v instanceof TextView ? String.valueOf(((TextView) v).getText()) : "<非文本>";
    }

    public static void main(String[] args) {
        String sentinelVersion = "9.9.9-probe";
        PackageManager.VERSION_NAME = sentinelVersion;
        PackageManager.THROW = false;

        // ① 建界面：结构、文案、版本行
        MenuActivity a = create(Theme.LIGHT);
        check(a.content != null, "onCreate 之后有内容视图");
        check(a.content instanceof android.widget.FrameLayout, "内容视图是浮层（FrameLayout）");
        android.view.ViewGroup holder = (android.view.ViewGroup) a.content;
        checkEq(2, holder.getChildCount(), "浮层里是「页面 + 水印」两个子视图");
        LinearLayout root = (LinearLayout) holder.getChildAt(0);
        checkEq(LinearLayout.VERTICAL, root.orientation, "根视图竖排");
        checkEq(10, root.getChildCount(), "页面 10 行（公告 / 标题 / 围棋 / 五子棋 / 象棋 / 军棋 / 2048 / 提示 / 撑底空隙 / 版本）");
        checkEq("陪练棋盘", textOf(root.getChildAt(1)), "标题文案（软件名）");
        check(root.getChildAt(2) instanceof Button, "第 3 行是按钮");
        checkEq("围　棋", textOf(root.getChildAt(2)), "围棋按钮文案（全角空格）");
        check(root.getChildAt(3) instanceof Button, "第 4 行是按钮");
        checkEq("五子棋", textOf(root.getChildAt(3)), "五子棋按钮文案");
        check(root.getChildAt(4) instanceof Button, "第 5 行是按钮");
        checkEq("象　棋", textOf(root.getChildAt(4)), "象棋按钮文案（全角空格）");
        check(root.getChildAt(5) instanceof Button, "第 6 行是按钮");
        checkEq("军　棋", textOf(root.getChildAt(5)), "军棋按钮文案（全角空格）");
        check(root.getChildAt(6) instanceof Button, "第 7 行是 2048 按钮");
        checkEq("2048", textOf(root.getChildAt(6)), "2048 按钮文案");
        String hint = textOf(root.getChildAt(7));
        check(hint.contains("围棋：9 / 13 / 19 路"), "提示行写明围棋路数（实际：" + hint + "）");
        check(hint.contains("五子棋：13 / 15 / 19 路"), "提示行写明五子棋路数（实际：" + hint + "）");
        check(hint.contains("象棋"), "提示行提到象棋（实际：" + hint + "）");
        check(hint.contains("军棋"), "提示行提到军棋（实际：" + hint + "）");
        check(hint.contains("2048"), "提示行提到 2048（实际：" + hint + "）");
        check(hint.contains("没有彩蛋"), "提示行写明 2048 没有彩蛋（实际：" + hint + "）");
        checkEq("版本 " + sentinelVersion + "（versionCode " + PackageManager.VERSION_CODE + "）", textOf(root.getChildAt(9)), "版本行读自 PackageManager（含 versionCode）");

        // ② 点按分流
        root.getChildAt(2).fakeClick();
        check(Activity.lastStarted != null && Activity.lastStarted.target == MainActivity.class,
                "点「围棋」跳 MainActivity（实际：" + (Activity.lastStarted == null ? "没跳" : Activity.lastStarted.target.getName()) + "）");
        check(!a.finishing, "跳转后本页不自己关闭（用户按返回能回来）");
        root.getChildAt(3).fakeClick();
        check(Activity.lastStarted != null && Activity.lastStarted.target == GomokuActivity.class,
                "点「五子棋」跳 GomokuActivity（实际：" + (Activity.lastStarted == null ? "没跳" : Activity.lastStarted.target.getName()) + "）");
        root.getChildAt(4).fakeClick();
        check(Activity.lastStarted != null && Activity.lastStarted.target == XiangqiActivity.class,
                "点「象棋」跳 XiangqiActivity（实际：" + (Activity.lastStarted == null ? "没跳" : Activity.lastStarted.target.getName()) + "）");
        root.getChildAt(5).fakeClick();
        check(Activity.lastStarted != null && Activity.lastStarted.target == JunqiActivity.class,
                "点「军棋」跳 JunqiActivity（实际：" + (Activity.lastStarted == null ? "没跳" : Activity.lastStarted.target.getName()) + "）");
        root.getChildAt(6).fakeClick();
        check(Activity.lastStarted != null && Activity.lastStarted.target == Game2048Activity.class,
                "点「2048」跳 Game2048Activity（实际：" + (Activity.lastStarted == null ? "没跳" : Activity.lastStarted.target.getName()) + "）");

        // ③ 主题档：深 / 浅各自落地，且配色跟着走
        MenuActivity d = create(Theme.DARK);
        checkEq(1, d.setThemeCount, "深色档：onCreate 里正好套一次窗口主题");
        checkEq(UiTheme.style(true), d.lastTheme, "深色档：套的是深色主题");
        checkEq(Palette.of(true).background, ((View) ((android.view.ViewGroup) d.content).getChildAt(0)).bgColor, "深色档：页面底色用深色配色");
        MenuActivity l = create(Theme.LIGHT);
        checkEq(UiTheme.style(false), l.lastTheme, "浅色档：套的是浅色主题");
        checkEq(Palette.of(false).background, ((View) ((android.view.ViewGroup) l.content).getChildAt(0)).bgColor, "浅色档：页面底色用浅色配色");

        // ④ 系统配置变化（清单声明 configChanges，本页不重建 ⇒ 必须自己重判重铺）
        MenuActivity c = create(Theme.SYSTEM);
        View before = c.content;
        android.content.res.Configuration night = new android.content.res.Configuration();
        night.uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES;
        lifecycle(c, "onConfigurationChanged",
                new Class<?>[]{android.content.res.Configuration.class}, night);
        checkEq(UiTheme.style(true), c.lastTheme, "系统转深色：重判后换成深色主题");
        check(c.content != before, "系统转深色：界面重铺（换了新的内容视图）");
        android.content.res.Configuration day = new android.content.res.Configuration();
        day.uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO;
        lifecycle(c, "onConfigurationChanged",
                new Class<?>[]{android.content.res.Configuration.class}, day);
        checkEq(UiTheme.style(false), c.lastTheme, "系统转浅色：重判后换回浅色主题");

        // ⑤ 回到前台：非「按时间自动」档不该动主题，但要重新按分钟盯点；onPause 注销
        MenuActivity r = create(Theme.SYSTEM);
        int themesBefore = r.setThemeCount;
        int postedBefore = Handler.POSTED.size();
        lifecycle(r, "onResume", new Class<?>[]{});
        checkEq(themesBefore, r.setThemeCount, "非自动档回到前台：不换主题");
        checkEq(postedBefore + 2, Handler.POSTED.size(), "回到前台：注册主题检查 + 两小时更新检查");
        checkEq(Long.valueOf(7200000L), Handler.DELAYS.get(Handler.DELAYS.size() - 1), "回到前台注册的更新检查延时是 7200000 毫秒（两小时）");
        int removedBefore = Handler.removed;
        lifecycle(r, "onPause", new Class<?>[]{});
        checkEq(removedBefore + 2, Handler.removed, "离开前台：注销主题/更新检查（不留常驻回调）");

        // ⑥ 版本号读不到时兜底
        PackageManager.THROW = true;
        MenuActivity t = create(Theme.LIGHT);
        checkEq("版本 ?（versionCode -1）", textOf(((ViewGroup) ((ViewGroup) t.content).getChildAt(0)).getChildAt(9)), "读版本失败时显示「版本 ?」（versionCode -1）而不是崩");
        PackageManager.THROW = false;

        // ⑧ 右上角「公告」按钮与左下角版本行（v127，用户要求）
        MenuActivity n8 = create(Theme.LIGHT);
        ViewGroup root8 = (ViewGroup) ((android.view.ViewGroup) n8.content).getChildAt(0);
        View top8 = root8.getChildAt(0);
        check(top8 instanceof LinearLayout, "第一行是「公告」所在的那一行");
        LinearLayout topRow8 = (LinearLayout) top8;
        checkEq(android.view.Gravity.RIGHT, topRow8.gravity, "公告行靠右（右上角）");
        checkEq("公告", textOf(topRow8.getChildAt(0)), "右上角按钮写着「公告」");
        checkEq(2, topRow8.getChildCount(), "右上角有「公告 / 更新」两个按钮");
        checkEq("更新", textOf(topRow8.getChildAt(1)), "右上角按钮写着「更新」");
        checkEq(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, topRow8.getChildAt(0).getLayoutParams().width,
                "「公告」按钮按内容宽，不占满整行");
        checkEq(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, topRow8.getChildAt(1).getLayoutParams().width,
                "「更新」按钮按内容宽，不会被挤成 0 宽");
        android.os.FakeLog.clear();
        topRow8.getChildAt(0).fakeClick();
        checkEq("更新了什么（" + Changelog.latest().version + "）", (android.os.FakeLog.findPrefix("dialog.title:") == null ? "" : android.os.FakeLog.findPrefix("dialog.title:").substring("dialog.title:".length())), "点「公告」弹出的标题带最新版本号");
        String msg8 = android.os.FakeLog.findPrefix("dialog.msg:");
        if (msg8 == null) msg8 = "";   // 按钮没接线时 findPrefix 回 null，要给干净 FAIL 而不是崩
        check(msg8 != null && msg8.contains("v183") && msg8.contains("v182") && !msg8.contains("v181") && !msg8.contains("v87"), "公告只列最近两个版本（v183、v182），不再列更早版本");
        checkEq("dialog.pos:知道了", android.os.FakeLog.findPrefix("dialog.pos:") == null ? null : android.os.FakeLog.findPrefix("dialog.pos:"), "弹窗有关闭按钮");
        checkEq(Long.valueOf(1L), Long.valueOf(android.os.FakeLog.countPrefix("dialog.show")), "点一次公告只弹一个窗");

        View ver8 = root8.getChildAt(9);
        String verText8 = String.valueOf(textOf(ver8));
        check(verText8.startsWith("版本 ") && verText8.contains("（versionCode "), "最后一行是版本信息（含 versionCode）");
        ViewGroup.LayoutParams vlp8 = ver8.getLayoutParams();
        checkEq(android.view.Gravity.LEFT, vlp8 == null ? null : vlp8.gravity, "版本行靠左（左下角）");
        View spacer8 = root8.getChildAt(8);
        View mark8 = com.autogo.Watermark.lastHolder.getChildAt(1);
        checkEq("穗安", textOf(mark8), "右下角水印写着作者名");
        checkEq(android.view.Gravity.BOTTOM | android.view.Gravity.RIGHT, ((android.widget.FrameLayout.LayoutParams) mark8.getLayoutParams()).gravity, "水印贴右下角（浮层，不占布局高度）");
        check(((android.widget.TextView) mark8).alpha < 1.0f, "水印是半透明的（alpha 0.6）");
        checkEq(10, root8.getChildCount(), "页面 10 行（水印在浮层里，不占行）");
        ViewGroup.LayoutParams slp8 = spacer8.getLayoutParams();
        check(slp8 != null && slp8.weight == 1f, "版本行前面有撑到底部的空隙（weight=1）");

        System.out.println("通过 " + passed + " 项，失败 " + failed + " 项");
        if (failed > 0) System.exit(1);
    }
}
