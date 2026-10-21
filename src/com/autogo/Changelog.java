/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

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
        new Entry("v189",
                "新增「数独」：9×9 标准数独，简单 / 普通 / 困难三档；困难档做到极难（提示数压到 26 以内，必须反复试填才能解）；填写时同一行、同一列、同一宫都不能出现相同数字，重复只标红不拦；支持笔记、提示、撤销、计时，关卡全部本地随机生成、可离线玩。"),
        new Entry("v188",
                "界面与公告文案精简：主菜单、2048 页的提示更简洁，去掉与玩法无关的说明。"),
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
