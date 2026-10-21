/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

/**
 * 黑夜 / 白天模式的判定。
 *
 * 纯 Java、不依赖 Android —— 这类「什么时候该用深色」的规则最容易写反，
 * 做成纯函数就能在 JVM 上把每个时段、每种模式都钉一遍。
 */
public final class Theme {

    /** 跟随系统的深色设置。 */
    public static final int SYSTEM = 0;
    /** 一直白天。 */
    public static final int LIGHT = 1;
    /** 一直黑夜。 */
    public static final int DARK = 2;
    /** 按时间自动（到点自己换）。 */
    public static final int AUTO = 3;

    public static final int MODE_COUNT = 4;

    public static final String[] LABELS = {"跟随系统", "白天", "黑夜", "按时间自动"};
    public static final String[] NOTES = {
            "用系统的深色开关", "始终亮色", "始终深色", "19:00~07:00 自动转深色"};

    /** 「按时间自动」的夜间区间：[19:00, 次日 07:00)。 */
    public static final int NIGHT_FROM = 19;
    public static final int NIGHT_TO = 7;

    private Theme() {
    }

    /** 这个钟点算夜间吗。 */
    public static boolean isNightHour(int hour) {
        return hour >= NIGHT_FROM || hour < NIGHT_TO;
    }

    /**
     * 现在该用深色吗。
     *
     * @param mode       四种模式之一（越界按「跟随系统」处理）
     * @param systemDark 系统当前是否深色
     * @param hour       本地小时 0~23
     */
    public static boolean isDark(int mode, boolean systemDark, int hour) {
        switch (clamp(mode)) {
            case LIGHT:
                return false;
            case DARK:
                return true;
            case AUTO:
                return isNightHour(hour);
            case SYSTEM:
            default:
                return systemDark;
        }
    }

    /** 偏好里存坏的值（负数、越界）一律当「跟随系统」。 */
    public static int clamp(int mode) {
        return (mode >= SYSTEM && mode <= AUTO) ? mode : SYSTEM;
    }

    public static String label(int mode) {
        return LABELS[clamp(mode)];
    }
}
