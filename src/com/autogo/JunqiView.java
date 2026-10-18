package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** 军棋棋盘（5×12）：铁路 / 行营 / 大本营 / 暗棋背面 / 选中与可走点。纯绘制，规则在 JunqiBoard。 */
public class JunqiView extends View {

    public interface OnCellTap {
        void onTap(int row, int col);
    }

    private JunqiBoard board;
    private boolean[] hidden;      // 暗棋：true = 只看背面
    private int selected = -1;
    private int[] legal;
    private int lastFrom = -1;
    private int lastTo = -1;
    private boolean inputEnabled = true;
    private OnCellTap tapListener;

    private final Paint bg = new Paint();
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint black = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float padX, padY, spacing;
    private static final int cols = JunqiBoard.COLS;
    private static final int rows = JunqiBoard.ROWS;
    private boolean coords;

    public JunqiView(Context c) {
        super(c);
        init();
    }

    public JunqiView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    private void init() {
        setFocusable(true);
        bg.setColor(0xFFF6F1E7);
        bg.setStyle(Paint.Style.FILL);
        line.setColor(0xFF8A6A44);
        line.setStrokeWidth(1.5f);
        line.setStyle(Paint.Style.STROKE);
        rail.setColor(0xFF5A4326);
        rail.setStrokeWidth(4f);
        rail.setStyle(Paint.Style.STROKE);
        text.setColor(0xFF222222);
        text.setTextAlign(Paint.Align.CENTER);
        face.setStyle(Paint.Style.FILL);
        red.setColor(0xFFC62828);
        black.setColor(0xFF263238);
        mark.setStyle(Paint.Style.STROKE);
        mark.setStrokeWidth(3f);
        mark.setColor(0xFF1A73E8);
        dot.setColor(0x881A73E8);
    }

    public void setBoard(JunqiBoard b) {
        board = b;
        spacing = 0f;
        invalidate();
    }

    public void setHidden(boolean[] h) { hidden = h; invalidate(); }
    public void setSelected(int row, int col) { selected = (board == null || row < 0) ? -1 : row * cols + col; invalidate(); }
    public void setLegalPoints(int[] points) { legal = points; invalidate(); }
    public void setLastMove(int fromRow, int fromCol, int toRow, int toCol) {
        lastFrom = (fromRow < 0) ? -1 : fromRow * cols + fromCol;
        lastTo = (fromRow < 0) ? -1 : toRow * cols + toCol;
        invalidate();
    }
    public void setInputEnabled(boolean on) { inputEnabled = on; invalidate(); }
    public void setOnCellTap(OnCellTap l) { tapListener = l; }

    private void geometry(int w, int h) {
        if (spacing > 0f) {
            return;
        }
        float pad = Math.max(6f, Math.min(w, h) * 0.02f);
        spacing = Math.min((w - 2 * pad) / (cols - 1), (h - 2 * pad) / (rows - 1));
        padX = (w - spacing * (cols - 1)) / 2f;
        padY = (h - spacing * (rows - 1)) / 2f;
    }

    private float cx(int col) { return padX + col * spacing; }
    private float cy(int row) { return padY + row * spacing; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        spacing = 0f;
        super.onSizeChanged(w, h, ow, oh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        geometry(getWidth(), getHeight());
        canvas.drawRect(0, 0, getWidth(), getHeight(), bg);
        BoardArt.boardEdge(canvas, 0, 0, getWidth(), getHeight());
        // 细网格
        for (int r = 0; r < rows; r++) {
            canvas.drawLine(cx(0), cy(r), cx(cols - 1), cy(r), line);
        }
        for (int c = 0; c < cols; c++) {
            canvas.drawLine(cx(c), cy(0), cx(c), cy(rows - 1), line);
        }
        // 铁路：整行 1/5/6/10 + 两侧列 1..10
        for (int r = 0; r < rows; r++) {
            if (JunqiBoard.isRail(r, 0)) {
                canvas.drawLine(cx(0), cy(r), cx(cols - 1), cy(r), rail);
            }
        }
        for (int r = 1; r < rows - 1; r++) {
            if (JunqiBoard.isRail(r, 0)) {
                canvas.drawLine(cx(0), cy(r), cx(0), cy(r), rail);
            }
        }
        canvas.drawLine(cx(0), cy(1), cx(0), cy(rows - 2), rail);
        canvas.drawLine(cx(cols - 1), cy(1), cx(cols - 1), cy(rows - 2), rail);
        // 行营与兵站格
        float half = spacing * 0.30f;
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (JunqiBoard.isCamp(r, c)) {
                    canvas.drawCircle(cx(c), cy(r), half * 0.8f, mark);
                } else if (JunqiBoard.isHq(r, c)) {
                    canvas.drawRect(cx(c) - half * 0.8f, cy(r) - half * 0.8f, cx(c) + half * 0.8f, cy(r) + half * 0.8f, mark);
                }
            }
        }
        if (board != null) {
            float size = spacing * 0.62f;
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int color = board.ownerAt(r, c);
                    if (color == JunqiBoard.EMPTY) {
                        continue;
                    }
                    int p = JunqiBoard.idx(r, c);
                    face.setColor(color == JunqiBoard.RED ? 0xFFFFE0E0 : 0xFFE0E6EA);
                    RectF rr = new RectF(cx(c) - size, cy(r) - size * 0.78f, cx(c) + size, cy(r) + size * 0.78f);
                    canvas.drawRoundRect(rr, size * 0.25f, size * 0.25f, face);
                    boolean back = hidden != null && p < hidden.length && hidden[p];
                    String label = back ? "" : JunqiBoard.typeName(board.at(r, c));
                    if (!back && label != null && label.length() > 0) {
                        text.setColor(color == JunqiBoard.RED ? red.getColor() : black.getColor());
                        text.setTextSize(size * 0.78f);
                        canvas.drawText(label, cx(c), cy(r) + size * 0.28f, text);
                    } else if (back) {
                        face.setColor(0xFF9E9E9E);
                        canvas.drawCircle(cx(c), cy(r), size * 0.35f, face);
                    }
                }
            }
        }
        if (lastFrom >= 0) {
            drawCellRing(canvas, lastFrom, 0xFFF9A825);
            drawCellRing(canvas, lastTo, 0xFFF9A825);
        }
        if (selected >= 0) {
            drawCellRing(canvas, selected, 0xFF1A73E8);
        }
        if (legal != null) {
            for (int p : legal) {
                canvas.drawCircle(cx(p % cols), cy(p / cols), spacing * 0.12f, dot);
            }
        }
    }

    private void drawCellRing(Canvas canvas, int p, int color) {
        mark.setColor(color);
        canvas.drawCircle(cx(p % cols), cy(p / cols), spacing * 0.5f, mark);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!inputEnabled || board == null || tapListener == null || e.getAction() != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(e);
        }
        geometry(getWidth(), getHeight());
        if (spacing <= 0f) {
            return super.onTouchEvent(e);
        }
        int col = Math.round((e.getX() - padX) / spacing);
        int row = Math.round((e.getY() - padY) / spacing);
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return super.onTouchEvent(e);
        }
        float dx = e.getX() - cx(col);
        float dy = e.getY() - cy(row);
        if (Math.hypot(dx, dy) > spacing * 0.55f) {
            return super.onTouchEvent(e);
        }
        tapListener.onTap(row, col);
        performClick();
        return true;
    }
}
