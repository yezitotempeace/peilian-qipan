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
        new Entry("v198",
                "新增用户协议（学习版声明）：首次启动必须点「我同意」才能进入，点「不同意」直接退出。",
                "各处加入版权与完整性标识（隐藏形式，不在界面上显眼展示）：标记被移除或篡改后，程序会在几小时到几天内提示「不是正版」并退出，不会当场报错。",
                "更新校验加固：只认官方仓库的安装包，且安装包签名必须与本程序一致，否则拒绝安装。"),
        new Entry("v197",
                "修正五子棋/象棋引擎在部分手机上升不起来的问题：改为运行时把引擎释放到应用私有目录再执行，绕开系统对外部程序的执行限制；真正起一次引擎自检通过才启用，否则退回内置 AI。"),
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
