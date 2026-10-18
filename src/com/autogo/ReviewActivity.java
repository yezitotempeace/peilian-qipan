package com.autogo;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.content.SharedPreferences;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 复盘：对每一手之前的局面让引擎重新分析，得到「当前行棋方胜率」，
 * 再和前一手对比算出每手的胜率损失，把损失大的挑出来当作失误手。
 *
 * 引擎盘面同步方式：kata-genmove_analyze 会让引擎真的落一手，
 * 分析完先 undo 撤掉它，再 play 棋谱上的实际那手 —— 这样只靠 undo/play
 * 就能逐手前进，不必每手都重放整盘（O(N²) 的 play 命令）。
 */
public class ReviewActivity extends Activity {

    public static final String EXTRA_SIZE = "size";
    public static final String EXTRA_KOMI = "komi";
    public static final String EXTRA_MOVES = "moves";
    public static final String EXTRA_VISITS = "visits";
    public static final String EXTRA_MODEL = "model";
    /** 终局时已经算好的整盘分析，带过来就不必再跑一遍。 */
    public static final String EXTRA_WR = "wr";
    public static final String EXTRA_LOSS = "loss";
    public static final String EXTRA_BEST = "best";
    public static final String EXTRA_BESTPV = "bestpv";
    public static final String EXTRA_VERDICT = "verdict";
    /** 本谱第一手的行棋方（Board.BLACK/WHITE）。不传就按黑先 —— 让子谱会整条曲线反掉。 */
    public static final String EXTRA_FIRST = "firstcolor";
    /** 起始局面快照（含摆子与 PL）。不传就等于在空盘上重放。 */
    public static final String EXTRA_START = "startsnapshot";
    /** 主界面预分享的结果是否建立在正确起点上（假 ⇒ 顶部提示「只作参考」）。 */
    public static final String EXTRA_START_OK = "startok";

    private int size = 19;
    private double komi = 7.5;
    private int visits = 100;
    private String modelAlias = EngineManager.DEFAULT_MODEL;
    private final List<int[]> moves = new ArrayList<>();

    private BoardView boardView;
    private WinrateChartView chart;
    private TextView statusView;
    /** 状态文字留一份：转屏会重建控件，重建时按这个贴回去，否则「分析中… 12 / 300」会变空。 */
    private CharSequence statusText = "";
    private TextView listView;
    private Button prevBtn, nextBtn;

    private double[] wrToMove;      // [i] = 第 i 手行棋方的胜率
    private String[] bestMove;      // [i] = 引擎推荐
    private String[] bestPv;
    private double[] loss;          // [i] = 第 i 手的胜率损失
    private boolean[] done;

    private int current = 0;
    private int firstColor = Board.BLACK;
    private String startSnapshot = null;
    /** 起点局面是否可信。false 时页面顶部会说明这次是在空盘上重放算的。 */
    private boolean startOk = true;
    /** 起点局面的格子（来自 startSnapshot）。复盘屏显示摆子要用它，不能只重放手顺。 */
    private int[] setupGrid = null;
    /** 终局摘要，显示在失误排行前面。 */
    private String verdictLine = "";

    // 黑夜 / 白天：和主界面共用同一个偏好
    private static final String PREF = "autogo";
    private Palette palette = Palette.of(false);
    private Screen screen;
    private volatile boolean cancelled = false;
    private GtpEngine engine;
    private ExecutorService exec;
    private final Handler ui = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        size = getIntent().getIntExtra(EXTRA_SIZE, 19);
        komi = getIntent().getDoubleExtra(EXTRA_KOMI, 7.5);
        visits = getIntent().getIntExtra(EXTRA_VISITS, 100);
        firstColor = getIntent().getIntExtra(EXTRA_FIRST, Board.BLACK);
        startSnapshot = getIntent().getStringExtra(EXTRA_START);
        if (getIntent().hasExtra(EXTRA_START_OK)) {
            startOk = getIntent().getBooleanExtra(EXTRA_START_OK, true);
        }
        if (startSnapshot != null && !startSnapshot.isEmpty()) {
            try {
                setupGrid = Sgf.parse(startSnapshot).board.grid.clone();
            } catch (RuntimeException ignore) {
                setupGrid = null;   // 解析不了就不摆（显示退化为旧行为，引擎侧另有 startOk 提示）
            }
        }
        String m = getIntent().getStringExtra(EXTRA_MODEL);
        if (m != null) modelAlias = m;
        int[] raw = getIntent().getIntArrayExtra(EXTRA_MOVES);
        if (raw != null) {
            for (int i = 0; i + 1 < raw.length; i += 2) moves.add(new int[]{raw[i], raw[i + 1]});
        }

        int n = moves.size();
        wrToMove = new double[n + 1];
        bestMove = new String[n + 1];
        bestPv = new String[n + 1];
        loss = new double[n];
        done = new boolean[n + 1];
        Arrays.fill(wrToMove, Double.NaN);
        Arrays.fill(loss, Double.NaN);

        // 配色要在 buildUi() 之前定好，控件才会上对色
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        boolean sysDark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        boolean dark = Theme.isDark(Theme.clamp(sp.getInt("themeMode", Theme.SYSTEM)),
                sysDark, hour);
        palette = Palette.of(dark);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);

        buildUi();
        showPosition(0);

        if (acceptPrecomputed()) {
            // 终局已经分析过，直接铺数据，不再等一遍引擎
            chart.setData(blackWinrates());
            rebuildList();
        } else {
            startAnalysis();
        }
    }

    /**
     * 如果主界面把整盘分析结果带过来了就采用它。
     *
     * 长度必须逐一对上才采用 —— 宁可重算一遍，也不能拿一份和棋谱不匹配的数据去画曲线。
     */
    private boolean acceptPrecomputed() {
        int n = moves.size();
        double[] wr = getIntent().getDoubleArrayExtra(EXTRA_WR);
        double[] ls = getIntent().getDoubleArrayExtra(EXTRA_LOSS);
        String[] bm = getIntent().getStringArrayExtra(EXTRA_BEST);
        String[] bp = getIntent().getStringArrayExtra(EXTRA_BESTPV);
        String vd = getIntent().getStringExtra(EXTRA_VERDICT);
        if (vd != null) verdictLine = vd;

        if (wr == null || ls == null || bm == null || bp == null) return false;
        if (wr.length != n + 1 || ls.length != n || bm.length != n + 1 || bp.length != n + 1) {
            return false;
        }
        System.arraycopy(wr, 0, wrToMove, 0, n + 1);
        System.arraycopy(ls, 0, loss, 0, n);
        System.arraycopy(bm, 0, bestMove, 0, n + 1);
        System.arraycopy(bp, 0, bestPv, 0, n + 1);
        Arrays.fill(done, true);
        return true;
    }

    /** 改状态文字：既写进控件，也留一份给重建用。 */
    private void setStatus(CharSequence s) {
        statusText = s;
        if (statusView != null) statusView.setText(s);
    }

    private void buildUi() {
        screen = Screen.read(this);
        final int p = screen.dp(8);
        final boolean landscape = screen.landscape;

        // 竖屏：棋盘在上、曲线与列表在下；横屏：棋盘在左、其余在右。
        // 棋盘用按屏幕算出的固定边长，剩下全部给列表，避免小屏上棋盘被压扁。
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        root.setPadding(p, p, p, p);

        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);

        // 返回按钮和状态文字共用一行（本屏是 NoActionBar 主题：既没有系统标题栏的返回箭头，
        // 容器里也未必有系统返回键 —— 用户实测反馈「复盘打开后没有返回」，所以必须自己给一个）。
        // 竖屏这一行放在棋盘上方、横屏放进右栏顶部：两种朝向都落在内容区左上角，而且横屏不会去抢
        // 棋盘的宽度（棋盘边长是按屏高算死的，抢宽度会把棋盘挤小）。单独占一行的话，320x480 这种
        // 小屏下面手顺列表会只剩约 48dp；共用一行省掉的就是那 20dp 的状态文字高度。
        Button backBtn = new Button(this);
        backBtn.setText("◀ 返回");
        backBtn.setAllCaps(false);
        backBtn.setOnClickListener(v -> finish());

        statusView = new TextView(this);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        statusView.setGravity(Gravity.CENTER);
        statusView.setTextColor(palette.text);
        statusView.setText(statusText);   // 转屏重建后贴回原状态文字

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.addView(backBtn, lp(-2, -2));
        header.addView(statusView, lp(0, -2, 1f));
        if (landscape) {
            side.addView(header, lp(-1, -2));
        } else {
            LinearLayout.LayoutParams headerLp = lp(-1, -2);
            headerLp.bottomMargin = Math.max(4, p / 2);
            root.addView(header, headerLp);
        }

        int boardDp = screen.boardSizeDp(8);
        if (landscape) boardDp = Math.min(boardDp, screen.heightDp - 16);
        else boardDp = Math.min(boardDp, (int) (screen.heightDp * 0.52f));
        boardView = new BoardView(this);
        boardView.setPalette(palette);
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                screen.dp(boardDp), screen.dp(boardDp));
        boardLp.gravity = Gravity.CENTER_HORIZONTAL;
        if (landscape) boardLp.rightMargin = p; else boardLp.bottomMargin = p;
        root.addView(boardView, boardLp);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        Button first = new Button(this);
        first.setText("⏮");
        first.setOnClickListener(v -> showPosition(0));
        prevBtn = new Button(this);
        prevBtn.setText("◀");
        prevBtn.setOnClickListener(v -> showPosition(current - 1));
        nextBtn = new Button(this);
        nextBtn.setText("▶");
        nextBtn.setOnClickListener(v -> showPosition(current + 1));
        Button last = new Button(this);
        last.setText("⏭");
        last.setOnClickListener(v -> showPosition(moves.size()));
        for (Button b : new Button[]{first, prevBtn, nextBtn, last}) {
            b.setAllCaps(false);
            nav.addView(b, lp(0, -2, 1f));
        }
        side.addView(nav, lp(-1, -2));

        chart = new WinrateChartView(this);
        chart.setPalette(palette);
        chart.setOnSeek(this::showPosition);
        // 曲线高度按屏高给（夹在 80~160dp），写死 110dp 在小屏上会挤掉列表
        side.addView(chart, lp(-1, screen.dp(screen.chartHeightDp())));

        listView = new TextView(this);
        listView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        listView.setTypeface(android.graphics.Typeface.MONOSPACE);
        listView.setTextColor(palette.textDim);
        listView.setMovementMethod(new ScrollingMovementMethod());
        listView.setPadding(dp(4), dp(4), dp(4), dp(4));
        side.addView(listView, lp(-1, 0, 1f));

        root.addView(side, landscape ? lp(0, -1, 1f) : lp(-1, 0, 1f));
        
        Watermark.wrap(this, root, palette, 6);
    }

    /** 转屏后重建界面：分析在后台线程继续跑，这里只需把已算出的数据重新铺上去。 */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 系统深浅色可能变了：重新判定配色，buildUi() 会用新的重铺
        boolean sysDark = (newConfig.uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        boolean dark = Theme.isDark(Theme.clamp(getSharedPreferences(PREF, MODE_PRIVATE)
                .getInt("themeMode", Theme.SYSTEM)), sysDark, hour);
        palette = Palette.of(dark);
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        int keep = current;
        CharSequence keepStatus = statusText;   // showPosition() 末尾会写「第 N 手…」，先留一份
        buildUi();
        showPosition(keep);
        // 再把转屏前的状态文字贴回来（分析进度 / 完成提示 / 上一次的手数说明）。
        // 不这么做的话 showPosition → updateMoveStatus 会把它覆盖成当前手数说明，
        // 「复盘完成（N 手，每手 M visits）…」这类信息转屏后就丢了（第 20 轮审计指出）。
        setStatus(keepStatus);
        chart.setData(blackWinrates());
        rebuildList();
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    private LinearLayout.LayoutParams lp(int w, int h, float weight) {
        return new LinearLayout.LayoutParams(w, h, weight);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    // ------------------------------------------------------------------
    // 分析
    // ------------------------------------------------------------------

    /**
     * 往分析线程投任务的唯一出口。
     *
     * 目前只有 onCreate 会调 startAnalysis，而 onDestroy 已经 shutdownNow，
     * 所以现在碰不到；加这层守卫是因为 MainActivity 那边踩过同一个坑 ——
     * 退出后往已 shutdown 的执行器 execute 会抛 RejectedExecutionException，
     * 抛在主线程没人接 → 闪退。哪天把它挂到按钮/回调上就会立刻复现。
     */
    private void postReview(Runnable r) {
        if (isFinishing() || isDestroyed() || exec == null || exec.isShutdown()) return;
        exec.execute(r);
    }

    private void startAnalysis() {
        exec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "review-worker");
            t.setDaemon(true);
            return t;
        });
        setStatus("正在启动引擎…");
        final int reviewVisits = Math.max(10, Math.min(visits, 200));
        postReview(() -> {
            EngineManager mgr = new EngineManager(ReviewActivity.this);
            try {
                engine = mgr.start(reviewVisits, EngineManager.defaultThreads(), false, modelAlias);
            } catch (IOException e) {
                final String msg = e.getMessage();
                ui.post(() -> setStatus("引擎启动失败：" + msg));
                return;
            }
            ui.post(() -> setStatus("分析中…"));
            try {
                runAnalysis();
            } catch (IOException e) {
                final String msg = e.getMessage();
                ui.post(() -> setStatus("分析中断：" + msg));
            } finally {
                if (engine != null) engine.close();
            }
        });
    }

    /**
     * 逐手分析整盘。
     *
     * **这里不再自己写一份分析循环**（审计员第 16/17 轮报的 J1）：引擎怎么用、逐手怎么推进、
     * 胜率/最佳手/PV/loss 怎么算，只有 {@link GameReview#analyze} 一份，本方法只留
     * Activity 特有的四件事：临时快照文件、每手的界面刷新、取消标记、跑完后的状态栏措辞。
     *
     * 线程可见性：回调在工作线程上把结果对象里的数组挂到字段上（对象本身不变），
     * 紧接着才 ui.post —— 与合并前的写法完全一致，既没变坏也没变好（README §八有记录）。
     */
    private void runAnalysis() throws IOException {
        final int n = moves.size();
        // 唯一名：主界面预分享那条路径也在写复盘用的临时谱，同名会互相覆盖，
        // 后写者赢了的话另一方会静默读到别人的起点（不报错、startLoaded 还是 true）。
        // 没有起点快照时 analyze 不会碰这个文件，也就不会留下垃圾。
        File tmp = new File(getCacheDir(),
                "autogo-review-" + Long.toHexString(System.nanoTime()) + ".sgf");
        GameReview r = GameReview.analyze(engine, moves, size, komi, visits, firstColor,
                startSnapshot, tmp, (rev, idx, tot) -> {
                    // 把这一轮已算出的数组挂到字段上：界面线程随后读的就是同一批数组
                    wrToMove = rev.wrToMove;
                    bestMove = rev.bestMove;
                    bestPv = rev.bestPv;
                    loss = rev.loss;
                    startOk = rev.startLoaded;
                    done[idx] = true;
                    ui.post(() -> {
                        updateProgress(idx, tot);
                        chart.setData(blackWinrates());
                        // 逐手重建整个列表字符串很贵（300 手时是 O(N²) 的 setText），
                        // 每 5 手刷一次就够看，最后一次必刷。
                        if (idx % 5 == 0 || idx == tot) rebuildList();
                    });
                    return !cancelled;
                });
        if (r.cancelled) return;   // 取消：状态栏停在中途，等用户自己重开
        ui.post(() -> {
            setStatus(String.format(Locale.US,
                    "复盘完成（%d 手，每手 %d visits）—— 拖动上方曲线或点 ◀▶ 查看每一手",
                    n, Math.max(10, Math.min(visits, 200))));
            rebuildList();
        });
    }

    private void updateProgress(int idx, int n) {
        setStatus(String.format(Locale.US, "分析中… %d / %d", idx, n));
    }

    /** 黑方胜率序列，用于画曲线。 */
    private double[] blackWinrates() {
        return GameReview.toBlackWinrates(wrToMove, moves, firstColor);
    }

    // ------------------------------------------------------------------
    // 显示
    // ------------------------------------------------------------------

    private void showPosition(int index) {
        int n = moves.size();
        current = Math.max(0, Math.min(n, index));
        // 摆子必须先摆上：只重放手顺会让复盘屏和曲线自相矛盾
        Board b = GameReview.viewBoard(size, komi, setupGrid, moves, current);
        boardView.setBoard(b);
        boardView.setLastMove(current > 0 ? moves.get(current - 1)[1] : Board.PASS);

        // 高亮引擎在当前局面的推荐点
        String bm = bestMove[current];
        if (bm != null && !bm.isEmpty() && !bm.equalsIgnoreCase("pass")) {
            try {
                boardView.setHighlight(new int[]{Board.gtpToPoint(bm, size)});
            } catch (IllegalArgumentException e) {
                boardView.setHighlight(new int[0]);
            }
        } else {
            boardView.setHighlight(new int[0]);
        }

        chart.setSelected(current);
        if (prevBtn != null) prevBtn.setEnabled(current > 0);
        if (nextBtn != null) nextBtn.setEnabled(current < n);
        updateMoveStatus();
    }

    private void updateMoveStatus() {
        int n = moves.size();
        StringBuilder sb = new StringBuilder();
        sb.append("第 ").append(current).append(" / ").append(n).append(" 手");
        if (current > 0) {
            int[] mv = moves.get(current - 1);
            sb.append("　").append(Board.colorToChar(mv[0])).append(' ')
                    .append(Board.pointToGtp(mv[1], size));
            double l = loss[current - 1];
            if (!Double.isNaN(l)) {
                sb.append(String.format(Locale.US, "　损失 %.1f%%", l * 100));
            }
        }
        if (current <= n && !Double.isNaN(wrToMove[current])) {
            double moverWr = wrToMove[current];
            int color = GameReview.sideToMoveOf(moves, firstColor, current);
            sb.append(String.format(Locale.US, "　%s胜率 %.1f%%",
                    Board.colorToChar(color), moverWr * 100));
        }
        setStatus(sb.toString());
    }

    private void rebuildList() {
        int n = moves.size();
        StringBuilder sb = new StringBuilder();
        if (!startOk) {
            sb.insert(0, "注意：这盘的起点局面没能交给引擎（摆子缺失或读入失败），"
                    + "下面的数字是在空盘上重放算的，只作参考\n\n");
        }
        if (verdictLine != null && !verdictLine.isEmpty()) {
            sb.append(verdictLine).append('\n').append('\n');
        }
        sb.append("失误排行（胜率损失从大到小）\n");
        List<int[]> ranked = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!Double.isNaN(loss[i])) ranked.add(new int[]{i});
        }
        ranked.sort((a, b) -> Double.compare(loss[b[0]], loss[a[0]]));
        int shown = 0;
        for (int[] item : ranked) {
            if (shown >= 10) break;
            int i = item[0];
            if (loss[i] < 0.02) break;   // 2% 以下的波动不值得列
            sb.append(String.format(Locale.US, "%3d. 第%3d手 %s %-4s 损失 %.1f%%  引擎推荐 %s\n",
                    ++shown, i + 1, Board.colorToChar(moves.get(i)[0]),
                    Board.pointToGtp(moves.get(i)[1], size), loss[i] * 100,
                    bestMove[i] == null ? "?" : bestMove[i]));
        }
        if (shown == 0) sb.append("（没有明显失误，或分析尚未覆盖到）\n");

        sb.append("\n逐手胜率\n");
        for (int i = 0; i < n; i++) {
            // 用 wrToMove 是否有效来判断，而不是 done[i]：引擎偶尔会返回解析不出
            // info 的响应（done 为 true 但胜率是 NaN），那种情况直接打印 NaN 会误导。
            if (Double.isNaN(wrToMove[i])) {
                sb.append(String.format(Locale.US, "%3d. %s %-4s  分析中…\n",
                        i + 1, Board.colorToChar(moves.get(i)[0]),
                        Board.pointToGtp(moves.get(i)[1], size)));
                continue;
            }
            double l = loss[i];
            String lossStr = Double.isNaN(l) ? "  --  " : String.format(Locale.US, "%.1f%%", l * 100);
            double moverWr = wrToMove[i];
            sb.append(String.format(Locale.US, "%3d. %s %-4s  胜率 %5.1f%%  损失 %6s\n",
                    i + 1, Board.colorToChar(moves.get(i)[0]),
                    Board.pointToGtp(moves.get(i)[1], size), moverWr * 100, lossStr));
        }
        listView.setText(sb.toString());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelled = true;
        if (exec != null) exec.shutdownNow();   // 先让 worker 停手，省得它和关闭抢引擎
        EngineManager.closeAsync(engine);
    }
}
