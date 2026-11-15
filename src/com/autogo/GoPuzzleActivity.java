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
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.IOException;

/** 围棋残局：黑先，从给定局面开始赢下 KataGo 即过关。档位越高引擎越强。 */
public class GoPuzzleActivity extends Activity {

    private Board board;
    private EndgamePuzzles.Puzzle puzzle;
    private int diff;
    private boolean aiThinking, engineStarting;
    private EngineManager engineManager;
    private GtpEngine engine;
    private String modelAlias;
    private int visits;

    private BoardView boardView;
    private TextView status;
    private Palette palette;
    private boolean darkTheme;

    private static final int ME = Board.BLACK;
    private static final int CPU = Board.WHITE;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        darkTheme = UiTheme.apply(this);
        palette = Palette.of(darkTheme);
        screen = Screen.read(this);
        diff = getIntent().getIntExtra("diff", 0);
        puzzle = EndgamePuzzles.get(EndgamePuzzles.GAME_GO, diff);
        modelAlias = diff == 0 ? "fast" : (diff == 1 ? "normal" : "strong");
        board = new Board(puzzle.size, 7.5);
        for (int[] s : puzzle.stones) {
            board.grid[board.idx(s[0], s[1])] = s[2];
        }
        engineManager = new EngineManager(this);
        visits = 120;
        buildUi();
        startEngine();
        sync();
    }

    private void startEngine() {
        if (engine != null || engineStarting) {
            return;
        }
        engineStarting = true;
        setStatus("引擎启动中…");
        final EngineManager mgr = engineManager;
        new Thread(new Runnable() {
            public void run() {
                GtpEngine e = null;
                try {
                    e = mgr.start(visits, EngineManager.defaultThreads(), false, modelAlias);
                } catch (IOException ex) {
                    e = null;
                }
                final GtpEngine ready = e;
                runOnUiThread(new Runnable() {
                    public void run() {
                        engineStarting = false;
                        engine = ready;
                        if (ready == null) {
                            setStatus("引擎没启动起来，残局暂时只能看不能下。");
                        } else {
                            sync();
                        }
                    }
                });
            }
        }, "go-puzzle-engine").start();
    }

    private void buildUi() {
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
        title.setText("围棋残局 · " + EndgamePuzzles.DIFF_NAMES[diff]);
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
        root.addView(top, matchWrap());

        boardView = new BoardView(this);
        boardView.setPalette(palette);
        boardView.setAnimationsEnabled(true);
        boardView.setBoard(board);
        boardView.setOnIntersectionTap(new BoardView.OnIntersectionTap() {
            public void onTap(int point) {
                onTap(point);
            }
        });
        root.addView(boardView, boardParams());

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.addView(buttonRow(new String[]{"重摆这局", "返回"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                resetBoard();
                                startEngine();
                                sync();
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
        goal.setText("【目标】" + puzzle.goal + "\n【规则】黑先。提掉对方棋子、围住地盘，终局数子多者胜。你执黑，电脑执白。");
        root.addView(goal, matchWrap());

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        RulesHelp.attach(this, palette,
                "【残局·围棋】从给定局面开始，你执黑先走，终局数子比电脑多就过关。\n"
              + "【提子】把对方棋子的气全部堵光就能提走；提子越大优势越大。\n"
              + "【气】棋子横竖相邻的空点叫气。\n"
              + "【难度】弱＝一手提子；中＝一整块只剩一口气；强＝要点隐藏，先点要害再提。\n"
              + "【提示】先数一数目标白棋还剩几口气，再找能把最后一口堵上的那个点。");
        setContentView(scroll);
    }

    private void resetBoard() {
        board = new Board(puzzle.size, 7.5);
        for (int[] s : puzzle.stones) {
            board.grid[board.idx(s[0], s[1])] = s[2];
        }
        aiThinking = false;
        boardView.setInputEnabled(true);
        boardView.setBoard(board);
    }

    private void onTap(int point) {
        if (aiThinking || engineStarting || board == null) {
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
            startEngine();
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
        final Board snapshot = board;
        final long timeout = Math.max(60_000L, visits * 1500L);
        new Thread(new Runnable() {
            public void run() {
                String vertex = null;
                try {
                    vertex = e.genmove(CPU, timeout);
                } catch (IOException ex) {
                    vertex = null;
                }
                final String v = vertex;
                runOnUiThread(new Runnable() {
                    public void run() {
                        aiThinking = false;
                        boardView.setInputEnabled(true);
                        if (v == null) {
                            setStatus("引擎没有回手，再试一次或重摆。");
                            return;
                        }
                        if ("PASS".equalsIgnoreCase(v.trim()) || "resign".equalsIgnoreCase(v.trim())) {
                            board.play(CPU, Board.PASS);
                            finishGame();
                            return;
                        }
                        int pt = Board.gtpToPoint(v, board.size);
                        if (!board.isLegal(CPU, pt)) {
                            setStatus("引擎回了非法点，跳过。");
                            return;
                        }
                        board.play(CPU, pt);
                        boardView.setLastMove(pt);
                        afterMove();
                    }
                });
            }
        }, "go-puzzle-ai").start();
    }

    private boolean bothPassed() {
        return board.moves.size() >= 2
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
            msg += "电脑赢了，再来一次。";
        } else {
            msg += "和棋。";
        }
        setStatus(msg);
    }

    private void sync() {
        if (boardView != null) {
            boardView.setBoard(board);
        }
        if (engine == null) {
            setStatus(engineStarting ? "引擎启动中…" : "引擎未就绪。");
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
        int side = dp(screen.boardSizeDp(puzzle.size));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(side, side);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    private Screen screen;

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
