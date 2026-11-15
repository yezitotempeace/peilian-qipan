/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.io.File;

/**
 * 围棋残局：本地随机生成一道「黑棋占优」的局面，黑先，和白方引擎（KataGo）对下，
 * 收完官子数子定胜负。强档对手是围棋最强引擎 KataGo。
 *
 * <p>残局不接彩蛋（WinEgg）。</p>
 */
public class GoPuzzleActivity extends Activity {

    private Board board;
    private EndgameGen.Puzzle puzzle;
    private int diff;

    private GtpEngine engine;
    private EngineManager engineMgr;
    private boolean engineFailed;
    private String engineFail;
    private volatile boolean aiThinking;
    private boolean gameOver;

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
        buildShell();
        generateAndStart();
    }

    @Override
    protected void onDestroy() {
        closeEngine();
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
                GoPuzzleActivity.this.onTap(point);
            }
        });
        root.addView(boardView, boardParams());

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.addView(buttonRow(new String[]{"认输", "数子", "换一局", "返回"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                finishGame(true);
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                finishGame(false);
                            }
                        },
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
        goal.setText("【目标】黑先。你执黑，白方是围棋引擎。下到双方都停一手就数子，黑棋占优，赢面在你。\n"
                + "【提示】盘面上白有一块棋气很紧，先把它围死再收官子，优势会越滚越大。\n"
                + "【难度】弱＝引擎算得浅；中＝正常；强＝KataGo 最强搜索。");
        root.addView(goal, matchWrap());

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        RulesHelp.attach(this, palette,
                "【残局·围棋】每局都是本地随机生成的黑优局面。你执黑，和白方引擎对下，下完数子定胜负。\n"
              + "【气】棋子横竖相邻的空点叫气；气被堵光就被提走。\n"
              + "【禁着】自己落子的地方如果也没气，是自杀，不允许；打劫须先隔一手。\n"
              + "【终局】双方连续停一手即终局，按中国规则数子，黑贴 7.5 目。\n"
              + "【按钮】「数子」= 现在就算结果；「换一局」= 重摆一道；「认输」= 直接判负。");
    }

    /** 后台：随机生成残局 + 启动引擎；完成后摆子、同步引擎、开下。 */
    private void generateAndStart() {
        aiThinking = false;
        gameOver = false;
        boardView.setInputEnabled(false);
        progress.setVisibility(View.VISIBLE);
        setStatus("正在随机生成残局…");
        closeEngine();
        final long seed = System.currentTimeMillis();
        final int d = diff;
        new Thread(new Runnable() {
            public void run() {
                final EndgameGen.Puzzle p = EndgameGen.generate(EndgameGen.GAME_GO, d, seed);
                GtpEngine ready = null;
                String fail = null;
                try {
                    engineMgr = new EngineManager(GoPuzzleActivity.this);
                    int visits = (d == 0) ? 48 : (d == 1 ? 160 : 900);
                    String alias = (d == 0) ? "fast" : (d == 1 ? "normal" : "strong");
                    ready = engineMgr.start(visits, EngineManager.defaultThreads(), false, alias);
                } catch (Throwable t) {
                    fail = String.valueOf(t);
                    ready = null;
                }
                final GtpEngine ready2 = ready;
                final String fail2 = fail;
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            puzzle = p;
                            board = new Board(p.size, 7.5);
                            for (int[] s : p.stones) {
                                board.grid[board.idx(s[0], s[1])] = s[2];
                            }
                            engine = ready2;
                            engineFailed = (ready2 == null);
                            engineFail = fail2;
                            progress.setVisibility(View.GONE);
                            boardView.setBoard(board);
                            syncEngineBoard();
                            boardView.setInputEnabled(true);
                            if (engineFailed) {
                                setStatus("引擎没启动起来：" + engineFail + "（你仍可点「数子」看形势）");
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

    /** 把当前盘面同步给引擎：每次都要重来，否则引擎按它自己记的盘面算，会和真实棋盘对不上。 */
    private void syncEngineBoard() {
        GtpEngine e = engine;
        if (e == null || board == null) {
            return;
        }
        try {
            e.boardsize(board.size);
            e.clearBoard();
            e.komi(7.5);
            int n = board.size;
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    int v = board.grid[board.idx(r, c)];
                    if (v != Board.EMPTY) {
                        e.play(v, Board.pointToGtp(board.idx(r, c), n));
                    }
                }
            }
        } catch (Throwable t) {
            engineFailed = true;
            engineFail = String.valueOf(t);
        }
    }

    private void onTap(int point) {
        try {
            onTapInner(point);
        } catch (Throwable t) {
            PuzzleCrash.report(this, "围棋残局 落子", t);
        }
    }

    private void onTapInner(int point) {
        if (aiThinking || gameOver || puzzle == null || board == null) {
            return;
        }
        if (!board.isLegal(ME, point)) {
            setStatus("这里不能下（自杀，或打劫须先隔一手）。");
            return;
        }
        board.play(ME, point);
        boardView.setLastMove(point);
        afterMove();
    }

    private void afterMove() {
        sync();
        if (gameOver) {
            finishGame(false);
            return;
        }
        if (engineFailed) {
            return;
        }
        aiTurn();
    }

    private void aiTurn() {
        if (engine == null || aiThinking || gameOver) {
            return;
        }
        aiThinking = true;
        boardView.setInputEnabled(false);
        setStatus("白方（引擎）思考中…");
        final GtpEngine e = engine;
        final long timeout = 120_000L;
        new Thread(new Runnable() {
            public void run() {
                String v = null;
                try {
                    syncEngineBoard();
                    v = e.genmove(CPU, timeout);
                } catch (Throwable t) {
                    v = null;
                }
                final String mv = v;
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            aiThinking = false;
                            boardView.setInputEnabled(true);
                            if (mv == null) {
                                board.play(CPU, Board.PASS);
                                afterMove();
                                return;
                            }
                            if ("resign".equalsIgnoreCase(mv.trim())) {
                                gameOver = true;
                                setStatus("白方认输了——你赢下这局！");
                                return;
                            }
                            if ("PASS".equalsIgnoreCase(mv.trim())) {
                                board.play(CPU, Board.PASS);
                                afterMove();
                                return;
                            }
                            int pt = Board.gtpToPoint(mv, board.size);
                            if (!board.isLegal(CPU, pt)) {
                                board.play(CPU, Board.PASS);
                                afterMove();
                                return;
                            }
                            board.play(CPU, pt);
                            boardView.setLastMove(pt);
                            afterMove();
                        } catch (Throwable t) {
                            PuzzleCrash.report(GoPuzzleActivity.this, "围棋残局 白方回手", t);
                        }
                    }
                });
            }
        }, "go-puzzle-ai").start();
    }

    private void sync() {
        if (boardView == null || board == null) {
            return;
        }
        boardView.setBoard(board);
    }

    /** 数子 / 认输，弹出结果。 */
    private void finishGame(boolean resigned) {
        if (board == null) {
            return;
        }
        boardView.setInputEnabled(false);
        if (resigned) {
            popup("你认输了。点「换一局」再战。");
            return;
        }
        double score = scoreChinese();
        final String msg = (score > 0 ? "黑胜 " + fmt(score) + " 子——过关！"
                : (score < 0 ? "白胜 " + fmt(-score) + " 子。点「换一局」再战。"
                : "和棋（罕见）。"));
        popup(msg);
    }

    /** 中国规则数子：黑方减去贴目 7.5，返回正数表示黑胜。 */
    private double scoreChinese() {
        int n = board.size;
        int black = 0, white = 0;
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                int pt = board.idx(r, c);
                if (board.grid[pt] == Board.BLACK) {
                    black++;
                } else if (board.grid[pt] == Board.WHITE) {
                    white++;
                } else {
                    // 空点归给只挨着一种颜色的一方（简化数子）
                    boolean b = false, w = false;
                    int[] dr = {-1, 1, 0, 0}, dc = {0, 0, -1, 1};
                    for (int k = 0; k < 4; k++) {
                        int rr = r + dr[k], cc = c + dc[k];
                        if (rr < 0 || rr >= n || cc < 0 || cc >= n) {
                            continue;
                        }
                        int v = board.grid[board.idx(rr, cc)];
                        if (v == Board.BLACK) {
                            b = true;
                        } else if (v == Board.WHITE) {
                            w = true;
                        }
                    }
                    if (b && !w) {
                        black++;
                    } else if (w && !b) {
                        white++;
                    }
                }
            }
        }
        return black - (white + 7.5);
    }

    private void popup(String msg) {
        try {
            new AlertDialog.Builder(this)
                    .setTitle("围棋残局 · " + EndgameGen.DIFF_NAMES[diff])
                    .setMessage(msg)
                    .setPositiveButton("知道了", null)
                    .show();
        } catch (Throwable ignore) {
        }
        setStatus(msg);
    }

    private String fmt(double d) {
        if (d == Math.rint(d)) {
            return String.valueOf((long) d);
        }
        return String.format(java.util.Locale.US, "%.1f", d);
    }

    private void closeEngine() {
        if (engine != null) {
            try {
                engine.close();
            } catch (Throwable ignore) {
            }
            engine = null;
        }
        engineFailed = false;
        engineFail = null;
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
            lp.setMargins(dp(3), dp(4), dp(3), dp(4));
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
