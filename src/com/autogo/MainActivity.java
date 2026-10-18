package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;

import java.util.ArrayList;
import java.util.List;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 主界面：人机对弈 / 自动对弈 / 复盘入口。
 *
 * 线程模型：所有 GTP 往返都丢到单线程的 engineExec 里，UI 线程只读状态、只刷界面。
 * 这样既不会卡界面，也不用给 GtpEngine 上锁 —— 同一时刻只有一个请求在飞。
 */
public class MainActivity extends Activity {

    private static final String PREF = "autogo";

    private BoardView boardView;
    private TextView statusView;
    private TextView infoView;
    private Button autoButton;
    private Button undoButton;
    private Button reviewButton;

    private EngineManager engineManager;
    private volatile GtpEngine engine;
    private ExecutorService engineExec;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Board board;
    private int boardSize = 19;
    private double komi = 7.5;
    private int visits = 100;
    private int threads;
    private boolean allowResign = true;
    private int humanColor = Board.BLACK;
    private String modelAlias = EngineManager.DEFAULT_MODEL;

    /** 构建界面时读到的屏幕信息，所有尺寸都由它算。 */
    private Screen screen;
    /** 最后一手的位置。BoardView 不对外暴露这个状态，转屏重建界面时要靠它还原。 */
    private int lastMovePoint = Board.PASS;
    private boolean showNumbers = false;

    private boolean autoPlay = false;
    private boolean thinking = false;
    private boolean gameOver = false;
    private boolean engineReady = false;
    private int consecutivePasses = 0;
    private String lastResult = "";
    /** 终局后的整盘分析结果（用于「为什么赢」摘要 + 传给复盘页，避免重复分析）。 */
    private GameReview review;
    /**
     * 分析代号。每次启动分析都自增，后台循环里拿自己那份去比对：
     * 不相等就说明这一轮已经作废（开了新局、悔了棋、或者又启动了新一轮分析）。
     *
     * 早先用的是布尔 reviewCancel，但 startGameReview 开头要把它清掉 ——
     * 若上一轮还在跑，它就再也收不到取消信号了。代号没有这个问题。
     */
    private final java.util.concurrent.atomic.AtomicInteger reviewGen =
            new java.util.concurrent.atomic.AtomicInteger();
    private double lastWinrate = Double.NaN;   // 当前行棋方视角
    private WinrateBarView winrateBar;
    /** 全屏时要用到的两个容器：根布局（改背景/内边距）和控件区（隐藏）。 */
    private LinearLayout rootLayout;
    private ScrollView controlsArea;
    private View dividerView;
    private LinearLayout statusPanelView;

    // ---- 黑夜 / 白天 ----
    private int themeMode = Theme.SYSTEM;
    /** 当前是否深色。由 resolveTheme() 按「模式 + 系统设置 + 钟点」算出来。 */
    private boolean dark;
    private Palette palette = Palette.of(false);
    /** 所有按钮，换配色时统一上色。 */
    private final List<Button> themeButtons = new ArrayList<>();
    /**
     * 对局代号。每次换盘面（新局 / 读谱）都 +1。
     *
     * 引擎搜索是异步的，回调回来时可能已经换了一盘棋；回调只带代号回比对，
     * 对不上就丢弃 —— 否则上一局的落子会被追加到新棋盘上。
     * （复盘那条路早就用 reviewGen 这么做了，落子这条路原先漏了。）
     */
    private final java.util.concurrent.atomic.AtomicInteger gameGen =
            new java.util.concurrent.atomic.AtomicInteger();
    /** 按时间自动时，每分钟检查一次有没有过点。 */
    private final Runnable themeTick = new Runnable() {
        @Override
        public void run() {
            maybeAutoSwitchTheme();
            ui.postDelayed(this, 60_000L);
        }
    };
    private int lastWinrateColor = Board.EMPTY;

    private int gameCounter = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences sp = getSharedPreferences(PREF, MODE_PRIVATE);
        boardSize = sp.getInt("size", 19);
        komi = Double.longBitsToDouble(sp.getLong("komi", Double.doubleToLongBits(7.5)));
        humanColor = sp.getInt("humanColor", Board.BLACK);
        // 先定网络，再按网络取 visits —— 每个档位各自记住自己的数值
        modelAlias = sp.getString("model", EngineManager.DEFAULT_MODEL);
        visits = visitsFor(modelAlias);
        allowResign = sp.getBoolean("allowResign", true);

        threads = EngineManager.defaultThreads();
        engineManager = new EngineManager(this);
        engineExec = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "engine-worker");
            t.setDaemon(true);
            return t;
        });

        // 装崩溃记录：必须在任何可能崩的代码之前
        CrashLog.install(this);

        themeMode = Theme.clamp(sp.getInt("themeMode", Theme.SYSTEM));
        resolveTheme();
        // 让对话框和之后新建的控件从一开始就用对明暗主题
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);

        buildUi();
        // 有存档就接着上一局下（进程被系统回收后回来）；否则开新局。
        if (savedInstanceState == null || !restoreGame(savedInstanceState)) {
            newGame(true);
        }
        showPendingCrash();
    }

    /**
     * 上次崩过就把栈显示出来。用户在手机上拿不到 logcat，只能靠这个把信息带出来。
     */
    private void showPendingCrash() {
        final String trace = CrashLog.readAndClear(this);
        if (trace == null) return;

        TextView tv = new TextView(this);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setTextIsSelectable(true);
        tv.setText(trace);
        tv.setPadding(dp(10), dp(10), dp(10), dp(10));
        ScrollView sc = new ScrollView(this);
        sc.addView(tv);

        new AlertDialog.Builder(this)
                .setTitle("上次崩溃了（请把这个发给我）")
                .setView(sc)
                .setPositiveButton("知道了", null)
                .setNeutralButton("复制", (d, w) -> {
                    android.content.ClipboardManager cm = (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("crash", trace));
                        toast("已复制到剪贴板");
                    }
                })
                .show();
    }

    // ------------------------------------------------------------------
    // 界面
    // ------------------------------------------------------------------

    private void buildUi() {
        // rebuildLayout() 会再调一次 buildUi()，旧的按钮已经没人引用了，
        // 不清空的话 themeButtons 会一轮轮攒下去（泄漏 + 每次换配色都白刷一堆死视图）。
        themeButtons.clear();
        screen = Screen.read(this);
        final int pad = screen.dp(8);
        final boolean landscape = screen.landscape;

        // 竖屏：棋盘在上、控件在下；横屏：棋盘在左、控件在右。
        // 棋盘用**按屏幕算出来的固定边长**，不再依赖 weight —— 这样无论控件区多高，
        // 棋盘都不会被挤成 0。
        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(landscape ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        rootLayout.setBackgroundColor(palette.background);
        rootLayout.setPadding(pad, pad, pad, pad);

        boardView = new BoardView(this);
        boardView.setAnimationsEnabled(true);
        boardView.setOnIntersectionTap(this::onIntersectionTap);
        int boardPx = screen.dp(screen.boardSizeDp(8));
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(boardPx, boardPx);
        boardLp.gravity = Gravity.CENTER;
        if (landscape) boardLp.rightMargin = pad;
        else boardLp.bottomMargin = pad;
        rootLayout.addView(boardView, boardLp);

        // 控件区一律塞进 ScrollView：小屏、横屏、系统字号放大时都不会溢出。
        // 注意棋盘**不能**放进 ScrollView（UNSPECIFIED 高度会让它塌掉），
        // 所以这里用的是「棋盘固定尺寸 + 控件可滚动」的组合。
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);

        // statusView 和 infoView 现在放进下面的 statusPanel，不直接加到 controls
        statusView = new TextView(this);
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen.shortScreen ? 14 : 15);
        statusView.setTextColor(palette.text);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(0, dp(4), 0, dp(6));

        infoView = new TextView(this);
        infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        infoView.setTextColor(palette.textDim);
        infoView.setGravity(Gravity.CENTER);
        infoView.setPadding(0, dp(6), 0, dp(6));

        Button btnNew = button("新局", v -> showNewGameDialog());
        autoButton = button("自动对弈", v -> toggleAutoPlay());
        Button btnPass = button("停一手", v -> humanPass());
        controls.addView(buttonRow(btnNew, autoButton, btnPass));

        undoButton = button("悔棋", v -> undo());
        Button btnResign = button("认输", v -> resign());
        reviewButton = button("复盘", v -> openReview());
        controls.addView(buttonRow(undoButton, btnResign, reviewButton));

        Button btnImport = button("读谱", v -> importSgf());
        Button btnNumbers = button("手数", v -> toggleNumbers());
        Button btnFullscreen = button("全屏", v -> toggleFullscreen());
        Button btnSettings = button("设置", v -> showSettingsDialog());
        controls.addView(buttonRow(btnImport, btnNumbers, btnFullscreen, btnSettings));

        // 状态面板：放在按钮下方。横屏时右下角本来空着，正好填上。
        // 竖屏时也在按钮下面，滚一滚就看得到。
        statusPanelView = new LinearLayout(this);
        statusPanelView.setBackgroundColor(palette.surface);
        LinearLayout statusPanel = statusPanelView;
        statusPanel.setOrientation(LinearLayout.VERTICAL);
        statusPanel.setPadding(dp(8), dp(10), dp(8), dp(4));

        // 分割线
        dividerView = new View(this);
        View divider = dividerView;
        divider.setBackgroundColor(palette.divider);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divLp.bottomMargin = dp(8);
        statusPanel.addView(divider, divLp);

        // 当前轮到谁下
        statusView.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen.shortScreen ? 14 : 16);
        statusView.setPadding(0, dp(2), 0, dp(4));
        statusPanel.addView(statusView, matchWrap());

        // 胜率条
        winrateBar = new WinrateBarView(this);
        LinearLayout.LayoutParams wrLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        wrLp.topMargin = dp(4);
        statusPanel.addView(winrateBar, wrLp);

        // 提子 + 手数
        infoView.setPadding(0, dp(6), 0, dp(2));
        statusPanel.addView(infoView, matchWrap());

        // 注意：这里**不能**用 weight。statusPanel 位于 ScrollView 里，
        // ScrollView 以 UNSPECIFIED 高度测量子视图，LinearLayout 拿不到剩余空间、
        // 分不了权重，weight=1 + height=0 会直接塌成 0 高（面板整块看不见）。
        // 所以用 WRAP_CONTENT + 最小高度：横屏给足高度去填右下角，竖屏给个够用的高度。
        int panelMinDp = landscape
                ? Math.max(140, (int) (screen.heightDp * 0.45f))
                : 120;
        statusPanel.setMinimumHeight(screen.dp(panelMinDp));
        controls.addView(statusPanel, matchWrap());

        controlsArea = new ScrollView(this);
        controlsArea.addView(controls);
        // 视图都建好了，把配色铺上去（自绘的棋盘/胜率条也要）
        applyPaletteToViews();
        LinearLayout.LayoutParams scLp = landscape
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        rootLayout.addView(controlsArea, scLp);

        
        Watermark.wrap(this, rootLayout, palette, 6);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * 转屏后重建界面，并把当前对局状态重新刷到新控件上。
     * manifest 里声明了 configChanges=orientation|screenSize，Activity 不会重建，
     * 所以这件事必须自己做，否则转屏后棋盘是空的。
     */
    private void rebuildLayout() {
        buildUi();
        if (board != null) {
            boardView.setBoard(board);
            setLastMove(lastMovePoint);
            boardView.setShowNumbers(showNumbers);
        }
        if (winrateBar != null && !Double.isNaN(lastWinrate)) {
            double blackWr = GameReview.blackView(lastWinrate, lastWinrateColor);
            winrateBar.setBlackWinrate(blackWr);
        }
        refreshStatus();
        // gameOver 时 refreshStatus() 第一句就 `updateButtons(); return;`，状态栏和
        // 信息条两行什么都不写；而 buildUi() 新建的这两个控件也没有初始文字 ⇒
        // 对局结束后转屏，它们会变成空白（「已存棋谱：…」和复盘结论都消失）。
        // 作者在原代码里已经为读谱路径单独补过一次写（见 loadParsedGame 里的注释），
        // 重建路径漏了同样的补偿。这里把上一次写进去的文字补回来。
        if (!lastStatusText.isEmpty()) statusView.setText(lastStatusText);
        if (!lastInfoText.isEmpty()) infoView.setText(lastInfoText);
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        // 系统深浅色可能变了（manifest 里声明了 uiMode，所以这里不会被重建、对局不丢）。
        // 重新判定一次配色，rebuildLayout() 就会用新配色建视图。
        resolveTheme();
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        // 转屏后先重建普通布局（恢复棋子、手数、胜率等状态），再按需套回全屏。
        // 只重建、不套全屏的话，界面会变回普通布局而 fullscreen 字段仍是 true，状态就对不上。
        rebuildLayout();
        if (fullscreen) applyFullscreenLayout();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        // 从别的应用切回来时系统会重新显示状态栏/导航栏，IMMERSIVE_STICKY 需要重新应用
        if (hasFocus && fullscreen) applyFullscreenFlags();
    }

    /**
     * 全屏模式下按返回键 → 退出全屏（而不是直接关 Activity）。
     * 普通模式下按返回键 → 走系统默认行为（关掉本页，回到选择页）。
     */
    @Override
    public void onBackPressed() {
        if (fullscreen) {
            exitFullscreen();
            return;
        }
        super.onBackPressed();
    }

    /** 棋盘上「最后一手」的标记统一走这里，保证字段和视图不会脱节。 */
    private void setLastMove(int point) {
        lastMovePoint = point;
        if (boardView != null) boardView.setLastMove(point);
    }

    private LinearLayout buttonRow(Button... buttons) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rowLp.topMargin = dp(4);
        row.setLayoutParams(rowLp);
        for (Button b : buttons) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            row.addView(b, lp);
        }
        return row;
    }

    private Button button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen != null ? screen.buttonTextSp() : 14);
        b.setPadding(dp(2), dp(6), dp(2), dp(6));
        b.setMinimumWidth(0);
        b.setTextColor(palette.buttonText);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.buttonBg));
        b.setOnClickListener(l);
        themeButtons.add(b);
        return b;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void toast(String s) {
        if (!alive()) return;
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /**
     * Activity 还活着吗。
     *
     * 销毁之后再弹窗/弹 Toast 会抛 WindowManager$BadTokenException。
     * 最容易踩到的路径：引擎正在思考时按返回键 —— onDestroy 里 shutdownNow()
     * 打断 worker，worker 的 catch 又 ui.post 一个报错对话框回来。
     */
    private boolean alive() {
        return !isFinishing() && !isDestroyed();
    }

    /** 还能往 engineExec 里丢任务吗。退出流程里执行器已经 shutdown，再丢会抛异常。 */
    private boolean canPost() {
        return !isFinishing() && !isDestroyed() && engineExec != null && !engineExec.isShutdown();
    }

    /**
     * 往引擎线程投任务的**唯一出口**。所有 engineExec.execute 都必须走这里。
     *
     * 退出流程里执行器已经被 shutdownNow()，此时 execute 会抛
     * RejectedExecutionException —— 它抛在主线程、没人接，CrashLog 转发给系统处理器，
     * 进程直接被杀。只在 restartEngine/launchEngine 两处单独守卫是不够的（漏过一遍）：
     * 最确定的一条是自动对弈 —— 每次引擎落子后都 postDelayed(400ms) 一个
     * maybeEngineMove，只要在这 400ms 里按返回，回调必然落在 onDestroy 之后，
     * 于是 engineMove 往死掉的执行器投任务，必崩。
     */
    private void postEngine(Runnable r) {
        if (!canPost()) return;
        engineExec.execute(r);
    }

    // ------------------------------------------------------------------
    // 对局
    // ------------------------------------------------------------------

    private void newGame(boolean startEngineIfNeeded) {
        cancelReview();
        // 换盘面 = 作废上一局的搜索（见 onEngineMoveDone 的代号校验）
        gameGen.incrementAndGet();
        // 旧搜索已作废，先把 UI 解锁；下面的同步和新的搜索会重新置位 thinking。
        thinking = false;
        board = new Board(boardSize, komi);
        boardView.setBoard(board);
        setLastMove(Board.PASS);
        boardView.setHighlight(new int[0]);
        boardView.setOwnership(null);
        gameOver = false;
        consecutivePasses = 0;
        lastResult = "";
        loadedSetup = "";
        loadedHandicap = "";
        firstColor = Board.BLACK;
        lastWinrate = Double.NaN;
        lastWinrateColor = Board.EMPTY;
        gameCounter++;
        refreshStatus();

        if (!engineReady) {
            if (startEngineIfNeeded) startEngineThenSync();
            return;
        }
        if (engineWantAtStart != null && !engineWantAtStart.equals(engineWant())) {
            // 「开新局」对话框里改了思考量/线程数/规则，而搜索引擎的搜索量是
            // **启动时**写进引擎配置的（maxVisits），GTP 命令里那个数字不是上限 ——
            // 实测同一条 kata-genmove_analyze B 1000，启动配置 maxVisits=1/40/200
            // 分别耗时 84ms / 1035ms / 3991ms。不重启引擎就根本改不动，
            // 而状态栏会立刻显示新值：那是对用户撒谎。所以这里真的重启一次。
            setStatus("引擎参数已变，正在按新参数重启引擎…");
            restartEngine();
            return;
        }
        syncEngineBoardThenPlay();
    }

    /** 引擎启动请求的代号。只有代号最新的那次启动才算数。 */
    private int engineStartReq;
    /** 当前是否已经有一次启动在飞。 */
    private boolean engineStarting;
    /** 引擎实际是按什么参数起的（worker 里紧挨着 start 之前取的指纹）。 */
    private String engineWantAtStart;

    private void startEngineThenSync() {
        // 启动窗口内重复点「新局」，原来每点一次就起一个 KataGo 进程，
        // 而且旧进程永远不会 close —— 实测 b10 每次 +130MB、b18 每次 +538MB，
        // 连点几下就是 GB 级常驻，很容易被系统的 LMK 杀掉。
        // 这里只允许一次启动在飞：后来的请求只把代号推进，
        // 由在飞的那次结束之后自己发现代号过期，关掉刚起的进程再按最新参数重来。
        engineStartReq++;
        if (engineStarting) return;
        launchEngine();
    }

    /**
     * 当前期望的引擎参数指纹，用来判断在飞的那次启动是不是**真的**过时了。
     * engineStartReq / engineStarting 只在 UI 线程读写。
     */
    private String engineWant() {
        return modelAlias + "|" + visits + "|" + threads + "|" + allowResign;
    }

    /** 真正去起引擎进程。返回前会先生成一次请求代号，供结束回调判新旧。 */
    private void launchEngine() {
        if (!canPost()) return;
        final int req = engineStartReq;
        engineStarting = true;
        setStatus("正在加载引擎…");
        postEngine(() -> {
            // 参数只读一次、读进局部变量：既要用它启动，也要拿它当「引擎实际按什么参数
            // 起的」指纹。分成两次读字段的话，中间被 UI 线程改掉就会出现
            // 「引擎按 200 起的、却记成 40」—— 状态栏说谎，而且没人能发现。
            final int wantVisits = visits;
            final int wantThreads = threads;
            final boolean wantResign = allowResign;
            final String wantModel = modelAlias;
            final String wantAtSpawn = wantModel + "|" + wantVisits + "|" + wantThreads
                    + "|" + wantResign;
            final GtpEngine started;
            try {
                started = engineManager.start(wantVisits, wantThreads, wantResign, wantModel);
            } catch (IOException ex) {
                final String msg = ex.getMessage();
                ui.post(() -> {
                    engineStarting = false;
                    if (req != engineStartReq) { launchEngine(); return; }
                    engineReady = false;
                    setStatus("引擎启动失败");
                    showErrorDialog("引擎启动失败", msg);
                });
                return;
            }
            ui.post(() -> {
                if (!canPost()) {
                    started.close();       // 退出流程中，别把刚起的引擎漏在后面
                    return;
                }
                if (req != engineStartReq && !wantAtSpawn.equals(engineWant())) {
                    // 代号过期**而且**参数确实变了（设置里换了模型/档位）：
                    // 关掉刚起的进程、按最新参数重来，否则它没人接管、白占内存。
                    // 只比代号是不够的 —— 连点 N 次「新局」会让每一代都作废上一代，
                    // 实测 8 次点击 = 5 次完整加载（b18 一次 3~9 秒、538MB 模型要重读），
                    // 越点越慢。参数没变就直接采用刚起好的这个，连点 N 次只启动 1 次。
                    started.close();
                    launchEngine();
                    return;
                }
                engineStarting = false;
                GtpEngine old = engine;
                engine = started;
                engineReady = true;
                // 记下这个引擎实际是按什么参数起的（见 newGame 里的比对）
                engineWantAtStart = wantAtSpawn;
                // 覆盖前关掉旧引擎，避免泄漏。旧引擎可能还在算上一局 ⇒ 用异步关，
                // 别让主线程等它的 waitFor（上限 3 秒，见 EngineManager.closeAsync 的实测）。
                if (old != null) EngineManager.closeAsync(old);
                syncEngineBoardThenPlay();
            });
        });
    }

    /** 把当前盘面同步给引擎（引擎盘面可能还停在上一局）。 */
    /**
     * 把当前这盘棋的摆子放回盘上。摆子只写在 {@code grid} 里、不在 {@code board.moves}
     * 里，所以任何「从手顺重建盘面」的地方都得补这一步。
     */
    private void applySetupStones(Board b) {
        if (loadedSetup.isEmpty() || b == null) return;
        try {
            for (int[] st : Sgf.setupStones(loadedSetup, b.size)) {
                if (st[1] >= 0 && st[1] < b.grid.length) b.grid[st[1]] = st[0];
            }
        } catch (RuntimeException e) {
            // 摆子恢复失败也不能闪退；盘上少子比进程被杀轻
            android.util.Log.e("AutoGo", "摆子恢复失败: " + e);
        }
    }

    /**
     * 把盘面（摆子 + 手顺）同步给引擎。
     *
     * 摆子不在 moves 里，只重放 moves 会让引擎看到**另一个局面** —— 实测
     * （真 KataGo v1.18.2）：`AB[cc][gg];W[ee]` 旧路径引擎只看到 1 子，题谱 0 手时
     * 引擎看到的是空盘。那样引擎的应手、胜率条、复盘全是在别的盘上算的。
     * 有摆子走 loadsgf（实测对齐），没有摆子仍走久经使用的 clear_board + 逐手 play。
     */
    private void syncEnginePosition(GtpEngine e, int size, double k,
            List<int[]> moves, String setupSgf) throws IOException {
        if (setupSgf != null) {
            final File tmp = new File(getCacheDir(), "autogo-sync.sgf");
            try {
                try (java.io.OutputStream os = new java.io.FileOutputStream(tmp)) {
                    os.write(setupSgf.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                e.loadsgf(tmp.getAbsolutePath());
                return;
            } catch (IOException ignore) {
                // loadsgf 不可用就退回旧路径：手顺至少对得上（摆子仍会缺，README 已记明）
            } finally {
                // 引擎已经把局面读进内存，临时文件不必留着（免得 cache 里堆旧局面）
                tmp.delete();
            }
        }
        e.boardsize(size);
        e.komi(k);
        e.clearBoard();
        for (int[] m : moves) {
            e.play(m[0], Board.pointToGtp(m[1], size));
        }
    }

    /** 当前局面要不要带摆子同步给引擎（有摆子就得走 loadsgf）。 */
    private String setupSgfSnapshot() {
        return loadedSetup.isEmpty() ? null
                : Sgf.toSgf(board, "AutoGo", "AutoGo", "", loadedHandicap, loadedSetup, firstColor);
    }

    /**
     * 只有摆子、没有手顺的起始局面快照（复盘用）。
     * 复盘是「从起点逐手重放」，引擎必须先站在正确起跑线上：摆子要摆上、先后手要对。
     * 没有摆子也没有让子就返回 null（常规谱空盘起就是对的）。
     */
    private String setupOnlySnapshot() {
        if (loadedSetup.isEmpty() && loadedHandicap.isEmpty()) return null;
        Board empty = new Board(boardSize, komi);
        return Sgf.toSgf(empty, "AutoGo", "AutoGo", "", loadedHandicap, loadedSetup, firstColor);
    }

    private void syncEngineBoardThenPlay() {
        // 必须拷贝成快照再交给 worker：worker 会在引擎线程逐手遍历，
        // 而 UI 线程随时可能 board.play() 往同一个 ArrayList 追加。
        // 那会抛出 ConcurrentModificationException —— 它不是 IOException，
        // catch(IOException) 接不住，会直接逃出去把进程干掉（闪退）。
        final List<int[]> moves = new ArrayList<>(board.moves);
        final int size = boardSize;
        final double k = komi;
        // SGF 快照也在 UI 线程取（worker 里读 board 会和 UI 线程的 play 抢）
        final String setupSgf = setupSgfSnapshot();
        postEngine(() -> {
            GtpEngine e = engine;
            if (e == null) return;
            try {
                syncEnginePosition(e, size, k, moves, setupSgf);
            } catch (IOException ex) {
                ui.post(() -> handleEngineError(e, ex));
                return;
            }
            ui.post(this::maybeEngineMove);
        });
    }

    private void onIntersectionTap(int point) {
        if (gameOver || thinking) return;
        if (autoPlay) {
            toast("自动对弈中，先暂停再落子");
            return;
        }
        if (sideToMove() != humanColor) return;

        int color = humanColor;
        String reason = board.illegalReason(color, point);
        if (reason != null) {
            toast(reason);
            return;
        }
        board.play(color, point);
        consecutivePasses = 0;
        setLastMove(point);
        boardView.invalidate();
        refreshStatus();

        postEngine(() -> {
            // e 提到 try 外面：catch 块里引用不到 try 块内声明的局部变量
            GtpEngine e = engine;
            try {
                if (e != null) e.play(color, Board.pointToGtp(point, boardSize));
            } catch (IOException ex) {
                ui.post(() -> handleEngineError(e, ex));
            }
            ui.post(this::maybeEngineMove);
        });
    }

    private void humanPass() {
        if (gameOver || thinking) return;
        if (autoPlay) {
            toast("自动对弈中");
            return;
        }
        int color = sideToMove();
        if (color != humanColor) {
            toast("还没轮到你");
            return;
        }
        doPass(color);
    }

    private void doPass(int color) {
        board.play(color, Board.PASS);
        consecutivePasses++;
        setLastMove(Board.PASS);
        boardView.invalidate();
        refreshStatus();
        postEngine(() -> {
            GtpEngine e = engine;
            try {
                if (e != null) e.play(color, "pass");
            } catch (IOException ex) {
                ui.post(() -> handleEngineError(e, ex));
            }
            ui.post(() -> {
                if (consecutivePasses >= 2) finishGame(null);
                else maybeEngineMove();
            });
        });
    }

    /**
     * 该谁走。**不能用「手数奇偶」**，有两个理由：
     * 1) 它假定了黑先，而让子谱（`HA[2]` 起）是白先。实测（真 KataGo v1.18.2）：
     *    `HA[2]AB[cc][gg]` 0 手时引擎认为该白走、我们按奇偶给出黑走，于是给引擎发
     *    `genmove B`；引擎不校验轮次、照常出子 ⇒ 白方白送一手。
     * 2) 同一方连下两手（SGF 允许，我们 parse 原样保留、导入非交替谱就能遇到）时，
     *    手数奇偶也不等于行棋方 —— 实测 `moves=[(B,e5),(B,f5)]` 奇偶给黑，
     *    而「一人不能连下两手」下唯一合理的答案是白。
     * 所以统一走 {@link GameReview#sideToMoveOf}，与复盘家族同一判据（交替谱下与旧写法等价）。
     */
    private int sideToMove() {
        return GameReview.sideToMoveOf(board.moves, firstColor, board.moves.size());
    }

    private void maybeEngineMove() {
        if (gameOver || thinking) return;
        int toMove = sideToMove();
        boolean engineTurn = autoPlay || toMove != humanColor;
        if (!engineTurn) {
            refreshStatus();
            return;
        }
        engineMove(toMove);
    }

    private void engineMove(int color) {
        if (engine == null) {
            toast("引擎未就绪");
            return;
        }
        final int gen = gameGen.get();
        thinking = true;
        setStatus((autoPlay ? "自动对弈" : "引擎") + "思考中…（"
                + Board.colorToChar(color) + "，" + visits + " visits）");
        updateButtons();
        final int timeout = Math.max(120_000, visits * 1500);
        postEngine(() -> {
            GtpEngine e = engine;          // 和本文件其它几处一样用局部变量 + null 检查：
            if (e == null) return;         // restartEngine() 会把字段置空，直接读字段会 NPE
            try {
                String resp = e.send("kata-genmove_analyze " + Board.colorToChar(color) + " 1000", timeout);
                Analysis.Result ar = Analysis.parse(resp);
                String mv = ar.playedMove;
                if (mv == null) {
                    Analysis.MoveInfo top = ar.top();
                    mv = top == null ? null : top.move;
                }
                if (mv == null) throw new IOException("引擎没有返回落子");
                final String moveText = mv;
                final double wr = ar.rootWinrate();
                final String bestPv = ar.top() == null ? "" : ar.top().pv;
                ui.post(() -> onEngineMoveDone(gen, color, moveText, wr, bestPv));
            } catch (IOException ex) {
                final String msg = ex.getMessage();
                // 超时之后引擎的响应流已经错位，继续用只会读到上一条的残留，
                // 所以这里直接重启引擎并重放当前局面，让对局能继续下去。
                final boolean isTimeout = (ex instanceof GtpEngine.GtpTimeout);
                // 引擎已经不能用了：超时会让它进入 poisoned（之后每条命令都直接抛），
                // 进程被 OOM 杀掉则 isAlive() 为 false。两种都只能换一个引擎对象。
                final boolean engineDead = isTimeout || !e.isAlive();
                ui.post(() -> {
                    // 重启引擎和「是哪一局」无关，必须无条件执行。
                    // 否则被换掉的那盘棋超时后，新局就永远对着一个只会报错的引擎 ——
                    // 而且没有别的自动恢复路径（restartEngine 全项目只有两个调用点）。
                    final boolean current = (gen == gameGen.get());
                    if (engineDead) {
                        if (current) {
                            toast(isTimeout ? "引擎响应超时，正在重启…" : "引擎进程已退出，正在重启…");
                        }
                        restartEngine();
                    }
                    if (!current) return;      // 上一局的报错，别去动新局的状态
                    if (!engineDead) {
                        thinking = false;
                        updateButtons();
                        setStatus("引擎出错");
                        showErrorDialog("引擎出错", msg);
                    }
                });
            }
        });
    }

    private void onEngineMoveDone(int gen, int color, String moveText, double winrate, String pv) {
        // 这一手属于已经被替换掉的那盘棋（新局 / 读谱）：直接丢。
        // 校验必须在 thinking = false 之前 —— 新一轮搜索可能已经在跑，
        // 那次搜索刚把 thinking 置 true，这里再置 false 就把 UI 误解锁了。
        if (gen != gameGen.get()) return;
        thinking = false;
        updateButtons();

        if (moveText.equalsIgnoreCase("resign")) {
            finishGame(Board.opponent(color) == Board.BLACK ? "B+R" : "W+R");
            return;
        }
        if (!Double.isNaN(winrate)) {
            lastWinrate = winrate;
            lastWinrateColor = color;
        }

        int point;
        try {
            point = Board.gtpToPoint(moveText, boardSize);
        } catch (IllegalArgumentException e) {
            point = Board.PASS;
        }

        if (point == Board.PASS) {
            consecutivePasses++;
            board.play(color, Board.PASS);
            setLastMove(Board.PASS);
        } else {
            String reason = board.illegalReason(color, point);
            if (reason != null) {
                // 引擎给了非法手：不静默兜底，直接报出来，否则盘面会和引擎悄悄错开
                showErrorDialog("引擎返回非法手",
                        "引擎想下 " + moveText + "，但本地规则判定为：" + reason
                                + "\n\n这通常意味着规则配置不一致，请把这一局的情况反馈出来。");
                gameOver = true;
                refreshStatus();
                return;
            }
            consecutivePasses = 0;
            board.play(color, point);
            setLastMove(point);
        }
        boardView.invalidate();
        refreshStatus();

        if (consecutivePasses >= 2) {
            finishGame(null);
            return;
        }
        if (autoPlay) {
            ui.postDelayed(this::maybeEngineMove, 400);
        } else {
            maybeEngineMove();
        }
    }

    private void toggleAutoPlay() {
        if (gameOver) {
            toast("先开新局");
            return;
        }
        autoPlay = !autoPlay;
        updateButtons();
        refreshStatus();
        if (autoPlay) maybeEngineMove();
    }

    private void undo() {
        if (thinking || engine == null) return;
        if (board.moves.size() < 2) {
            toast("没有可悔的棋");
            return;
        }
        cancelReview();
        int removed = 0;
        // 撤到轮到人走为止：人机模式撤 2 手，自动对弈撤 1 手。
        // 提子数/劫点不能在这里手工回滚，交给 rebuildBoardFromMoves() 整体重算。
        int want = autoPlay ? 1 : 2;
        while (removed < want && board.moves.size() > 0) {
            board.moves.remove(board.moves.size() - 1);
            removed++;
        }
        rebuildBoardFromMoves();
        gameOver = false;
        consecutivePasses = 0;
        boardView.setOwnership(null);
        boardView.setHighlight(new int[0]);
        refreshStatus();
        syncEngineBoardThenPlay();
    }

    /** 悔棋后按剩余着法重建盘面（提子/劫点都要重算，不能只回滚 grid）。 */
    private void rebuildBoardFromMoves() {
        java.util.List<int[]> history = new java.util.ArrayList<>(board.moves);
        Board fresh = new Board(boardSize, komi);
        // 摆子不在 board.moves 里，不补的话按一次「悔棋」盘上的摆子就没了
        applySetupStones(fresh);
        for (int[] m : history) {
            try {
                fresh.play(m[0], m[1]);
            } catch (Board.IllegalMove e) {
                break;
            }
        }
        board = fresh;
        boardView.setBoard(board);
        setLastMove(board.moves.isEmpty() ? Board.PASS
                : board.moves.get(board.moves.size() - 1)[1]);
    }

    private void resign() {
        if (gameOver || autoPlay) return;
        // 人认输：赢家永远是人的对手；不能看「轮到谁」——
        // 电脑思考时人点认输，轮到的是电脑，按旧写法会把赢家判成人（第 112 轮审计 中-2）。
        finishGame(WinEgg.resignResult(humanColor));
    }

    private void finishGame(String forcedResult) {
        boolean wasAutoPlay = autoPlay;
        // 终局之后不该再有引擎落子落地：把在飞的那次搜索作废。
        // 不作废的话，认输时正在跑的搜索回来会把一颗子加到已经终局的盘面上；
        // 更麻烦的是 startGameReview 拍快照时是 N 手、之后变成 N+1 手，
        // openReview() 的 review.n == board.moves.size() 就不成立，
        // 刚算好的复盘数据被丢掉，用户点「复盘」得重算整盘（19 路要一两分钟）。
        gameGen.incrementAndGet();
        thinking = false;
        gameOver = true;
        autoPlay = false;
        updateButtons();

        String result = forcedResult;
        if (result == null) {
            result = board.resultString();
        }
        lastResult = result;
        double[] s = board.scoreArea();
        String detail = String.format(java.util.Locale.US,
                "终局：%s（黑 %.1f : 白 %.1f，含贴目 %.1f）", result, s[0], s[1], komi);
        setStatus(detail);

        try {
            File f = Sgf.write(engineManager.gamesDir(), board,
                    humanColor == Board.BLACK ? "Human" : "AutoGo",
                    humanColor == Board.WHITE ? "Human" : "AutoGo",
                    result, loadedHandicap, loadedSetup, firstColor);
            setInfo("已存棋谱：" + f.getName() + "　点「复盘」让引擎逐手分析");
        } catch (IOException e) {
            setInfo("棋谱保存失败：" + e.getMessage());
        }
        refreshStatus();
        if (!wasAutoPlay && WinEgg.eggAllowed(WinEgg.goLevel(modelAlias), 0)
                && WinEgg.goHumanWon(result, humanColor)) {
            // 只有「普通 / 强」档（网络）赢了才弹：快档不弹；自动对弈没有「人」，也不弹
            WinEgg.showWin(this);
        }
        startGameReview(result);
    }

    /**
     * 终局后在后台逐手分析整盘，回答「这一局为什么赢」。
     *
     * 跑在 engineExec 上（单线程），所以和落子/搜等引擎操作天然串行，不会串包。
     * 用户可以随时开新局或悔棋 —— 那时 reviewGen 自增，分析在下一手前退出。
     */
    private void startGameReview(final String result) {
        cancelReview();
        if (engine == null || board == null || board.moves.size() < 2) return;

        final List<int[]> snapshot = new ArrayList<>(board.moves);
        final double[] score = board.scoreArea();
        final int total = snapshot.size();
        final int first0 = firstColor;
        final String start0 = setupOnlySnapshot();
        final File tmp0 = new File(getCacheDir(),
                "autogo-review-" + Long.toHexString(System.nanoTime()) + ".sgf");
        final int gen = reviewGen.incrementAndGet();
        setInfo("正在分析胜负原因… 0 / " + total);

        postEngine(() -> {
            // 和其它几处一样先取局部变量：restartEngine() 会把 engine 字段置空，
            // worker 里直接读字段会 NPE —— 而 NPE 不是 IOException，下面的 catch 接不住，
            // 会从 worker 线程逃出去把进程干掉。
            final GtpEngine eng = engine;
            if (eng == null) {
                ui.post(() -> {
                    if (gen != reviewGen.get()) return;
                    setInfo("胜负分析中断：引擎未就绪（点「复盘」可重试）");
                });
                return;
            }
            final GameReview r;
            try {
                r = GameReview.analyze(eng, snapshot, boardSize, komi, visits,
                        first0, start0, tmp0,
                        (rev, idx, tot) -> {
                            if (gen != reviewGen.get()) return false;
                            if (idx % 3 == 0 || idx == tot) {
                                ui.post(() -> {
                                    if (gen == reviewGen.get()) {
                                        setInfo("正在分析胜负原因… " + idx + " / " + tot);
                                    }
                                });
                            }
                            return true;
                        });
            } catch (IOException ex) {
                final String msg = ex.getMessage();
                ui.post(() -> {
                    if (gen != reviewGen.get()) return;
                    setInfo("胜负分析中断：" + msg + "（点「复盘」可重试）");
                    resyncEngineAfterReview();
                });
                return;
            } catch (RuntimeException ex) {
                // 兜住 NPE / ConcurrentModificationException 之类：
                // 它们不是 IOException，任其从 worker 线程逃出去同样是闪退。
                // 带上栈顶位置：容器里读不到 logcat，用户截图的这行就是唯一线索。
                StackTraceElement[] st = ex.getStackTrace();
                final String msg = st.length > 0
                        ? ex.getClass().getSimpleName() + " @ " + st[0]
                        : String.valueOf(ex);
                ui.post(() -> {
                    if (gen != reviewGen.get()) return;
                    setInfo("胜负分析异常：" + msg + "（点「复盘」可重试）");
                    resyncEngineAfterReview();
                });
                return;
            }
            ui.post(() -> {
                // 取消时的善后交给取消方（新局/悔棋本来就会重新同步引擎），
                // 这里再插一次会和它抢执行顺序。
                if (gen != reviewGen.get()) return;
                review = r;
                GameReview.Verdict v = r.verdict(result, snapshot);
                setStatus("终局：" + v.oneLine());
                setInfo(String.format(java.util.Locale.US,
                        "黑 %.1f : 白 %.1f（含贴目 %.1f）　%s　点「复盘」看逐手明细",
                        score[0], score[1], komi,
                        v.worstIndex >= 0
                                ? "关键在第 " + (v.worstIndex + 1) + " 手" : "无明显失误")
                        + (r.startLoaded ? ""
                                : "　（注意：这盘的起点局面没能交给引擎，复盘是按空盘重放的，只作参考）"));
                resyncEngineAfterReview();
            });
        });
    }

    /** 停掉后台分析（新局、悔棋时调用），并丢弃已经不可信的结果。 */
    private void cancelReview() {
        reviewGen.incrementAndGet();
        review = null;
    }

    /**
     * 复盘把引擎盘面带偏了（最后一手的分析没有 undo），重放一遍把引擎对齐到当前盘面。
     * 失败也不致命：下一次落子前还会再同步一次。
     */
    private void resyncEngineAfterReview() {
        if (board == null) return;
        final List<int[]> snapshot = new ArrayList<>(board.moves);
        final int size = boardSize;
        final double k = komi;
        final String setupSgf = setupSgfSnapshot();
        postEngine(() -> {
            GtpEngine e = engine;
            if (e == null) return;
            try {
                syncEnginePosition(e, size, k, snapshot, setupSgf);
            } catch (IOException ignored) {
                // 对齐失败无所谓，下一手还会同步
            }
        });
    }

    // ------------------------------------------------------------------
    // 复盘
    // ------------------------------------------------------------------

    private void openReview() {
        if (board == null || board.moves.isEmpty()) {
            toast("还没有棋可复盘");
            return;
        }
        if (engine == null) {
            toast("引擎未就绪");
            return;
        }
        Intent i = new Intent(this, ReviewActivity.class);
        i.putExtra(ReviewActivity.EXTRA_SIZE, boardSize);
        i.putExtra(ReviewActivity.EXTRA_KOMI, komi);
        i.putExtra(ReviewActivity.EXTRA_MOVES, movesToIntArray());
        i.putExtra(ReviewActivity.EXTRA_VISITS, visits);
        i.putExtra(ReviewActivity.EXTRA_MODEL, modelAlias);
        i.putExtra(ReviewActivity.EXTRA_FIRST, firstColor);
        i.putExtra(ReviewActivity.EXTRA_START, setupOnlySnapshot());
        // 终局已经逐手分析过就直接把结果带过去，省掉一次完整复盘（19 路要等一两分钟）
        if (review != null && !review.cancelled && review.n == board.moves.size()) {
            // 复盘页会用这批数、不再自己算，所以「起点是否可信」也得跟着传过去，
            // 否则页面上那行「只作参考」的提示会消失（数字还是在错起点上算的）。
            i.putExtra(ReviewActivity.EXTRA_START_OK, review.startLoaded);
            i.putExtra(ReviewActivity.EXTRA_WR, review.wrToMove);
            i.putExtra(ReviewActivity.EXTRA_LOSS, review.loss);
            i.putExtra(ReviewActivity.EXTRA_BEST, review.bestMove);
            i.putExtra(ReviewActivity.EXTRA_BESTPV, review.bestPv);
            try {
                i.putExtra(ReviewActivity.EXTRA_VERDICT,
                        review.verdict(lastResult, board.moves).oneLine());
            } catch (RuntimeException ignored) {
                // 摘要算不出来不影响看明细
            }
        }
        startActivity(i);
    }

    private int[] movesToIntArray() {
        int[] out = new int[board.moves.size() * 2];
        for (int i = 0; i < board.moves.size(); i++) {
            out[i * 2] = board.moves.get(i)[0];
            out[i * 2 + 1] = board.moves.get(i)[1];
        }
        return out;
    }

    // ------------------------------------------------------------------
    // 读谱
    // ------------------------------------------------------------------

    private static final int REQ_OPEN_SGF = 1001;

    private void importSgf() {
        if (thinking) {
            toast("引擎思考中，稍后再试");
            return;
        }
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            startActivityForResult(i, REQ_OPEN_SGF);
        } catch (android.content.ActivityNotFoundException e) {
            showErrorDialog("打不开文件选择器", "这台设备上没有可用的文件选择器。\n" + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_OPEN_SGF || resultCode != RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        setStatus("正在读取棋谱…");
        postEngine(() -> {
            try {
                String text = readText(uri);
                Sgf.Parsed parsed = Sgf.parse(text);
                ui.post(() -> loadParsedGame(parsed));
            } catch (Exception ex) {
                final String msg = ex.getMessage();
                ui.post(() -> {
                    setStatus("");
                    showErrorDialog("读取棋谱失败", msg);
                });
            }
        });
    }

    /** 棋谱文件大小上限。文件选择器用的是 *\/*，用户可能误选视频之类的大文件。 */
    private static final int MAX_SGF_BYTES = 4 << 20;   // 4 MB

    private String readText(Uri uri) throws IOException {
        try (java.io.InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("打不开这个文件");
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[1 << 16];
            int n, total = 0;
            while ((n = in.read(buf)) > 0) {
                total += n;
                // 不设上限的话，误选一个大文件会把整个读进内存然后 OOM 崩掉
                if (total > MAX_SGF_BYTES) {
                    throw new IOException("文件超过 " + (MAX_SGF_BYTES >> 20)
                            + " MB，不像是棋谱。请选择 .sgf 文件。");
                }
                bos.write(buf, 0, n);
            }
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private void loadParsedGame(Sgf.Parsed parsed) {
        // 换了一盘棋：在飞的搜索和复盘都要作废。
        // 不作废复盘的话，读入一盘手数相同的谱时 openReview() 的
        // review.n == board.moves.size() 会成立，于是把上一局的复盘曲线和摘要显示出来；
        // 而且旧分析还在 engineExec 里占着队，新对局的同步排在它后面，
        // 界面一直显示「思考中」却不动（悔棋/复盘也被 thinking 挡住）。
        gameGen.incrementAndGet();
        cancelReview();
        board = parsed.board;
        boardSize = board.size;
        komi = board.komi;
        autoPlay = false;
        thinking = false;
        consecutivePasses = 0;
        lastWinrate = Double.NaN;
        lastWinrateColor = Board.EMPTY;
        lastResult = parsed.result();
        // 导入的死活题/让子谱带 AB/AW/HA，保存时得原样写回去，
        // 否则文件里的摆子会静默消失（屏幕上明明有子）。
        loadedSetup = parsed.setup;
        loadedHandicap = parsed.handicap;
        firstColor = parsed.firstColor;

        boardView.setBoard(board);
        setLastMove(board.moves.isEmpty() ? Board.PASS
                : board.moves.get(board.moves.size() - 1)[1]);
        boardView.setOwnership(null);
        boardView.setHighlight(new int[0]);

        int n = board.moves.size();
        boolean endedByPass = n >= 2
                && board.moves.get(n - 1)[1] == Board.PASS
                && board.moves.get(n - 2)[1] == Board.PASS;
        // 已经下完的谱就别再让引擎接着走了，直接等用户点「复盘」
        gameOver = endedByPass;
        savePrefs();
        refreshStatus();
        // gameOver 时 refreshStatus() 会提前 return、不写状态栏，这里补上
        setStatus(String.format(java.util.Locale.US, "已读入棋谱：%d 手，%d 路%s",
                n, board.size, parsed.result().isEmpty() ? "" : "，结果 " + parsed.result()));
        setInfo(String.format(java.util.Locale.US,
                "已读入 %d 手（%d 路，贴 %.1f）%s",
                n, board.size, board.komi,
                parsed.result().isEmpty() ? "" : "，棋谱记录结果 " + parsed.result())
                // 这两条是「读进来的比文件里的少」的如实提示：解析器读到分支点就停
                // （合法 SGF 的注释谱、教材谱很常见），多局文件只读第一局。
                // 「变了图」和「因为变化图而丢了主线后续手」是两回事，得分开说：
                // 前者一手没丢（合法谱常见），后者是真丢手（A31）。共用一句会让
                // 「没丢手」的用户以为丢了手，而真丢手的用户看不出严重性。
                + (parsed.mainLineTruncated
                        ? "　（主线在变化图之后还有手，只读入了前 " + n + " 手）"
                        : parsed.hasVariations ? "　（本谱含变化图，分支未读入）" : "")
                + (parsed.multiGame ? "　（此文件含多局，只读入第 1 局）" : "")
                + (gameOver ? "　点「复盘」让引擎逐手分析" : "　可继续下"));
        if (!gameOver) syncEngineBoardThenPlay();
    }

    // ------------------------------------------------------------------
    // 对话框
    // ------------------------------------------------------------------

    private void showNewGameDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, p, p, p);

        TextView t1 = new TextView(this);
        t1.setText("棋盘大小");
        box.addView(t1);
        RadioGroup sizes = new RadioGroup(this);
        sizes.setOrientation(RadioGroup.HORIZONTAL);
        int[] opts = {9, 13, 19};
        for (int o : opts) {
            RadioButton rb = new RadioButton(this);
            rb.setText(o + " 路");
            rb.setId(o);
            rb.setChecked(o == boardSize);
            sizes.addView(rb);
        }
        box.addView(sizes);

        TextView t2 = new TextView(this);
        t2.setText("我执");
        t2.setPadding(0, dp(10), 0, 0);
        box.addView(t2);
        RadioGroup colors = new RadioGroup(this);
        colors.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbBlack = new RadioButton(this);
        rbBlack.setText("黑（先手）");
        rbBlack.setId(Board.BLACK);
        rbBlack.setChecked(humanColor == Board.BLACK);
        RadioButton rbWhite = new RadioButton(this);
        rbWhite.setText("白（后手）");
        rbWhite.setId(Board.WHITE);
        rbWhite.setChecked(humanColor == Board.WHITE);
        colors.addView(rbBlack);
        colors.addView(rbWhite);
        box.addView(colors);

        TextView t3 = new TextView(this);
        t3.setText("每手 visits（越大越强越慢）");
        t3.setPadding(0, dp(10), 0, 0);
        box.addView(t3);
        EditText visitsInput = new EditText(this);
        visitsInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        visitsInput.setText(String.valueOf(visits));
        box.addView(visitsInput);

        // 横屏时对话框可用高度很小，内容会溢出，所以套一层 ScrollView
        ScrollView newGameScroller = new ScrollView(this);
        newGameScroller.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("开新局")
                .setView(newGameScroller)
                .setPositiveButton("开始", (d, w) -> {
                    int sel = sizes.getCheckedRadioButtonId();
                    if (sel > 0) boardSize = sel;
                    humanColor = colors.getCheckedRadioButtonId() == Board.WHITE ? Board.WHITE : Board.BLACK;
                    try {
                        int v = Integer.parseInt(visitsInput.getText().toString().trim());
                        if (v > 0) visits = v;
                    } catch (NumberFormatException ignored) {
                    }
                    savePrefs();
                    autoPlay = false;
                    newGame(true);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSettingsDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        box.setPadding(p, p, p, p);

        TextView tth = new TextView(this);
        tth.setText("界面主题");
        box.addView(tth);
        RadioGroup themeGroup = new RadioGroup(this);
        themeGroup.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < Theme.MODE_COUNT; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(Theme.LABELS[i] + "　" + Theme.NOTES[i]);
            // id 从 100 起，避开下面 modelGroup 用的 1..N
            rb.setId(100 + i);
            rb.setChecked(i == themeMode);
            themeGroup.addView(rb);
        }
        box.addView(themeGroup);

        TextView tm = new TextView(this);
        tm.setText("引擎网络");
        tm.setPadding(0, dp(10), 0, 0);
        box.addView(tm);
        RadioGroup modelGroup = new RadioGroup(this);
        modelGroup.setOrientation(RadioGroup.VERTICAL);
        for (int i = 0; i < EngineManager.MODELS.length; i++) {
            EngineManager.ModelSpec m = EngineManager.MODELS[i];
            RadioButton rb = new RadioButton(this);
            rb.setText(m.label + "　" + m.note);
            rb.setId(i + 1);
            rb.setChecked(m.alias.equals(modelAlias));
            modelGroup.addView(rb);
        }
        box.addView(modelGroup);

        TextView t0 = new TextView(this);
        t0.setText("线程数（当前 " + threads + " / " + Runtime.getRuntime().availableProcessors() + " 核）");
        t0.setPadding(0, dp(10), 0, 0);
        box.addView(t0);
        EditText threadInput = new EditText(this);
        threadInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        threadInput.setText(String.valueOf(threads));
        box.addView(threadInput);

        TextView t1 = new TextView(this);
        t1.setText("贴目");
        t1.setPadding(0, dp(10), 0, 0);
        box.addView(t1);
        EditText komiInput = new EditText(this);
        komiInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        komiInput.setText(String.valueOf(komi));
        box.addView(komiInput);

        CheckBox resignBox = new CheckBox(this);
        resignBox.setText("允许引擎认输");
        resignBox.setChecked(allowResign);
        resignBox.setPadding(0, dp(10), 0, 0);
        box.addView(resignBox);

        TextView diag = new TextView(this);
        diag.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        diag.setTextColor(palette.textDim);
        diag.setPadding(0, dp(10), 0, 0);
        // 把读到的屏幕信息和版本号也列出来
        StringBuilder diagText = new StringBuilder();
        diagText.append("陪练棋盘 v").append(getVersionName()).append('\n');
        if (screen != null) {
            diagText.append(screen.describe()).append('\n');
            diagText.append("棋盘边长 ").append(screen.boardSizeDp(8)).append(" dp");
        }
        diagText.append('\n').append(TextUtils.join("\n", engineManager.diagnostics()));
        diag.setText(diagText.toString());
        box.addView(diag);

        // 设置项变多了（三个网络 + 线程 + 贴目 + 自检信息），小屏上会超出屏幕，
        // 套一层 ScrollView。这里的子视图都是 WRAP_CONTENT，不受 UNSPECIFIED 测量影响。
        ScrollView settingsScroller = new ScrollView(this);
        settingsScroller.addView(box);
        new AlertDialog.Builder(this)
                .setTitle("设置")
                .setView(settingsScroller)
                .setPositiveButton("保存并重启引擎", (d, w) -> {
                    try {
                        int t = Integer.parseInt(threadInput.getText().toString().trim());
                        if (t > 0) threads = Math.min(t, Runtime.getRuntime().availableProcessors() * 2);
                    } catch (NumberFormatException ignored) {
                    }
                    try {
                        komi = Double.parseDouble(komiInput.getText().toString().trim());
                    } catch (NumberFormatException ignored) {
                    }
                    allowResign = resignBox.isChecked();
                    int pickedTheme = themeGroup.getCheckedRadioButtonId();
                    if (pickedTheme >= 100 && pickedTheme < 100 + Theme.MODE_COUNT) {
                        setThemeMode(pickedTheme - 100);
                    }
                    int picked = modelGroup.getCheckedRadioButtonId();
                    if (picked >= 1 && picked <= EngineManager.MODELS.length) {
                        String newAlias = EngineManager.MODELS[picked - 1].alias;
                        if (!newAlias.equals(modelAlias)) {
                            modelAlias = newAlias;
                            visits = visitsFor(modelAlias);   // 换成该档自己的数值
                            toast("已切到 " + EngineManager.model(modelAlias).label
                                    + "，" + visits + " visits");
                        }
                    }
                    savePrefs();
                    restartEngine();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void restartEngine() {
        // 退出流程里不要重启：onDestroy 已经 close() + shutdownNow()，
        // 此时再往执行器里丢任务会抛 RejectedExecutionException ——
        // 它抛在主线程、没人接，进程直接被杀。
        // 最容易踩到的路径：引擎正在思考时按返回键 —— 被中断的搜索会被判成
        // engineDead，回调里无条件重启，就正好打在已经关掉的执行器上。
        if (!canPost()) return;
        engineReady = false;
        thinking = false;
        // 换引擎同样作废在飞的那次搜索：否则它的回调还能通过代号校验，
        // 把「用户落子之前的局面」算出来的那一手盖到现在的盘面上
        // （轻则多一子，重则命中「引擎返回非法手」直接把对局判终局）。
        gameGen.incrementAndGet();
        GtpEngine old = engine;
        engine = null;
        postEngine(() -> {
            if (old != null) old.close();
            ui.post(this::startEngineThenSync);
        });
    }

    /**
     * 取某个网络的 visits。三个档位的速度差十几倍，共用一个数值没法用 ——
     * b18 用 300 visits 一手要一分钟，b6c96 用 40 又太浪费。所以按网络分别记。
     */
    private int visitsFor(String alias) {
        EngineManager.ModelSpec m = EngineManager.model(alias);
        return getSharedPreferences(PREF, MODE_PRIVATE)
                .getInt("visits_" + m.alias, m.recommendedVisits);
    }

    /** 从 PackageManager 读 versionName，避免在代码里硬编码版本号。 */
    private String getVersionName() {
        try {
            return getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    // ------------------------------------------------------------------
    // 进程被回收后把对局接回来
    // ------------------------------------------------------------------

    private static final String STATE_MOVES = "autogo.moves";
    private static final String STATE_SIZE = "autogo.size";
    private static final String STATE_KOMI = "autogo.komi";
    private static final String STATE_HUMAN = "autogo.human";
    private static final String STATE_OVER = "autogo.over";
    private static final String STATE_RESULT = "autogo.result";
    // 摆子/让子也要进 Bundle：只存 moves 的话，进程被回收再回来摆子就没了，
    // 之后保存出来的棋谱比屏幕上少子（第 8 轮 T1 的症状在这条路径上复现）。
    private static final String STATE_SETUP = "autogo.setup";
    private static final String STATE_HANDICAP = "autogo.handicap";
    private static final String STATE_FIRST = "autogo.firstcolor";

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (board == null) return;
        // 中盘的棋谱只在终局时才写 SGF，所以进程一被系统回收，这一局就彻底没了。
        // 这里把当前局面塞进 Bundle，重建时能接着下。
        int n = board.moves.size();
        int[] flat = new int[n * 2];
        for (int i = 0; i < n; i++) {
            int[] m = board.moves.get(i);
            flat[i * 2] = m[0];
            flat[i * 2 + 1] = m[1];
        }
        out.putIntArray(STATE_MOVES, flat);
        out.putInt(STATE_SIZE, boardSize);
        out.putDouble(STATE_KOMI, komi);
        out.putInt(STATE_HUMAN, humanColor);
        out.putBoolean(STATE_OVER, gameOver);
        out.putString(STATE_RESULT, lastResult);
        out.putString(STATE_SETUP, loadedSetup);
        out.putString(STATE_HANDICAP, loadedHandicap);
        out.putInt(STATE_FIRST, firstColor);
    }

    /** 从 Bundle 接回上一局。返回 false 表示没有可恢复的局面（那就开新局）。 */
    private boolean restoreGame(Bundle b) {
        int[] flat = b.getIntArray(STATE_MOVES);
        String setup = b.getString(STATE_SETUP, "");
        if (setup == null) setup = "";
        if (flat == null || flat.length % 2 != 0) return false;
        // 0 手但有摆子的题谱也是有效局面（原来 0 手直接当「没得恢复」丢了）
        if (flat.length == 0 && setup.isEmpty()) return false;
        int size = b.getInt(STATE_SIZE, boardSize);
        double k = b.getDouble(STATE_KOMI, komi);
        loadedSetup = setup;
        loadedHandicap = b.getString(STATE_HANDICAP, "");
        firstColor = b.getInt(STATE_FIRST, Board.BLACK);
        Board restored = new Board(size, k);
        applySetupStones(restored);   // 必须在落子之前：手顺可能吃掉摆子
        for (int i = 0; i < flat.length; i += 2) {
            // 存档坏了（颜色错位、非法手）就老实开新局，别把脏局面接着下
            if (restored.illegalReason(flat[i], flat[i + 1]) != null) return false;
            restored.play(flat[i], flat[i + 1]);
        }
        boardSize = size;
        komi = k;
        humanColor = b.getInt(STATE_HUMAN, humanColor);
        board = restored;
        consecutivePasses = 0;
        lastWinrate = Double.NaN;
        lastWinrateColor = Board.EMPTY;
        gameOver = b.getBoolean(STATE_OVER, false);
        lastResult = b.getString(STATE_RESULT, "");

        boardView.setBoard(board);
        setLastMove(flat[flat.length - 1]);
        boardView.setOwnership(null);
        boardView.setHighlight(new int[0]);
        refreshStatus();
        setStatus(String.format(java.util.Locale.US, "已接回上一局：%d 手，%d 路，贴 %.1f",
                board.moves.size(), boardSize, komi));
        if (!gameOver) {
            if (!engineReady) startEngineThenSync();
            else syncEngineBoardThenPlay();
        }
        return true;
    }

    // ------------------------------------------------------------------
    // 黑夜 / 白天
    // ------------------------------------------------------------------

    /** 按「模式 + 系统深色开关 + 当前钟点」算出该用哪套配色。 */
    private void resolveTheme() {
        dark = Theme.isDark(themeMode, systemDark(), currentHour());
        palette = Palette.of(dark);
    }

    private boolean systemDark() {
        int m = getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
        return m == android.content.res.Configuration.UI_MODE_NIGHT_YES;
    }

    private int currentHour() {
        return java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
    }

    /** 把当前配色刷到已有控件上（改设置、到点切换时用，不必重建界面）。 */
    private void applyPaletteToViews() {
        if (rootLayout != null && !fullscreen) {
            rootLayout.setBackgroundColor(palette.background);
        }
        if (statusView != null) statusView.setTextColor(palette.text);
        if (infoView != null) infoView.setTextColor(palette.textDim);
        if (dividerView != null) dividerView.setBackgroundColor(palette.divider);
        if (statusPanelView != null) statusPanelView.setBackgroundColor(palette.surface);
        if (boardView != null) boardView.setPalette(palette);
        if (winrateBar != null) winrateBar.setPalette(palette);
        for (Button b : themeButtons) {
            b.setTextColor(palette.buttonText);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.buttonBg));
        }
    }

    /** 改主题模式：记住、立刻生效。 */
    private void setThemeMode(int mode) {
        themeMode = Theme.clamp(mode);
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putInt("themeMode", themeMode).apply();
        resolveTheme();
        // setTheme 之后新建的对话框才会是正确的一明一暗，当前界面则靠 applyPaletteToViews 刷新
        setTheme(dark ? android.R.style.Theme_Material_NoActionBar
                : android.R.style.Theme_Material_Light_NoActionBar);
        applyPaletteToViews();
    }

    /** 「按时间自动」下，过点了就换 —— 回到前台时、以及每分钟各查一次。 */
    private void maybeAutoSwitchTheme() {
        if (themeMode != Theme.AUTO) return;
        boolean now = Theme.isDark(themeMode, systemDark(), currentHour());
        if (now != dark) {
            setThemeMode(themeMode);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 可能是搁了一夜才回来，先判一次；之后按分钟盯着
        maybeAutoSwitchTheme();
        ui.removeCallbacks(themeTick);
        ui.postDelayed(themeTick, 60_000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(themeTick);
    }

    private void savePrefs() {
        getSharedPreferences(PREF, MODE_PRIVATE).edit()
                .putInt("size", boardSize)
                .putInt("visits_" + modelAlias, visits)
                .putLong("komi", Double.doubleToLongBits(komi))
                .putInt("visits", visits)
                .putInt("humanColor", humanColor)
                .putBoolean("allowResign", allowResign)
                .putString("model", modelAlias)
                .apply();
    }

    /**
     * 引擎命令失败后的统一善后。必须在 UI 线程调用。
     *
     * 引擎一旦不可用（进程被杀，或超时后进入 poisoned）自己不会好。
     * 以前只有 engineMove 那一条路径会重启引擎，人落子 / 过一手 / 复盘后同步
     * 这几条只会一直弹「与引擎通信失败」，用户只能自己去设置里手动重启。
     */
    private void handleEngineError(GtpEngine e, IOException ex) {
        if (e != null && (ex instanceof GtpEngine.GtpTimeout || !e.isAlive())) {
            toast("引擎不可用，正在重启…");
            restartEngine();
        } else {
            showErrorDialog("与引擎通信失败", ex.getMessage());
        }
    }

    private void showErrorDialog(String title, String msg) {
        if (!alive()) return;
        // 去重：同一条错误可能连续触发（例如引擎起不来时，每点一次「新局」都会走
        // 到这里一次）。原先每次都 new 一个对话框 show() 出来，窗口会一个个叠上去，
        // 用户得连按十几次「知道了」。已经在显示就只换文字。
        String text = msg == null ? "（无详情）" : msg;
        if (errorDialog != null && errorDialog.isShowing()) {
            errorDialog.setTitle(title);
            errorDialog.setMessage(text);
            return;
        }
        errorDialog = new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(text)
                .setPositiveButton("知道了", null)
                .create();
        errorDialog.show();
    }

    /** 当前正在显示的错误对话框，用来去重（见 showErrorDialog）。 */
    private AlertDialog errorDialog;

    // ------------------------------------------------------------------
    // 状态刷新
    // ------------------------------------------------------------------

    private void toggleNumbers() {
        showNumbers = !showNumbers;
        boardView.setShowNumbers(showNumbers);
        toast(showNumbers ? "显示手数（含被提子）" : "隐藏手数");
    }

    /**
     * 进入/退出全屏棋盘模式。
     * 全屏时隐藏状态栏和控件区，棋盘占满屏幕，左上角点「← 返回」退出。
     */
    private void toggleFullscreen() {
        // 延后到当前这轮触摸派发结束再改视图树，别在按钮的 onTouchEvent 里动层次结构
        rootLayout.post(() -> {
            if (fullscreen) {
                exitFullscreen();
            } else {
                enterFullscreen();
            }
        });
    }

    private boolean fullscreen = false;
    private int restoreSystemUi;

    @android.annotation.SuppressLint("InlinedApi")
    private void applyFullscreenFlags() {
        getWindow().getDecorView().setSystemUiVisibility(
                android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                        | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    /**
     * 进入全屏外观：**就地**隐藏控件区、把棋盘撑满，不重建内容视图。
     *
     * 早先的做法是新建一个只含棋盘的布局，再 boardView 搬过去。但那会在
     * boardView 已有 parent（root）的情况下再 addView，直接抛
     * IllegalStateException: The specified child already has a parent —— 按一下全屏就闪退。
     * 就地改可见性和布局参数没有这个问题，也不必在触摸派发过程中替换整棵视图树。
     */
    private void applyFullscreenLayout() {
        boardView.setFullscreen(true);
        // 退出回调延后到当前这轮触摸/消息处理结束再执行，避免在 onTouchEvent 里改视图树
        boardView.setOnExitFullscreen(() -> rootLayout.post(this::exitFullscreen));
        if (controlsArea != null) controlsArea.setVisibility(View.GONE);
        rootLayout.setBackgroundColor(0xFF000000);
        rootLayout.setPadding(0, 0, 0, 0);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        boardView.setLayoutParams(lp);
        rootLayout.requestLayout();
    }

    private void enterFullscreen() {
        if (fullscreen) return;
        fullscreen = true;
        // 先记住原系统栏状态，退出时还原
        restoreSystemUi = getWindow().getDecorView().getSystemUiVisibility();
        applyFullscreenFlags();
        applyFullscreenLayout();
    }

    private void exitFullscreen() {
        if (!fullscreen) return;
        fullscreen = false;
        getWindow().getDecorView().setSystemUiVisibility(restoreSystemUi);
        boardView.setFullscreen(false);
        // 整棵普通布局重建一次，背景、内边距、控件区可见性、棋盘尺寸全部复位
        rebuildLayout();
    }

    private void refreshStatus() {
        if (gameOver) {
            updateButtons();
            return;
        }
        int toMove = sideToMove();
        boolean mine = !autoPlay && toMove == humanColor;
        String who = autoPlay ? "自动对弈" : (mine ? "轮到你" : "引擎");
        StringBuilder sb = new StringBuilder();
        sb.append(who).append("（").append(Board.colorToChar(toMove)).append("）");
        sb.append("　难度：").append(WinEgg.goLevelName(modelAlias));
        sb.append("　第 ").append(board.moves.size() + 1).append(" 手");
        if (!Double.isNaN(lastWinrate)) {
            double wr = lastWinrate;
            double blackWr = GameReview.blackView(wr, lastWinrateColor);
            sb.append(String.format(java.util.Locale.US, "　黑胜率 %.1f%%", blackWr * 100));
        }
        setStatus(sb.toString());

        setInfo(String.format(java.util.Locale.US,
                "提子 黑%d 白%d　%s", board.captures[Board.BLACK], board.captures[Board.WHITE],
                gameOver ? lastResult : ""));

        // 胜率条
        if (winrateBar != null) {
            if (!Double.isNaN(lastWinrate)) {
                double blackWr = GameReview.blackView(lastWinrate, lastWinrateColor);
                winrateBar.setBlackWinrate(blackWr);
                winrateBar.setLabel("第 " + board.moves.size() + " 手　"
                        + (autoPlay ? "自动对弈" : (mine ? "你的回合" : "引擎回合")));
            } else {
                winrateBar.setBlackWinrate(Double.NaN);
                winrateBar.setLabel("第 " + board.moves.size() + " 手");
            }
        }

        updateButtons();
    }

    private void setStatus(String s) {
        lastStatusText = s;
        if (statusView != null) statusView.setText(s);
    }

    /**
     * 写信息条，同时记住内容 —— rebuildLayout 会重建控件，得把文字补回去。
     * 见 rebuildLayout 里的说明：gameOver 时 refreshStatus() 提前 return、不写这两行。
     */
    private void setInfo(String s) {
        lastInfoText = s;
        if (infoView != null) infoView.setText(s);
    }

    /** 上一次写进状态栏的文字，供 rebuildLayout 复原。 */
    private String lastStatusText = "";
    /** 上一次写进信息条的文字，供 rebuildLayout 复原。 */
    private String lastInfoText = "";
    /** 当前这盘棋是从哪份棋谱读来的摆子/让子（新局清空；保存时原样写回）。 */
    private String loadedSetup = "";
    private String loadedHandicap = "";
    /** 本局先走的一方（让子谱是白先）。见 {@link Sgf.Parsed#firstColor}。 */
    private int firstColor = Board.BLACK;

    private void updateButtons() {
        if (autoButton != null) autoButton.setText(autoPlay ? "暂停自动" : "自动对弈");
        if (undoButton != null) undoButton.setEnabled(!thinking && board != null && board.moves.size() >= 2);
        if (reviewButton != null) reviewButton.setEnabled(!thinking);
        boardView.setInputEnabled(!thinking && !gameOver && !autoPlay);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        GtpEngine e = engine;
        // 置空字段：worker 和 UI 回调里那些「engine == null 就跳过」的检查
        // 靠的就是它（onDestroy 前只 close()，字段仍指向已关闭的对象，检查全失效）。
        engine = null;
        // close() 会阻塞到引擎把 quit 排完队：空闲时 48/68 ms，在算长搜索时 0.66~3 秒（都是实测）。
        // 所以别压在主线程上。
        EngineManager.closeAsync(e);
        reviewGen.incrementAndGet();
        engineExec.shutdownNow();
    }
}
