package com.autogo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/**
 * 象棋棋盘视图：10 行 × 9 列，红黑棋子用圆片 + 汉字画。
 * 立体感走 {@link BoardArt}（跟围棋/五子棋同一套）。
 */
public class XiangqiView extends View {

    /** 交点点击回调（row 0 = 黑底线）。 */
    public interface OnIntersectionTap {
        void onTap(int row, int col);
    }

    /** 红方棋子字。 */
    private static final String[] RED_CHARS = {"", "帅", "仕", "相", "马", "车", "炮", "兵"};
    /** 黑方棋子字。 */
    private static final String[] BLACK_CHARS = {"", "将", "士", "象", "马", "车", "炮", "卒"};

    private final Paint woodPaint = new Paint();
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint riverPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint discPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint charPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private Palette palette = Palette.of(false);
    private XiangqiBoard board;
    private OnIntersectionTap tapListener;
    private boolean inputEnabled = true;
    private boolean showCoordinates = true;
    private float padX, padY, spacing;
    private int selected = -1;
    private int hint = -1;   // 复盘页的「推荐点」（-1 = 不画）
    private int[] legal = new int[0];
    private int lastFrom = -1;
    private int lastTo = -1;

    public XiangqiView(Context context) {
        this(context, null);
    }

    public XiangqiView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setFocusable(true);
        linePaint.setStyle(Paint.Style.STROKE);
        riverPaint.setStyle(Paint.Style.FILL);
        riverPaint.setTextAlign(Paint.Align.CENTER);
        rimPaint.setStyle(Paint.Style.STROKE);
        discPaint.setStyle(Paint.Style.FILL);
        charPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        markPaint.setStyle(Paint.Style.STROKE);
        dotPaint.setStyle(Paint.Style.FILL);
        selPaint.setStyle(Paint.Style.STROKE);
    }

    /** 设置主题色（棋盘木色、坐标文字色）。 */
    public void setPalette(Palette p) {
        palette = p == null ? Palette.of(false) : p;
        woodPaint.setColor(palette.boardWood);
        linePaint.setColor(0xCC3B2A17);
        riverPaint.setColor(0xCC3B2A17);
        labelPaint.setColor(palette.boardLabel);
    }

    /** 换棋局（清掉选中与上一手）。 */
    public void setBoard(XiangqiBoard b) {
        board = b;
        selected = -1;
        legal = new int[0];
        lastFrom = -1;
        lastTo = -1;
        invalidate();
    }

    public XiangqiBoard board() {
        return board;
    }

    /** 选中某一格（row/col 越大越靠下/右）；传负数清掉。 */
    public void setHint(int point) {
        hint = point;
        invalidate();
    }

    public void setSelected(int row, int col) {
        selected = (row < 0 || col < 0) ? -1 : row * XiangqiBoard.COLS + col;
        invalidate();
    }

    /** 选中格子的可走点（row * COLS + col）。 */
    public void setLegalPoints(int[] points) {
        legal = points == null ? new int[0] : points;
        invalidate();
    }

    /** 标记上一手（from/to 为 row * COLS + col，负数清掉）。 */
    public void setLastMove(int from, int to) {
        lastFrom = from;
        lastTo = to;
        invalidate();
    }

    public void setInputEnabled(boolean on) {
        inputEnabled = on;
    }

    public void setShowCoordinates(boolean on) {
        showCoordinates = on;
        invalidate();
    }

    public void setOnIntersectionTap(OnIntersectionTap l) {
        tapListener = l;
    }

    private void recompute(int w, int h) {
        float margin = Math.max(18f, Math.min(w, h) * 0.07f);
        padX = margin;
        padY = margin;
        float sx = (w - 2f * margin) / (XiangqiBoard.COLS - 1);
        float sy = (h - 2f * margin) / (XiangqiBoard.ROWS - 1);
        spacing = Math.max(1f, Math.min(sx, sy));
        padX = (w - spacing * (XiangqiBoard.COLS - 1)) / 2f;
        padY = (h - spacing * (XiangqiBoard.ROWS - 1)) / 2f;
    }

    private float x(int col) {
        return padX + col * spacing;
    }

    private float y(int row) {
        return padY + row * spacing;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recompute(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        canvas.drawRect(0f, 0f, w, h, woodPaint);
        if (spacing <= 0f) {
            recompute(w, h);
        }
        float left = x(0);
        float right = x(XiangqiBoard.COLS - 1);
        float top = y(0);
        float bottom = y(XiangqiBoard.ROWS - 1);
        BoardArt.boardEdge(canvas, left, top, right, bottom);

        linePaint.setStrokeWidth(Math.max(1.2f, spacing * 0.028f));
        for (int r = 0; r < XiangqiBoard.ROWS; r++) {
            canvas.drawLine(left, y(r), right, y(r), linePaint);
        }
        for (int c = 0; c < XiangqiBoard.COLS; c++) {
            if (c == 0 || c == XiangqiBoard.COLS - 1) {
                canvas.drawLine(x(c), top, x(c), bottom, linePaint);
            } else {
                canvas.drawLine(x(c), top, x(c), y(4), linePaint);
                canvas.drawLine(x(c), y(5), x(c), bottom, linePaint);
            }
        }
        // 九宫斜线
        linePaint.setStrokeWidth(Math.max(1f, spacing * 0.022f));
        canvas.drawLine(x(3), y(0), x(5), y(2), linePaint);
        canvas.drawLine(x(5), y(0), x(3), y(2), linePaint);
        canvas.drawLine(x(3), y(7), x(5), y(9), linePaint);
        canvas.drawLine(x(5), y(7), x(3), y(9), linePaint);

        // 河界
        riverPaint.setTextSize(Math.max(12f, spacing * 0.52f));
        float midY = (y(4) + y(5)) / 2f;
        canvas.drawText("楚  河", x(2), midY + spacing * 0.18f, riverPaint);
        canvas.drawText("漢  界", x(6), midY + spacing * 0.18f, riverPaint);

        if (showCoordinates) {
            labelPaint.setTextSize(Math.max(10f, spacing * 0.34f));
            for (int c = 0; c < XiangqiBoard.COLS; c++) {
                canvas.drawText(XiangqiBoard.colName(c), x(c), top - spacing * 0.42f, labelPaint);
            }
            for (int r = 0; r < XiangqiBoard.ROWS; r++) {
                canvas.drawText(String.valueOf(r + 1), x(0) - spacing * 0.62f, y(r) + spacing * 0.24f, labelPaint);
            }
        }

        if (board != null) {
            for (int r = 0; r < XiangqiBoard.ROWS; r++) {
                for (int c = 0; c < XiangqiBoard.COLS; c++) {
                    int piece = board.at(r, c);
                    if (piece != 0) {
                        drawDisc(canvas, x(c), y(r), piece);
                    }
                }
            }
        }

        // 上一手
        if (lastTo >= 0) {
            int lr = lastTo / XiangqiBoard.COLS;
            int lc = lastTo % XiangqiBoard.COLS;
            markPaint.setColor(0xFFE53935);
            markPaint.setStrokeWidth(Math.max(1.5f, spacing * 0.06f));
            canvas.drawCircle(x(lc), y(lr), spacing * 0.46f, markPaint);
            if (lastFrom >= 0) {
                int fr = lastFrom / XiangqiBoard.COLS;
                int fc = lastFrom % XiangqiBoard.COLS;
                canvas.drawCircle(x(fc), y(fr), spacing * 0.46f, markPaint);
            }
        }
        // 选中
        if (selected >= 0) {
            int sr = selected / XiangqiBoard.COLS;
            int sc = selected % XiangqiBoard.COLS;
            selPaint.setColor(0xFF1A73E8);
            selPaint.setStrokeWidth(Math.max(2f, spacing * 0.075f));
            canvas.drawCircle(x(sc), y(sr), spacing * 0.5f, selPaint);
        }
        // 推荐点（复盘页）：绿圈
        if (hint >= 0 && hint < XiangqiBoard.ROWS * XiangqiBoard.COLS) {
            int hr = hint / XiangqiBoard.COLS;
            int hc = hint % XiangqiBoard.COLS;
            markPaint.setColor(0xFF2E7D32);
            markPaint.setStrokeWidth(Math.max(2f, spacing * 0.075f));
            canvas.drawCircle(x(hc), y(hr), spacing * 0.30f, markPaint);
        }
        // 可走点
        dotPaint.setColor(0xCC1A73E8);
        for (int p : legal) {
            int pr = p / XiangqiBoard.COLS;
            int pc = p % XiangqiBoard.COLS;
            canvas.drawCircle(x(pc), y(pr), spacing * 0.14f, dotPaint);
        }
        super.onDraw(canvas);
    }

    private void drawDisc(Canvas canvas, float cx, float cy, int piece) {
        float radius = spacing * 0.42f;
        boolean red = XiangqiBoard.colorOf(piece) == XiangqiBoard.RED;
        canvas.save();
        canvas.translate(cx, cy);
        BoardArt.stoneShadow(canvas, radius);
        discPaint.setColor(0xFFF7E8C8);
        canvas.drawCircle(0f, 0f, radius, discPaint);
        int ink = red ? 0xFFB3261E : 0xFF2B2B2B;
        rimPaint.setColor(ink);
        rimPaint.setStrokeWidth(Math.max(1.4f, radius * 0.09f));
        canvas.drawCircle(0f, 0f, radius * 0.88f, rimPaint);
        BoardArt.stoneGloss(canvas, radius);
        String[] chars = red ? RED_CHARS : BLACK_CHARS;
        int type = XiangqiBoard.typeOf(piece);
        charPaint.setColor(ink);
        charPaint.setTextSize(radius * 1.08f);
        canvas.drawText(type < chars.length ? chars[type] : "?", 0f, radius * 0.36f, charPaint);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!inputEnabled || board == null || tapListener == null || spacing <= 0f) {
            return super.onTouchEvent(e);
        }
        if (e.getAction() != MotionEvent.ACTION_DOWN) {
            return super.onTouchEvent(e);
        }
        int col = Math.round((e.getX() - padX) / spacing);
        int row = Math.round((e.getY() - padY) / spacing);
        if (row < 0 || row >= XiangqiBoard.ROWS || col < 0 || col >= XiangqiBoard.COLS) {
            return true;
        }
        float dx = e.getX() - x(col);
        float dy = e.getY() - y(row);
        if (Math.hypot(dx, dy) > spacing * 0.55f) {
            return true;
        }
        tapListener.onTap(row, col);
        performClick();
        return true;
    }
}
