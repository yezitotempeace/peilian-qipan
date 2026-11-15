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

/** 五子棋残局：本地随机生成一道可胜残局，黑先，连成五子即过关。 */
public class GomokuPuzzleActivity extends Activity {

    private GomokuBoard board;
    private final LocalGomokuAI ai = new LocalGomokuAI();
    private EndgameGen.Puzzle puzzle;
    private int diff;
    private int size = 15;
    private boolean aiThinking;
    private final String label = "五子棋残局";

    private GomokuView boardView;
    private TextView status;
    private ProgressBar progress;
    private Palette palette;
    private Screen screen;
    private boolean darkTheme;

    private static final int ME = GomokuBoard.BLACK;
    private static final int CPU = GomokuBoard.WHITE;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        PuzzleCrash.install(this);
        darkTheme = UiTheme.apply(this);
        palette = Palette.of(darkTheme);
        screen = Screen.read(this);
        diff = getIntent().getIntExtra("diff", 1);
        size = getIntent().getIntExtra("size", 15);
        buildShell();
        generateAndStart();
    }

    /** 搭好外壳（标题 / 状态 / 进度条 / 棋盘容器），棋盘数据等生成完再填。 */
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
        title.setText("五子棋残局 · " + EndgameGen.DIFF_NAMES[diff]);
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

        boardView = new GomokuView(this);
        boardView.setPalette(palette);
        boardView.setAnimationsEnabled(true);
        boardView.setOnIntersectionTap(new GomokuView.OnIntersectionTap() {
            public void onTap(int point) {
                GomokuPuzzleActivity.this.onTap(point);
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
        goal.setText("【目标】黑先，横竖斜连成五子即过关。你执黑，电脑执白。\n【难度】弱＝一手连珠；中＝活三成活四；强＝双活三，对手也最强。");
        root.addView(goal, matchWrap());

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        RulesHelp.attach(this, palette,
                "【残局·五子棋】每局都是本地随机生成的一道可胜残局。你执黑先走，连成五子即过关。\n"
              + "【方向】横、竖、两条斜线都可以。\n"
              + "【难度】弱＝一手连珠；中＝活三补成活四；强＝双活三，而且对手用的是最强搜索。\n"
              + "【失败】被电脑先连成五子、或棋盘下满都没赢，就点「换一局」重开。\n"
              + "【提示】先看自己有没有一步成五的点；没有就找能同时形成两个威胁的落点。");
    }

    /** 后台生成一道随机残局，完成后把棋盘摆上。 */
    private void generateAndStart() {
        aiThinking = false;
        boardView.setInputEnabled(false);
        progress.setVisibility(View.VISIBLE);
        setStatus("正在随机生成残局…");
        final long seed = System.currentTimeMillis();
        final int d = diff;
        final int ps = size;
        new Thread(new Runnable() {
            public void run() {
                final EndgameGen.Puzzle p = EndgameGen.generate(EndgameGen.GAME_GOMOKU, d, seed, ps);
                runOnUiThread(new Runnable() {
                    public void run() {
                        puzzle = p;
                        board = new GomokuBoard(p.size, false);
                        for (int[] s : p.stones) {
                            board.grid[board.idx(s[0], s[1])] = s[2];
                        }
                        progress.setVisibility(View.GONE);
                        boardView.setBoard(board);
                        boardView.setInputEnabled(true);
                        sync();
                    }
                });
            }
        }, "gomoku-puzzle-gen").start();
    }

    private void onTap(int point) {
        try {
            onTapInner(point);
        } catch (Throwable t) {
            PuzzleCrash.report(this, "五子棋残局 落子", t);
        }
    }

    private void onTapInner(int point) {
        if (aiThinking || puzzle == null || board == null || board.gameOver) {
            return;
        }
        if (!board.isLegal(ME, point)) {
            setStatus("这里不能下。");
            return;
        }
        board.play(ME, point);
        boardView.setLastMove(point);
        afterMove();
    }

    private void afterMove() {
        sync();
        if (board.gameOver) {
            finishGame();
            return;
        }
        if (board.moves.size() % 2 == 0) {
            setStatus("轮到你（黑）。");
        } else {
            aiTurn();
        }
    }

    private void aiTurn() {
        if (board.gameOver) {
            finishGame();
            return;
        }
        aiThinking = true;
        boardView.setInputEnabled(false);
        setStatus("电脑思考中…");
        final GomokuBoard snapshot = board.copy();
        new Thread(new Runnable() {
            public void run() {
                final int mv = ai.genmove(snapshot, CPU);
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            aiThinking = false;
                            boardView.setInputEnabled(true);
                            if (mv < 0) {
                                setStatus("电脑无棋可走。");
                                return;
                            }
                            if (!board.isLegal(CPU, mv)) {
                                PuzzleCrash.report(GomokuPuzzleActivity.this, label + " 电脑回了非法点 " + mv,
                                        new IllegalStateException("电脑回了非法点 " + mv));
                                return;
                            }
                            board.play(CPU, mv);
                            boardView.setLastMove(mv);
                            afterMove();
                        } catch (Throwable t) {
                            PuzzleCrash.report(GomokuPuzzleActivity.this, label + " 电脑回手", t);
                        }
                    }
                });
            }
        }, "gomoku-puzzle-ai").start();
    }

    private void finishGame() {
        boardView.setInputEnabled(false);
        if (board.winner == ME) {
            setStatus("过关！" + board.endReason);
        } else if (board.winner == CPU) {
            setStatus("电脑赢了，点「换一局」再来。" + board.endReason);
        } else {
            setStatus("和棋，点「换一局」再来。");
        }
    }

    private void sync() {
        if (boardView == null || board == null) {
            return;
        }
        boardView.setBoard(board);
        boardView.setWinLine(board.winLine);
        if (board.gameOver) {
            finishGame();
        } else if (aiThinking) {
            setStatus("电脑思考中…");
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
        int side = dp(screen == null ? 340 : screen.boardSizeDp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(side, side);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
