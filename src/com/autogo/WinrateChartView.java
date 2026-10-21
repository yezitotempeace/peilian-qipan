/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 胜率曲线：横轴手数，纵轴黑方胜率 0~100%。
 * 触摸可以定位到某一手，回调给复盘界面跳转。
 */
public class WinrateChartView extends View {

    public interface OnSeek {
        void onSeek(int moveIndex);
    }

    private double[] blackWinrate = new double[0];
    private int selected = -1;
    private OnSeek seekListener;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint area = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint axis = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursor = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float left, top, right, bottom;

    private Palette palette = Palette.of(false);

    public WinrateChartView(Context c) {
        super(c);
        init();
    }

    public WinrateChartView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        grid.setStrokeWidth(1f);
        line.setStrokeWidth(3f);
        line.setStyle(Paint.Style.STROKE);
        area.setStyle(Paint.Style.FILL);
        axis.setStrokeWidth(1.5f);
        cursor.setStrokeWidth(2f);
        setPalette(Palette.of(false));
    }

    /** 换配色（黑夜/白天）。 */
    public void setPalette(Palette p) {
        if (p == null) return;
        palette = p;
        bg.setColor(p.chartBg);
        grid.setColor(p.chartGrid);
        line.setColor(p.chartLine);
        area.setColor(p.chartArea);
        axis.setColor(p.chartAxis);
        cursor.setColor(p.chartCursor);
        invalidate();
    }

    public void setData(double[] blackWinrate) {
        this.blackWinrate = blackWinrate == null ? new double[0] : blackWinrate;
        invalidate();
    }

    public void setSelected(int index) {
        selected = index;
        invalidate();
    }

    public void setOnSeek(OnSeek l) {
        seekListener = l;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        canvas.drawRect(0, 0, w, h, bg);
        left = dp(6);
        right = w - dp(6);
        top = dp(6);
        bottom = h - dp(6);
        if (right <= left || bottom <= top) return;

        // 50% 中线 + 25/75 参考线
        for (int pct = 25; pct <= 75; pct += 25) {
            float y = yFor(pct / 100.0);
            canvas.drawLine(left, y, right, y, pct == 50 ? axis : grid);
        }
        canvas.drawLine(left, top, left, bottom, axis);
        canvas.drawLine(left, bottom, right, bottom, axis);

        if (blackWinrate.length == 0) return;

        int n = blackWinrate.length;
        Path path = new Path();
        Path fill = new Path();
        for (int i = 0; i < n; i++) {
            float x = xFor(i, n);
            float y = yFor(blackWinrate[i]);
            if (i == 0) {
                path.moveTo(x, y);
                fill.moveTo(x, bottom);
                fill.lineTo(x, y);
            } else {
                path.lineTo(x, y);
                fill.lineTo(x, y);
            }
        }
        if (n > 1) {
            fill.lineTo(xFor(n - 1, n), bottom);
            fill.close();
            canvas.drawPath(fill, area);
        }
        canvas.drawPath(path, line);

        if (selected >= 0 && selected < n) {
            float x = xFor(selected, n);
            canvas.drawLine(x, top, x, bottom, cursor);
            canvas.drawCircle(x, yFor(blackWinrate[selected]), dp(4), cursor);
        }
    }

    private float xFor(int i, int n) {
        if (n <= 1) return left;
        return left + (right - left) * i / (float) (n - 1);
    }

    private float yFor(double wr) {
        // NaN 会原样穿透 Math.min/max（Math.min(1, NaN) == NaN），lineTo(x, NaN)
        // 会把整条折线画废。现在的数据源是 GameReview.toBlackWinrates —— 它把「还没算到」的
        // NaN 槽位顺延成前一个点的值，所以正常路径不会把 NaN 传进来；这里只是兜底，
        // 防的是将来换数据源。（旧注释写「数据源已把空槽位填成 0.5」，第 18 轮复核时已过期。）
        double v = Double.isNaN(wr) ? 0.5 : Math.max(0, Math.min(1, wr));
        return bottom - (float) ((bottom - top) * v);
    }

    private float dp(float v) {
        return getResources().getDisplayMetrics().density * v;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // right/left 只在 onDraw 里赋值：还没画过就收到触摸时会拿 0 去算命中比例，
        // 结果恒定跳到最后一手（夹取挡住了越界，但跳错手一样是错的）。
        if (blackWinrate.length == 0 || right <= left) return super.onTouchEvent(e);
        if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
            int n = blackWinrate.length;
            float rel = (e.getX() - left) / Math.max(1f, right - left);
            int idx = Math.round(rel * (n - 1));
            idx = Math.max(0, Math.min(n - 1, idx));
            selected = idx;
            invalidate();
            if (seekListener != null) seekListener.onSeek(idx);
            return true;
        }
        return super.onTouchEvent(e);
    }
}
