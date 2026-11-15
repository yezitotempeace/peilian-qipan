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
        new Entry("v208",
                "围棋残局重新接上白方引擎：可以和 KataGo 真刀真枪对下了（之前的崩溃是点击监听器无限递归，上版已修）。",
                "围棋残局出题加了合法性校验（每个棋组的气都不为空），喂给引擎更稳，不再有非法局面。"),
        new Entry("v207",
                "修复残局点击就白屏退出的真凶：棋盘点击监听器里误调用了自己（无限递归导致 StackOverflowError），改为调用页面的处理方法。",
                "三个残局（五子棋 / 围棋 / 象棋）都已修复。"),
        new Entry("v206",
                "围棋残局重做为中国规则吃子题：提掉被围住的白棋即过关，彻底解决之前的原生崩溃。"),
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
    /** 公告最多只列最近两版（产品的明确要求：翻旧账没意义）。 */
    private static final int NOTICE_VERSIONS = 2;

    public static String text() {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (Entry e : ENTRIES) {
            if (shown >= NOTICE_VERSIONS) {
                break;
            }
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.version).append('\n');
            for (String line : e.lines) sb.append("· ").append(line).append('\n');
            shown++;
        }
        return sb.toString().trim();
    }

    private Changelog() {
    }
}
