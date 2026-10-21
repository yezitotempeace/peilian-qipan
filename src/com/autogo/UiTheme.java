/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/**
 * 主题取值口径的统一入口（入口页与五子棋页共用）。
 *
 * 原先这套口径在三处各抄了一份（MainActivity.onCreate、MainActivity.onConfigurationChanged、
 * ReviewActivity 的两处），第 40 轮审计提示「再加一页就会漂」。这里把它收成一处：新页面一律走
 * 这里，别再抄第四份。MainActivity 自己那份保持原样不动 —— 那一页经过历轮审计与真机前实测，
 * 不为了代码好看去动它。
 *
 * 口径逐项与 MainActivity 一致：偏好文件 "autogo"、键 "themeMode"、默认 Theme.SYSTEM、
 * 越界用 Theme.clamp()、AUTO 档看钟点（Theme.NIGHT_FROM/NIGHT_TO，19:00–07:00 算夜间）、
 * 其余档看系统 uiMode 的 UI_MODE_NIGHT_YES。
 */
public final class UiTheme {

    /** 主题偏好文件名（与 MainActivity.PREF 同值）。 */
    public static final String PREF = "autogo";
    /** 主题档在偏好里的键名。 */
    public static final String KEY_MODE = "themeMode";

    private UiTheme() {
    }

    /** 设置里的主题档（越界自动 clamp）。 */
    public static int mode(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        return Theme.clamp(sp.getInt(KEY_MODE, Theme.SYSTEM));
    }

    /** 这份配置算不算深色系统。 */
    public static boolean systemDark(Configuration cfg) {
        return (cfg.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** 当前钟点（0–23）。 */
    public static int hour() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
    }

    /** 按「主题档 + 这份配置 + 当前钟点」算出实际明暗。 */
    public static boolean dark(int mode, Configuration cfg) {
        return Theme.isDark(Theme.clamp(mode), systemDark(cfg), hour());
    }

    /** 按当前设置算出实际明暗。 */
    public static boolean dark(Context ctx) {
        return dark(mode(ctx), ctx.getResources().getConfiguration());
    }

    /** 明暗对应的窗口主题 id（setTheme 用，必须在 setContentView 之前调）。 */
    public static int style(boolean dark) {
        return dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar;
    }

    /** 套上窗口主题（在 setContentView 之前调）；返回是否深色，省得调用方再算一遍。 */
    public static boolean apply(Activity act) {
        boolean dark = dark(act);
        act.setTheme(style(dark));
        return dark;
    }
}
