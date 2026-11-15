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

/**
 * 围棋残局：本地随机生成一道吃子题，黑先，把被围的白棋提掉即过关。
 *
 * <p>这是「死活/吃子」型的独立残局：不接 KataGo（之前的原生崩溃就出在这），
 * 规则判定全部走本地 {@link Board}，稳定且可离线玩。</p>
 */
public class GoPuzzleActivity extends Activity {

    private Board board;
    private EndgameGen.Puzzle puzzle;
    private int diff;
    private int targetCaptures;

    private BoardView boardView;
    private TextView status;
    private ProgressBar progress;
    private Palette palette;
    private Screen screen;
    private boolean darkTheme;

    private static final int ME = Board.BLACK;

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
        goal.setText("【目标】黑先。把被围住的那块白棋提掉，提够 " + targetCaptures + " 颗就过关。\n【难度】弱＝一口气，一手提；中＝全歼四子；强＝两口气，要先点在要害上。");
        root.addView(goal, matchWrap());

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        RulesHelp.attach(this, palette,
                "【残局·围棋】每局都是本地随机生成的吃子题。你执黑，把被围住的白棋提掉就过关。\n"
              + "【气】棋子横竖相邻的空点叫气；气被堵光就被提走。\n"
              + "【难度】弱＝白棋只剩一口气，一手提掉；中＝白棋四子被围，闭气全歼；强＝白棋有两口气，要先点在两口气共用的要害上。\n"
              + "【注意】自己落子的地方如果也没气，是自杀，不允许。\n"
              + "【提示】先数一数目标白棋还剩几口气，再找能把最后一口堵上的那个点。");
    }

    /** 后台生成一道随机吃子残局，完成后把棋盘摆上。 */
    private void generateAndStart() {
        boardView.setInputEnabled(false);
        progress.setVisibility(View.VISIBLE);
        setStatus("正在随机生成残局…");
        final long seed = System.currentTimeMillis();
        final int d = diff;
        new Thread(new Runnable() {
            public void run() {
                final EndgameGen.Puzzle p = EndgameGen.generate(EndgameGen.GAME_GO, d, seed);
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            puzzle = p;
                            board = new Board(p.size, 7.5);
                            for (int[] s : p.stones) {
                                board.grid[board.idx(s[0], s[1])] = s[2];
                            }
                            // 目标 = 初始局面上白棋的总数（全部提掉才过关）
                            int w = 0;
                            for (int i = 0; i < board.grid.length; i++) {
                                if (board.grid[i] == Board.WHITE) w++;
                            }
                            targetCaptures = w;
                            progress.setVisibility(View.GONE);
                            boardView.setBoard(board);
                            boardView.setInputEnabled(true);
                            sync();
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
        if (puzzle == null || board == null) {
            return;
        }
        if (!board.isLegal(ME, point)) {
            setStatus("这里不能下（自杀，或打劫须先隔一手）。");
            return;
        }
        board.play(ME, point);
        boardView.setLastMove(point);
        sync();
        if (board.captures[ME] >= targetCaptures) {
            boardView.setInputEnabled(false);
            setStatus("过关！提掉 " + board.captures[ME] + " 颗白子。点「换一局」再来。");
        }
    }

    private void sync() {
        if (boardView == null || board == null) {
            return;
        }
        boardView.setBoard(board);
        int left = Math.max(0, targetCaptures - board.captures[ME]);
        setStatus("还差提 " + left + " 颗白子 · 已提 " + board.captures[ME] + " / " + targetCaptures);
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
