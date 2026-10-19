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
        new Entry("v182",
                "公告与仓库只保留最近两个版本：应用内公告只显示最近两个版本的改动，GitHub Releases 也只留最近两个版本（更早的版本本地仍保留）。"),
        new Entry("v181",
                "军棋去掉 60 个重复的「兵站」小字：空棋盘只留行营、大本营、山界、前线标记，画面不再像表格。",
                "更新检查改走国内可访问的 gh-proxy.com 镜像：GitHub API 403/超时自动回退直连；下载也优先走镜像。",
                "检查失败时弹窗可直接「打开仓库」手动下载；2048 与其余逻辑不变，2048 仍然没有彩蛋。",
                "2048 支持深色：棋盘底/空格换深灰、页面跟随主题；转屏或换主题整页重铺，夜里不再一片白。"),
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
