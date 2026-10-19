package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * 启动页装饰背景：把围棋 / 五子棋 / 象棋 / 军棋 / 2048 的特征都叠进去的满铺背景。
 *
 * <p>不是简约留白：底部一张淡化的围棋盘 + 黑白子、右上角五子棋连五、左上角「楚河 汉界」+ 将帅、
 * 左下角一串半透明 2048 方块、底边几段军棋铁路虚线；中间一圈柔光把标题托起来。
 * 深浅两套配色走 {@link #setDark(boolean)}，颜色都以半透明叠加，不会抢按钮的对比度。</p>
 */
public class MenuBackground extends View {

    private boolean dark = true;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public MenuBackground(Context c) {
        super(c);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
    }

    public void setDark(boolean dark) {
        if (this.dark != dark) {
            this.dark = dark;
            invalidate();
        }
    }

    public boolean isDark() {
        return dark;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }

        int top = dark ? 0xFF0F1824 : 0xFFF7EEDD;
        int bottom = dark ? 0xFF22303F : 0xFFE7D3AE;
        p.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        p.setStyle(Paint.Style.FILL);
        canvas.drawRect(0, 0, w, h, p);
        p.setShader(null);

        int glow = dark ? 0x1A7FB6E0 : 0x22C89A62;
        p.setShader(new RadialGradient(w * 0.5f, h * 0.34f, Math.max(w, h) * 0.5f,
                glow, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, p);
        p.setShader(null);

        drawGo(canvas, w, h);
        drawGomoku(canvas, w, h);
        drawXiangqi(canvas, w, h);
        draw2048(canvas, w, h);
        drawRails(canvas, w, h);
    }

    private int ink() {
        return dark ? 0x40E8F1FF : 0x30907654;
    }

    private void drawGo(Canvas c, int w, int h) {
        // 右下角一张 9 路淡化围棋盘 + 几颗黑白子
        float side = Math.min(w, h) * 0.34f;
        float x0 = w - side * 1.06f, y0 = h - side * 1.02f;
        if (x0 < 0 || y0 < 0) return;
        p.setColor(ink());
        p.setStrokeWidth(Math.max(1f, side * 0.006f));
        int n = 9;
        float cell = side / (n - 1);
        for (int i = 0; i < n; i++) {
            c.drawLine(x0, y0 + i * cell, x0 + side, y0 + i * cell, p);
            c.drawLine(x0 + i * cell, y0, x0 + i * cell, y0 + side, p);
        }
        int stoneInk = dark ? 0x66FFFFFF : 0x663A2E1A;
        int stoneWhite = dark ? 0x66DFE8F2 : 0x66FFFDF6;
        p.setStyle(Paint.Style.FILL);
        int[][] stones = {{3, 3}, {6, 6}, {3, 6}, {6, 3}, {4, 4}, {5, 5}, {2, 7}};
        for (int i = 0; i < stones.length; i++) {
            p.setColor(i % 2 == 0 ? stoneInk : stoneWhite);
            c.drawCircle(x0 + stones[i][0] * cell, y0 + stones[i][1] * cell, cell * 0.42f, p);
        }
    }

    private void drawGomoku(Canvas c, int w, int h) {
        // 右上角：一张小五子棋盘 + 一列五连黑子
        float side = Math.min(w, h) * 0.22f;
        float x0 = w - side * 1.06f, y0 = side * 0.12f;
        if (x0 < 0 || y0 < 0) return;
        p.setColor(ink());
        p.setStrokeWidth(Math.max(1f, side * 0.006f));
        int n = 5;
        float cell = side / (n - 1);
        for (int i = 0; i < n; i++) {
            c.drawLine(x0, y0 + i * cell, x0 + side, y0 + i * cell, p);
            c.drawLine(x0 + i * cell, y0, x0 + i * cell, y0 + side, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark ? 0x66000000 : 0x66301F12);
        for (int i = 0; i < 5; i++) {
            c.drawCircle(x0 + i * cell, y0 + 2f * cell, cell * 0.4f, p);
        }
        p.setColor(dark ? 0x66DFE8F2 : 0x66FFFDF6);
        c.drawCircle(x0 + 1f * cell, y0 + 3.2f * cell, cell * 0.4f, p);
        c.drawCircle(x0 + 3f * cell, y0 + 1.2f * cell, cell * 0.4f, p);
    }

    private void drawXiangqi(Canvas c, int w, int h) {
        // 左上角：「楚河 汉界」竖排 + 一枚帅/将圆子
        float m = Math.min(w, h) * 0.05f;
        float x = w * 0.09f, y0 = h * 0.16f;
        p.setColor(ink());
        p.setStrokeWidth(Math.max(1f, Math.min(w, h) * 0.004f));
        c.drawLine(x, y0, x, y0 + Math.min(w, h) * 0.20f, p);
        text.setColor(dark ? 0x4AD7E3F2 : 0x40835A38);
        text.setTextSize(Math.min(w, h) * 0.052f);
        c.drawText("楚河", x, y0 + Math.min(w, h) * 0.06f, text);
        c.drawText("汉界", x, y0 + Math.min(w, h) * 0.13f, text);
        // 帅 / 将 圆子
        p.setStyle(Paint.Style.FILL);
        p.setColor(dark ? 0x59B32E2C : 0x59B5483A);
        c.drawCircle(x + m * 2.6f, y0 + Math.min(w, h) * 0.09f, m * 1.1f, p);
        p.setColor(dark ? 0x59262D3B : 0x592E1F10);
        c.drawCircle(x + m * 4.6f, y0 + Math.min(w, h) * 0.14f, m * 1.1f, p);
        text.setColor(0x80FFFFFF);
        text.setTextSize(m * 1.4f);
        c.drawText("帥", x + m * 2.6f, y0 + Math.min(w, h) * 0.09f + m * 0.5f, text);
        c.drawText("將", x + m * 4.6f, y0 + Math.min(w, h) * 0.14f + m * 0.5f, text);
    }

    private void draw2048(Canvas c, int w, int h) {
        // 左下角一串半透明 2048 方块
        int[] vals = {2, 4, 8, 16, 64, 128, 256, 512, 1024, 2048};
        float tile = Math.min(w, h) * 0.072f;
        float gap = tile * 0.18f;
        float x = w * 0.06f, y = h - tile * 1.6f;
        RectF rf = new RectF();
        p.setStyle(Paint.Style.FILL);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        for (int i = 0; i < vals.length; i++) {
            int v = vals[i];
            p.setColor(tileColor(v));
            rf.set(x, y, x + tile, y + tile);
            c.drawRoundRect(rf, tile * 0.16f, tile * 0.16f, p);
            text.setColor(v <= 4 ? 0x5A6E5F4F : 0x66FFFFFF);
            text.setTextSize(tile * (v >= 100 ? 0.30f : 0.44f));
            c.drawText(String.valueOf(v), x + tile / 2f, y + tile * 0.66f, text);
            x += tile + gap;
            if (x + tile > w * 0.62f) {
                x = w * 0.06f;
                y -= tile + gap;
            }
        }
    }

    private int tileColor(int v) {
        switch (v) {
            case 2: return dark ? 0x40EEE4DA : 0x46EEE4DA;
            case 4: return dark ? 0x40EDE0C8 : 0x46EDE0C8;
            case 8: return dark ? 0x46F2B179 : 0x46F2B179;
            case 16: return dark ? 0x46F59563 : 0x46F59563;
            case 32: return dark ? 0x46F67C5F : 0x46F67C5F;
            case 64: return dark ? 0x46F65E3B : 0x46F65E3B;
            case 128: return dark ? 0x46EDCF72 : 0x46EDCF72;
            case 256: return dark ? 0x46EDCC61 : 0x46EDCC61;
            case 512: return dark ? 0x46EDC850 : 0x46EDC850;
            case 1024: return dark ? 0x46EDC53F : 0x46EDC53F;
            case 2048: return dark ? 0x50EDC22E : 0x50EDC22E;
            default: return dark ? 0x46E8E8E8 : 0x46C9BBA0;
        }
    }

    private void drawRails(Canvas c, int w, int h) {
        // 底边几段军棋铁路：黑底白虚线
        float y = h - Math.min(w, h) * 0.05f;
        float dash = Math.min(w, h) * 0.05f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, Math.min(w, h) * 0.006f));
        p.setColor(dark ? 0x2E000000 : 0x2E3A2E1A);
        c.drawLine(w * 0.16f, y, w * 0.84f, y, p);
        p.setColor(dark ? 0x40C7D2E0 : 0x40FFFDF6);
        for (float x = w * 0.16f; x < w * 0.84f; x += dash * 2f) {
            c.drawLine(x, y, Math.min(x + dash, w * 0.84f), y, p);
        }
    }
}
