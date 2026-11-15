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

/** 五子棋残局：黑先，从给定局面开始连成五子即过关。对手强度跟档位走。 */
public class GomokuPuzzleActivity extends Activity {

    private GomokuBoard board;
    private final LocalGomokuAI ai = new LocalGomokuAI();
    private EndgamePuzzles.Puzzle puzzle;
    private int diff;
    private boolean aiThinking;

    private GomokuView boardView;
    private TextView status;
    private Palette palette;
    private Screen screen;
    private boolean darkTheme;

    private static final int ME = GomokuBoard.BLACK;
    private static final int CPU = GomokuBoard.WHITE;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        darkTheme = UiTheme.apply(this);
        palette = Palette.of(darkTheme);
        screen = Screen.read(this);
        diff = getIntent().getIntExtra("diff", 0);
        puzzle = EndgamePuzzles.get(EndgamePuzzles.GAME_GOMOKU, diff);
        ai.setLevel(diff);
        board = new GomokuBoard(puzzle.size, false);
        for (int[] s : puzzle.stones) {
            board.grid[board.idx(s[0], s[1])] = s[2];
        }
        buildUi();
        sync();
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
        title.setText("五子棋残局 · " + EndgamePuzzles.DIFF_NAMES[diff]);
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

        boardView = new GomokuView(this);
        boardView.setPalette(palette);
        boardView.setAnimationsEnabled(true);
        boardView.setBoard(board);
        boardView.setOnIntersectionTap(new GomokuView.OnIntersectionTap() {
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
        goal.setText("【目标】" + puzzle.goal + "\n【规则】黑先，横竖斜连成五子即胜。你执黑，电脑执白。");
        root.addView(goal, matchWrap());

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        RulesHelp.attach(this, palette,
                "【残局·五子棋】从给定局面开始，你执黑先走，连成五子即过关。\n"
              + "【方向】横、竖、两条斜线都可以。\n"
              + "【难度】弱＝一眼能看出的胜着；中＝要做双威胁；强＝要连续冲四，而且对手守得很好。\n"
              + "【失败】被电脑先连成五子、或棋盘下满都没赢，就重摆再来。\n"
              + "【提示】先看自己有没有一步成五的点；没有就找能同时形成两个威胁的落点。");
        setContentView(scroll);
    }

    private void resetBoard() {
        board = new GomokuBoard(puzzle.size, false);
        for (int[] s : puzzle.stones) {
            board.grid[board.idx(s[0], s[1])] = s[2];
        }
        aiThinking = false;
        boardView.setInputEnabled(true);
        boardView.setBoard(board);
    }

    private void onTap(int point) {
        if (aiThinking || board.gameOver) {
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
            // 又轮到黑方（白方被跳过的情况在这里不会发生，五子棋没有弃权）
            showTurn();
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
                        aiThinking = false;
                        boardView.setInputEnabled(true);
                        if (mv < 0) {
                            setStatus("电脑无棋可走。");
                            return;
                        }
                        board.play(CPU, mv);
                        boardView.setLastMove(mv);
                        afterMove();
                    }
                });
            }
        }, "gomoku-puzzle-ai").start();
    }

    private void showTurn() {
        setStatus("轮到你（黑）。");
    }

    private void finishGame() {
        if (board.winner == ME) {
            setStatus("过关！" + board.endReason);
        } else if (board.winner == CPU) {
            setStatus("电脑赢了，再来一次。" + board.endReason);
        } else {
            setStatus("和棋，再来一次。");
        }
        boardView.setInputEnabled(false);
    }

    private void sync() {
        if (boardView != null) {
            boardView.setBoard(board);
            boardView.setWinLine(board.winLine);
        }
        if (board.gameOver) {
            finishGame();
        } else if (board.moves.size() % 2 == 0) {
            setStatus("轮到你（黑）。");
        } else {
            setStatus("电脑思考中…");
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
        int side = dp(screenSquareDp());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(side, side);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    private int screenSquareDp() {
        return screen == null ? 340 : screen.boardSizeDp(15);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
