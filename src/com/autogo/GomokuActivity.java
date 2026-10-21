/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 五子棋对局页：15 路、人执黑先手、内置 AI（{@link LocalGomokuAI}）应手。
 *
 * 按钮：新局 / 悔棋 / 认输 / 难度（快·普通·强）/ 禁手（黑棋长连·四四·三三判负）/ 手顺 / 全屏 / 复盘 / 返回。
 * 「复盘」把当前手顺交给 {@link GomokuReview} 逐手算一遍（快档，后台线程），算完打开
 * {@link GomokuReviewActivity} 看胜率曲线、失误排行与推荐点；算的过程中棋局变了就作废重来。
 * 终局在状态条写明结果，并弹一个「再来一局 / 返回」的对话框；获胜那串子会画一条实线。
 *
 * 主题走 {@link UiTheme}（与 MainActivity 同一份偏好、同一套判据），并像选择页那样自己补
 * onConfigurationChanged / onResume 两次重判、每分钟查一次自动主题过没过点 —— 清单声明了 configChanges，
 * 本页不会被系统重建。
 *
 * 设置：棋盘 13 / 15 / 19 路、「我执」黑或白（执白时电脑先走）。改设置默认下一局生效，
 * 空盘或终局时立刻生效。
 *
 * 状态保存：棋局只是一串落子点，进程内换页/重建（转屏、主题变化）时靠静态字段带过去；
 * 用户按返回离开本页（isFinishing）时清掉，下次进来是干净的新局。
 */
public class GomokuActivity extends android.app.Activity {

    /** 可选的棋盘路数（改的是「下一局」，空盘时立刻生效）。 */
    private static final int[] SIZES = {13, 15, 19};
    private static final String[] LEVEL_NAMES = {"快", "普通", "强"};

    /** 跨 recreate 保存棋局（只存落子顺序；黑先白后，颜色按手数奇偶推）。 */
    private static int[] savedMoves;
    private static boolean savedForbid;
    private static int savedLevel = LocalGomokuAI.NORMAL;
    /** 跨 recreate 保存设置：savedSize/savedHuman 是当前这盘，savedNext* 是下一盘要用的。 */
    private static int savedSize = 15;
    private static int savedHuman = GomokuBoard.BLACK;
    private static int savedNextSize = 15;
    private static int savedNextHuman = GomokuBoard.BLACK;
    /** 电脑对战开关（进程内重建也跟着恢复）。 */
    private static boolean savedAiVsAi;

    private GomokuBoard board;
    /** 当前这盘的参数（新局时从 nextSize/nextHuman 取过来）。 */
    private int size = 15;
    private int humanColor = GomokuBoard.BLACK;
    /** 「棋盘」「我执」两个开关改的是这两个：默认下一局生效，空盘时立刻生效。 */
    private int nextSize = 15;
    private int nextHuman = GomokuBoard.BLACK;
    private final GomokuEngine engine = new LocalGomokuAI();
    /** 「强」档用的外部引擎 Rapfi（懒启动）。找不到可执行文件就整个退回内置 AI。 */
    private RapfiGomokuEngine rapfi;
    private boolean rapfiChecked;
    private boolean rapfiOk;
    private int level = LocalGomokuAI.NORMAL;
    private boolean forbid = false;
    private boolean showNumbers = false;
    private boolean fullscreen = false;
    /** 由「实际可用空间」算出的棋盘边长（px）；0 表示还没算过，退回按屏宽估算。 */
    private int boardSidePx = 0;
    /** 学习库（学你 + 记住实战经验）；随进程重建重读。 */
    private GomokuLearn learn;
    private boolean aiThinking = false;
    /** 电脑对电脑：双方都由电脑下（用户要求「AI 对 AI」）。 */
    private boolean aiVsAi = false;

    /** 学习功能是否生效：只有人机模式、并且学习库可用时才记（电脑对战不进学习库）。 */
    private boolean learnEnabled() {
        return learn != null && !aiVsAi;
    }

    /** 复盘成功后，把人在这局里的大亏记进学习库（电脑对战不记）；真记了东西才落盘。 */
    private void maybeRecordBlunders(java.util.List<int[]> moveList, int boardSize, double[] loss) {
        if (!learnEnabled() || moveList == null || loss == null) return;
        try {
            if (learn.recordBlunders(moveList, boardSize, humanColor, loss, GomokuReview.BLUNDER_MIN) > 0) {
                saveLearn();
            }
        } catch (RuntimeException e) {
            // 记失误失败不影响看复盘
        }
    }
    /** 电脑对战里的暂停开关（点「对战」暂停 / 继续）。 */
    private boolean aiPaused = false;
    /**
     * 电脑思考的「代次」：暂停 / 新局 / 悔棋时 +1，用来作废还在算的那一手 ——
     * 否则「暂停时在算的一手」和「继续后新起的一手」会同时落到盘上（该点已有子）。
     */
    private int aiGeneration = 0;
    /** 正在后台算复盘（防重入）。 */
    private boolean reviewing = false;
    private boolean alive = true;

    private Palette palette;
    private Screen screen;
    /** 这一页当前是不是深色（用来判断要不要重铺）。 */
    private boolean dark;
    private String statusText = "";

    private ScrollView scroll;
    private LinearLayout root;
    private LinearLayout topChrome;
    private LinearLayout bottomChrome;
    private GomokuView boardView;
    private TextView status;

    /** 每分钟查一次「是不是过了自动模式的切换点」。 */
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable themeTick = new Runnable() {
        @Override
        public void run() {
            maybeAutoSwitchTheme();
            ui.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 先定窗口主题再建视图：深色下进这一页不会先闪一下白底。
        dark = UiTheme.apply(this);
        palette = Palette.of(dark);
        screen = Screen.read(this);
        learn = GomokuLearn.load(learnFile());
        if (savedMoves != null) {
            forbid = savedForbid;
            level = savedLevel;
        } else {
            // 上一局已结束时 saveState() 不保留棋局（savedMoves == null），但设置仍要记住：
            // 否则刚选的 19 路 / 执白会在页面被回收重建后退回默认（第 51 轮审计 低-2）。
            forbid = savedForbid;
            level = savedLevel;
            nextSize = savedNextSize;
            nextHuman = savedNextHuman;
        }
        buildUi();
        if (savedMoves != null) restoreGame();
        else newGame();
    }

    /** 系统深浅色变了：清单里声明了 uiMode，本页不会被重建，得自己重判、重铺。 */
    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshTheme(newConfig);
    }

    @Override
    protected void onResume() {
        super.onResume();
        alive = true;
        // 可能是搁了一夜才回来：先判一次过没过点，之后按分钟盯着（与选择页同一套）
        maybeAutoSwitchTheme();
        ui.removeCallbacks(themeTick);
        ui.postDelayed(themeTick, 60_000L);
        // 从设置页回来时档位本身可能变了：空局直接重建（丢不了东西），
        // 下到一半的棋局先留着，换色等下次进这一页（重建会把棋局清掉，那更糟）。
        boolean now = UiTheme.dark(this);
        if (now != dark) {
            dark = now;
            palette = Palette.of(now);
            setTheme(UiTheme.style(now));
            if (board == null || board.moves.size() <= 1) {
                recreate();
            } else {
                screen = Screen.read(this);
                buildUi();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(themeTick);
        saveState();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        if (rapfi != null) {
            rapfi.close();
            rapfi = null;
        }
        super.onDestroy();
        // 用户是「离开这一页」而不是系统重建：把跨页保存的棋局清掉
        if (isFinishing()) savedMoves = null;
    }

    /**
     * 系统配置变了（转屏 / 系统字号 / 密度 / 深浅色…）：清单声明了这些项由本页自己处理，不会重建。
     * 明暗真变了才换窗口主题；**界面一律重铺** —— 字号是在 setTextSize 时折成 px 的，
     * 不重铺就不会跟系统字号/屏幕尺寸走（对局页与复盘页都是无条件重铺，这里保持同一行为）。
     */
    private void refreshTheme(android.content.res.Configuration cfg) {
        boolean now = UiTheme.dark(UiTheme.mode(this), cfg);
        if (now != dark) {
            dark = now;
            palette = Palette.of(dark);
            setTheme(UiTheme.style(dark));
        }
        screen = Screen.read(this);
        buildUi();
    }

    /** 「按时间自动」下，过点了就换 —— 回到前台时、以及每分钟各查一次（与选择页同一套）。 */
    private void maybeAutoSwitchTheme() {
        if (UiTheme.mode(this) != Theme.AUTO) return;
        boolean now = UiTheme.dark(this);
        if (now != dark) {
            dark = now;
            palette = Palette.of(dark);
            setTheme(UiTheme.style(dark));
            screen = Screen.read(this);
            buildUi();
        }
    }

    // ==== 棋局 ====

    /** 「强」档的外部引擎在 nativeLibraryDir 里（和 KataGo 同一套：只有那个目录允许 App 执行）。 */
    private String rapfiPath() {
        try {
            return new java.io.File(getApplicationInfo().nativeLibraryDir, "librapfi.so").getAbsolutePath();
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** 「强」档是不是真用上了 Rapfi（结果缓存，免得每次查文件）。 */
    private boolean rapfiUsable() {
        if (!rapfiChecked) {
            rapfiChecked = true;
            String p = rapfiPath();
            rapfiOk = !p.isEmpty() && new java.io.File(p).canExecute();
        }
        return rapfiOk;
    }

    /** 这一手交给谁算：强档 + Rapfi 可用 ⇒ Rapfi；否则内置 AI。 */
    /** 外部强引擎 Rapfi 是 15×15 的引擎：非 15 路一律走内置 AI（第 51 轮审计 中-1）。 */
    private boolean rapfiUsableForThisBoard() {
        return size == 15 && rapfiUsable();
    }

    private GomokuEngine engineForMove() {
        if (level == LocalGomokuAI.STRONG && rapfiUsableForThisBoard()) {
            if (rapfi == null) rapfi = new RapfiGomokuEngine(rapfiPath());
            rapfi.setLevel(level);
            return rapfi;
        }
        engine.setLevel(level);
        return engine;
    }

    /** 状态条上的难度名字：强档没 Rapfi 时说实话。 */
    private String levelLabel() {
        if (level == LocalGomokuAI.STRONG && !rapfiUsableForThisBoard()) return LEVEL_NAMES[level] + "（内置）";
        return LEVEL_NAMES[level];
    }

    /** 轮到哪一方走（黑先白后，只看手数奇偶）。 */
    private int sideToMove() {
        return sideToMove(board == null ? 0 : board.moves.size());
    }

    private static int sideToMove(int plies) {
        return plies % 2 == 0 ? GomokuBoard.BLACK : GomokuBoard.WHITE;
    }

    /** 电脑执的颜色（跟人相反）。 */
    private int aiColor() {
        return GomokuBoard.opponent(humanColor);
    }

    private String startText() {
        if (aiVsAi) return "电脑对战：黑先，双方都由电脑下。";
        return humanColor == GomokuBoard.BLACK ? "你执黑先走。" : "你执白后走，电脑先下。";
    }

    /** 该电脑走就接着走：电脑对战时两边都是电脑；暂停时不走。 */
    private void maybeStartAiTurn() {
        if (board == null || board.gameOver || aiPaused) return;
        if (aiVsAi || sideToMove() != humanColor) startAiTurn();
    }

    private String optionsText() {
        return "棋盘：" + size + " 路　难度：" + levelLabel()
                + (forbid ? "　禁手：开" : "　禁手：关");
    }

    private void newGame() {
        aiGeneration++;
        size = nextSize;
        humanColor = nextHuman;
        board = new GomokuBoard(size, forbid);
        engine.setLevel(level);
        aiThinking = false;
        savedMoves = null;
        syncBoardView();
        setStatus(startText() + optionsText() + (aiPaused ? "（已暂停，点「对战」继续）" : ""));
        maybeStartAiTurn();
    }

    /** 换棋盘路数（13 / 15 / 19）。 */
    private void cycleSize() {
        int idx = 0;
        for (int i = 0; i < SIZES.length; i++) if (SIZES[i] == nextSize) idx = i;
        nextSize = SIZES[(idx + 1) % SIZES.length];
        afterSettingChange("棋盘改成 " + nextSize + " 路");
    }

    /** 换「我执」黑还是白（执白时电脑先走）。 */
    private void toggleHuman() {
        nextHuman = GomokuBoard.opponent(nextHuman);
        afterSettingChange(nextHuman == GomokuBoard.BLACK ? "你执黑先走" : "你执白后走（电脑先下）");
    }

    /** 设置变了：空盘（或已终局）就立刻按新设置开一局，否则点「新局」时生效。 */
    private void afterSettingChange(String what) {
        if (board == null || board.moves.isEmpty() || board.gameOver) {
            newGame();
            toast(what);
            return;
        }
        toast(what + "（点「新局」生效）");
        setStatus(what + "（点「新局」生效）。");
    }

    /** 重放保存的落子，恢复上次的棋局（转屏 / 主题变化后）。 */
    private void restoreGame() {
        size = savedSize;
        humanColor = savedHuman;
        nextSize = savedNextSize;
        nextHuman = savedNextHuman;
        aiVsAi = savedAiVsAi;
        aiPaused = false;
        List<int[]> list = new ArrayList<int[]>();
        for (int i = 0; i < savedMoves.length; i++) {
            list.add(new int[]{sideToMove(i), savedMoves[i]});
        }
        board = GomokuBoard.replay(list, size, forbid);
        engine.setLevel(level);
        aiThinking = false;
        syncBoardView();
        setStatus("棋局已恢复。");
        maybeStartAiTurn();
    }

    private void saveState() {
        savedForbid = forbid;
        savedLevel = level;
        savedSize = size;
        savedHuman = humanColor;
        savedNextSize = nextSize;
        savedNextHuman = nextHuman;
        savedAiVsAi = aiVsAi;
        // 已经分出胜负的棋局不再留着：下次进来是新局，免得「认输之后又满血复活」
        savedMoves = (board == null || board.gameOver) ? null : moveArray();
    }

    private int[] moveArray() {
        int[] out = new int[board.moves.size()];
        for (int i = 0; i < out.length; i++) out[i] = board.moves.get(i)[1];
        return out;
    }

    private void syncBoardView() {
        if (boardView == null) return;
        boardView.setPalette(palette);
        boardView.setShowNumbers(showNumbers);
        boardView.setBoard(board);
        boardView.setWinLine(board == null ? null : board.winLine);
        boardView.setLastMove(board == null || board.moves.isEmpty()
                ? GomokuBoard.PASS
                : board.moves.get(board.moves.size() - 1)[1]);
        boardView.setInputEnabled(board != null && !board.gameOver && !aiThinking);
    }

    private void onHumanTap(int point) {
        if (board == null || board.gameOver) return;
        if (aiVsAi) return;   // 电脑对战：人不参与
        if (aiThinking) {
            toast("电脑正在思考…");
            return;
        }
        if (sideToMove() != humanColor) return;   // 还没轮到你（例如你执白、电脑该先走）
        String why = board.illegalReason(humanColor, point);
        if (why != null) {
            toast("不能下这里：" + why);
            return;
        }
        play(humanColor, point);
        maybeStartAiTurn();
    }

    /** 学习库文件（放在 App 私有目录，卸载才清）。 */
    private File learnFile() {
        return new File(getFilesDir(), GomokuLearn.FILE_NAME);
    }

    /** 存学习库；存不下只是少学一点，绝不能因此崩。 */
    private void saveLearn() {
        if (learn == null) return;
        try {
            learn.save(learnFile());
        } catch (IOException e) {
            // 磁盘满 / 权限问题：忽略
        }
    }

    private void play(int color, int point) {
        board.play(color, point);
        saveState();
        syncBoardView();
        if (board.gameOver) finishGame();
    }

    private void finishGame() {
        boardView.setInputEnabled(false);
        // 记下这一盘（含和棋），下次它会更了解你
        if (board != null && learnEnabled() && learn.recordGame(board.moves, board.size, board.winner)) {
            saveLearn();
        }
        String text;
        if (board.winner == GomokuBoard.EMPTY) text = "和棋。";
        else if (aiVsAi) text = board.winner == GomokuBoard.BLACK ? "执黑的电脑赢了。" : "执白的电脑赢了。";
        else if (board.winner == humanColor) text = "你赢了！";
        else text = "电脑赢了。";
        setStatus(text + "（" + board.endReason + "）");
        showEndDialog(text);
    }

    private void startAiTurn() {
        if (board == null || board.gameOver) return;
        aiThinking = true;
        // 这一手替「现在轮到的一方」下：
        // 人机模式它就是电脑那方；电脑对战模式两边轮流，若一直用 aiColor() 会变成白棋连下（真机反馈：黑棋不出手）。
        final int mover = sideToMove();
        final int myGen = ++aiGeneration;
        boardView.setInputEnabled(false);
        setStatus("电脑思考中…");
        final GomokuBoard snapshot = board;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final GomokuEngine ai = engineForMove();
                int mv;
                String note = null;
                try {
                    mv = ai.genmove(snapshot, mover);
                } catch (RuntimeException e) {
                    // 外部引擎出了问题（进程没起来 / 超时 / 通信断了）：退回内置 AI，别让这一手作废
                    mv = engine.genmove(snapshot, mover);
                    note = "外部引擎出错，这一手改用内置 AI：" + e.getMessage();
                }
                // 学习：只在引擎给的候选里挑（历史上赢过的着法 / 把你带进你常犯错的局面）；
                // 没有经验可用时原样返回引擎那一手，所以绝不会因此下坏棋。
                if (mv >= 0 && learnEnabled()) {   // 电脑对电脑的棋不进学习库
                    try {
                        int[] ranked = engine.topMoves(snapshot, mover, 5);
                        mv = learn.pick(snapshot, mover, ranked, mv);
                    } catch (RuntimeException e) {
                        // 学习出问题绝不影响出招
                    }
                }
                final int fmv = mv;
                final String fnote = note;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        aiThinking = false;
                        // 认输 / 新局 / 悔棋之后这一手就作废：棋局换了对象或已结束，直接丢
                        if (!alive || aiGeneration != myGen || board != snapshot || board.gameOver) return;
                        if (fnote != null) toast(fnote);
                        if (fmv < 0) {
                            // 满盘时 gameOver 会先置位；真走到这里就当无处可下
                            setStatus("电脑无处可下。");
                            syncBoardView();
                            return;
                        }
                        play(mover, fmv);
                        if (!board.gameOver) {
                            setStatus(aiVsAi ? "电脑对战中…" : "轮到你落子。");
                            syncBoardView();
                            maybeStartAiTurn();
                        }
                    }
                });
            }
        }, "gomoku-ai");
        t.setDaemon(true);
        t.start();
    }

    private void undo() {
        if (board == null) return;
        if (aiVsAi) { toast("电脑对战不能悔棋，点「新局」重开。"); return; }
        // 电脑思考时也能悔棋：把它这一手打断（它回来时会被「board != snapshot」丢掉）
        aiThinking = false;
        aiGeneration++;
        if (board.gameOver) {
            toast("棋局已结束，开新局吧。");
            return;
        }
        int n = board.moves.size();
        if (n == 0) {
            toast("还没落子。");
            return;
        }
        // 退回到「轮到你走」：至少退一手，再看手数奇偶决定要不要多退一手
        //（黑先白后，颜色只看手数；你执白时「电脑应完再悔棋」同样能退回你的回合）
        int keep = n - 1;
        if (sideToMove(keep) != humanColor) keep--;
        if (keep < 0) keep = 0;
        // GomokuBoard 没有 undo（它给 AI 的搜索棋盘是只读契约），用重放重建；
        // 重放的口径（自由规则 + 重放完置回开关）统一在 GomokuBoard.replay 里
        List<int[]> list = new ArrayList<int[]>();
        for (int i = 0; i < keep; i++) list.add(board.moves.get(i));
        board = GomokuBoard.replay(list, size, forbid);
        saveState();
        syncBoardView();
        setStatus(keep == 0 ? "已悔棋，" + startText() : "已悔棋，轮到你落子。");
        if (!board.gameOver && sideToMove() != humanColor) startAiTurn();
    }

    private void resign() {
        if (board == null || board.gameOver) return;
        if (aiVsAi) { toast("电脑对战没有认输，点「新局」重开。"); return; }
        board.resign(humanColor);
        saveState();
        syncBoardView();
        finishGame();
    }

    private void cycleLevel() {
        level = (level + 1) % LEVEL_NAMES.length;
        engine.setLevel(level);
        savedLevel = level;
        toast("难度：" + levelLabel() + "（快 → 普通 → 强）");
        if (!board.gameOver) setStatus("难度改成「" + levelLabel() + "」，下一手生效。");
    }

    private void toggleForbid() {
        forbid = !forbid;
        savedForbid = forbid;
        if (board != null && !board.gameOver) board.forbiddenForBlack = forbid;
        toast(forbid ? "黑棋禁手：开（长连 / 四四 / 三三）" : "黑棋禁手：关（自由规则）");
        saveState();
    }

    private void toggleNumbers() {
        showNumbers = !showNumbers;
        boardView.setShowNumbers(showNumbers);
        toast(showNumbers ? "显示手顺。" : "隐藏手顺。");
    }

    // ==== 复盘 ====

    /** 点「复盘」：现算一份（快档，后台线程）。防重入 + 棋局变了作废。 */
    /** 「学习」：看它学到了什么，并可一键清空。 */
    private void showLearnDialog() {
        if (learn == null) return;
        new AlertDialog.Builder(this)
                .setTitle("陪练学习")
                .setMessage(learn.describe()
                        + "\n\n它只会从引擎给的候选里挑（赢过的着法 / 把你带进你常犯错的局面），"
                        + "没有经验可用时照旧走引擎那一手。")
                .setPositiveButton("知道了", null)
                .setNeutralButton("清除记录", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        learn.clear();
                        saveLearn();
                        toast("学习记录已清除。");
                    }
                })
                .show();
    }

    private void startReview() {
        if (board == null || reviewing) return;
        if (aiThinking) {
            // 电脑正在想：先让它下完。执白新局等第一手时也是空盘，但这时该说的是这一句
            // （次序不能反 —— 第 65 轮审计 G6 点出的老缺口）。
            toast("等电脑下完这一手再复盘。");
            return;
        }
        if (board.moves.isEmpty()) {
            // 空盘上没有可复盘的手顺（新局后立刻点「复盘」）：直接提示，别开一个空页面
            toast("还没有落子，没法复盘。");
            return;
        }
        reviewing = true;
        final List<int[]> snapshot = new ArrayList<int[]>();
        for (int i = 0; i < board.moves.size(); i++) {
            int[] m = board.moves.get(i);
            snapshot.add(new int[]{m[0], m[1]});
        }
        final int sz = board.size;
        final boolean fb = forbid;
        final int winner = board.winner;
        final String end = board.gameOver ? board.endReason : "";
        final String previous = statusText;
        setStatus("正在复盘…（" + snapshot.size() + " 手）");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final GomokuReview.Review r;
                try {
                    r = GomokuReview.analyze(snapshot, sz, fb, engine, LocalGomokuAI.FAST);
                } catch (final RuntimeException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            reviewing = false;
                            if (!alive || isFinishing()) return;
                            setStatus(previous);
                            toast("复盘失败：" + e.getMessage());
                        }
                    });
                    return;
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        reviewing = false;
                        if (!alive || isFinishing() || board == null) return;
                        if (board.moves.size() != snapshot.size()) {
                            // 棋局变了：别无条件写回「复盘前那句」——如果这中间有人已经写过状态条
                            // （典型是点了「新局」），写回去会把新局的状态覆盖掉（第 45 轮审计提示）。
                            // 只有当状态条还停在「正在复盘…」时才恢复原样。
                            if (statusText != null && statusText.startsWith("正在复盘")) {
                                setStatus(previous);
                            }
                            toast("棋局已经变了，再点一次「复盘」。");
                            return;
                        }
                        maybeRecordBlunders(snapshot, sz, r.loss);   // 电脑对电脑的棋不进学习库
                        setStatus(previous);
                        openReview(r, winner, end);
                    }
                });
            }
        }, "gomoku-review");
        t.setDaemon(true);
        t.start();
    }

    /** 把算好的复盘交给复盘页（数组口径与围棋复盘一致）。 */
    private void openReview(GomokuReview.Review r, int winner, String end) {
        Intent it = new Intent(this, GomokuReviewActivity.class);
        int[] raw = new int[r.moves.size() * 2];
        for (int i = 0; i < r.moves.size(); i++) {
            raw[i * 2] = r.moves.get(i)[0];
            raw[i * 2 + 1] = r.moves.get(i)[1];
        }
        it.putExtra(GomokuReviewActivity.EXTRA_MOVES, raw);
        it.putExtra(GomokuReviewActivity.EXTRA_SIZE, r.size);
        it.putExtra(GomokuReviewActivity.EXTRA_FORBID, r.forbiddenForBlack);
        it.putExtra(GomokuReviewActivity.EXTRA_FIRST, r.firstColor);
        it.putExtra(GomokuReviewActivity.EXTRA_WR, r.wrToMove);
        it.putExtra(GomokuReviewActivity.EXTRA_LOSS, r.loss);
        it.putExtra(GomokuReviewActivity.EXTRA_BEST, r.bestMove);
        it.putExtra(GomokuReviewActivity.EXTRA_BESTPT, r.bestPoint);
        it.putExtra(GomokuReviewActivity.EXTRA_VERDICT, GomokuReview.verdict(r));
        it.putExtra(GomokuReviewActivity.EXTRA_WINNER, winner);
        it.putExtra(GomokuReviewActivity.EXTRA_END, end);
        try {
            startActivity(it);
        } catch (RuntimeException e) {
            toast("打不开复盘页：" + e.getClass().getSimpleName());
        }
    }

    private void showEndDialog(String text) {
        if (!alive || isFinishing()) return;
        if (android.os.Build.VERSION.SDK_INT >= 17 && isDestroyed()) return;
        boolean humanWon = !aiVsAi && board.winner == humanColor;
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("对局结束")
                .setMessage(text + "\n" + board.endReason)
                .setPositiveButton("再来一局", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        newGame();
                    }
                })
                .setNeutralButton("复盘", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        startReview();
                    }
                })
                .setNegativeButton("返回", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        // 只关弹窗：留在棋盘上看这盘棋（要离开本页用页面上的「返回」）
                        d.dismiss();
                    }
                })
                .setCancelable(true);
        if (humanWon && WinEgg.eggAllowed(level, LocalGomokuAI.FAST)) {
            b.setView(WinEgg.buildBody(this));
        }
        b.show();
    }

    // ==== 视图 ====

    private void buildUi() {
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);
        scroll.setFillViewport(true);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(8);
        root.setPadding(pad, pad, pad, pad);
        
        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Watermark.wrap(this, scroll, palette, 6);

        topChrome = new LinearLayout(this);
        topChrome.setOrientation(LinearLayout.VERTICAL);
        topChrome.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(topChrome, paramsLine());

        TextView title = new TextView(this);
        title.setText("五　子　棋");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp() + 4);
        title.setTextColor(palette.text);
        title.setGravity(Gravity.CENTER);
        topChrome.addView(title, paramsLine());

        status = new TextView(this);
        status.setText(statusText);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp());
        status.setTextColor(palette.text);
        status.setGravity(Gravity.CENTER);
        topChrome.addView(status, paramsLine());

        boardView = new GomokuView(this);
        boardView.setAnimationsEnabled(true);
        boardView.setOnIntersectionTap(new GomokuView.OnIntersectionTap() {
            @Override
            public void onTap(int point) {
                onHumanTap(point);
            }
        });
        boardView.setOnExitFullscreen(new Runnable() {
            @Override
            public void run() {
                exitFullscreen();
            }
        });
        root.addView(boardView, paramsBoard());

        bottomChrome = new LinearLayout(this);
        bottomChrome.setOrientation(LinearLayout.VERTICAL);
        bottomChrome.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(bottomChrome, paramsLine());

        bottomChrome.addView(buttonRow(
                new String[]{"新局", "悔棋", "认输", "难度"},
                new View.OnClickListener[]{newGameClick, undoClick, resignClick, levelClick}));
        bottomChrome.addView(buttonRow(
                new String[]{"棋盘", "我执", "禁手", "手顺"},
                new View.OnClickListener[]{sizeClick, humanClick, forbidClick, numbersClick}));
        bottomChrome.addView(buttonRow(
                new String[]{"对战", "学习", "全屏", "复盘", "返回"},
                new View.OnClickListener[]{modeClick, learnClick, fullscreenClick, reviewClick, backClick}));

        syncBoardView();
        setStatus(statusText);
        if (fullscreen) applyFullscreenLayout();
    }

    private final View.OnClickListener modeClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            toggleAiVsAi();
        }
    };

    /** 「对战」：人机 → 电脑对战 → 暂停 → 继续 ……（电脑对战时点棋盘没用）。 */
    private void toggleAiVsAi() {
        if (board == null) return;
        if (!aiVsAi) {
            aiVsAi = true;
            aiPaused = false;
            newGame();
            toast("电脑对电脑：双方都由电脑下；点「对战」可暂停。");
            return;
        }
        if (!aiPaused) {
            aiPaused = true;
            aiGeneration++;   // 作废正在算的那一手：暂停期间不许再落子
            setStatus("电脑对战：已暂停（点「对战」继续）。");
            return;
        }
        aiPaused = false;
        setStatus(startText() + optionsText());
        maybeStartAiTurn();
    }

    private final View.OnClickListener newGameClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            newGame();
        }
    };
    private final View.OnClickListener undoClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            undo();
        }
    };
    private final View.OnClickListener resignClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            resign();
        }
    };
    private final View.OnClickListener levelClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            cycleLevel();
        }
    };
    private final View.OnClickListener learnClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            showLearnDialog();
        }
    };
    private final View.OnClickListener sizeClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            cycleSize();
        }
    };
    private final View.OnClickListener humanClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            toggleHuman();
        }
    };
    private final View.OnClickListener forbidClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            toggleForbid();
        }
    };
    private final View.OnClickListener numbersClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            toggleNumbers();
        }
    };
    private final View.OnClickListener fullscreenClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            enterFullscreen();
        }
    };
    private final View.OnClickListener reviewClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            startReview();
        }
    };
    private final View.OnClickListener backClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            finish();
        }
    };

    private View buttonRow(String[] labels, View.OnClickListener[] clicks) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rp.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.setLayoutParams(rp);
        for (int i = 0; i < labels.length; i++) {
            Button b = new Button(this);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp());
            b.setTextColor(palette.buttonText);
            b.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
            b.setPadding(dp(2), dp(6), dp(2), dp(6));
            b.setMinimumWidth(0);
            b.setOnClickListener(clicks[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            b.setLayoutParams(lp);
            row.addView(b);
        }
        return row;
    }

    private LinearLayout.LayoutParams paramsLine() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(dp(2), dp(2), dp(2), dp(2));
        return p;
    }

    private LinearLayout.LayoutParams paramsBoard() {
        int px = boardSidePx > 0 ? boardSidePx : dp(screen.boardSizeDp(8));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(px, px);
        p.gravity = Gravity.CENTER_HORIZONTAL;
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    private void setStatus(String s) {
        statusText = s;
        if (status != null) status.setText(s);
    }

    private void enterFullscreen() {
        if (fullscreen) return;
        fullscreen = true;
        applyFullscreenLayout();
    }

    private void exitFullscreen() {
        if (!fullscreen) return;
        fullscreen = false;
        applyFullscreenLayout();
    }

    /** 全屏：藏起状态与按钮，棋盘铺满；退出时按原样铺回来。 */
    private void applyFullscreenLayout() {
        if (topChrome == null || bottomChrome == null || boardView == null) return;
        topChrome.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        bottomChrome.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        boardView.setFullscreen(fullscreen);
        boardView.setLayoutParams(fullscreen
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT)
                : paramsBoard());
        // 注意：root 的父视图是 ScrollView（FrameLayout）。真机上给它换成 LinearLayout.LayoutParams /
        // 裸 ViewGroup.LayoutParams，下一次布局时 FrameLayout 会强转成 FrameLayout.LayoutParams 而崩
        // （崩溃表现就是「按全屏键像按了返回键」：进程被杀、回到上一层）。root 加进来时已经是
        // MATCH_PARENT/MATCH_PARENT，两种模式下都够用，所以这里再也不动它。
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) fitBoardToScreen();
    }

    /** 按「屏幕剩余高度 / 宽度」定棋盘边长：顶栏、底栏、内边距都扣掉，保证一屏放得下。 */
    private void fitBoardToScreen() {
        if (fullscreen || scroll == null || topChrome == null || bottomChrome == null
                || boardView == null) {
            return;
        }
        int availW = scroll.getWidth() - dp(16);
        int availH = scroll.getHeight() - topChrome.getHeight() - bottomChrome.getHeight() - dp(16);
        if (availW <= 0 || availH <= 0) return;
        int side = Math.max(dp(144), Math.min(availW, availH));
        if (side == boardSidePx) return;
        boardSidePx = side;
        ViewGroup.LayoutParams lp = boardView.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            lp.width = side;
            lp.height = side;
            boardView.setLayoutParams(lp);
        }
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            exitFullscreen();
            return;
        }
        super.onBackPressed();
    }

    private void toast(String s) {
        if (!alive || isFinishing()) return;
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
