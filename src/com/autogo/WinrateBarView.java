/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/**
 * 胜率条 —— 一根横向分两段的颜色条，左黑右白，中间分界线就是胜率。
 *
 * 做成独立 View 而不是画在棋盘上，是因为它要放在横屏右下角的状态面板里，
 * 独立 View 可以自己管尺寸（onMeasure 给个合理高度），不挤占棋盘。
 */
public class WinrateBarView extends View {

    private double blackWinrate = 0.5;   // 0..1，黑方胜率
    private boolean valid = false;
    private String label = "";

    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edgePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 「等待数据」的灰条。提到字段里，别每次 onDraw 都 new。 */
    private final Paint emptyPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 黑白交界的分隔竖线。同样提到字段里 —— 原先每帧 new 一个，跟本文件这句注释自相矛盾。 */
    private final Paint dividerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Palette palette = Palette.of(false);

    public WinrateBarView(Context context) {
        super(context);
        init();
    }

    public WinrateBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        blackPaint.setColor(0xFF1A1A1A);
        whitePaint.setColor(0xFFEEEEEE);
        edgePaint.setColor(0xFF888888);
        edgePaint.setStyle(Paint.Style.STROKE);
        edgePaint.setStrokeWidth(1.5f);
        dividerPaint.setColor(0xFFFF3B30);
        dividerPaint.setStrokeWidth(2f);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        emptyPaint.setColor(0xFFCCCCCC);
        labelPaint.setColor(0xFF333333);
    }

    /** 换配色（黑夜/白天）。 */
    public void setPalette(Palette p) {
        if (p == null) return;
        palette = p;
        emptyPaint.setColor(p.barEmpty);
        labelPaint.setColor(p.barLabel);
        invalidate();
    }

    /** 设置黑方胜率（0..1）。传 NaN 或不在 [0,1] 范围则显示「等待」。 */
    public void setBlackWinrate(double wr) {
        if (Double.isNaN(wr) || wr < 0 || wr > 1) {
            valid = false;
            blackWinrate = 0.5;
        } else {
            valid = true;
            blackWinrate = wr;
        }
        invalidate();
    }

    public void setLabel(String s) {
        label = s == null ? "" : s;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // 用 resolveSize 尊重父容器给的测量模式：
        // EXACTLY 就听父容器的，AT_MOST 取较小值，UNSPECIFIED（放进 ScrollView/WRAP_CONTENT
        // 的容器时）才用我们的理想高度。直接返回 spec size 在 UNSPECIFIED 下会得到 0。
        int w = resolveSize(MeasureSpec.getSize(widthSpec), widthSpec);
        float density = getResources().getDisplayMetrics().density;
        int ideal = Math.max(Math.round(36 * density), getSuggestedMinimumHeight());
        int h = resolveSize(ideal, heightSpec);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float barH = h * 0.55f;
        float barTop = h * 0.08f;
        float barBottom = barTop + barH;
        float radius = barH * 0.25f;

        if (!valid) {
            // 没有数据时显示灰色条 + 「等待数据」
            RectF rect = new RectF(0, barTop, w, barBottom);
            canvas.drawRoundRect(rect, radius, radius, emptyPaint);
            canvas.drawRoundRect(rect, radius, radius, edgePaint);
            labelPaint.setTextSize(h * 0.28f);
            canvas.drawText("胜率 等待数据", w / 2f, barBottom + h * 0.32f, labelPaint);
            return;
        }

        // 黑色段（左）
        float split = (float) (w * blackWinrate);
        RectF blackRect = new RectF(0, barTop, split, barBottom);
        canvas.drawRoundRect(blackRect, radius, radius, blackPaint);
        // 白色段（右）
        RectF whiteRect = new RectF(split, barTop, w, barBottom);
        canvas.drawRoundRect(whiteRect, radius, radius, whitePaint);
        // 中间交界用一个竖线标清楚
        if (split > radius && split < w - radius) {
            canvas.drawLine(split, barTop - 2, split, barBottom + 2, dividerPaint);
        }

        // 百分比标签
        textPaint.setTextSize(h * 0.30f);
        labelPaint.setTextSize(h * 0.28f);
        // 黑方百分比（画在黑段中间，白色字）
        if (split > h * 0.6f) {
            textPaint.setColor(Color.WHITE);
            canvas.drawText(String.format(java.util.Locale.US, "%.1f%%", blackWinrate * 100),
                    split / 2f, barTop + barH * 0.72f, textPaint);
        }
        // 白方百分比（画在白段中间，黑色字）
        if (w - split > h * 0.6f) {
            textPaint.setColor(Color.BLACK);
            canvas.drawText(String.format(java.util.Locale.US, "%.1f%%", (1 - blackWinrate) * 100),
                    (split + w) / 2f, barTop + barH * 0.72f, textPaint);
        }

        // 底部标签
        canvas.drawText(label, w / 2f, barBottom + h * 0.32f, labelPaint);
    }
}
