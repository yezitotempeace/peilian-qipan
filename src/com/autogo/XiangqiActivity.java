/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 象棋对局页：人执红先走，电脑执黑。
 *
 * <p>规则与胜负判定都在纯逻辑的 {@link XiangqiBoard}（JVM 断言 67 项 + 6 条反证），
 * 电脑走法来自 {@link XiangqiAI}（三档难度）；本页只负责画棋盘、接手指、驱动思考线程与界面状态。
 */
public class XiangqiActivity extends Activity {

    private static final int SIZE_ROWS = XiangqiBoard.ROWS;
    private static final int SIZE_COLS = XiangqiBoard.COLS;

    private static final String[] LEVEL_NAMES = {"快", "普通", "强"};

    private XiangqiBoard board;
    private final XiangqiAI ai = new XiangqiAI();
    /** 外部强引擎 ElephantEye（懒启动；起不来就整个退回内置 AI）。 */
    private XiangqiEngine engine;
    private boolean engineChecked;
    private boolean engineOk;
    private int level = XiangqiAI.NORMAL;

    private XiangqiView boardView;
    private TextView statusText;
    private ScrollView scroll;
    private LinearLayout root;
    private LinearLayout topChrome;
    private LinearLayout bottomChrome;

    private boolean aiThinking;
    private boolean alive = true;

    /** 象棋学习库：记录电脑每一步的局面特征与最终胜负，跨局积累经验。 */
    private XiangqiLearn learn;
    private final java.util.List<String> learnKeys =
            java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
    private boolean learnRecorded;

    /** 复盘正在后台算（防止连点）。 */
    private boolean reviewing;
    private boolean fullscreen;
    private Palette palette;
    private Screen screen;
    private boolean dark;
    private int boardSidePx;
    private int selRow = -1;
    private int selCol = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = UiTheme.apply(this);
        palette = Palette.of(dark);
        screen = Screen.read(this);
        learn = XiangqiLearn.load(new java.io.File(getFilesDir(), XiangqiLearn.FILE_NAME));
        board = new XiangqiBoard();
        buildUi();
        syncBoardView();
        setStatus("你先走（红方）。难度：" + LEVEL_NAMES[level]);
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        // 顺序注意：AOSP 里 onConfigurationChanged 返回后才 ViewRootImpl.updateConfiguration → requestLayout，
        // 所以在本回调里量不到新尺寸；重算交给 scroll 的 OnLayoutChangeListener，布局完成后再算。
        if (UiTheme.dark(this) != dark) {
            rebuildForTheme(UiTheme.dark(this));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            fitBoardToScreen();   // 兜底：个别机型转屏不重跑布局监听时也补一次
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (UiTheme.dark(this) != dark) {
            rebuildForTheme(UiTheme.dark(this));
        }
    }

    /** 主题变了：原地重建控件（不 recreate）——棋局/手顺/选中都在字段里，重建不会丢。 */
    private void rebuildForTheme(boolean now) {
        dark = now;
        palette = Palette.of(now);
        setTheme(UiTheme.style(now));
        screen = Screen.read(this);
        buildUi();
        if (fullscreen) {
            topChrome.setVisibility(View.GONE);
            bottomChrome.setVisibility(View.GONE);
        }
        syncBoardView();
        refreshStatus();
    }

    private void refreshStatus() {
        if (board == null) {
            return;
        }
        if (board.gameOver()) {
            setStatus(board.resultString());
        } else if (aiThinking) {
            setStatus("电脑思考中…");
        } else {
            setStatus("轮到你走（红方）。难度：" + LEVEL_NAMES[level]);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        if (engine != null) {
            try { engine.close(); } catch (RuntimeException ignored) { }
            engine = null;
        }
        super.onDestroy();
    }

    private void buildUi() {
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(8), dp(8), dp(8), dp(8));
        scroll.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        topChrome = new LinearLayout(this);
        topChrome.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("象　棋");
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp() + 4);
        title.setTextColor(palette.text);
        title.setGravity(Gravity.CENTER);
        topChrome.addView(title, textRowParams(0, 2));
        statusText = new TextView(this);
        statusText.setTextColor(palette.textDim);
        statusText.setGravity(Gravity.CENTER);
        statusText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp() - 2);
        topChrome.addView(statusText, textRowParams(0, 6));
        root.addView(topChrome, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        boardView = new XiangqiView(this);
        boardView.setAnimationsEnabled(true);
        boardView.setPalette(palette);
        boardView.setBoard(board);
        boardView.setOnIntersectionTap(new XiangqiView.OnIntersectionTap() {
            @Override
            public void onTap(int row, int col) {
                onBoardTap(row, col);
            }
        });
        root.addView(boardView, boardParams());

        bottomChrome = new LinearLayout(this);
        bottomChrome.setOrientation(LinearLayout.VERTICAL);
        bottomChrome.addView(buttonRow(new String[]{"新局", "悔棋", "认输", "复盘"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                newGame();
                            }
                        },
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                undo();
                            }
                        },
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                resign();
                            }
                        },
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                startReview();
                            }
                        }
                }), linearWrap(0, 2));
        bottomChrome.addView(buttonRow(new String[]{"难度", "全屏", "返回"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                cycleLevel();
                            }
                        },
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                toggleFullscreen();
                            }
                        },
                        new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                finish();
                            }
                        }
                }), linearWrap(0, 6));
        root.addView(bottomChrome, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Watermark.wrap(this, scroll, palette, 6);
        // 布局完成后再按真实可用空间算棋盘：转屏、分屏、全屏都由它触发（比在 onConfigurationChanged 里 post 可靠）
        scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                fitBoardToScreen();
            }
        });
    }

    private LinearLayout.LayoutParams textRowParams(int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(topDp);
        lp.bottomMargin = dp(bottomDp);
        return lp;
    }

    private LinearLayout.LayoutParams linearWrap(int topDp, int bottomDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        lp.bottomMargin = dp(bottomDp);
        return lp;
    }

    private LinearLayout.LayoutParams boardParams() {
        int side = boardSidePx > 0 ? boardSidePx : dp(screen.boardSizeDp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(side, side);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        lp.topMargin = dp(4);
        return lp;
    }

    private LinearLayout buttonRow(String[] names, View.OnClickListener[] clicks) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            Button b = new Button(this);
            b.setAllCaps(false);
            b.setText(names[i]);
            b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp());
            b.setTextColor(palette.buttonText);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.buttonBg));
            b.setPadding(dp(2), dp(6), dp(2), dp(6));
            b.setMinimumWidth(0);
            b.setOnClickListener(clicks[i]);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), 0, dp(2), 0);
            row.addView(b, lp);
        }
        return row;
    }

    private void newGame() {
        board = new XiangqiBoard();
        selRow = -1;
        selCol = -1;
        aiThinking = false;
        learnRecorded = false;
        synchronized (learnKeys) {
            learnKeys.clear();
        }
        syncBoardView();
        setStatus("你先走（红方）。难度：" + LEVEL_NAMES[level]);
    }

    private void cycleLevel() {
        level = (level + 1) % 3;
        ai.setLevel(level);
        setStatus("难度：" + LEVEL_NAMES[level]);
    }

    private void undo() {
        if (board == null) {
            return;
        }
        if (aiThinking) {
            setStatus("等电脑下完这一手再悔棋。");
            return;
        }
        java.util.List<int[]> hist = board.history();
        if (hist.isEmpty()) {
            setStatus("还没落子，没法悔棋。");
            return;
        }
        int keep = hist.size() - 1;
        if (keep > 0 && hist.get(keep - 1) != null) {
            keep = keep - 1;    // 退一整个回合（电脑 + 你）
        }
        java.util.List<int[]> keepList = new java.util.ArrayList<int[]>();
        for (int i = 0; i < keep; i++) {
            keepList.add(hist.get(i));
        }
        XiangqiBoard replayed = new XiangqiBoard();   // 标准开局：悔到开局不能变成空盘
        for (int[] mv : keepList) {
            replayed.play(mv[0], mv[1], mv[2], mv[3]);
        }
        board = replayed;
        selRow = -1;
        selCol = -1;
        syncBoardView();
        setStatus("已悔棋，轮到你走。");
    }

    private void resign() {
        if (board == null || board.gameOver()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("认输")
                .setMessage("确认认输？")
                .setPositiveButton("认输", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        setStatus("你认输了。");
                        recordLearning(XiangqiLearn.WIN);   // 人认输 = 电脑赢
                        showEndDialog("你认输了。\n" + learnInfo(), false);
                    }
                })
                .setNegativeButton("继续下", null)
                .show();
    }

    private void onBoardTap(int row, int col) {
        if (board == null || board.gameOver() || aiThinking) {
            return;
        }
        if (board.sideToMove() != XiangqiBoard.RED) {
            return;
        }
        int piece = board.at(row, col);
        if (selRow >= 0) {
            if (piece != 0 && XiangqiBoard.colorOf(piece) == XiangqiBoard.RED) {
                select(row, col);
                return;
            }
            if (board.isLegal(selRow, selCol, row, col)) {
                play(selRow, selCol, row, col);
                return;
            }
            clearSelection();
            setStatus("这一手不合规则，重新选。");
            return;
        }
        if (piece != 0 && XiangqiBoard.colorOf(piece) == XiangqiBoard.RED) {
            select(row, col);
        }
    }

    private void select(int row, int col) {
        selRow = row;
        selCol = col;
        boardView.setSelected(row, col);
        java.util.List<int[]> moves = board.legalMoves(XiangqiBoard.RED);
        java.util.List<Integer> points = new java.util.ArrayList<Integer>();
        for (int[] mv : moves) {
            if (mv[0] == row && mv[1] == col) {
                points.add(mv[2] * XiangqiBoard.COLS + mv[3]);
            }
        }
        int[] arr = new int[points.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = points.get(i);
        }
        boardView.setLegalPoints(arr);
        setStatus("已选 " + XiangqiBoard.colName(col) + (row + 1) + "，可走 " + arr.length + " 个点。");
    }

    private void clearSelection() {
        selRow = -1;
        selCol = -1;
        boardView.setSelected(-1, -1);
        boardView.setLegalPoints(new int[0]);
    }

    private void play(int fr, int fc, int tr, int tc) {
        if (!board.play(fr, fc, tr, tc)) {
            return;
        }
        boardView.setLastMove(fr * XiangqiBoard.COLS + fc, tr * XiangqiBoard.COLS + tc);
        clearSelection();
        syncBoardView();
        if (board.gameOver()) {
            finishGame();
            return;
        }
        setStatus("轮到你走。");
        if (board.sideToMove() != XiangqiBoard.RED) {
            startAiTurn();
        }
    }

    /** ElephantEye 可执行文件的路径（从 assets 释放到 filesDir，绕开 SELinux exec 限制）。 */
    private String enginePath() {
        java.io.File f = NativeExe.prepare(this, "engine/eleeye", "libeleye.so");
        return f == null ? "" : f.getAbsolutePath();
    }

    /** 引擎到底能不能用（结果缓存）。真的起一次进程握手才算数。 */
    private boolean engineUsable() {
        if (!engineChecked) {
            engineChecked = true;
            String p = enginePath();
            if (p.isEmpty()) {
                engineOk = false;
            } else {
                try {
                    XiangqiEngine probe = new XiangqiEngine(p);
                    XiangqiBoard b = new XiangqiBoard();
                    probe.setLevel(XiangqiAI.FAST);
                    int[] mv = probe.genmove(b);
                    probe.close();
                    engineOk = mv != null;
                } catch (RuntimeException e) {
                    engineOk = false;
                }
            }
        }
        return engineOk;
    }

    /** 这一手交给谁算：引擎可用就走引擎，否则内置 AI。 */
    private XiangqiEngineApi engineForMove() {
        if (engineUsable()) {
            if (engine == null) {
                engine = new XiangqiEngine(enginePath());
            }
            engine.setLevel(level);
            return engine;
        }
        ai.setLevel(level);
        return ai;
    }

    /**
     * 学习层加权：引擎给出最优着法后，在「同一起点或同一兵种」的合法着法里挑一个
     * 历史胜率更高的（样本不足时保持引擎原选择）。这就是「随机应变 + 积累经验」：
     * 同一类局面下赢过的手法，下次更愿意用。
     */
    private int[] applyLearn(XiangqiBoard snapshot, int[] engineMove) {
        if (learn == null || engineMove == null) {
            return engineMove;
        }
        java.util.List<int[]> legal = snapshot.legalMoves(XiangqiBoard.BLACK);
        if (legal.isEmpty()) {
            return engineMove;
        }
        int bestScore = Integer.MIN_VALUE;
        int[] best = engineMove;
        for (int[] mv : legal) {
            String key = XiangqiLearn.keyFor(snapshot, XiangqiBoard.BLACK, mv);
            int bonus = learn.bonus(key);
            // 引擎那一手本身给一个基础加权，避免被无经验着法顶掉
            if (mv[0] == engineMove[0] && mv[1] == engineMove[1]
                    && mv[2] == engineMove[2] && mv[3] == engineMove[3]) {
                bonus += 40;
            }
            if (bonus > bestScore) {
                bestScore = bonus;
                best = mv;
            }
        }
        return best;
    }

    /** 记一条本局特征（电脑走的每一手）。 */
    private void learnKeyFor(XiangqiBoard snapshot, int[] mv) {
        if (learn == null || snapshot == null || mv == null) {
            return;
        }
        String key = XiangqiLearn.keyFor(snapshot, XiangqiBoard.BLACK, mv);
        if (key != null) {
            learnKeys.add(key);
        }
    }

    /** 把本局结果记进学习库并落盘（只记一次）。 */
    private void recordLearning(int result) {
        if (learn == null || learnRecorded) {
            return;
        }
        learnRecorded = true;
        learn.recordGame(learnKeys, result);
        learn.save(new java.io.File(getFilesDir(), XiangqiLearn.FILE_NAME));
    }

    private String learnInfo() {
        return learn == null ? "" : "（" + learn.describe() + "）";
    }

    private void startAiTurn() {
        if (board == null || board.gameOver()) {
            return;
        }
        aiThinking = true;
        setStatus("电脑思考中…");
        final XiangqiBoard snapshot = board;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                ai.setLevel(level);
                int[] mv;
                String engineNote = null;
                XiangqiEngineApi use = engineForMove();
                try {
                    mv = use.genmove(snapshot);
                } catch (RuntimeException e) {
                    // 外部引擎出问题（进程没起来/超时/通信断）：退回内置 AI，别让这一手作废
                    mv = ai.genmove(snapshot);
                    engineNote = "外部引擎出错，这一手改用内置 AI：" + e.getMessage();
                }
                // 学习层：在引擎这一手附近挑一个历史胜率更高的同类走法（样本不够时原样返回）
                try {
                    mv = applyLearn(snapshot, mv);
                } catch (RuntimeException ignored) {
                    // 学习出问题绝不影响出招
                }
                final int[] finalMv = mv;
                final String finalNote = engineNote;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        aiThinking = false;
                        if (!alive || board != snapshot || board.gameOver()) {
                            return;
                        }
                        if (finalMv == null) {
                            setStatus("电脑无棋可走。");
                            return;
                        }
                        learnKeyFor(snapshot, finalMv);
                        board.play(finalMv[0], finalMv[1], finalMv[2], finalMv[3]);
                        boardView.setLastMove(finalMv[0] * XiangqiBoard.COLS + finalMv[1],
                                finalMv[2] * XiangqiBoard.COLS + finalMv[3]);
                        syncBoardView();
                        if (board.gameOver()) {
                            finishGame();
                            return;
                        }
                        setStatus(finalNote != null ? finalNote : "轮到你走。");
                    }
                });
            }
        }, "xiangqi-ai");
        t.setDaemon(true);
        t.start();
    }

    private void finishGame() {
        String text = board.resultString();
        setStatus(text);
        boolean humanWon = board.winner() == XiangqiBoard.RED;
        recordLearning(humanWon ? XiangqiLearn.LOSS : XiangqiLearn.WIN);
        showEndDialog(text + "\n" + learnInfo(), humanWon);
    }

    private void showEndDialog(final String text, boolean humanWon) {
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("对局结束")
                .setMessage(text)
                .setPositiveButton("再来一局", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        newGame();
                    }
                })
                .setNegativeButton("返回", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        d.dismiss();
                    }
                })
                .setCancelable(true);
        if (humanWon && WinEgg.eggAllowed(level, XiangqiAI.STRONG)) {
            b.setView(WinEgg.buildBody(this));
        }
        b.show();
    }

    private void toggleFullscreen() {
        fullscreen = !fullscreen;
        topChrome.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        bottomChrome.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        fitBoardToScreen();
    }

    private void fitBoardToScreen() {
        if (scroll == null || boardView == null || topChrome == null || bottomChrome == null) {
            return;
        }
        int availW = scroll.getWidth() - dp(16);
        int availH = scroll.getHeight() - dp(16)
                - (fullscreen ? 0 : topChrome.getHeight() + bottomChrome.getHeight());
        int side = Math.max(dp(160), Math.min(availW, availH));
        if (side <= 0) {
            return;
        }
        boardSidePx = side;
        ViewGroup.LayoutParams lp = boardView.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            lp.width = side;
            lp.height = side;
            boardView.setLayoutParams(lp);
        }
    }

    private void syncBoardView() {
        if (boardView != null && board != null) {
            boardView.setBoard(board);
            boardView.invalidate();
        }
    }

    /** 复盘：后台跑 {@link XiangqiReview#analyze}，算完打开复盘页。 */
    private void startReview() {
        if (board == null || reviewing) {
            return;
        }
        if (board.history().isEmpty()) {
            toast("还没有走子，没法复盘。");
            return;
        }
        reviewing = true;
        final String prev = statusText == null ? "" : String.valueOf(statusText.getText());
        setStatus("正在复盘…（" + board.history().size() + " 手）");
        final XiangqiBoard snapshot = board.copy();
        final java.util.List<int[]> moves = snapshot.history();
        new Thread(new Runnable() {
            @Override
            public void run() {
                XiangqiReview.Review r = null;
                String err = null;
                try {
                    r = XiangqiReview.analyze(moves, new XiangqiAI(), XiangqiAI.FAST);
                } catch (RuntimeException e) {
                    err = e.getMessage();
                }
                final XiangqiReview.Review fr = r;
                final String ferr = err;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        reviewing = false;
                        if (!alive || isFinishing()) {
                            return;
                        }
                        setStatus(prev);
                        if (fr == null) {
                            toast("复盘失败：" + ferr);
                            return;
                        }
                        int[] raw = new int[moves.size() * 4];
                        for (int i = 0; i < moves.size(); i++) {
                            int[] mv = moves.get(i);
                            for (int k = 0; k < 4; k++) {
                                raw[i * 4 + k] = mv[k];
                            }
                        }
                        String[] bestText = new String[fr.bestMove.length];
                        for (int i = 0; i < bestText.length; i++) {
                            bestText[i] = fr.bestMove[i] == null ? "" : XiangqiBoard.moveText(fr.bestMove[i]);
                        }
                        android.content.Intent it = new android.content.Intent(
                                XiangqiActivity.this, XiangqiReviewActivity.class);
                        it.putExtra(XiangqiReviewActivity.EXTRA_MOVES, raw);
                        it.putExtra(XiangqiReviewActivity.EXTRA_WR, fr.wrToMove);
                        it.putExtra(XiangqiReviewActivity.EXTRA_LOSS, fr.loss);
                        it.putExtra(XiangqiReviewActivity.EXTRA_BEST, bestText);
                        it.putExtra(XiangqiReviewActivity.EXTRA_BESTPT, fr.bestPoint);
                        it.putExtra(XiangqiReviewActivity.EXTRA_VERDICT, XiangqiReview.verdict(fr));
                        it.putExtra(XiangqiReviewActivity.EXTRA_WINNER, fr.winner);
                        it.putExtra(XiangqiReviewActivity.EXTRA_END, fr.endReason);
                        try {
                            startActivity(it);
                        } catch (RuntimeException e) {
                            toast("打不开复盘页：" + e.getMessage());
                        }
                    }
                });
            }
        }, "xiangqi-review").start();
    }

    private void setStatus(String text) {
        if (statusText != null) {
            statusText.setText(text);
        }
    }

    private void toast(String text) {
        if (!alive) {
            return;
        }
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
