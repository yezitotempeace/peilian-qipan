/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

/**
 * 用户协议（仅学习版声明）的**纯逻辑**部分：条款正文、版本号、以及「要不要再弹一次」的判定。
 *
 * <p>把正文和判定放在这里而不是 Activity 里，是为了能在 JVM 上直接单测
 * （{@code EulaTest}）：条款必须包含「学习」「禁止商业」这些字样这件事，得有人盯着，
 * 否则哪天有人顺手删两句也没人发现。</p>
 *
 * <p>版本号规则：{@link #VERSION} 一旦改动，已经同意过的用户会**重新看到**协议
 * （因为存的是「同意的是哪一版」）。改条款就该重新征求同意，这也顺带让测试能验证
 * 「版本变了必须重弹」。</p>
 */
public final class Eula {

    /** 协议版本。改条款时 +1，已同意的用户会重新看到协议。 */
    public static final int VERSION = 1;

    /** 存进 SharedPreferences 的键（同 "autogo" 这一份偏好）。 */
    public static final String KEY_ACCEPTED = "eulaAcceptedVersion";

    /**
     * 条款正文，一行一句。
     *
     * <p>口径：本程序是**学习版**，只供个人学习、研究与自娱；禁止任何商业用途。
     * 用词要具体到能执行（卖、租、收费分发、商业产品内嵌、以本程序提供服务收费…），
     * 不能只写一句“禁止商用”糊过去。</p>
     */
    public static final String[] LINES = {
        "一、本程序《陪练棋盘》是个人学习作品，仅供个人学习、技术研究与自娱自乐使用。",
        "二、本程序不提供任何形式的商业服务，作者也未就本程序收取任何费用。",
        "三、禁止将本程序或其任何部分用于商业目的，包括但不限于：出售、出租、有偿分发、"
            + "作为付费商品或付费服务的赠品、嵌入商业产品或商业服务中、"
            + "以本程序或其改编版对外提供收费的技术支持、培训或咨询。",
        "四、禁止在应用商店、软件下载站、网盘、公众号、短视频等任何渠道以盈利或引流为目的"
            + "发布、转载、代下载本程序。",
        "五、禁止删除、隐藏或篡改本程序中的版权声明、作者署名、许可声明与本协议。",
        "六、本程序以 AGPL-3.0 许可开源；随包附带的第三方引擎（KataGo、Rapfi、ElephantEye 等）"
            + "各自遵循其原有许可，详见仓库 THIRD-PARTY-NOTICES.md。",
        "七、本程序按“现状”提供，不附带任何明示或默示的担保；因使用本程序产生的任何后果"
            + "由使用者自行承担。",
        "八、作者可能随时修改本协议与程序功能；条款变更后会重新征求你的同意。",
        "",
        "点击「我同意」表示你已阅读并接受上述全部条款；"
            + "点击「不同意」将直接退出，不进入本程序。",
    };

    private Eula() {
    }

    /**
     * 要不要显示协议。
     *
     * @param acceptedVersion 之前同意过的协议版本（0 = 从未同意）
     * @return true = 需要显示（未同意，或同意过的是旧版本）
     */
    public static boolean shouldShow(int acceptedVersion) {
        return acceptedVersion < VERSION;
    }

    /** 条款全文（用 \n 连起来，给界面和测试共用）。 */
    public static String text() {
        StringBuilder sb = new StringBuilder();
        for (String line : LINES) {
            sb.append(line).append('\n');
        }
        return sb.toString().trim();
    }
}
