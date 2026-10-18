package com.autogo;

/**
 * 更新公告：标题 + 分条要点。纯 Java（无 Android 依赖），所以能在 JVM 套件里单测。
 * 菜单页右上角的「公告」按钮用它填弹窗内容。
 */
public final class Changelog {

    /** 一条公告。 */
    public static final class Entry {
        public final String version;
        public final String[] lines;

        Entry(String version, String... lines) {
            this.version = version;
            this.lines = lines == null ? new String[0] : lines;
        }
    }

    /** 最新的排在前面。 */
    private static final Entry[] ENTRIES = {
        new Entry("v171",
                "象棋、军棋对局页支持转屏：屏幕方向一变，棋盘按新的可用空间重新算（以前转屏后还是竖屏尺寸）。",
                "军棋棋盘按 5×12 的真实格子比例铺开，不再在两侧留出大片空白。"),
        new Entry("v163",
                "军棋暗棋：棋子走到空格不再露兵种，只有真打过仗的格子才露（以前会提前暴露）。",
                "象棋复盘：选中某一手时，棋盘上用绿圈标出「这一步该下哪」——推荐点以前只写在文字里。"),
        new Entry("v158",
                "新增「军棋」：人执红、电脑执黑，开局可选明棋 / 暗棋；暗棋里只有打过仗的格子才露兵种。",
                "军棋棋盘画了铁路、行营和大本营；点自己的子看能走到哪，点亮的格子就能走。"),
        new Entry("v135",
                "修好「电脑对电脑」：以前只有白棋在下、黑棋一动不动，现在黑白两边轮流下。",
                "电脑对电脑的棋不再记进「学习」记录（那只该学你）。"),
        new Entry("v129",
                "内部质量：给「电脑对战」的提示补上测试，更新日志正文开始逐句核对。",
                "这一版 App 界面没有变化。"),
        new Entry("v128",
                "五子棋新增「对战」：电脑对电脑，可暂停 / 继续。",
                "电脑对战时人点棋盘不下子；悔棋与认输请改用「新局」。"),
        new Entry("v127",
                "启动页右上角新增「公告」按钮：点开看每一版更新了什么。",
                "版本号移到左下角，并显示 versionCode。"),
        new Entry("v126",
            "五子棋会「学你」了：记下你在某个局面的走法、你赢过的手、你复盘里亏得多的局面",
            "菜单第四行加「学习」按钮：能看到学到了多少，也能一键清除记录"),
        new Entry("v125",
            "名字改成「陪练棋盘」，图标换新",
            "围棋 / 五子棋的入口与玩法不变"),
        new Entry("v124",
            "修掉点「全屏」会退出程序的毛病",
            "五子棋界面改成按屏幕自适应，棋盘不会再顶出屏幕",
            "复盘页加了棋盘：能看到每一手落在哪儿，点失误行直接跳过去",
            "难度按钮的提示写明「快 → 普通 → 强」"),
        new Entry("v87",
            "五子棋模式上线：13 / 15 / 19 路、我执黑或白、禁手开关、悔棋 / 认输 / 手顺 / 全屏",
            "自研 AI 三档难度；复盘有胜率曲线、失误排行、推荐点"),
    };

    public static Entry[] entries() {
        return ENTRIES.clone();
    }

    public static Entry latest() {
        return ENTRIES.length == 0 ? null : ENTRIES[0];
    }

    /** 弹窗标题，例如「更新了什么（v126）」。 */
    public static String title() {
        Entry e = latest();
        return e == null ? "更新了什么" : "更新了什么（" + e.version + "）";
    }

    /** 弹窗正文：每条公告一段。 */
    public static String text() {
        StringBuilder sb = new StringBuilder();
        for (Entry e : ENTRIES) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.version).append('\n');
            for (String line : e.lines) sb.append("· ").append(line).append('\n');
        }
        return sb.toString().trim();
    }

    private Changelog() {
    }
}
