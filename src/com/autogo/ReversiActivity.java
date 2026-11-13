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

/** 黑白棋对局页：人执黑先行，电脑执白。无棋可走自动弃权，双方都无棋或下满结束。 */
public class ReversiActivity extends Activity {

    private ReversiBoard board;
    private final ReversiAi ai = new ReversiAi();
    private int level = ReversiAi.NORMAL;
    private boolean aiThinking;

    private ReversiView boardView;
    private TextView status;
    private Palette palette;
    private Screen screen;
    private boolean darkTheme;
    private ScrollView scroll;
    private LinearLayout topChrome;
    private LinearLayout bottomChrome;
    private int boardSide;

    private static final int PLAYER = ReversiBoard.BLACK;
    private static final int CPU = ReversiBoard.WHITE;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        darkTheme = UiTheme.apply(this);
        palette = Palette.of(darkTheme);
        screen = Screen.read(this);
        newGame();
        buildUi();
    }

    private void newGame() {
        board = new ReversiBoard();
        aiThinking = false;
        ai.setLevel(level);
        if (boardView != null) {
            boardView.setBoard(board);
            boardView.clearFlipAnims();
        }
    }

    private void buildUi() {
        scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        int pad = dp(8);
        root.setPadding(pad, pad, pad, pad);

        topChrome = new LinearLayout(this);
        topChrome.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText("黑白棋");
        title.setTextSize(20f);
        title.setTextColor(palette.text);
        title.setGravity(Gravity.CENTER);
        topChrome.addView(title);
        status = new TextView(this);
        status.setTextColor(palette.textDim);
        status.setTextSize(13f);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(6), dp(2), dp(6), dp(2));
        topChrome.addView(status);
        root.addView(topChrome, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        boardView = new ReversiView(this);
        boardView.setDark(palette.dark);
        boardView.setAnimationsEnabled(true);
        boardView.setBoard(board);
        boardView.setOnCellTap(new ReversiView.OnCellTap() {
            public void onTap(int row, int col) {
                onCellTap(row, col);
            }
        });
        root.addView(boardView, boardParams());

        bottomChrome = new LinearLayout(this);
        bottomChrome.setOrientation(LinearLayout.VERTICAL);
        bottomChrome.addView(buttonRow(new String[]{"重新开始", "难度", "返回"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                newGame();
                                sync();
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                level = (level + 1) % 3;
                                ai.setLevel(level);
                                setStatus("难度：" + levelName());
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                finish();
                            }
                        }}));
        root.addView(bottomChrome, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView rules = new TextView(this);
        rules.setTextSize(11f);
        rules.setTextColor(palette.textDim);
        rules.setPadding(dp(12), dp(8), dp(12), dp(8));
        rules.setText(
                "【落子】必须落在能夹住对方棋子的空格上，夹住的棋子全部翻成自己的颜色\n"
              + "【先行】你执黑先行，电脑执白\n"
              + "【弃权】一方没有合法落点就自动弃权，另一方继续下\n"
              + "【结束】双方都无棋可走，或 64 格下满\n"
              + "【胜负】棋子多的一方赢\n"
              + "【要点】四个角最值钱，角旁边的格子最容易送角，别乱占");
        root.addView(rules, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        RulesHelp.attach(this, palette, "【目的】结束时棋子比对方多就赢。\n【落子】必须落在能夹住对方棋子的空格：横、竖、斜任一方向，从新子到自己的旧子之间全是对方棋子。\n【翻转】被夹住的对方棋子全部翻成你的颜色。\n【弃权】一方没有合法落点就自动弃权，另一方继续下。\n【结束】双方都无棋可走，或 64 格下满。\n【要点】四个角一旦占了就翻不掉，最值钱；角旁边的格子最容易把角送给对方。");
        sync();
    }

    private String levelName() {
        return level == ReversiAi.STRONG ? "强" : level == ReversiAi.NORMAL ? "普通" : "快";
    }

    private LinearLayout.LayoutParams boardParams() {
        int side = boardSide > 0 ? boardSide : dp(screen.boardSizeDp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(side, side);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout buttonRow(String[] labels, View.OnClickListener[] actions) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            Button btn = new Button(this);
            btn.setText(labels[i]);
            btn.setTextColor(palette.buttonText);
            btn.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            btn.setLayoutParams(lp);
            btn.setOnClickListener(actions[i]);
            row.addView(btn);
        }
        return row;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ------------------------------------------------------------ 对局流程

    private void onCellTap(int r, int c) {
        if (aiThinking || board == null || board.isGameOver()) {
            return;
        }
        if (board.sideToMove() != PLAYER) {
            return;
        }
        if (!board.isLegal(PLAYER, r, c)) {
            setStatus("这里不能下：必须能夹住对方的棋子。");
            return;
        }
        playAndAnimate(PLAYER, r, c);
        afterMove();
    }

    private void afterMove() {
        sync();
        if (board.isGameOver()) {
            finishGame();
            return;
        }
        int next = board.sideToMove();
        if (next == PLAYER) {
            if (board.legalMoves(PLAYER).isEmpty()) {
                setStatus("你没有合法落点，弃权一次；电脑继续下。");
                aiTurn();
            } else {
                setStatus("轮到你（黑）。");
                showLegal();
            }
        } else {
            aiTurn();
        }
    }

    private void aiTurn() {
        if (board.isGameOver()) {
            finishGame();
            return;
        }
        aiThinking = true;
        boardView.setInputEnabled(false);
        setStatus("电脑思考中…");
        final ReversiBoard snapshot = board.copy();
        new Thread(new Runnable() {
            public void run() {
                final int[] mv = ai.genmove(snapshot);
                runOnUiThread(new Runnable() {
                    public void run() {
                        aiThinking = false;
                        boardView.setInputEnabled(true);
                        if (mv == null) {
                            // 电脑无棋可走 → 你继续
                            setStatus("电脑无棋可走，弃权一次；轮到你。");
                            showLegal();
                            return;
                        }
                        playAndAnimate(CPU, mv[0], mv[1]);
                        afterMove();
                    }
                });
            }
        }, "reversi-ai").start();
    }

    /** 落子并把被翻的棋子做成翻牌动画。 */
    private void playAndAnimate(int side, int r, int c) {
        int[] before = snapshot();
        board.play(side, r, c);
        int[] after = snapshot();
        for (int i = 0; i < 64; i++) {
            if (i == r * 8 + c) {
                continue;
            }
            if (before[i] != after[i] && before[i] != ReversiBoard.EMPTY
                    && after[i] != ReversiBoard.EMPTY) {
                boardView.animateFlip(i / 8, i % 8, before[i], after[i]);
            }
        }
        boardView.setLastMove(r, c);
    }

    private int[] snapshot() {
        int[] out = new int[64];
        for (int r = 0; r < 8; r++) {
            for (int c = 0; c < 8; c++) {
                out[r * 8 + c] = board.at(r, c);
            }
        }
        return out;
    }

    private void showLegal() {
        java.util.List<int[]> ms = board.legalMoves(PLAYER);
        int[] pts = new int[ms.size()];
        for (int i = 0; i < ms.size(); i++) {
            pts[i] = ms.get(i)[0] * 8 + ms.get(i)[1];
        }
        boardView.setLegalPoints(pts);
    }

    private void finishGame() {
        boardView.setLegalPoints(new int[0]);
        int w = board.winner();
        int mine = board.count(PLAYER), other = board.count(CPU);
        String msg = "黑 " + mine + " : 白 " + other + "  ";
        if (w == PLAYER) {
            msg += "你赢了！";
        } else if (w == CPU) {
            msg += "电脑赢了。";
        } else {
            msg += "平局。";
        }
        setStatus(msg + "（点「重新开始」再来一局）");
    }

    private void sync() {
        if (boardView != null) {
            boardView.setBoard(board);
        }
        if (board.isGameOver()) {
            finishGame();
        } else if (board.sideToMove() == PLAYER) {
            showLegal();
            setStatus("轮到你（黑）· 黑 " + board.count(PLAYER) + " : 白 " + board.count(CPU));
        } else {
            setStatus("电脑思考中… · 黑 " + board.count(PLAYER) + " : 白 " + board.count(CPU));
        }
    }

    private void setStatus(String s) {
        if (status != null) {
            status.setText(s);
        }
    }
}
