package com.autogo;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

/**
 * 屏幕信息与自适应尺寸计算。
 *
 * 为什么要单独做一层：布局里到处写死 dp 值，换个小屏或转横屏就会挤成一团或者溢出。
 * 这里在界面构建时读一次屏幕，之后所有尺寸都由「可用空间」算出来，而不是拍脑袋定死。
 *
 * 取值说明：
 * - 用 Configuration.screenWidthDp/screenHeightDp 而不是 DisplayMetrics 的像素值，
 *   前者已经是「扣除系统栏、按 density 归一化」的 dp，直接就是布局能用的空间。
 * - 老设备上这两个字段可能为 0，那就退回用像素除以 density 估算。
 */
public final class Screen {

    public final int widthDp;
    public final int heightDp;
    public final int smallestWidthDp;
    public final int widthPx;
    public final int heightPx;
    public final float density;

    /** 横屏：宽度大于高度。 */
    public final boolean landscape;
    /** 窄屏：竖屏宽度不足 360dp，一行放三个按钮会挤。 */
    public final boolean narrow;
    /** 矮屏：可用高度不足 640dp，棋盘必须给控件让出更多空间。 */
    public final boolean shortScreen;

    private Screen(Configuration cfg, DisplayMetrics dm) {
        density = dm.density > 0 ? dm.density : 1f;
        widthPx = dm.widthPixels;
        heightPx = dm.heightPixels;

        int w = cfg.screenWidthDp;
        int h = cfg.screenHeightDp;
        if (w <= 0) w = Math.round(widthPx / density);
        if (h <= 0) h = Math.round(heightPx / density);
        widthDp = w;
        heightDp = h;

        int sw = cfg.smallestScreenWidthDp;
        smallestWidthDp = sw > 0 ? sw : Math.min(w, h);

        landscape = w > h;
        narrow = w < 360;
        shortScreen = h < 640;
    }

    public static Screen read(Context ctx) {
        return new Screen(ctx.getResources().getConfiguration(),
                ctx.getResources().getDisplayMetrics());
    }

    public int dp(float v) {
        return Math.round(v * density);
    }

    /** 棋盘边长（dp）。算法在 {@link LayoutMath} 里，那里是纯函数、可单测。 */
    public int boardSizeDp(int marginDp) {
        return LayoutMath.boardSizeDp(widthDp, heightDp, marginDp, landscape);
    }

    /** 胜率曲线高度（dp）。 */
    public int chartHeightDp() {
        return LayoutMath.chartHeightDp(heightDp);
    }

    /** 按钮字号（sp）。 */
    public int buttonTextSp() {
        return LayoutMath.buttonTextSp(widthDp);
    }

    /** 给设置页/诊断用的一行摘要。 */
    public String describe() {
        return String.format(java.util.Locale.US,
                "屏幕: %d×%d dp（%d×%d px，density %.2f）%s%s%s",
                widthDp, heightDp, widthPx, heightPx, density,
                landscape ? " 横屏" : " 竖屏",
                narrow ? " 窄屏" : "",
                shortScreen ? " 矮屏" : "");
    }
}
