/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
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
    private long animDuration = AnimMath.MOVE_MS;
    /** 一条翻牌动画：起点 long 时间戳（float 存毫秒会丢精度，动画会瞬间结束或卡住）。 */
    private static final class FlipAnim {
        final int row, col, color, type;
        final long startMs;
        final boolean stay;
        FlipAnim(int row, int col, int color, int type, long startMs, boolean stay) {
            this.row = row; this.col = col; this.color = color; this.type = type;
            this.startMs = startMs; this.stay = stay;
        }
    }

    private final java.util.ArrayList<FlipAnim> flips = new java.util.ArrayList<FlipAnim>();

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
    /** 可复用的临时 RectF，避免 onDraw 每帧分配。 */
    private final RectF tmpR1 = new RectF();
    private final RectF tmpR2 = new RectF();
    private final RectF tmpR3 = new RectF();
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
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
        shadow.setStyle(Paint.Style.FILL);
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
        // 翻牌（起点=终点）不是位移：不播滑动动画。
        // 之前这里会照样起动画，导致动画期间该格被 drawPieces 跳过，
        // 看起来就像「棋子翻开后消失了一下」——尤其是翻到对方的子时。
        if (animationsEnabled && fromRow >= 0 && toRow >= 0 && nf != nt) {
            animFrom = lastFrom;
            animTo = lastTo;
            // 时长分层：短步快、铁路长滑行慢、吃子再久一点 —— 每一手都看得清
            int cheb = Math.max(Math.abs(toRow - fromRow), Math.abs(toCol - fromCol));
            animDuration = 320L + 70L * cheb;
            if (board != null && board.lastWasFight()) animDuration += 150L;
            animDuration = Math.min(animDuration, 720L);
            animStart = System.currentTimeMillis();
            animActive = true;
        } else {
            animActive = false;
        }
        invalidate();
    }

    /** 吃子/被吃后，把阵亡的棋子「背面→正面」翻过来给人看，然后缩小消失。 */
    public void revealFlip(int row, int col, int color, int type) {
        flips.add(new FlipAnim(row, col, color, type, System.currentTimeMillis(), false));
        invalidate();
    }

    /**
     * 翻棋模式下「翻开棋子」的翻牌动画：扣着的棋子翻到正面后**停住显示**（不缩小消失）。
     * 动画期间 drawPieces 会跳过该格，只画这个翻牌，翻完再由 drawPieces 画出正面。
     */
    public void flipToReveal(int row, int col, int color, int type) {
        // 同一格若已在翻，先去掉旧的，避免重复叠画
        int idx = idxOfFlip(row, col);
        if (idx >= 0) {
            flips.remove(idx);
        }
        flips.add(new FlipAnim(row, col, color, type, System.currentTimeMillis(), true));
        invalidate();
    }

    private int idxOfFlip(int row, int col) {
        for (int i = 0; i < flips.size(); i++) {
            FlipAnim f = flips.get(i);
            if (f.row == row && f.col == col) {
                return i;
            }
        }
        return -1;
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
        // 棋子占格宽 85%、格高 75%，让整盘看起来满而不挤（正规军棋比例）
        stationW = cw * 0.85f;
        stationH = ch * 0.75f;
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
                    float hw = stationW * 1.15f;   // 大本营略宽于普通棋子
                    tmpR1.set(x - hw / 2f, y - stationH / 2f, x + hw / 2f, y + stationH / 2f);
                    canvas.drawRoundRect(tmpR1, stationH * 0.25f, stationH * 0.25f, hqFill);
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
            animP = AnimMath.progress(animStart, System.currentTimeMillis(), animDuration);
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
                if (p == animPoint && animFrom != animTo) {
                    continue;   // 只有真正的移动才跳过终点格；翻牌（起=终）永远照常画
                }
                // 正在播翻牌动画的格子跳过：只由 drawFlip 画，避免正面与翻牌同时出现
                if (idxOfFlip(r, c) >= 0) {
                    continue;
                }
                boolean back = board.faceDown(r, c);
                drawPiece(canvas, cx(c), cy(r), color, board.typeAt(r, c), back);
                JunqiBoard.Pc pc = board.at(r, c);
                if (pc != null && pc.side == JunqiBoard.BLACK && pc.moved && !pc.revealed) {
                    // 已移动过的敌子 ⇒ 一定不是地雷/军旗：画个小点帮玩家推断
                    mark.setStyle(Paint.Style.FILL);
                    mark.setColor(0xCC1A73E8);
                    canvas.drawCircle(cx(c) + stationW * 0.30f, cy(r) - stationH * 0.34f,
                            Math.max(2.5f, cw * 0.045f), mark);
                }
            }
        }
        if (animPoint >= 0) {
            int tr = animTo / cols, tc = animTo % cols;
            JunqiBoard.Pc mv = board.at(tr, tc);
            if (mv != null) {
                float e = AnimMath.easeOut(animP);
                float x = AnimMath.lerp(cx(animFrom % cols), cx(tc), e);
                float gy = AnimMath.lerp(cy(animFrom / cols), cy(tr), e);
                // 微跳跃：棋子沿抛物线抬起，影子留在地面并变淡
                float hopFrac = (float) Math.sin(Math.PI * e);
                float hop = hopFrac * ch * 0.55f;
                shadow.setColor(((int) (0x50 * (1f - 0.65f * hopFrac)) << 24));
                tmpR3.set(x - stationW * 0.40f, gy + stationH * 0.28f,
                        x + stationW * 0.40f, gy + stationH * 0.50f);
                canvas.drawOval(tmpR3, shadow);
                boolean back = board.faceDown(tr, tc);
                drawPiece(canvas, x, gy - hop, mv.side, mv.type, back,
                        1f + 0.07f * hopFrac);
            }
            // 落点冲击环：让「这一手落在哪」看得清
            float fa = 0.55f * (1f - animP);
            mark.setColor(((int) (fa * 255) << 24) | 0x00FFB300);
            mark.setStrokeWidth(Math.max(2f, cw * 0.04f));
            canvas.drawCircle(cx(tc), cy(tr), stationW * (0.55f + 0.35f * AnimMath.easeOut(animP)), mark);
            // 吃子（交战）再叠一个红色爆点，让「吃掉对方」这一下看得见
            if (board.lastWasFight()) {
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
            FlipAnim f = flips.get(i);
            float p = AnimMath.progress(f.startMs, now, AnimMath.FLIP_MS);
            if (p >= 1f) {
                flips.remove(i);
            } else {
                drawFlipPiece(canvas, f.row, f.col, f.color, f.type, p, f.stay);
            }
        }
        postInvalidateOnAnimation();   // 继续下一帧；全部翻完后再刷一帧清掉残影
    }

    private void drawFlipPiece(Canvas canvas, int row, int col, int color, int type, float p, boolean stay) {
        float ang = p * (float) Math.PI;              // 0..π：先压扁背面，再展开正面
        float sx = Math.abs((float) Math.cos(ang));   // 宽度 1→0→1
        boolean faceUp = p >= 0.5f;
        // 「停住」模式翻完不缩；「阵亡」模式收尾缩小到消失
        float shrink = (!stay && p > 0.72f) ? Math.max(0f, 1f - (p - 0.72f) / 0.28f) : 1f;
        float pw = stationW * 0.94f * shrink, ph = stationH * 0.94f * shrink;
        float w = pw * sx;
        float radius = ph * 0.26f * sx;
        float x = cx(col), y = cy(row);
        y -= (float) Math.sin(ang) * stationH * 0.18f;   // 翻的时候微微抬起
        boolean red = color == JunqiBoard.RED;
        int edge = dark ? (red ? 0xFF8A2A26 : 0xFF5D7A99) : 0xFF2E1F10;
        int body = dark ? (red ? 0xFFD64541 : 0xFF2C3E50) : (red ? 0xFFB5483A : 0xFF4A3B2C);
        int backc = dark ? 0xFF546E7A : 0xFF9E8F72;
        int backdot = dark ? 0xFF7A8FA3 : 0xFF6B5740;
        int txt = dark ? 0xFFFFFFFF : (red ? 0xFF7A2A20 : 0xFF2E1F10);
        face.setStyle(Paint.Style.FILL);
        face.setColor(edge);
        tmpR1.set(x - w / 2f, y - ph / 2f, x + w / 2f, y + ph / 2f);
        canvas.drawRoundRect(tmpR1, radius, radius, face);
        face.setColor(faceUp ? body : backc);
        tmpR2.set(x - w * 0.45f, y - ph * 0.42f, x + w * 0.45f, y + ph * 0.42f);
        canvas.drawRoundRect(tmpR2, radius * 0.7f, radius * 0.7f, face);
        if (faceUp) {
            String label = JunqiBoard.typeName(type);
            if (label != null && label.length() > 0) {
                text.setColor(txt);
                text.setTextSize(ph * 0.72f);
                canvas.drawText(label, x, y + ph * 0.23f, text);
            }
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
        float radius = ph * 0.24f;
        boolean red = color == JunqiBoard.RED;

        // 柔和配色：棋身用米色 / 深木色，红蓝只出现在文字与一圈细边线上，不再整块大红大蓝
        int faceTop, faceBot, edge, txtCol, dotCol;
        if (dark) {
            faceTop = 0xFF6E5C44; faceBot = 0xFF453827;
            edge    = 0xFF2B2318;
            txtCol  = red ? 0xFFF0B4A8 : 0xFFA9C6E2;
            dotCol  = 0xFF8FA3B8;
        } else {
            faceTop = 0xFFFCF3DE; faceBot = 0xFFE4CFA4;
            edge    = 0xFFB0986F;
            txtCol  = red ? 0xFFA93A2E : 0xFF2F5C8A;
            dotCol  = 0xFF9C8B6A;
        }
        if (back) {
            faceTop = dark ? 0xFF57636E : 0xFFDAC89F;
            faceBot = dark ? 0xFF3D4954 : 0xFFC2AC83;
            dotCol  = dark ? 0xFF8FA3B8 : 0xFF9C8B6A;
        }

        // 投影：棋子压在盘面上的一圈软阴影（立体感）
        shadow.setStyle(Paint.Style.FILL);
        shadow.setColor(0x2E000000);
        tmpR3.set(x - pw * 0.50f, y - ph * 0.30f + ph * 0.14f,
                  x + pw * 0.50f, y + ph * 0.62f + ph * 0.14f);
        canvas.drawRoundRect(tmpR3, radius, radius, shadow);

        // 侧面（深一档）：做出厚度
        face.setStyle(Paint.Style.FILL);
        face.setColor(edge);
        tmpR1.set(x - pw / 2f, y - ph / 2f + ph * 0.07f,
                  x + pw / 2f, y + ph / 2f + ph * 0.07f);
        canvas.drawRoundRect(tmpR1, radius, radius, face);

        // 正面：上浅下深（两段叠出渐变，立体感；不依赖 Shader，桩测环境也能画）
        face.setStyle(Paint.Style.FILL);
        face.setColor(faceBot);
        tmpR1.set(x - pw / 2f, y - ph / 2f, x + pw / 2f, y + ph / 2f);
        canvas.drawRoundRect(tmpR1, radius, radius, face);
        face.setColor(faceTop);
        tmpR1.set(x - pw * 0.46f, y - ph * 0.46f, x + pw * 0.46f, y + ph * 0.14f);
        canvas.drawRoundRect(tmpR1, radius * 0.85f, radius * 0.85f, face);

        // 顶部受光高光 + 底部收暗
        pieceLine.setStrokeWidth(Math.max(1f, ph * 0.05f));
        pieceLine.setColor(0x59FFFFFF);
        tmpR2.set(x - pw * 0.38f, y - ph * 0.38f, x + pw * 0.38f, y + ph * 0.06f);
        canvas.drawRoundRect(tmpR2, radius * 0.8f, radius * 0.8f, pieceLine);
        pieceLine.setColor(0x24000000);
        tmpR2.set(x - pw * 0.38f, y + ph * 0.08f, x + pw * 0.38f, y + ph * 0.38f);
        canvas.drawRoundRect(tmpR2, radius * 0.8f, radius * 0.8f, pieceLine);

        // 细边线：红 / 蓝只在这里轻轻一圈
        pieceLine.setStrokeWidth(Math.max(1f, ph * 0.035f));
        pieceLine.setColor(edge);
        tmpR2.set(x - pw * 0.46f, y - ph * 0.46f, x + pw * 0.46f, y + ph * 0.46f);
        canvas.drawRoundRect(tmpR2, radius, radius, pieceLine);

        // 文字 / 背面圆点
        if (back) {
            face.setStyle(Paint.Style.FILL);
            face.setColor(dotCol);
            canvas.drawCircle(x, y, ph * 0.18f, face);
        } else {
            text.setColor(txtCol);
            text.setTextSize(ph * 0.60f);
            canvas.drawText(JunqiBoard.typeName(type), x, y + ph * 0.21f, text);
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
        tmpR1.set(cx(c) - stationW * 0.62f, cy(r) - stationH * 0.72f,
                cx(c) + stationW * 0.62f, cy(r) + stationH * 0.72f);
        canvas.drawRoundRect(tmpR1, stationH * 0.3f, stationH * 0.3f, mark);
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
