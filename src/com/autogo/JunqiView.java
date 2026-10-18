package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 军棋棋盘（5 列 × 12 行）：铁路 / 行营 / 大本营 / 暗棋背面 / 选中与可走点。纯绘制，规则在 JunqiBoard。
 *
 * <p>棋盘按「方格」画：每个交叉点是一枚棋子格（不是围棋那种线条交点），铁路画在格子后面，
 * 只从格缝里露出来；棋子格占 0.88 格、棋子占 0.84×0.70 格，保证相邻棋子之间留出空隙。
 */
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
    private boolean animationsEnabled;
    private boolean animActive;
    private long animStart;
    private int animFrom = -1;
    private int animTo = -1;

    private final Paint bg = new Paint();
    private final Paint cellFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rail = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pieceLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint camp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hq = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** 每个格子的边长（px）与棋盘左上角。 */
    private float cell, originX, originY;
    private static final int cols = JunqiBoard.COLS;
    private static final int rows = JunqiBoard.ROWS;

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
        cellFill.setStyle(Paint.Style.FILL);
        cellFill.setColor(0xFFFFFCF4);
        cellLine.setStyle(Paint.Style.STROKE);
        cellLine.setColor(0x558A6A44);
        rail.setColor(0x996D4C33);
        rail.setStyle(Paint.Style.STROKE);
        text.setColor(0xFF222222);
        text.setTextAlign(Paint.Align.CENTER);
        face.setStyle(Paint.Style.FILL);
        pieceLine.setStyle(Paint.Style.STROKE);
        mark.setStyle(Paint.Style.STROKE);
        dot.setStyle(Paint.Style.FILL);
        camp.setStyle(Paint.Style.FILL_AND_STROKE);
        camp.setColor(0x332E7D32);
        hq.setStyle(Paint.Style.FILL);
        hq.setColor(0x33F9A825);
    }

    public void setBoard(JunqiBoard b) {
        board = b;
        cell = 0f;
        invalidate();
    }

    public void setHidden(boolean[] h) { hidden = h; invalidate(); }
    public void setSelected(int row, int col) { selected = (board == null || row < 0) ? -1 : row * cols + col; invalidate(); }
    public void setLegalPoints(int[] points) { legal = points; invalidate(); }
    public void setAnimationsEnabled(boolean on) { animationsEnabled = on; }

    public void setLastMove(int fromRow, int fromCol, int toRow, int toCol) {
        int nf = (fromRow < 0) ? -1 : fromRow * cols + fromCol;
        int nt = (fromRow < 0) ? -1 : toRow * cols + toCol;
        boolean changed = nf != lastFrom || nt != lastTo;
        lastFrom = nf;
        lastTo = nt;
        if (!changed) {
            // 同一步（点选/刷新）不重播，否则每点一下棋子上一手都会回退再滑
            invalidate();
            return;
        }
        if (animationsEnabled && fromRow >= 0 && toRow >= 0) {
            animFrom = lastFrom;
            animTo = lastTo;
            animStart = System.currentTimeMillis();
            animActive = true;
        } else {
            animActive = false;
        }
        invalidate();
    }
    public void setInputEnabled(boolean on) { inputEnabled = on; invalidate(); }
    public void setOnCellTap(OnCellTap l) { tapListener = l; }

    private void geometry(int w, int h) {
        if (cell > 0f) {
            return;
        }
        float pad = Math.max(8f, Math.min(w, h) * 0.03f);
        cell = Math.min((w - 2 * pad) / cols, (h - 2 * pad) / rows);
        originX = (w - cell * cols) / 2f;
        originY = (h - cell * rows) / 2f;
    }

    private float cx(int col) { return originX + (col + 0.5f) * cell; }
    private float cy(int row) { return originY + (row + 0.5f) * cell; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cell = 0f;
        super.onSizeChanged(w, h, ow, oh);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        geometry(getWidth(), getHeight());
        if (cell <= 0f) {
            return;
        }
        canvas.drawRect(0, 0, getWidth(), getHeight(), bg);

        // 每格：普通方格 / 行营圆 / 大本营方块
        cellLine.setStrokeWidth(Math.max(1f, cell * 0.018f));
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x = cx(c), y = cy(r);
                if (JunqiBoard.isCamp(r, c)) {
                    camp.setStrokeWidth(Math.max(1.5f, cell * 0.035f));
                    camp.setColor(0x332E7D32);
                    canvas.drawCircle(x, y, cell * 0.47f, camp);
                } else if (JunqiBoard.isHq(r, c)) {
                    float s = cell * 0.47f;
                    canvas.drawRect(x - s, y - s, x + s, y + s, hq);
                    canvas.drawRect(x - s, y - s, x + s, y + s, cellLine);
                } else {
                    float s = cell * 0.44f;
                    canvas.drawRect(x - s, y - s, x + s, y + s, cellFill);
                    canvas.drawRect(x - s, y - s, x + s, y + s, cellLine);
                }
            }
        }

        // 铁路：压在格子上（半透明，仍看得见连续轨道），棋子再盖在它上面
        rail.setStrokeWidth(Math.max(2.5f, cell * 0.065f));
        for (int r = 0; r < rows; r++) {
            // 只看「整行铁路」：isRail(r, 0) 会被「第 0 列也是铁路」的规则命中，
            // 第 1..10 行都会返回 true（第 109 轮审计 高-1）；用中间列避开列规则。
            if (JunqiBoard.isRail(r, cols / 2)) {
                canvas.drawLine(originX + cell * 0.10f, cy(r), originX + cell * (cols - 0.10f), cy(r), rail);
            }
        }
        for (int r = 1; r < rows - 1; r++) {
            if (JunqiBoard.isRail(r, 0)) {
                canvas.drawLine(cx(0), originY + cell * 1.10f, cx(0), originY + cell * (rows - 1.10f), rail);
                canvas.drawLine(cx(cols - 1), originY + cell * 1.10f, cx(cols - 1), originY + cell * (rows - 1.10f), rail);
                break;
            }
        }

        // 棋子：宽 0.84 格、高 0.70 格（相邻棋子之间留缝）；走子时滑过去
        int animPoint = -1;
        float animP = 1f;
        if (animActive) {
            animP = AnimMath.progress(animStart, System.currentTimeMillis(), AnimMath.MOVE_MS);
            if (animP >= 1f) {
                animActive = false;
            } else {
                animPoint = animTo;
            }
        }
        if (board != null) {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int color = board.ownerAt(r, c);
                    if (color == JunqiBoard.EMPTY) {
                        continue;
                    }
                    int p = JunqiBoard.idx(r, c);
                    if (p == animPoint) {
                        continue;
                    }
                    boolean back = hidden != null && p < hidden.length && hidden[p];
                    drawPiece(canvas, cx(c), cy(r), color, board.at(r, c), back);
                }
            }
        }
        if (animPoint >= 0) {
            float e = AnimMath.easeOut(animP);
            float x = AnimMath.lerp(cx(animFrom % cols), cx(animTo % cols), e);
            float y = AnimMath.lerp(cy(animFrom / cols), cy(animTo / cols), e);
            boolean back = hidden != null && animTo < hidden.length && hidden[animTo];
            // 只有「走子方最后占了目标格」才滑；撞死/同归于尽只闪一下，别把守方或空格滑过来
            int mover = board == null ? 0 : JunqiBoard.opponent(board.sideToMove());
            if (board != null && board.ownerAt(animTo / cols, animTo % cols) == mover) {
                drawPiece(canvas, x, y, board.ownerAt(animTo / cols, animTo % cols), board.at(animTo / cols, animTo % cols), back);
            }
            // 吃子闪光
            float fa = 0.55f * (1f - animP);
            mark.setColor(((int) (fa * 255) << 24) | 0x00FFB300);
            mark.setStrokeWidth(Math.max(2f, cell * 0.06f));
            canvas.drawCircle(cx(animTo % cols), cy(animTo / cols), cell * (0.30f + 0.30f * e), mark);
            postInvalidateOnAnimation();
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
                canvas.drawCircle(cx(p % cols), cy(p / cols), cell * 0.10f, dot);
            }
        }
        BoardArt.boardEdge(canvas, 0, 0, getWidth(), getHeight());
    }

    private void drawPiece(Canvas canvas, float x, float y, int color, int type, boolean back) {
        float pw = cell * 0.84f, ph = cell * 0.70f, radius = cell * 0.11f;
        pieceLine.setStrokeWidth(Math.max(1.5f, cell * 0.025f));
        pieceLine.setColor(color == JunqiBoard.RED ? 0xFFB71C1C : 0xFF37474F);
        face.setStyle(Paint.Style.FILL);
        face.setColor(pieceLine.getColor());
        RectF outer = new RectF(x - pw / 2f, y - ph / 2f, x + pw / 2f, y + ph / 2f);
        canvas.drawRoundRect(outer, radius, radius, face);
        face.setColor(back ? 0xFFB0BEC5 : (color == JunqiBoard.RED ? 0xFFFFE3E3 : 0xFFE6ECEF));
        RectF inner = new RectF(x - pw * 0.455f, y - ph * 0.44f, x + pw * 0.455f, y + ph * 0.44f);
        canvas.drawRoundRect(inner, radius * 0.8f, radius * 0.8f, face);
        if (back) {
            face.setColor(0xFF546E7A);
            canvas.drawCircle(x, y, cell * 0.10f, face);
        } else {
            String label = JunqiBoard.typeName(type);
            if (label != null && label.length() > 0) {
                text.setColor(color == JunqiBoard.RED ? 0xFFB71C1C : 0xFF263238);
                text.setTextSize(cell * 0.32f);
                canvas.drawText(label, x, y + cell * 0.113f, text);
            }
        }
    }

    private void drawCellRing(Canvas canvas, int p, int color) {
        mark.setColor(color);
        mark.setStrokeWidth(Math.max(2f, cell * 0.05f));
        canvas.drawCircle(cx(p % cols), cy(p / cols), cell * 0.47f, mark);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!inputEnabled || board == null || tapListener == null || e.getAction() != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(e);
        }
        geometry(getWidth(), getHeight());
        if (cell <= 0f) {
            return super.onTouchEvent(e);
        }
        float x = e.getX(), y = e.getY();
        if (x < originX || y < originY || x >= originX + cell * cols || y >= originY + cell * rows) {
            return super.onTouchEvent(e);
        }
        int col = (int) ((x - originX) / cell);
        int row = (int) ((y - originY) / cell);
        if (col < 0 || col >= cols || row < 0 || row >= rows) {
            return super.onTouchEvent(e);
        }
        tapListener.onTap(row, col);
        performClick();
        return true;
    }
}
