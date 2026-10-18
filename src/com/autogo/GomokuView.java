package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 五子棋棋盘绘制与触摸。
 *
 * 与围棋的 {@link BoardView} 是两份独立实现：提子、形势归属、幽灵子这些概念在五子棋里
 * 都无从谈起，硬塞开关只会把两边都弄复杂。保持一致的只有坐标系与几何口径 ——
 * idx = row * size + col（row = 0 在顶部）；交点屏幕位置 = (padX + col * spacing, padY + row * spacing)；
 * 几何走 {@link LayoutMath#boardGeometry(int, int, int, float)}，路数通用（15 路直接能用）。
 */
public class GomokuView extends View {

    public interface OnIntersectionTap {
        void onTap(int point);
    }

    private GomokuBoard board;
    private int lastMove = GomokuBoard.PASS;
    private int[] highlight = new int[0];   // 复盘用：推荐点
    private int[] winLine;                  // 获胜连线（终局/复盘高亮），null = 不画
    private boolean showNumbers = false;
    private boolean inputEnabled = true;
    private boolean fullscreen = false;
    private Runnable onExitFullscreen;
    private OnIntersectionTap tapListener;

    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint woodPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint whitePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stoneEdge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint winPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fsBtnBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fsBtnText = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 当前配色（黑夜/白天）。默认浅色，Activity 建好视图后会 setPalette。 */
    private Palette palette = Palette.of(false);

    private float padX, padY, spacing, stoneRadius;

    public GomokuView(Context context) {
        super(context);
        init();
    }

    public GomokuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        gridPaint.setStrokeWidth(1.6f);
        stoneEdge.setStyle(Paint.Style.STROKE);
        stoneEdge.setStrokeWidth(1.2f);

        markerPaint.setStyle(Paint.Style.STROKE);
        markerPaint.setStrokeWidth(3f);
        markerPaint.setColor(0xFFFF3B30);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        hintPaint.setStyle(Paint.Style.STROKE);
        hintPaint.setStrokeWidth(2.5f);
        hintPaint.setColor(0xFF1E88E5);
        winPaint.setStyle(Paint.Style.STROKE);
        winPaint.setColor(0xCCE53935);
        fsBtnBg.setColor(0xCC2B2B2B);
        fsBtnText.setColor(Color.WHITE);
        fsBtnText.setTextAlign(Paint.Align.CENTER);
        applyPaletteColors();
        setFocusable(true);
    }

    /** 换配色（黑夜/白天）。 */
    public void setPalette(Palette p) {
        if (p == null) return;
        palette = p;
        applyPaletteColors();
        invalidate();
    }

    private void applyPaletteColors() {
        woodPaint.setColor(palette.boardWood);
        gridPaint.setColor(palette.boardLine);
        labelPaint.setColor(palette.boardLabel);
        stoneEdge.setColor(palette.boardStoneEdge);
    }

    public void setBoard(GomokuBoard b) {
        // 路数变了必须让几何重算：视图的像素边长由屏幕决定、与路数无关，
        // 所以 15 路换成别的路数时 onSizeChanged 不会触发（围棋 BoardView 踩过这个坑）。
        if (board == null || b == null || b.size != board.size) spacing = 0;
        board = b;
        invalidate();
    }

    public void setLastMove(int point) {
        lastMove = point;
        invalidate();
    }

    public void setHighlight(int[] points) {
        highlight = points == null ? new int[0] : points;
        invalidate();
    }

    public void setWinLine(int[] points) {
        winLine = points;
        invalidate();
    }

    public void setShowNumbers(boolean show) {
        showNumbers = show;
        invalidate();
    }

    public boolean getShowNumbers() {
        return showNumbers;
    }

    public void setFullscreen(boolean fs) {
        fullscreen = fs;
        invalidate();
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setOnExitFullscreen(Runnable r) {
        onExitFullscreen = r;
    }

    public void setInputEnabled(boolean enabled) {
        inputEnabled = enabled;
    }

    public void setOnIntersectionTap(OnIntersectionTap l) {
        tapListener = l;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        if (fullscreen) {
            setMeasuredDimension(w, h);
        } else {
            int s = Math.min(w, h);
            setMeasuredDimension(s, s);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        recompute(w, h);
    }

    private void recompute(int w, int h) {
        int n = board == null ? 15 : board.size;
        // 以较短边为基准；全屏时左上角有返回按钮，顶部预留它的高度
        float topInset = fullscreen ? (fullscreenButtonRect().height() + dp(10)) : 0f;
        LayoutMath.BoardGeometry g = LayoutMath.boardGeometry(w, h, n, topInset);
        padX = g.padX;
        padY = g.padY;
        spacing = g.spacing;
        stoneRadius = g.stoneRadius;
        float side = Math.min(w, Math.max(1f, h - topInset));
        float labelRoom = Math.max(18f, side * 0.045f);
        gridPaint.setStrokeWidth(Math.max(1f, spacing * 0.045f));
        textPaint.setTextSize(spacing * 0.52f);
        labelPaint.setTextSize(Math.max(10f, labelRoom * 0.62f));
        winPaint.setStrokeWidth(Math.max(2f, spacing * 0.14f));
        rebuildStoneShaders();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (board == null) return;
        final int n = board.size;
        if (spacing <= 0) recompute(getWidth(), getHeight());

        RectF full = new RectF(0, 0, getWidth(), getHeight());
        canvas.drawRect(full, woodPaint);
        BoardArt.boardEdge(canvas, 0f, 0f, getWidth(), getHeight());

        float left = padX, top = padY;
        float right = padX + (n - 1) * spacing, bottom = padY + (n - 1) * spacing;

        // 网格
        for (int i = 0; i < n; i++) {
            float y = top + i * spacing;
            float x = left + i * spacing;
            canvas.drawLine(left, y, right, y, gridPaint);
            canvas.drawLine(x, top, x, bottom, gridPaint);
        }

        // 星位：15 路通常只在 (3,3) 四个角点 + 天元做小圆点
        for (int r : starPoints(n)) {
            for (int c : starPoints(n)) {
                canvas.drawCircle(left + c * spacing, top + r * spacing, Math.max(2f, spacing * 0.075f), gridPaint);
            }
        }

        // 坐标（列 A..，行从下往上数）
        for (int i = 0; i < n; i++) {
            canvas.drawText(String.valueOf(GomokuBoard.GTP_COLS.charAt(i)), left + i * spacing, top - spacing * 0.28f, labelPaint);
            canvas.drawText(String.valueOf(n - i), left - spacing * 0.62f, top + i * spacing + labelPaint.getTextSize() * 0.35f, labelPaint);
        }

        // 推荐点（复盘用）
        for (int p : highlight) {
            if (p < 0 || p >= n * n) continue;
            float cx = left + (p % n) * spacing, cy = top + (p / n) * spacing;
            canvas.drawCircle(cx, cy, stoneRadius * 0.92f, hintPaint);
        }

        // 棋子
        for (int i = 0; i < n * n; i++) {
            int v = board.grid[i];
            if (v == GomokuBoard.EMPTY) continue;
            float cx = left + (i % n) * spacing, cy = top + (i / n) * spacing;
            drawStone(canvas, cx, cy, v);
        }

        // 获胜连线：穿过这一串子的实线，比只圈最后一手显眼
        if (winLine != null && winLine.length >= 2) {
            int p0 = winLine[0], p1 = winLine[winLine.length - 1];
            canvas.drawLine(left + (p0 % n) * spacing, top + (p0 / n) * spacing,
                    left + (p1 % n) * spacing, top + (p1 / n) * spacing, winPaint);
        }

        // 最后一手标记
        if (lastMove != GomokuBoard.PASS && lastMove >= 0 && lastMove < n * n) {
            float cx = left + (lastMove % n) * spacing, cy = top + (lastMove / n) * spacing;
            markerPaint.setColor(0xFFFF3B30);
            canvas.drawCircle(cx, cy, stoneRadius * 0.34f, markerPaint);
        }

        if (fullscreen) drawFullscreenButton(canvas);

        // 手顺
        if (showNumbers) {
            int total = board.moves.size();
            for (int i = 0; i < total; i++) {
                int[] m = board.moves.get(i);
                if (m[1] == GomokuBoard.PASS) continue;
                int point = m[1];
                float cx = left + (point % n) * spacing, cy = top + (point / n) * spacing;
                textPaint.setColor(m[0] == GomokuBoard.BLACK ? Color.WHITE : Color.BLACK);
                String s = String.valueOf(i + 1);
                textPaint.setTextSize(s.length() > 2 ? spacing * 0.36f : spacing * 0.5f);
                canvas.drawText(s, cx, cy + textPaint.getTextSize() * 0.35f, textPaint);
            }
            textPaint.setColor(Color.WHITE);
        }
    }

    /** 棋子高光渐变：只跟 stoneRadius 有关，尺寸变化时建一次，之后复用。 */
    private void rebuildStoneShaders() {
        if (stoneRadius <= 0) return;
        float r = stoneRadius * 1.25f;
        float off = -stoneRadius * 0.3f;
        blackPaint.setShader(new RadialGradient(
                off, off, r, 0xFF6E6E6E, 0xFF0A0A0A, Shader.TileMode.CLAMP));
        whitePaint.setShader(new RadialGradient(
                off, off, r, 0xFFFFFFFF, 0xFFCFCFCF, Shader.TileMode.CLAMP));
    }

    private void drawStone(Canvas canvas, float cx, float cy, int color) {
        if (stoneRadius <= 0) return;
        // 平移到棋子中心再画，渐变坐标就与棋子位置无关，两个 Shader 可一直复用
        canvas.save();
        canvas.translate(cx, cy);
        BoardArt.stoneShadow(canvas, stoneRadius);
        canvas.drawCircle(0f, 0f, stoneRadius, color == GomokuBoard.BLACK ? blackPaint : whitePaint);
        canvas.drawCircle(0f, 0f, stoneRadius, stoneEdge);
        BoardArt.stoneGloss(canvas, stoneRadius);
        canvas.restore();
    }

    /** 星位行列（15 路：四个角点 + 天元）。 */
    private static int[] starPoints(int n) {
        if (n == 15) return new int[]{3, 7, 11};
        if (n >= 5) {
            int edge = n >= 13 ? 3 : 2;
            if (n % 2 == 1) return new int[]{edge, n / 2, n - 1 - edge};
        }
        return new int[0];
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // 全屏退出按钮最先判定，且不受 inputEnabled 影响 ——
        // 否则电脑思考时按钮点不动，用户只能按物理返回键。
        if (fullscreen && e.getAction() == MotionEvent.ACTION_DOWN) {
            RectF btn = fullscreenButtonRect();
            float slop = btn.height() * 0.3f;
            if (e.getX() >= btn.left - slop && e.getX() <= btn.right + slop
                    && e.getY() >= btn.top - slop && e.getY() <= btn.bottom + slop) {
                if (onExitFullscreen != null) onExitFullscreen.run();
                return true;
            }
        }

        if (!inputEnabled || board == null || tapListener == null) return super.onTouchEvent(e);
        if (e.getAction() != MotionEvent.ACTION_DOWN) return true;
        if (spacing <= 0) return true;

        int n = board.size;
        int col = Math.round((e.getX() - padX) / spacing);
        int row = Math.round((e.getY() - padY) / spacing);
        if (col < 0 || col >= n || row < 0 || row >= n) return true;
        float cx = padX + col * spacing, cy = padY + row * spacing;
        if (Math.hypot(e.getX() - cx, e.getY() - cy) > spacing * 0.6f) return true;

        tapListener.onTap(row * n + col);
        performClick();
        return true;
    }

    /** 全屏返回按钮的位置：绘制与点击判定共用同一个矩形。 */
    private RectF fullscreenButtonRect() {
        float h = getHeight();
        float btnH = Math.max(dp(30), Math.min(dp(52), h * 0.10f));
        float btnW = btnH * 2.4f;
        float m = btnH * 0.45f;
        return new RectF(m, m, m + btnW, m + btnH);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void drawFullscreenButton(Canvas canvas) {
        RectF btn = fullscreenButtonRect();
        float r = btn.height() * 0.3f;
        canvas.drawRoundRect(btn, r, r, fsBtnBg);
        fsBtnText.setTextSize(btn.height() * 0.42f);
        canvas.drawText("← 返回", btn.centerX(),
                btn.centerY() + fsBtnText.getTextSize() * 0.35f, fsBtnText);
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
