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
        new Entry("v195",
                "军棋翻牌动画真修复（时间戳精度问题导致动画丢失/翻回背面）；翻牌期间锁输入，电脑与我方翻牌严格错开。",
                "五子棋全部难度改用 Rapfi 强引擎（15 路棋盘）。",
                "象棋新增 12 种真实开局体系随机出招，不再每局都一样。"),
        new Entry("v194",
                "军棋翻棋修正：翻开任意棋子（我方或对方）都有完整的翻牌动画，翻开的棋子定格显示不再有重影／两个同时翻的问题；电脑翻牌也走同一套动画，并与玩家翻牌错开播放。"),
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
