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
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 黑白棋棋盘视图：8×8 绿色绒面、立体圆子、落子/翻牌动画、合法点提示。
 */
public class ReversiView extends View {

    public interface OnCellTap {
        void onTap(int row, int col);
    }

    private ReversiBoard board;
    private boolean dark;
    private OnCellTap tapListener;
    private boolean inputEnabled = true;
    private boolean animationsEnabled = true;

    // 几何
    private float cw, ox, oy;
    private int lastW = -1, lastH = -1;
    private static final int UNITS = 9;   // 8 格 + 两侧各半格留白

    // 标记
    private int lastMove = -1;
    private int[] legal = new int[0];

    // 翻牌动画
    private static class Flip {
        int idx, from, to;
        long start;
    }
    private final List<Flip> flips = new ArrayList<Flip>();

    // 画笔
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint felt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint star = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    public ReversiView(Context c) {
        super(c);
        bg.setStyle(Paint.Style.FILL);
        felt.setStyle(Paint.Style.FILL);
        grid.setStyle(Paint.Style.STROKE);
        star.setStyle(Paint.Style.FILL);
        face.setStyle(Paint.Style.FILL);
        ring.setStyle(Paint.Style.STROKE);
        mark.setStyle(Paint.Style.STROKE);
        hint.setStyle(Paint.Style.FILL);
        shadow.setStyle(Paint.Style.FILL);
    }

    public void setBoard(ReversiBoard b) {
        board = b;
        flips.clear();
        lastMove = -1;
        invalidate();
    }

    public void setDark(boolean d) {
        dark = d;
        invalidate();
    }

    public void setAnimationsEnabled(boolean on) {
        animationsEnabled = on;
    }

    public void setInputEnabled(boolean on) {
        inputEnabled = on;
        invalidate();
    }

    public void setOnCellTap(OnCellTap l) {
        tapListener = l;
    }

    public void setLastMove(int r, int c) {
        lastMove = (r < 0) ? -1 : r * 8 + c;
        invalidate();
    }

    public void setLegalPoints(int[] pts) {
        legal = pts == null ? new int[0] : pts;
        invalidate();
    }

    /** 通知某个格子上的棋子从 from 色翻成 to 色（播翻牌动画）。 */
    public void animateFlip(int r, int c, int from, int to) {
        Flip f = new Flip();
        f.idx = r * 8 + c;
        f.from = from;
        f.to = to;
        f.start = System.currentTimeMillis();
        flips.add(f);
        invalidate();
    }

    public void clearFlipAnims() {
        flips.clear();
        invalidate();
    }

    private float cx(int col) { return ox + col * cw; }

    private float cy(int row) { return oy + row * cw; }

    private void geometry(int w, int h) {
        if (cw > 0f && Math.abs(w - lastW) < 1 && Math.abs(h - lastH) < 1) {
            return;
        }
        lastW = w;
        lastH = h;
        cw = Math.min(w, h) / (float) UNITS;
        ox = (w - 8f * cw) / 2f + cw / 2f;
        oy = (h - 8f * cw) / 2f + cw / 2f;
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cw = 0f;
        lastW = lastH = -1;
        super.onSizeChanged(w, h, ow, oh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        canvas.drawRect(0, 0, w, h, bg);
        geometry(w, h);
        if (cw <= 0f || board == null) {
            return;
        }
        drawBoard(canvas);
        drawDiscs(canvas);
        drawMarks(canvas);
        BoardArt.boardEdge(canvas, 0, 0, w, h);
    }

    private void drawBoard(Canvas canvas) {
        // 绒面底
        float left = ox - cw / 2f, top = oy - cw / 2f;
        float size = 8f * cw;
        felt.setColor(dark ? 0xFF1E4D30 : 0xFF2E7D46);
        tmp.set(left, top, left + size, top + size);
        canvas.drawRoundRect(tmp, cw * 0.18f, cw * 0.18f, felt);
        // 边框
        ring.setColor(dark ? 0xFF14351F : 0xFF1B5E33);
        ring.setStrokeWidth(Math.max(2f, cw * 0.06f));
        canvas.drawRoundRect(tmp, cw * 0.18f, cw * 0.18f, ring);
        // 网格
        grid.setColor(dark ? 0xFF2A6B42 : 0xFF256B3C);
        grid.setStrokeWidth(Math.max(1f, cw * 0.02f));
        for (int i = 0; i <= 8; i++) {
            canvas.drawLine(left, top + i * cw, left + size, top + i * cw, grid);
            canvas.drawLine(left + i * cw, top, left + i * cw, top + size, grid);
        }
        // 四个星位
        star.setColor(dark ? 0xFF14351F : 0xFF1B5E33);
        for (int r : new int[]{2, 6}) {
            for (int c : new int[]{2, 6}) {
                canvas.drawCircle(cx(c), cy(r), cw * 0.06f, star);
            }
        }
    }

    private void drawDiscs(Canvas canvas) {
        long now = System.currentTimeMillis();
        boolean animating = false;
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                int v = board.at(r, c);
                if (v == ReversiBoard.EMPTY) {
                    continue;
                }
                int idx = r * 8 + c;
                Flip f = findFlip(idx);
                if (f != null) {
                    float p = animationsEnabled
                            ? AnimMath.progress(f.start, now, AnimMath.FLIP_MS) : 1f;
                    if (p >= 1f) {
                        flips.remove(f);
                        drawDisc(canvas, cx(c), cy(r), v, 1f);
                    } else {
                        float ang = p * (float) Math.PI;
                        float sx = Math.max(0.06f, Math.abs((float) Math.cos(ang)));
                        int show = p < 0.5f ? f.from : f.to;
                        float lift = (float) Math.sin(ang) * cw * 0.10f;
                        drawDisc(canvas, cx(c), cy(r) - lift, show, sx);
                        animating = true;
                    }
                } else {
                    drawDisc(canvas, cx(c), cy(r), v, 1f);
                }
            }
        }
        if (animating) {
            postInvalidateOnAnimation();
        }
    }

    private Flip findFlip(int idx) {
        for (Flip f : flips) {
            if (f.idx == idx) {
                return f;
            }
        }
        return null;
    }

    /** 立体圆子：投影 + 侧面 + 顶面渐变 + 高光。 */
    private void drawDisc(Canvas canvas, float x, float y, int color, float scaleX) {
        float d = cw * 0.78f;
        float rw = d * 0.5f * scaleX, rh = d * 0.5f;
        boolean black = color == ReversiBoard.BLACK;

        // 投影
        shadow.setColor(0x33000000);
        tmp.set(x - rw * 1.02f, y - rh + rh * 0.22f, x + rw * 1.02f, y + rh + rh * 0.22f);
        canvas.drawOval(tmp, shadow);
        // 侧面（深一档，做厚度）
        face.setStyle(Paint.Style.FILL);
        face.setColor(black ? 0xFF101418 : 0xFFB9C4CE);
        tmp.set(x - rw, y - rh + rh * 0.16f, x + rw, y + rh + rh * 0.16f);
        canvas.drawOval(tmp, face);
        // 顶面
        face.setColor(black ? (dark ? 0xFF2B323B : 0xFF23282E) : (dark ? 0xFFE3E9EE : 0xFFFAF6EC));
        tmp.set(x - rw, y - rh, x + rw, y + rh);
        canvas.drawOval(tmp, face);
        // 高光
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(Math.max(1f, rh * 0.10f));
        ring.setColor(0x50FFFFFF);
        tmp.set(x - rw * 0.62f, y - rh * 0.62f, x + rw * 0.62f, y + rh * 0.10f);
        canvas.drawOval(tmp, ring);
        ring.setColor(0x28000000);
        tmp.set(x - rw * 0.70f, y - rh * 0.10f, x + rw * 0.70f, y + rh * 0.62f);
        canvas.drawOval(tmp, ring);
    }

    private void drawMarks(Canvas canvas) {
        if (lastMove >= 0) {
            int r = lastMove / 8, c = lastMove % 8;
            mark.setColor(0xFFF9A825);
            mark.setStrokeWidth(Math.max(2f, cw * 0.05f));
            canvas.drawCircle(cx(c), cy(r), cw * 0.44f, mark);
        }
        hint.setColor(0x66000000);
        for (int p : legal) {
            canvas.drawCircle(cx(p % 8), cy(p / 8), cw * 0.11f, hint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!inputEnabled || board == null || tapListener == null
                || e.getAction() != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(e);
        }
        geometry(getWidth(), getHeight());
        if (cw <= 0f) {
            return super.onTouchEvent(e);
        }
        int col = Math.round((e.getX() - ox) / cw);
        int row = Math.round((e.getY() - oy) / cw);
        if (row < 0 || row >= 8 || col < 0 || col >= 8) {
            return super.onTouchEvent(e);
        }
        if (Math.abs(e.getX() - cx(col)) > cw * 0.48f
                || Math.abs(e.getY() - cy(row)) > cw * 0.48f) {
            return super.onTouchEvent(e);
        }
        tapListener.onTap(row, col);
        performClick();
        return true;
    }
}
