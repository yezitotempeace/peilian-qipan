package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 军棋棋盘：5 列 × 12 行共 60 个点位（兵站 / 行营 / 大本营），棋子是横向长方块，站在点位上。
 *
 * <p>比例按实体棋盘来：列距 250、行距 150、棋子站 100×50（单位比例），
 * 整盘约 1200×2100（1:1.75）；铁路是黑白相间的粗线，从棋子站后面穿过，只从缝里露出来；
 * 中线第 2/4 列是山界，第 3 列有一小段前线铁路。</p>
 */
public class JunqiView extends View {

    public interface OnCellTap {
        void onTap(int row, int col);
    }

    private JunqiBoard board;
    private boolean[] hidden;
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
    private final Paint road = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint railBase = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint railDash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stationFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stationLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hqFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pieceLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mark = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint camp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mountain = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cw, ch, ox, oy;
    private float stationW, stationH;
    private static final int cols = JunqiBoard.COLS;
    private static final int rows = JunqiBoard.ROWS;
    private static final float BOARD_W_UNITS = 4.8f;   // 4 个列距 + 左右各 0.4
    private static final float BOARD_H_UNITS = 8.4f;   // 11 个行距 + 上下各 1.5

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
        road.setStyle(Paint.Style.STROKE);
        road.setColor(0x883B3B3B);
        railBase.setStyle(Paint.Style.STROKE);
        railBase.setColor(0xFF161616);
        railDash.setStyle(Paint.Style.STROKE);
        railDash.setColor(0xFFFFFFFF);
        stationFill.setStyle(Paint.Style.FILL);
        stationFill.setColor(0xFFFFFDF8);
        stationLine.setStyle(Paint.Style.STROKE);
        stationLine.setColor(0xFF2B2B2B);
        hqFill.setStyle(Paint.Style.FILL);
        hqFill.setColor(0xFF1B1B1B);
        text.setColor(0xFF222222);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        face.setStyle(Paint.Style.FILL);
        pieceLine.setStyle(Paint.Style.STROKE);
        mark.setStyle(Paint.Style.STROKE);
        dot.setStyle(Paint.Style.FILL);
        camp.setStyle(Paint.Style.FILL);
        camp.setColor(0xFFFFFFFF);
        mountain.setStyle(Paint.Style.FILL);
        mountain.setColor(0xFFFFFDF8);
    }

    public void setBoard(JunqiBoard b) {
        board = b;
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
        if (cw > 0f && Math.abs(w - lastW) < 1 && Math.abs(h - lastH) < 1) {
            return;
        }
        lastW = w;
        lastH = h;
        float unit = Math.min(w / BOARD_W_UNITS, h / BOARD_H_UNITS);
        cw = unit;
        ch = unit * 0.6f;
        ox = (w - 4.8f * cw) / 2f + 0.4f * cw;
        oy = (h - 8.4f * cw) / 2f + 0.5f * ch;   // 上边距半行；中线另有 2 行空带
        stationW = cw * 0.40f;
        stationH = ch / 3f;
    }

    private int lastW = -1, lastH = -1;

    private float cx(int col) { return ox + col * cw; }

    /** 实体棋谱：前 6 行正常，中线上方空 2 行，后 6 行接上（第 6 行中心 +2ch）。 */
    private float cy(int row) { return oy + row * ch + (row >= 6 ? 2f * ch : 0f); }

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
        road.setStrokeWidth(Math.max(1f, cw * 0.012f));
        stationLine.setStrokeWidth(Math.max(1.2f, cw * 0.016f));

        drawRoads(canvas);
        drawRails(canvas);
        drawMountains(canvas);
        drawStations(canvas);
        drawPieces(canvas);
        drawMarks(canvas);

        BoardArt.boardEdge(canvas, 0, 0, w, h);
    }

    private void drawRoads(Canvas canvas) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols - 1; c++) {
                canvas.drawLine(cx(c), cy(r), cx(c + 1), cy(r), road);
            }
        }
        for (int c = 0; c < cols; c++) {
            for (int r = 0; r < rows - 1; r++) {
                // 中线第 2/4 列是山界，不通
                if (r == 5 && (c == 1 || c == 3)) {
                    continue;
                }
                canvas.drawLine(cx(c), cy(r), cx(c), cy(r + 1), road);
            }
        }
        // 行营八个方向：四角斜线（横竖已由上面的公路给出）
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (!JunqiBoard.isCamp(r, c)) {
                    continue;
                }
                for (int dr = -1; dr <= 1; dr += 2) {
                    for (int dc = -1; dc <= 1; dc += 2) {
                        int rr = r + dr, cc = c + dc;
                        if (rr >= 0 && rr < rows && cc >= 0 && cc < cols) {
                            canvas.drawLine(cx(c), cy(r), cx(cc), cy(rr), road);
                        }
                    }
                }
            }
        }
    }

    private void drawRails(Canvas canvas) {
        float rw = Math.max(4f, cw * 0.08f);
        railBase.setStrokeWidth(rw);
        railDash.setStrokeWidth(rw * 0.52f);
        for (int r : new int[]{1, 5, 6, 10}) {
            railH(canvas, r);
        }
        railV(canvas, 0, 1, 10);
        railV(canvas, 4, 1, 10);
        railV(canvas, 2, 5, 6);   // 中线第 3 列的一小段前线铁路
    }

    private void railH(Canvas canvas, int row) {
        float x1 = cx(0) - stationW * 0.5f, x2 = cx(cols - 1) + stationW * 0.5f;
        float y = cy(row);
        canvas.drawLine(x1, y, x2, y, railBase);
        float dash = Math.max(5f, cw * 0.075f);
        for (float x = x1; x < x2; x += dash * 2f) {
            canvas.drawLine(x, y, Math.min(x + dash, x2), y, railDash);
        }
    }

    private void railV(Canvas canvas, int col, int r1, int r2) {
        float y1 = cy(r1) - stationH * 0.5f, y2 = cy(r2) + stationH * 0.5f;
        float x = cx(col);
        canvas.drawLine(x, y1, x, y2, railBase);
        float dash = Math.max(5f, cw * 0.075f);
        for (float y = y1; y < y2; y += dash * 2f) {
            canvas.drawLine(x, y, x, Math.min(y + dash, y2), railDash);
        }
    }

    private void drawStations(Canvas canvas) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                float x = cx(c), y = cy(r);
                boolean campCell = JunqiBoard.isCamp(r, c);
                boolean hqCell = JunqiBoard.isHq(r, c);
                boolean occupied = board.ownerAt(r, c) != JunqiBoard.EMPTY;
                if (campCell) {
                    canvas.drawCircle(x, y, stationW * 0.5f, camp);
                    canvas.drawCircle(x, y, stationW * 0.5f, stationLine);
                    if (!occupied) {
                        drawCellText(canvas, "行营", x, y, stationH * 0.52f, 0xFF2B2B2B);
                    }
                } else if (hqCell) {
                    float hw = stationW * 1.5f;   // 实体大本营 150×50，比兵站宽一半
                    RectF rf = new RectF(x - hw / 2f, y - stationH / 2f, x + hw / 2f, y + stationH / 2f);
                    canvas.drawRoundRect(rf, stationH * 0.25f, stationH * 0.25f, hqFill);
                    if (!occupied) {
                        drawCellText(canvas, "大本营", x, y, stationH * 0.42f, 0xFFFFFFFF);
                    }
                } else {
                    RectF rf = new RectF(x - stationW / 2f, y - stationH / 2f, x + stationW / 2f, y + stationH / 2f);
                    canvas.drawRoundRect(rf, stationH * 0.25f, stationH * 0.25f, stationFill);
                    canvas.drawRoundRect(rf, stationH * 0.25f, stationH * 0.25f, stationLine);
                    if (!occupied) {
                        drawCellText(canvas, "兵站", x, y, stationH * 0.52f, 0xFF2B2B2B);
                    }
                }
            }
        }
    }

    private void drawCellText(Canvas canvas, String s, float x, float y, float size, int color) {
        text.setColor(color);
        text.setTextSize(size);
        canvas.drawText(s, x, y + size * 0.36f, text);
    }

    private void drawPieces(Canvas canvas) {
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
        if (animPoint >= 0) {
            int tr = animTo / cols, tc = animTo % cols;
            int mover = JunqiBoard.opponent(board.sideToMove());
            if (board.ownerAt(tr, tc) == mover) {
                float e = AnimMath.easeOut(animP);
                float x = AnimMath.lerp(cx(animFrom % cols), cx(tc), e);
                float y = AnimMath.lerp(cy(animFrom / cols), cy(tr), e);
                boolean back = hidden != null && animTo < hidden.length && hidden[animTo];
                drawPiece(canvas, x, y, mover, board.at(tr, tc), back);
            }
            float fa = 0.55f * (1f - animP);
            mark.setColor(((int) (fa * 255) << 24) | 0x00FFB300);
            mark.setStrokeWidth(Math.max(2f, cw * 0.04f));
            canvas.drawCircle(cx(tc), cy(tr), stationW * (0.55f + 0.35f * AnimMath.easeOut(animP)), mark);
            postInvalidateOnAnimation();
        }
    }

    private void drawPiece(Canvas canvas, float x, float y, int color, int type, boolean back) {
        float pw = stationW * 0.94f, ph = stationH * 0.94f;
        float radius = ph * 0.26f;
        pieceLine.setStrokeWidth(Math.max(1.5f, cw * 0.018f));
        pieceLine.setColor(color == JunqiBoard.RED ? 0xFFB71C1C : 0xFF263238);
        face.setStyle(Paint.Style.FILL);
        face.setColor(pieceLine.getColor());
        RectF outer = new RectF(x - pw / 2f, y - ph / 2f, x + pw / 2f, y + ph / 2f);
        canvas.drawRoundRect(outer, radius, radius, face);
        face.setColor(back ? 0xFFB0BEC5
                : (color == JunqiBoard.RED ? 0xFFFFE3E3 : 0xFFE6ECEF));
        RectF inner = new RectF(x - pw * 0.45f, y - ph * 0.42f, x + pw * 0.45f, y + ph * 0.42f);
        canvas.drawRoundRect(inner, radius * 0.7f, radius * 0.7f, face);
        if (back) {
            face.setColor(0xFF546E7A);
            canvas.drawCircle(x, y, ph * 0.22f, face);
        } else {
            String label = JunqiBoard.typeName(type);
            if (label != null && label.length() > 0) {
                text.setColor(color == JunqiBoard.RED ? 0xFFB71C1C : 0xFF263238);
                text.setTextSize(ph * 0.64f);
                canvas.drawText(label, x, y + ph * 0.23f, text);
            }
        }
    }

    private void drawMountains(Canvas canvas) {
        float y = (cy(5) + cy(6)) / 2f;
        for (int c : new int[]{1, 3}) {
            float x = cx(c);
            canvas.drawCircle(x, y, cw * 0.30f, mountain);
            canvas.drawCircle(x, y, cw * 0.30f, stationLine);
            drawCellText(canvas, "山界", x, y, stationH * 0.50f, 0xFF2B2B2B);
        }
        // 中线第 1/3/5 列是前线；第 2/4 列是山界
        for (int c : new int[]{0, 2, 4}) {
            drawCellText(canvas, "前线", cx(c), y, stationH * 0.46f, 0xFF2B2B2B);
        }
    }

    private void drawMarks(Canvas canvas) {
        if (lastFrom >= 0) {
            drawStationRing(canvas, lastFrom, 0xFFF9A825);
            drawStationRing(canvas, lastTo, 0xFFF9A825);
        }
        if (selected >= 0) {
            drawStationRing(canvas, selected, 0xFF1A73E8);
        }
        if (legal != null) {
            for (int p : legal) {
                canvas.drawCircle(cx(p % cols), cy(p / cols), stationW * 0.13f, dot);
            }
        }
    }

    private void drawStationRing(Canvas canvas, int p, int color) {
        int r = p / cols, c = p % cols;
        mark.setColor(color);
        mark.setStrokeWidth(Math.max(2f, cw * 0.035f));
        RectF rf = new RectF(cx(c) - stationW * 0.62f, cy(r) - stationH * 0.72f,
                cx(c) + stationW * 0.62f, cy(r) + stationH * 0.72f);
        canvas.drawRoundRect(rf, stationH * 0.3f, stationH * 0.3f, mark);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!inputEnabled || board == null || tapListener == null || e.getAction() != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(e);
        }
        geometry(getWidth(), getHeight());
        if (cw <= 0f) {
            return super.onTouchEvent(e);
        }
        int col = Math.round((e.getX() - ox) / cw);
        if (col < 0 || col >= cols) {
            return super.onTouchEvent(e);
        }
        // 中线空 2 行，纵坐标不能按 row*ch 反算；扫 12 行取最近的点位
        int row = -1;
        float best = Float.MAX_VALUE;
        for (int r = 0; r < rows; r++) {
            float d = Math.abs(e.getY() - cy(r));
            if (d < best) {
                best = d;
                row = r;
            }
        }
        if (row < 0 || best > ch * 0.48f) {
            return super.onTouchEvent(e);
        }
        if (Math.abs(e.getX() - cx(col)) > cw * 0.48f) {
            return super.onTouchEvent(e);
        }
        tapListener.onTap(row, col);
        performClick();
        return true;
    }
}
