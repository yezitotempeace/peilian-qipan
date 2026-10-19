package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 2048 棋盘：4×4 圆角方块、经典配色、分数在 Activity。滑动手势四方向；新子/合并子弹出一下。
 * 纯绘制与手势，规则在 {@link Game2048}。
 */
public class Game2048View extends View {

    public interface OnSwipe {
        void onSwipe(int dir);
    }

    private Game2048 game;
    private OnSwipe swipe;
    private boolean animationsEnabled = true;
    private long animStart;
    private boolean animActive;
    private float downX, downY;
    private boolean dragging;

    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint board = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cell = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tile = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

    public Game2048View(Context c) { super(c); init(); }
    public Game2048View(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        bg.setColor(0xFFFAF8EF);
        board.setColor(0xFFBBADA0);
        cell.setColor(0xFFCDC1B4);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        setFocusable(true);
    }

    public void setGame(Game2048 g) { game = g; animActive = false; invalidate(); }
    public void setOnSwipe(OnSwipe l) { swipe = l; }
    public void setAnimationsEnabled(boolean on) { animationsEnabled = on; }

    /** 走完一步后调用：让新子和合并子弹一下。 */
    public void onMoved() {
        animStart = System.currentTimeMillis();
        animActive = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawRect(0, 0, getWidth(), getHeight(), bg);
        if (game == null) {
            return;
        }
        float side = Math.min(getWidth(), getHeight());
        float left = (getWidth() - side) / 2f;
        float top = (getHeight() - side) / 2f;
        float gap = side * 0.025f;
        float cellSize = (side - gap * 5f) / 4f;
        board.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(left, top, left + side, top + side), side * 0.03f, side * 0.03f, board);
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                float x = left + gap + c * (cellSize + gap);
                float y = top + gap + r * (cellSize + gap);
                cell.setColor(0xFFCDC1B4);
                canvas.drawRoundRect(new RectF(x, y, x + cellSize, y + cellSize),
                        cellSize * 0.12f, cellSize * 0.12f, cell);
            }
        }
        long now = System.currentTimeMillis();
        long elapsed = now - animStart;
        float slide = animationsEnabled && animActive && !game.lastAnims().isEmpty()
                ? clamp(elapsed / 180f) : 1f;
        if (slide < 1f) {
            // 旧块从原位置滑到目标位置；原地不动的块直接画在终点
            for (Game2048.MoveAnim a : game.lastAnims()) {
                float x, y;
                if (a.from == a.to) {
                    x = centerX(left, gap, cellSize, a.to % 4);
                    y = centerY(top, gap, cellSize, a.to / 4);
                } else {
                    float e = AnimMath.easeOut(slide);
                    x = AnimMath.lerp(centerX(left, gap, cellSize, a.from % 4),
                            centerX(left, gap, cellSize, a.to % 4), e);
                    y = AnimMath.lerp(centerY(top, gap, cellSize, a.from / 4),
                            centerY(top, gap, cellSize, a.to / 4), e);
                }
                drawTile(canvas, x, y, a.value, cellSize, 1f);
            }
            postInvalidateOnAnimation();
            return;
        }
        float pop = animationsEnabled && animActive && !game.lastAnims().isEmpty()
                ? clamp((elapsed - 180f) / 120f) : 1f;
        if (pop < 1f) {
            postInvalidateOnAnimation();
        } else {
            animActive = false;
        }
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) {
                int v = game.at(r, c);
                if (v == 0) {
                    continue;
                }
                int idx = r * 4 + c;
                float scale = (animationsEnabled && pop < 1f
                        && (idx == game.lastSpawnIndex() || game.mergedAt(idx)))
                        ? 0.78f + 0.22f * AnimMath.easeOut(pop) : 1f;
                drawTile(canvas, centerX(left, gap, cellSize, c), centerY(top, gap, cellSize, r), v, cellSize, scale);
            }
        }
    }

    private static float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static float centerX(float left, float gap, float cell, int col) {
        return left + gap + col * (cell + gap) + cell / 2f;
    }

    private static float centerY(float top, float gap, float cell, int row) {
        return top + gap + row * (cell + gap) + cell / 2f;
    }

    private void drawTile(Canvas canvas, float cx, float cy, int v, float cellSize, float scale) {
        float half = cellSize / 2f * scale;
        RectF dr = new RectF(cx - half, cy - half, cx + half, cy + half);
        tile.setColor(tileColor(v));
        canvas.drawRoundRect(dr, cellSize * 0.12f * scale, cellSize * 0.12f * scale, tile);
        text.setColor(v <= 4 ? 0xFF776E65 : 0xFFF9F6F2);
        text.setTextSize(cellSize * (v >= 1000 ? 0.30f : (v >= 100 ? 0.36f : 0.44f)) * scale);
        canvas.drawText(String.valueOf(v), cx, cy + cellSize * 0.15f * scale, text);
    }

    private static int tileColor(int v) {
        switch (v) {
            case 2: return 0xFFEEE4DA;
            case 4: return 0xFFEDE0C8;
            case 8: return 0xFFF2B179;
            case 16: return 0xFFF59563;
            case 32: return 0xFFF67C5F;
            case 64: return 0xFFF65E3B;
            case 128: return 0xFFEDCF72;
            case 256: return 0xFFEDCC61;
            case 512: return 0xFFEDC850;
            case 1024: return 0xFFEDC53F;
            case 2048: return 0xFFEDC22E;
            default: return 0xFF3C3A32;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (game == null || swipe == null) {
            return super.onTouchEvent(e);
        }
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
            dragging = true;
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_UP && dragging) {
            dragging = false;
            float dx = e.getX() - downX;
            float dy = e.getY() - downY;
            float threshold = Math.min(getWidth(), getHeight()) * 0.05f;
            if (Math.abs(dx) < threshold && Math.abs(dy) < threshold) {
                return true;
            }
            int dir;
            if (Math.abs(dx) > Math.abs(dy)) {
                dir = dx > 0 ? Game2048.RIGHT : Game2048.LEFT;
            } else {
                dir = dy > 0 ? Game2048.DOWN : Game2048.UP;
            }
            swipe.onSwipe(dir);
            performClick();
            return true;
        }
        if (e.getAction() == MotionEvent.ACTION_CANCEL) {
            dragging = false;
        }
        return true;
    }

    @Override
    public boolean performClick() {
        super.performClick();
        return true;
    }
}
