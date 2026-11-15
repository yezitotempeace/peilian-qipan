/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;

/** 围棋残局：本地随机生成一道可胜残局，黑先，赢下 KataGo 即过关。 */
public class GoPuzzleActivity extends Activity {

    private Board board;
    private EndgameGen.Puzzle puzzle;
    private int diff;
    private boolean aiThinking, engineStarting, engineFailed;
    private EngineManager engineManager;
    private GtpEngine engine;
    private String modelAlias;
    private int visits;

    private BoardView boardView;
    private TextView status;
    private ProgressBar progress;
    private Palette palette;
    private Screen screen;
    private boolean darkTheme;

    private static final int ME = Board.BLACK;
    private static final int CPU = Board.WHITE;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        PuzzleCrash.install(this);
        darkTheme = UiTheme.apply(this);
        palette = Palette.of(darkTheme);
        screen = Screen.read(this);
        diff = getIntent().getIntExtra("diff", 1);
        modelAlias = diff == 0 ? "fast" : (diff == 1 ? "normal" : "strong");
        visits = diff == 0 ? 200 : (diff == 1 ? 120 : 300);
        engineManager = new EngineManager(this);
        buildShell();
        generateAndStart();
    }

    @Override
    protected void onDestroy() {
        PuzzleCrash.uninstall(this);
        super.onDestroy();
    }

    private void buildShell() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        int pad = dp(8);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("围棋残局 · " + EndgameGen.DIFF_NAMES[diff]);
        title.setTextSize(19f);
        title.setTextColor(palette.text);
        title.setGravity(Gravity.CENTER);
        top.addView(title);
        status = new TextView(this);
        status.setTextColor(palette.textDim);
        status.setTextSize(13f);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(6), dp(2), dp(6), dp(2));
        top.addView(status);
        progress = new ProgressBar(this);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.gravity = Gravity.CENTER_HORIZONTAL;
        top.addView(progress, pp);
        root.addView(top, matchWrap());

        boardView = new BoardView(this);
        boardView.setPalette(palette);
        boardView.setAnimationsEnabled(true);
        boardView.setOnIntersectionTap(new BoardView.OnIntersectionTap() {
            public void onTap(int point) {
                onTap(point);
            }
        });
        root.addView(boardView, boardParams());

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.addView(buttonRow(new String[]{"换一局", "返回"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                generateAndStart();
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                finish();
                            }
                        }}));
        root.addView(bottom, matchWrap());

        TextView goal = new TextView(this);
        goal.setTextSize(12f);
        goal.setTextColor(palette.textDim);
        goal.setPadding(dp(12), dp(6), dp(12), dp(6));
        goal.setText("【目标】黑先，提掉对方的子、围住地盘，终局数子多者胜。你执黑，电脑执白（KataGo）。\n【难度】弱＝一手提子；中＝全歼一块；强＝先点要害再提，对手也最强。");
        root.addView(goal, matchWrap());

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        RulesHelp.attach(this, palette,
                "【残局·围棋】每局都是本地随机生成的一道可胜残局。你执黑先走，终局数子比电脑多就过关。\n"
              + "【提子】把对方棋子的气全部堵光就能提走；提子越大优势越大。\n"
              + "【气】棋子横竖相邻的空点叫气。\n"
              + "【难度】弱＝一手提子；中＝全歼一块；强＝先点要害再提，而且对手是 KataGo 最强档。\n"
              + "【提示】先数一数目标白棋还剩几口气，再找能把最后一口堵上的那个点。");
    }

    /** 后台生成随机残局，并同时把 KataGo 拉起来（首次要把权重释放到本地，较慢）。 */
    private void generateAndStart() {
        aiThinking = false;
        boardView.setInputEnabled(false);
        progress.setVisibility(View.VISIBLE);
        setStatus("正在随机生成残局并启动引擎…");
        final long seed = System.currentTimeMillis();
        final int d = diff;
        new Thread(new Runnable() {
            public void run() {
                final EndgameGen.Puzzle p = EndgameGen.generate(EndgameGen.GAME_GO, d, seed);
                GtpEngine e = null;
                String err = null;
                try {
                    EngineManager mgr = new EngineManager(GoPuzzleActivity.this);
                    e = mgr.start(visits, EngineManager.defaultThreads(), false, modelAlias);
                } catch (Throwable ex) {
                    err = String.valueOf(ex.getMessage());
                }
                final GtpEngine ready = e;
                final String fail = err;
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            puzzle = p;
                            board = new Board(p.size, 7.5);
                            for (int[] s : p.stones) {
                                board.grid[board.idx(s[0], s[1])] = s[2];
                            }
                            engine = ready;
                            engineFailed = ready == null;
                            progress.setVisibility(View.GONE);
                            boardView.setBoard(board);
                            boardView.setInputEnabled(true);
                            if (engineFailed) {
                                setStatus("引擎没启动起来：" + fail);
                            } else {
                                sync();
                            }
                        } catch (Throwable t) {
                            PuzzleCrash.report(GoPuzzleActivity.this, "围棋残局 摆子", t);
                        }
                    }
                });
            }
        }, "go-puzzle-gen").start();
    }

    private void onTap(int point) {
        try {
            onTapInner(point);
        } catch (Throwable t) {
            PuzzleCrash.report(this, "围棋残局 落子", t);
        }
    }

    private void onTapInner(int point) {
        if (aiThinking || engineStarting || puzzle == null || board == null || bothPassed()) {
            return;
        }
        if (engine == null) {
            setStatus("引擎还没就绪，稍等一下。");
            return;
        }
        if (!board.isLegal(ME, point)) {
            setStatus("这里不能下（自杀 / 已有子 / 打劫须先隔一手）。");
            return;
        }
        board.play(ME, point);
        boardView.setLastMove(point);
        afterMove();
    }

    private void afterMove() {
        sync();
        if (bothPassed()) {
            finishGame();
            return;
        }
        aiTurn();
    }

    private void aiTurn() {
        if (engine == null) {
            startEngineAgain();
            return;
        }
        if (bothPassed()) {
            finishGame();
            return;
        }
        aiThinking = true;
        boardView.setInputEnabled(false);
        setStatus("KataGo 思考中…");
        final GtpEngine e = engine;
        final long timeout = Math.max(60_000L, visits * 1500L);
        new Thread(new Runnable() {
            public void run() {
                String vertex = null;
                try {
                    // KataGo 自己记盘，所以每次都要把当前局面重新喂给它，
                    // 否则它按空盘算，回的点和真实棋盘对不上。
                    e.clearBoard();
                    int n = board.size;
                    for (int r = 0; r < n; r++) {
                        for (int c = 0; c < n; c++) {
                            int v = board.grid[board.idx(r, c)];
                            if (v != Board.EMPTY) {
                                e.play(v, Board.pointToGtp(board.idx(r, c), n));
                            }
                        }
                    }
                    vertex = e.genmove(CPU, timeout);
                } catch (Throwable ex) {
                    vertex = null;
                }
                final String v = vertex;
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            aiThinking = false;
                            boardView.setInputEnabled(true);
                            if (v == null) {
                                setStatus("引擎没有回手，点「换一局」再来。");
                                return;
                            }
                            if ("resign".equalsIgnoreCase(v.trim())) {
                                setStatus("电脑认输了——过关！");
                                return;
                            }
                            if ("PASS".equalsIgnoreCase(v.trim())) {
                                board.play(CPU, Board.PASS);
                                finishGame();
                                return;
                            }
                            int pt = Board.gtpToPoint(v, board.size);
                            if (!board.isLegal(CPU, pt)) {
                                setStatus("引擎回了非法点，点「换一局」再来。");
                                return;
                            }
                            board.play(CPU, pt);
                            boardView.setLastMove(pt);
                            afterMove();
                        } catch (Throwable t) {
                            PuzzleCrash.report(GoPuzzleActivity.this, "围棋残局 电脑回手", t);
                        }
                    }
                });
            }
        }, "go-puzzle-ai").start();
    }

    private void startEngineAgain() {
        engine = null;
        engineStarting = false;
        generateAndStart();
    }

    private boolean bothPassed() {
        return board != null && board.moves.size() >= 2
                && board.moves.get(board.moves.size() - 1)[1] == Board.PASS
                && board.moves.get(board.moves.size() - 2)[1] == Board.PASS;
    }

    private void finishGame() {
        boardView.setInputEnabled(false);
        double[] sc = board.scoreArea();
        double mine = sc[0], other = sc[1];
        String msg = "黑 " + String.format("%.1f", mine) + " : 白 " + String.format("%.1f", other) + "  ";
        if (mine > other) {
            msg += "你赢了！";
        } else if (other > mine) {
            msg += "电脑赢了，点「换一局」再来。";
        } else {
            msg += "和棋。";
        }
        setStatus(msg);
    }

    private void sync() {
        if (boardView == null || board == null) {
            return;
        }
        boardView.setBoard(board);
        if (engineFailed) {
            return;
        }
        if (aiThinking) {
            setStatus("KataGo 思考中…");
        } else {
            setStatus("轮到你（黑）。");
        }
    }

    private void setStatus(String s) {
        if (status != null) {
            status.setText(s);
        }
    }

    private LinearLayout buttonRow(String[] labels, View.OnClickListener[] actions) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            Button btn = new Button(this);
            btn.setText(labels[i]);
            btn.setTextColor(palette.buttonText);
            btn.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
            btn.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            btn.setLayoutParams(lp);
            btn.setOnClickListener(actions[i]);
            row.addView(btn);
        }
        return row;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams boardParams() {
        int side = dp(screen == null ? 340 : screen.boardSizeDp(9));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(side, side);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
