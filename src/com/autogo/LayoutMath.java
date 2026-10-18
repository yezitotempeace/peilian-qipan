package com.autogo;

/**
 * 布局尺寸计算。
 *
 * 单独抽出来是因为这些全是纯函数 —— 不碰任何 Android 类，所以能在 JVM 上
 * 拿一堆屏幕尺寸跑单测，验证小屏、横屏、平板都不会算出荒谬的结果。
 * {@link Screen} 负责读屏幕，然后委托到这里算。
 */
public final class LayoutMath {

    private LayoutMath() {
    }

    /** 棋盘边长下限。再小就没法下棋了，宁可让控件区去滚动。 */
    public static final int MIN_BOARD_DP = 140;

    /** 竖屏时留给控件的最小高度（dp）。低于这个值按钮会挤成一条缝。 */
    public static final int MIN_CONTROLS_RESERVE_DP = 260;

    /** 横屏时棋盘最多占屏幕宽度的比例。 */
    public static final float LANDSCAPE_BOARD_WIDTH_RATIO = 0.62f;

    /**
     * 棋盘边长（dp）。
     *
     * 竖屏：棋盘在上、控件在下，给控件留 40%（至少 260dp）的高度。
     * 横屏：棋盘在左、控件在右，此时限制棋盘的是高度而不是宽度，宽度最多占 62%。
     *
     * 最后夹在 [MIN_BOARD_DP, 可用空间] 之间。
     */
    public static int boardSizeDp(int widthDp, int heightDp, int marginDp, boolean landscape) {
        int byWidth = widthDp - marginDp * 2;
        int budget;
        if (landscape) {
            budget = heightDp - marginDp * 2;
            byWidth = Math.min(byWidth, (int) (widthDp * LANDSCAPE_BOARD_WIDTH_RATIO));
        } else {
            int reserve = Math.max(MIN_CONTROLS_RESERVE_DP, (int) (heightDp * 0.40f));
            budget = heightDp - reserve;
        }
        return Math.max(MIN_BOARD_DP, Math.min(byWidth, budget));
    }

    /** 棋盘绘制几何：交点间距、落子半径、棋盘左上角原点。 */
    public static final class BoardGeometry {
        public final float padX;
        public final float padY;
        public final float spacing;
        public final float stoneRadius;

        BoardGeometry(float padX, float padY, float spacing, float stoneRadius) {
            this.padX = padX;
            this.padY = padY;
            this.spacing = spacing;
            this.stoneRadius = stoneRadius;
        }

        /** 棋盘网格右下角的 x 坐标。 */
        public float right(int n) {
            return padX + (n - 1) * spacing;
        }

        /** 棋盘网格右下角的 y 坐标。 */
        public float bottom(int n) {
            return padY + (n - 1) * spacing;
        }
    }

    /**
     * 算棋盘的绘制几何。
     *
     * 关键点：棋盘必须是正方形，所以以**较短边**为基准（side = min(w, availH)）。
     * 若只拿宽度算，全屏（视图非正方形）时横屏会把棋盘下半截切出屏幕。
     *
     * @param topInset 顶部要预留的高度（全屏时留给返回按钮）；非全屏传 0
     */
    public static BoardGeometry boardGeometry(int w, int h, int n, float topInset) {
        float availH = Math.max(1f, h - topInset);
        float side = Math.min(w, availH);
        float labelRoom = Math.max(18f, side * 0.045f);
        float margin = labelRoom + 4f;
        float usable = Math.max(1f, side - 2 * margin);
        float spacing = usable / (n - 1);
        // 居中放置：非全屏时 w == h、topInset == 0，padX/padY 恰好等于 margin
        float padX = (w - usable) / 2f;
        float padY = topInset + (availH - usable) / 2f;
        return new BoardGeometry(padX, padY, spacing, spacing * 0.47f);
    }

    /** 胜率曲线高度：按屏高比例给，夹在 80~160dp。 */
    public static int chartHeightDp(int heightDp) {
        return Math.max(80, Math.min(160, heightDp / 6));
    }

    /** 按钮字号：窄屏收一号，否则三个汉字会换行。 */
    public static int buttonTextSp(int widthDp) {
        return widthDp < 360 ? 12 : 14;
    }
}
