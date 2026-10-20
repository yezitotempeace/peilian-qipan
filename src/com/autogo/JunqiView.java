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
    /** 正在翻转显露的阵亡棋子：{row, col, color, type, startMs}。 */
    private final java.util.ArrayList<float[]> flips = new java.util.ArrayList<float[]>();

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

    private boolean dark;
    private int ink = 0xFF3A2410;
    private int inkHq = 0xFF3A2410;

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
        bg.setStyle(Paint.Style.FILL);
        road.setStyle(Paint.Style.STROKE);
        railBase.setStyle(Paint.Style.STROKE);
        railDash.setStyle(Paint.Style.STROKE);
        stationFill.setStyle(Paint.Style.FILL);
        stationLine.setStyle(Paint.Style.STROKE);
        hqFill.setStyle(Paint.Style.STROKE);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        face.setStyle(Paint.Style.FILL);
        pieceLine.setStyle(Paint.Style.STROKE);
        mark.setStyle(Paint.Style.STROKE);
        dot.setStyle(Paint.Style.FILL);
        camp.setStyle(Paint.Style.FILL);
        mountain.setStyle(Paint.Style.FILL);
        applyPalette();
    }

    /** 深色 = 手机军棋 App 风（深底 + 彩色圆角子）；浅色 = 经典木质（木色 + 木纹子）。 */
    public void setDark(boolean dark) {
        if (this.dark != dark) {
            this.dark = dark;
            applyPalette();
            invalidate();
        }
    }

    private void applyPalette() {
        if (dark) {
            bg.setColor(0xFF1B2230);
            road.setColor(0xFF3A4A63);
            railBase.setColor(0xFF14181F);
            railDash.setColor(0xFFD8E0EC);
            stationFill.setColor(0xFF242E40);
            stationLine.setColor(0xFF7A90B0);
            hqFill.setColor(0xFFE6B84C);
            text.setColor(0xFF9FB6D4);
            camp.setColor(0xFF242E40);
            mountain.setColor(0xFF242E40);
            ink = 0xFF9FB6D4;
            inkHq = 0xFFE6B84C;
        } else {
            bg.setColor(0xFF9C6B3C);
            road.setColor(0xFF4E2F18);
            railBase.setColor(0xFF161616);
            railDash.setColor(0xFFFFFFFF);
            stationFill.setColor(0xFF9C6B3C);
            stationLine.setColor(0xFF4E2F18);
            hqFill.setColor(0xFF4E2F18);
            text.setColor(0xFF3A2410);
            camp.setColor(0xFF9C6B3C);
            mountain.setColor(0xFF9C6B3C);
            ink = 0xFF3A2410;
            inkHq = 0xFF3A2410;
        }
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

    /** 吃子/被吃后，把阵亡的棋子「背面→正面」翻过来给人看，然后缩小消失。 */
    public void revealFlip(int row, int col, int color, int type) {
        flips.add(new float[]{row, col, color, type, System.currentTimeMillis()});
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
        drawFlip(canvas);
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
                        drawCellText(canvas, "行营", x, y, stationH * 0.56f, ink);
                    }
                } else if (hqCell) {
                    float hw = stationW * 1.5f;   // 大本营比兵站宽一半，只画描边框
                    RectF rf = new RectF(x - hw / 2f, y - stationH / 2f, x + hw / 2f, y + stationH / 2f);
                    canvas.drawRoundRect(rf, stationH * 0.25f, stationH * 0.25f, hqFill);
                    if (!occupied) {
                        drawCellText(canvas, "大本营", x, y, stationH * 0.42f, inkHq);
                    }
                }
                // 普通兵站不再画小方块：网格线已经给出棋盘，空站点留白，
                // 棋子直接站在交叉点上，整体更像实体/手机军棋。
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
                // 落子带缩放：从小到大落下，滑动不再是纯位移
                drawPiece(canvas, x, y, mover, board.at(tr, tc), back, 0.72f + 0.28f * e);
            }
            float fa = 0.55f * (1f - animP);
            mark.setColor(((int) (fa * 255) << 24) | 0x00FFB300);
            mark.setStrokeWidth(Math.max(2f, cw * 0.04f));
            canvas.drawCircle(cx(tc), cy(tr), stationW * (0.55f + 0.35f * AnimMath.easeOut(animP)), mark);
            // 吃子（交战）再叠一个红色爆点，让「吃掉对方」这一下看得见
            if (board.lastDefType() != JunqiBoard.EMPTY) {
                float bp = AnimMath.easeOut(animP);
                int ba = (int) (0.55f * (1f - animP) * 255);
                mark.setColor((ba << 24) | 0x00FF5A3C);
                mark.setStrokeWidth(Math.max(3f, cw * 0.06f));
                canvas.drawCircle(cx(tc), cy(tr), stationW * (0.5f + 1.0f * bp), mark);
            }
            postInvalidateOnAnimation();
        }
    }

    private void drawFlip(Canvas canvas) {
        if (flips.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (int i = flips.size() - 1; i >= 0; i--) {
            float[] f = flips.get(i);
            float p = AnimMath.progress((long) f[4], now, AnimMath.FLIP_MS);
            if (p >= 1f) {
                flips.remove(i);
            } else {
                drawFlipPiece(canvas, (int) f[0], (int) f[1], (int) f[2], (int) f[3], p);
            }
        }
        postInvalidateOnAnimation();   // 继续下一帧；全部翻完后再刷一帧清掉残影
    }

    private void drawFlipPiece(Canvas canvas, int row, int col, int color, int type, float p) {
        float ang = p * (float) Math.PI;              // 0..π：先压扁背面，再展开正面
        float sx = Math.abs((float) Math.cos(ang));   // 宽度 1→0→1
        boolean faceUp = p >= 0.5f;
        float shrink = p > 0.72f ? Math.max(0f, 1f - (p - 0.72f) / 0.28f) : 1f;  // 收尾缩小到消失
        float pw = stationW * 0.98f * shrink, ph = stationH * 0.98f * shrink;
        float w = pw * sx;
        float radius = ph * 0.26f * sx;
        float x = cx(col), y = cy(row);
        y -= (float) Math.sin(ang) * stationH * 0.6f;   // 翻的时候抬起来
        boolean red = color == JunqiBoard.RED;
        int edge = dark ? (red ? 0xFF8A2A26 : 0xFF5D7A99) : 0xFF2E1F10;
        int body = dark ? (red ? 0xFFD64541 : 0xFF2C3E50) : (red ? 0xFFB5483A : 0xFF4A3B2C);
        int backc = dark ? 0xFF546E7A : 0xFF9E8F72;
        int backdot = dark ? 0xFF7A8FA3 : 0xFF6B5740;
        int txt = dark ? 0xFFFFFFFF : (red ? 0xFF7A2A20 : 0xFF2E1F10);
        face.setStyle(Paint.Style.FILL);
        face.setColor(edge);
        RectF outer = new RectF(x - w / 2f, y - ph / 2f, x + w / 2f, y + ph / 2f);
        canvas.drawRoundRect(outer, radius, radius, face);
        face.setColor(faceUp ? body : backc);
        RectF inner = new RectF(x - w * 0.45f, y - ph * 0.42f, x + w * 0.45f, y + ph * 0.42f);
        canvas.drawRoundRect(inner, radius * 0.7f, radius * 0.7f, face);
        if (faceUp) {
            text.setColor(txt);
            text.setTextSize(ph * 0.72f);
            canvas.drawText(JunqiBoard.typeName(type), x, y + ph * 0.23f, text);
        } else {
            face.setColor(backdot);
            canvas.drawCircle(x, y, ph * 0.22f, face);
        }
    }

    private void drawPiece(Canvas canvas, float x, float y, int color, int type, boolean back) {
        drawPiece(canvas, x, y, color, type, back, 1f);
    }

    private void drawPiece(Canvas canvas, float x, float y, int color, int type, boolean back, float scl) {
        float pw = stationW * 0.94f * scl, ph = stationH * 0.94f * scl;
        float radius = ph * 0.26f;
        boolean red = color == JunqiBoard.RED;
        int edge = dark ? (red ? 0xFF8A2A26 : 0xFF5D7A99) : 0xFF2E1F10;
        int body = dark ? (red ? 0xFFD64541 : 0xFF2C3E50) : (red ? 0xFFB5483A : 0xFF4A3B2C);
        int inner = dark ? (red ? 0xFFF07E76 : 0xFF46627A) : (red ? 0xFFE8C9A8 : 0xFFD8C6A8);
        int txt = dark ? 0xFFFFFFFF : (red ? 0xFF7A2A20 : 0xFF2E1F10);
        pieceLine.setStrokeWidth(Math.max(1.5f, cw * 0.018f));
        pieceLine.setColor(edge);
        face.setStyle(Paint.Style.FILL);
        face.setColor(edge);
        RectF outer = new RectF(x - pw / 2f, y - ph / 2f, x + pw / 2f, y + ph / 2f);
        canvas.drawRoundRect(outer, radius, radius, face);
        face.setColor(back ? (dark ? 0xFF546E7A : 0xFF9E8F72) : body);
        RectF innerR = new RectF(x - pw * 0.45f, y - ph * 0.42f, x + pw * 0.45f, y + ph * 0.42f);
        canvas.drawRoundRect(innerR, radius * 0.7f, radius * 0.7f, face);
        if (back) {
            face.setColor(dark ? 0xFF7A8FA3 : 0xFF6B5740);
            canvas.drawCircle(x, y, ph * 0.22f, face);
        } else {
            String label = JunqiBoard.typeName(type);
            if (label != null && label.length() > 0) {
                text.setColor(txt);
                text.setTextSize(ph * 0.72f);
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
            drawCellText(canvas, "山界", x, y, stationH * 0.50f, ink);
        }
        // 中线第 1/3/5 列是前线；第 2/4 列是山界
        for (int c : new int[]{0, 2, 4}) {
            drawCellText(canvas, "前线", cx(c), y, stationH * 0.46f, ink);
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
