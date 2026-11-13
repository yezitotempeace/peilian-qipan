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
        new Entry("v201",
                "新增黑白棋：8×8 翻转规则，黑先白后，无棋可走自动弃权，子多者胜；三档难度。",
                "每个棋的右上角新增「规则」按钮，点开就是这一棋的完整玩法说明（怎么下、什么吃什么、怎么赢）。",
                "困难模式全面上引擎：五子棋 15 路用 Rapfi 引擎、象棋用 ElephantEye（象眼）引擎、围棋用最大的 KataGo 网络，思考时间都加到 8 秒。",
                "内置 AI 也加强：五子棋非 15 路的强档加深到 6 层搜索，黑白棋强档 6 层 + 终局精确算完。"),
        new Entry("v200",
                "军棋全面重做：棋盘、规则按标准军棋重写（铁路滑行、行营保护、山界、大本营、吃子与胜负判定）。",
                "军棋 AI 重写：困难模式会算风险、会推进抢旗，明显更强。",
                "翻棋模式：50 子随机扣着铺满棋盘，翻牌算一步，翻到什么是什么。",
                "新增布阵阶段：开战前可点选两枚己方棋子对调，调整阵型后再开战。"),
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
