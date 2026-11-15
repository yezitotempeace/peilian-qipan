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
        new Entry("v211",
                "修复围棋残局回合错误：你走一步后，白方（引擎）只回一步就轮到你；之前白方会连着下个不停，玩家插不进手。"),
        new Entry("v210",
                "所有残局棋盘尺寸改成和正常对局完全一样（同一套自适应算法，随屏幕自适应），不再有「就几横几竖」的小盘或固定尺寸。",
                "围棋残局改用和正常对局相同的 19 路盘面（战斗放在角上，更像真实局部攻杀）。",
                "残局 AI 全面加强：五子棋强档搜索加深加宽、象棋强档搜索加深、围棋强档 KataGo 算量提升。"),
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
