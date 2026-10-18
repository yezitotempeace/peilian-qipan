package com.autogo;

/**
 * 明暗两套配色。
 *
 * 只有 int 常量、不依赖 Android，所以能在 JVM 上直接检查配色性质
 * （比如「深色模式的底色确实比浅色模式暗」「文字和底色的对比度够」）。
 */
public final class Palette {

    public final boolean dark;

    /** 页面底色 */
    public final int background;
    /** 状态面板这类「卡片」的底色 */
    public final int surface;
    /** 主要文字 */
    public final int text;
    /** 次要文字 */
    public final int textDim;
    /** 分割线 */
    public final int divider;

    /** 棋盘木色 */
    public final int boardWood;
    /** 棋盘线 */
    public final int boardLine;
    /** 坐标文字 */
    public final int boardLabel;
    /**
     * 棋子描边。深色模式下必须换成浅色 ——
     * 深色棋盘的亮度接近黑子，没有浅色描边就分不出棋子边界。
     */
    public final int boardStoneEdge;

    public final int chartBg;
    public final int chartGrid;
    public final int chartLine;
    public final int chartArea;
    public final int chartAxis;
    public final int chartCursor;

    public final int buttonBg;
    public final int buttonText;

    /** 胜率条「还没数据」时的灰条 */
    public final int barEmpty;
    /** 胜率条下方的说明文字 */
    public final int barLabel;

    private Palette(boolean dark, int background, int surface, int text, int textDim, int divider,
                    int boardWood, int boardLine, int boardLabel, int boardStoneEdge,
                    int chartBg, int chartGrid, int chartLine, int chartArea, int chartAxis,
                    int chartCursor, int buttonBg, int buttonText, int barEmpty, int barLabel) {
        this.dark = dark;
        this.background = background;
        this.surface = surface;
        this.text = text;
        this.textDim = textDim;
        this.divider = divider;
        this.boardWood = boardWood;
        this.boardLine = boardLine;
        this.boardLabel = boardLabel;
        this.boardStoneEdge = boardStoneEdge;
        this.chartBg = chartBg;
        this.chartGrid = chartGrid;
        this.chartLine = chartLine;
        this.chartArea = chartArea;
        this.chartAxis = chartAxis;
        this.chartCursor = chartCursor;
        this.buttonBg = buttonBg;
        this.buttonText = buttonText;
        this.barEmpty = barEmpty;
        this.barLabel = barLabel;
    }

    private static final Palette LIGHT = new Palette(
            false,
            0xFFF5F0E6,   // background：米色，和原来的观感一致
            0xFFEDE6D6,   // surface
            0xFF2B2B2B,   // text
            0xFF4A4A4A,   // textDim
            0xFFD4C4A8,   // divider
            0xFFE3B778,   // boardWood
            0xFF3A2A15,   // boardLine
            0xFF6B4E2E,   // boardLabel
            0x33000000,   // boardStoneEdge：浅色棋盘上用淡淡的暗描边
            0xFFFFFDF7,   // chartBg
            0xFFE0D8C8,   // chartGrid
            0xFF1E6FD9,   // chartLine
            0x221E6FD9,   // chartArea
            0xFF7A6A50,   // chartAxis
            0xFFE53935,   // chartCursor
            0xFFE0D8C8,   // buttonBg
            0xFF2B2B2B,   // buttonText
            0xFFCCCCCC,   // barEmpty
            0xFF333333);  // barLabel

    private static final Palette DARK = new Palette(
            true,
            0xFF121212,   // background：标准的夜间底色
            0xFF1E1E1E,   // surface
            0xFFE8E8E8,   // text
            0xFFA8A8A8,   // textDim
            0xFF3A3A3A,   // divider
            0xFF6F5B3F,   // boardWood：深胡桃木，比黑子明显亮，夜里也不刺眼
            0xFF241A0D,   // boardLine
            0xFFC8B48C,   // boardLabel
            0x66FFFFFF,   // boardStoneEdge：浅色描边，黑子边界才看得清
            0xFF1E1E1E,   // chartBg
            0xFF3A3A3A,   // chartGrid
            0xFF6FA8FF,   // chartLine
            0x336FA8FF,   // chartArea
            0xFF9A9A9A,   // chartAxis
            0xFFFF6B61,   // chartCursor
            0xFF3A3A3A,   // buttonBg
            0xFFE8E8E8,   // buttonText
            0xFF444444,   // barEmpty
            0xFFB0B0B0);  // barLabel

    public static Palette of(boolean dark) {
        return dark ? DARK : LIGHT;
    }

    /** 相对亮度（WCAG 定义）。用于单测对比度。 */
    public static double luminance(int color) {
        double r = channel((color >> 16) & 0xFF);
        double g = channel((color >> 8) & 0xFF);
        double b = channel(color & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double channel(int v) {
        double s = v / 255.0;
        return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
    }

    /** 两色对比度（1 ~ 21）。 */
    public static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        double hi = Math.max(la, lb);
        double lo = Math.min(la, lb);
        return (hi + 0.05) / (lo + 0.05);
    }
}
