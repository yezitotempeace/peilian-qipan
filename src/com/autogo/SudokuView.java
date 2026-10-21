/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 数独棋盘：9×9 网格，3×3 宫的线加粗，题面数字加粗、玩家填的用强调色。
 *
 * <p>绘制尺寸一律由「可用空间的短边」算出来再居中（{@link #side()}），
 * 所以任何屏幕比例、任何剩余高度下都不会溢出或被裁掉 —— 这是 2048 那套做法。</p>
 *
 * <p>冲突格（同行/同列/同宫重复）填成红底红字，只提示不拦。</p>
 */
public class SudokuView extends View {

    /** 点了一个格子。 */
    public interface OnCellTap {
        void onCellTap(int row, int col);
    }

    private SudokuGame game;
    private OnCellTap tap;
    private Palette palette = Palette.of(false);
    private boolean dark;
    private int selRow = -1, selCol = -1;

    private final Paint boardPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thinLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint thickLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint digit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint note = new Paint(Paint.ANTI_ALIAS_FLAG);

    public SudokuView(Context c) { super(c); init(); }

    public SudokuView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        digit.setTextAlign(Paint.Align.CENTER);
        note.setTextAlign(Paint.Align.CENTER);
        thinLine.setStyle(Paint.Style.STROKE);
        thickLine.setStyle(Paint.Style.STROKE);
        setFocusable(true);
    }

    public void setGame(SudokuGame g) {
        game = g;
        selRow = selCol = -1;
        invalidate();
    }

    public void setOnCellTap(OnCellTap l) { tap = l; }

    public void setPalette(Palette p) {
        if (p == null) {
            return;
        }
        palette = p;
        dark = p.dark;
        invalidate();
    }

    public void setDark(boolean d) {
        if (dark != d) {
            dark = d;
            palette = Palette.of(d);
            invalidate();
        }
    }

    public void select(int r, int c) {
        selRow = r;
        selCol = c;
        invalidate();
    }

    public int selectedRow() { return selRow; }

    public int selectedCol() { return selCol; }

    /** 棋盘方形边长（px）：取可用空间的短边。 */
    public float side() {
        return Math.min(getWidth(), getHeight());
    }

    public float originX() { return (getWidth() - side()) / 2f; }

    public float originY() { return (getHeight() - side()) / 2f; }

    public float cellSize() { return side() / SudokuGame.N; }

    /** 屏幕坐标 → 格子；超出棋盘返回 null。 */
    public int[] hitCell(float x, float y) {
        float sd = side();
        if (sd <= 0) {
            return null;
        }
        float lx = x - originX();
        float ly = y - originY();
        if (lx < 0 || ly < 0 || lx >= sd || ly >= sd) {
            return null;
        }
        int c = (int) (lx / (sd / SudokuGame.N));
        int r = (int) (ly / (sd / SudokuGame.N));
        if (r < 0 || r > 8 || c < 0 || c > 8) {
            return null;
        }
        return new int[]{r, c};
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sd = side();
        if (sd <= 0) {
            return;
        }
        float left = originX();
        float top = originY();

        // 底板
        boardPaint.setColor(dark ? 0xFF1B1B1B : 0xFFFFFFFF);
        canvas.drawRect(left, top, left + sd, top + sd, boardPaint);

        // 格子底色：选中 / 同数字 / 冲突
        float cs = cellSize();
        int selVal = game == null ? 0 : game.at(selRow, selCol);
        for (int r = 0; r < SudokuGame.N; r++) {
            for (int c = 0; c < SudokuGame.N; c++) {
                int color = 0;
                if (r == selRow && c == selCol) {
                    color = dark ? 0xFF1E3A5F : 0xFFBBDEFB;
                } else if (selVal != 0 && game != null && game.at(r, c) == selVal) {
                    color = dark ? 0xFF232F3E : 0xFFE3F2FD;
                }
                if (color != 0) {
                    fillPaint.setColor(color);
                    canvas.drawRect(left + c * cs, top + r * cs,
                            left + (c + 1) * cs, top + (r + 1) * cs, fillPaint);
                }
                if (game != null && game.conflictAt(r, c)) {
                    fillPaint.setColor(dark ? 0xFF4A1F1F : 0xFFFFCDD2);
                    canvas.drawRect(left + c * cs, top + r * cs,
                            left + (c + 1) * cs, top + (r + 1) * cs, fillPaint);
                }
            }
        }

        // 细网格
        thinLine.setColor(dark ? 0xFF3A3A3A : 0xFFD5D5D5);
        thinLine.setStrokeWidth(Math.max(1f, sd * 0.0015f));
        for (int i = 1; i < SudokuGame.N; i++) {
            if (i % 3 == 0) {
                continue;
            }
            float x = left + i * cs;
            float y = top + i * cs;
            canvas.drawLine(x, top, x, top + sd, thinLine);
            canvas.drawLine(left, y, left + sd, y, thinLine);
        }

        // 粗线：3×3 宫 + 外框
        thickLine.setColor(dark ? 0xFFBEBEBE : 0xFF404040);
        thickLine.setStrokeWidth(Math.max(2f, sd * 0.006f));
        for (int i = 0; i <= SudokuGame.N; i += 3) {
            float x = left + i * cs;
            float y = top + i * cs;
            canvas.drawLine(x, top, x, top + sd, thickLine);
            canvas.drawLine(left, y, left + sd, y, thickLine);
        }

        // 数字与笔记
        if (game == null) {
            return;
        }
        for (int r = 0; r < SudokuGame.N; r++) {
            for (int c = 0; c < SudokuGame.N; c++) {
                int v = game.at(r, c);
                float cx = left + c * cs + cs / 2f;
                float cy = top + r * cs + cs / 2f;
                if (v != 0) {
                    boolean bad = game.conflictAt(r, c);
                    digit.setColor(bad ? (dark ? 0xFFFF6B61 : 0xFFC62828)
                            : (game.isGiven(r, c) ? palette.text
                            : (dark ? 0xFF6FA8FF : 0xFF1565C0)));
                    digit.setFakeBoldText(game.isGiven(r, c));
                    digit.setTextSize(cs * 0.62f);
                    canvas.drawText(String.valueOf(v), cx, cy + cs * 0.22f, digit);
                } else {
                    int mask = game.noteMask(r, c);
                    if (mask != 0) {
                        drawNotes(canvas, mask, left + c * cs, top + r * cs, cs);
                    }
                }
            }
        }
    }

    private void drawNotes(Canvas canvas, int mask, float x, float y, float cs) {
        note.setColor(dark ? 0xFF9A9A9A : 0xFF6B6B6B);
        note.setTextSize(cs * 0.26f);
        for (int d = 1; d <= 9; d++) {
            if ((mask & (1 << d)) == 0) {
                continue;
            }
            int idx = d - 1;
            float nx = x + (idx % 3) * cs / 3f + cs / 6f;
            float ny = y + (idx / 3) * cs / 3f + cs / 6f;
            canvas.drawText(String.valueOf(d), nx, ny + cs * 0.09f, note);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP) {
            int[] rc = hitCell(e.getX(), e.getY());
            if (rc != null) {
                select(rc[0], rc[1]);
                if (tap != null) {
                    tap.onCellTap(rc[0], rc[1]);
                }
                performClick();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
