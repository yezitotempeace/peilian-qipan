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

import java.util.ArrayList;
import java.util.List;

/**
 * 棋盘绘制与触摸。
 *
 * 坐标系：内部索引 idx = row * size + col，row = 0 在顶部。
 * 屏幕坐标：交点 (row, col) 的中心 = (padX + col * spacing, padY + row * spacing)。
 */
public class BoardView extends View {

    public interface OnIntersectionTap {
        void onTap(int point);
    }

    private Board board;
    private int lastMove = Board.PASS;
    private boolean animationsEnabled;
    private boolean animActive;
    private long animStart;
    private int[] highlight = new int[0];
    private double[] ownership;          // 逐点归属，用于复盘时的形势显示
    private boolean showNumbers = false;
    private boolean inputEnabled = true;
    /** 全屏模式：棋盘占满屏幕，左上角显示返回按钮。 */
    private boolean fullscreen = false;
    /** 全屏模式下点返回时的回调。 */
    private Runnable onExitFullscreen;
    private int[] legalPoints = null;    // null = 不限制；否则只允许这些点

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
    /** 被提掉的子的「幽灵」画笔 —— 半透明，让手数在空交点上也能看见。 */
    private final Paint ghostBlack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostWhite = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 全屏返回按钮的画笔。提到字段里，避免每帧 onDraw 都 new 一次。 */
    private final Paint fsBtnBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fsBtnText = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 当前配色（黑夜/白天）。默认浅色，Activity 建好视图后会 setPalette。 */
    private Palette palette = Palette.of(false);

    private float padX, padY, spacing, stoneRadius;

    public BoardView(Context context) {
        super(context);
        init();
    }

    public BoardView(Context context, AttributeSet attrs) {
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
        // 幽灵棋子：被提掉的位置画半透明底色，让手数看得见
        ghostBlack.setColor(0x55222222);
        ghostWhite.setColor(0x88DDDDDD);
        fsBtnBg.setColor(0xCC2B2B2B);
        fsBtnText.setColor(Color.WHITE);
        fsBtnText.setTextAlign(Paint.Align.CENTER);
        applyPaletteColors();
        setFocusable(true);
    }

    /**
     * 换配色（黑夜/白天）。
     *
     * 深色模式下棋盘木色变暗，所以棋子描边也一并换成浅色 ——
     * 否则黑子和深色棋盘的亮度太接近，看不出棋子边界。
     */
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

    public void setBoard(Board b) {
        // 路数变了必须让几何重算：棋盘视图的像素边长只由屏幕决定
        // （Screen.boardSizeDp，与路数无关），所以 19 路换成 9 路时视图尺寸没变、
        // onSizeChanged 不会触发，spacing 会一直沿用 19 路的值 ——
        // 实测 9 路盘只画到视图的 44%、缩在左上角，右下留一大片空木纹。
        // 置 0 即可，下一帧 onDraw 的兜底会把几何算回来。
        if (board == null || b == null || b.size != board.size) spacing = 0;
        board = b;
        invalidate();
    }

    public void setAnimationsEnabled(boolean on) { animationsEnabled = on; }

    public void setLastMove(int point) {
        if (animationsEnabled && point >= 0 && point != lastMove) {
            animStart = System.currentTimeMillis();
            animActive = true;
        }
        lastMove = point;
        invalidate();
    }

    public void setHighlight(int[] points) {
        highlight = points == null ? new int[0] : points;
        invalidate();
    }

    public void setOwnership(double[] own) {
        ownership = own;
        invalidate();
    }

    public void setShowNumbers(boolean show) {
        showNumbers = show;
        invalidate();
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

    public boolean getShowNumbers() {
        return showNumbers;
    }

    public void setInputEnabled(boolean enabled) {
        inputEnabled = enabled;
    }

    /** 限制可落子的点；传 null 表示不限制。 */
    public void setLegalPoints(int[] points) {
        legalPoints = points;
    }

    public void setOnIntersectionTap(OnIntersectionTap l) {
        tapListener = l;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = MeasureSpec.getSize(heightSpec);
        if (fullscreen) {
            // 全屏模式：占满整个可用区域（宽高都取最大），不强制正方形
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
        int n = board == null ? 19 : board.size;
        // 棋盘必须是正方形，所以以**较短边**为基准。
        // 非全屏时视图本来就是正方形（onMeasure 取 min），w == h，结果与从前一致；
        // 全屏时视图是 w×h，若仍只用 w 算，横屏会把棋盘下半截切出屏幕、竖屏会缩在左上角。
        // 全屏时左上角有返回按钮，顶部要预留出它的高度，
        // 否则 19 路盘会被压住左上角一片交叉点。
        float topInset = fullscreen ? (fullscreenButtonRect().height() + dp(10)) : 0f;
        // 几何计算抽在 LayoutMath 里（纯函数，可在 JVM 上验证「棋盘不越界」）
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

        // 形势归属底色（复盘用）
        if (ownership != null && ownership.length == n * n) {
            for (int i = 0; i < n * n; i++) {
                double v = ownership[i];
                if (Math.abs(v) < 0.25) continue;
                int alpha = (int) Math.min(110, Math.abs(v) * 130);
                int color = v > 0 ? Color.BLACK : Color.WHITE;
                woodPaint.setColor((alpha << 24) | (color & 0x00FFFFFF));
                float cx = left + (i % n) * spacing, cy = top + (i / n) * spacing;
                canvas.drawRect(cx - spacing / 2, cy - spacing / 2, cx + spacing / 2, cy + spacing / 2, woodPaint);
            }
            woodPaint.setColor(palette.boardWood);
        }

        // 网格
        for (int i = 0; i < n; i++) {
            float y = top + i * spacing;
            float x = left + i * spacing;
            canvas.drawLine(left, y, right, y, gridPaint);
            canvas.drawLine(x, top, x, bottom, gridPaint);
        }

        // 星位
        for (int r : starRows(n)) {
            for (int c : starRows(n)) {
                canvas.drawCircle(left + c * spacing, top + r * spacing, Math.max(2f, spacing * 0.075f), gridPaint);
            }
        }

        // 坐标
        for (int i = 0; i < n; i++) {
            canvas.drawText(String.valueOf(Board.GTP_COLS.charAt(i)), left + i * spacing, top - spacing * 0.28f, labelPaint);
            canvas.drawText(String.valueOf(n - i), left - spacing * 0.62f, top + i * spacing + labelPaint.getTextSize() * 0.35f, labelPaint);
        }

        // 引擎候选点提示
        for (int p : highlight) {
            if (p < 0 || p >= n * n) continue;
            float cx = left + (p % n) * spacing, cy = top + (p / n) * spacing;
            canvas.drawCircle(cx, cy, stoneRadius * 0.92f, hintPaint);
        }

        // 棋子；新落的一手弹一下
        float animP = 1f;
        if (animActive) {
            animP = AnimMath.progress(animStart, System.currentTimeMillis(), AnimMath.MOVE_MS);
            if (animP >= 1f) {
                animActive = false;
            }
        }
        for (int i = 0; i < n * n; i++) {
            int v = board.grid[i];
            if (v == Board.EMPTY) continue;
            if (animActive && i == lastMove) continue;
            float cx = left + (i % n) * spacing, cy = top + (i / n) * spacing;
            drawStone(canvas, cx, cy, v);
        }
        if (animActive && lastMove >= 0 && lastMove < n * n && board.grid[lastMove] != Board.EMPTY) {
            float cx = left + (lastMove % n) * spacing, cy = top + (lastMove / n) * spacing;
            float scl = 0.55f + 0.45f * AnimMath.easeOut(animP);
            canvas.save();
            canvas.scale(scl, scl, cx, cy);
            drawStone(canvas, cx, cy, board.grid[lastMove]);
            canvas.restore();
            postInvalidateOnAnimation();
        }

        // 最后一手标记
        if (lastMove != Board.PASS && lastMove >= 0 && lastMove < n * n) {
            float cx = left + (lastMove % n) * spacing, cy = top + (lastMove / n) * spacing;
            int color = board.grid[lastMove];
            markerPaint.setColor(color == Board.BLACK ? 0xFFFF3B30 : 0xFFD32F2F);
            canvas.drawCircle(cx, cy, stoneRadius * 0.34f, markerPaint);
        }

        // 全屏模式的返回按钮
        if (fullscreen) drawFullscreenButton(canvas);

        // 手数
        if (showNumbers) {
            int total = board.moves.size();
            for (int i = 0; i < total; i++) {
                int[] m = board.moves.get(i);
                if (m[1] == Board.PASS) continue;
                int point = m[1];
                float cx = left + (point % n) * spacing, cy = top + (point / n) * spacing;
                boolean alive = point >= 0 && point < n * n && board.grid[point] != Board.EMPTY;
                // 被提掉的位置画半透明「幽灵」棋子底色，否则白字在木色背景上根本看不见
                if (!alive) {
                    canvas.drawCircle(cx, cy, stoneRadius, m[0] == Board.BLACK ? ghostBlack : ghostWhite);
                }
                // 手数文字颜色：活着的子取反色；幽灵子按原色取深色对比
                textPaint.setColor(alive
                        ? (board.grid[point] == Board.BLACK ? Color.WHITE : Color.BLACK)
                        : (m[0] == Board.BLACK ? Color.WHITE : Color.BLACK));
                String s = String.valueOf(i + 1);
                if (s.length() > 2) textPaint.setTextSize(spacing * 0.38f);
                else textPaint.setTextSize(spacing * 0.52f);
                canvas.drawText(s, cx, cy + textPaint.getTextSize() * 0.35f, textPaint);
            }
        }
    }

    /**
     * 建棋子高光用的渐变。
     *
     * 渐变只跟 stoneRadius 有关，所以只在尺寸变化时建一次，之后所有棋子复用。
     * 早先是每画一颗子就 new 一个 RadialGradient —— 19 路盘一屏近 300 颗子就是 300 次分配，
     * 一局下来几十万次，全是 GC 压力。
     */
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
        // 先把画布平移到棋子中心、在原点作画：渐变的坐标就与棋子位置无关，
        // 于是 two 个 Shader 可以一直复用（见 rebuildStoneShaders）。
        canvas.save();
        canvas.translate(cx, cy);
        BoardArt.stoneShadow(canvas, stoneRadius);
        canvas.drawCircle(0f, 0f, stoneRadius, color == Board.BLACK ? blackPaint : whitePaint);
        canvas.drawCircle(0f, 0f, stoneRadius, stoneEdge);
        BoardArt.stoneGloss(canvas, stoneRadius);
        canvas.restore();
    }

    /** 星位（按盘面大小给标准位置）。 */
    private static int[] starRows(int n) {
        if (n == 9) return new int[]{2, 4, 6};
        if (n == 13) return new int[]{3, 6, 9};
        if (n == 19) return new int[]{3, 9, 15};
        if (n >= 5) {
            int edge = n >= 13 ? 3 : 2;
            int mid = n / 2;
            if (n % 2 == 1) return new int[]{edge, mid, n - 1 - edge};
        }
        return new int[0];
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        // 全屏退出按钮必须**最先**判定，且不受 inputEnabled 影响。
        // 否则引擎思考中或自动对弈时 inputEnabled=false，按钮点不动，
        // 用户会卡在全屏里出不来（只能靠物理返回键）。
        if (fullscreen && e.getAction() == MotionEvent.ACTION_DOWN) {
            RectF btn = fullscreenButtonRect();
            // 触摸目标略微放大，手指没点准也能触发
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
        // 离交点太远就不响应，避免误触
        float cx = padX + col * spacing, cy = padY + row * spacing;
        float dx = e.getX() - cx, dy = e.getY() - cy;
        if (Math.hypot(dx, dy) > spacing * 0.6f) return true;

        int point = row * n + col;
        if (legalPoints != null) {
            boolean ok = false;
            for (int p : legalPoints) if (p == point) { ok = true; break; }
            if (!ok) return true;
        }
        tapListener.onTap(point);
        performClick();
        return true;
    }

    /**
     * 全屏返回按钮的位置。按视图尺寸算（不依赖 spacing），
     * 且「绘制」和「点击判定」共用这一个矩形，避免画的和点的对不上。
     */
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

    /** 全屏模式下在左上角画一个「← 返回」按钮。 */
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

    /** 返回所有当前可落子的点（用于高亮/限制）。 */
    public static List<Integer> legalMoves(Board b, int color) {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < b.grid.length; i++) {
            if (b.isLegal(color, i)) out.add(i);
        }
        return out;
    }
}
