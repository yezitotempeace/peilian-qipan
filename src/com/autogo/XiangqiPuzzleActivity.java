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

import java.util.List;

/** 象棋残局：本地随机生成一道可胜残局，红先，把黑将将死即过关。 */
public class XiangqiPuzzleActivity extends Activity {

    private XiangqiBoard board;
    private final XiangqiAI ai = new XiangqiAI();
    /** 「强」档用外部强引擎 ElephantEye；起不来就退回内置 AI。 */
    private XiangqiEngine engine;
    private boolean engineChecked, engineOk;
    private EndgameGen.Puzzle puzzle;
    private int diff;
    private boolean aiThinking;

    private XiangqiView boardView;
    private TextView status;
    private ProgressBar progress;
    private Palette palette;
    private Screen screen;
    private boolean darkTheme;

    private int selectedRow = -1, selectedCol = -1;
    private int[] legalPoints = new int[0];

    private static final int ME = XiangqiBoard.RED;
    private static final int CPU = XiangqiBoard.BLACK;

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
        title.setText("象棋残局 · " + EndgameGen.DIFF_NAMES[diff]);
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

        boardView = new XiangqiView(this);
        boardView.setPalette(palette);
        boardView.setAnimationsEnabled(true);
        boardView.setOnIntersectionTap(new XiangqiView.OnIntersectionTap() {
            public void onTap(int row, int col) {
                XiangqiPuzzleActivity.this.onTap(row, col);
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
        goal.setText("【目标】红先，把黑将将死（或让它无棋可走）即过关。\n【难度】弱＝一步将死；中＝两步；强＝三步，而且对手全力顽抗。");
        root.addView(goal, matchWrap());

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        RulesHelp.attach(this, palette,
                "【残局·象棋】每局都是本地随机生成的一道可胜残局。你执红先走，把黑将将死（或让它无棋可走）就过关。\n"
              + "【走法】先点自己要动的棋子，再点要去的格子；车马炮相士各有各的走法。\n"
              + "【将军】攻击黑将叫将军，黑必须应将。\n"
              + "【难度】弱＝一步将死；中＝两步；强＝三步，而且对手会全力顽抗。\n"
              + "【提示】先想清楚黑方有哪些应将手段，再选择能把它们全部封死的攻法。");
    }

    private void generateAndStart() {
        aiThinking = false;
        boardView.setInputEnabled(false);
        progress.setVisibility(View.VISIBLE);
        setStatus("正在随机生成残局…");
        final long seed = System.currentTimeMillis();
        final int d = diff;
        new Thread(new Runnable() {
            public void run() {
                final EndgameGen.Puzzle p = EndgameGen.generate(EndgameGen.GAME_XIANGQI, d, seed);
                runOnUiThread(new Runnable() {
                    public void run() {
                        puzzle = p;
                        board = new XiangqiBoard(true);
                        for (int[] q : p.pieces) {
                            board.put(q[0], q[1], q[2], q[3]);
                        }
                        board.testSetSideToMove(ME);
                        progress.setVisibility(View.GONE);
                        boardView.setBoard(board);
                        boardView.setInputEnabled(true);
                        sync();
                    }
                });
            }
        }, "xiangqi-puzzle-gen").start();
    }

    private void onTap(int row, int col) {
        try {
            onTapInner(row, col);
        } catch (Throwable t) {
            PuzzleCrash.report(this, "象棋残局 落子", t);
        }
    }

    private void onTapInner(int row, int col) {
        if (aiThinking || puzzle == null || board == null || board.gameOver()) {
            return;
        }
        if (board.sideToMove() != ME) {
            return;
        }
        if (selectedRow >= 0) {
            if (isIn(legalPoints, row, col)) {
                int fromPt = selectedRow * 9 + selectedCol;
                board.play(selectedRow, selectedCol, row, col);
                boardView.setLastMove(fromPt, row * 9 + col);
                clearSelect();
                afterMove();
                return;
            }
        }
        int piece = board.at(row, col);
        if (piece != 0 && XiangqiBoard.colorOf(piece) == ME) {
            selectedRow = row;
            selectedCol = col;
            List<int[]> ms = board.legalMoves(ME);
            java.util.ArrayList<Integer> pts = new java.util.ArrayList<Integer>();
            for (int[] m : ms) {
                if (m[0] == row && m[1] == col) {
                    pts.add(m[2] * 9 + m[3]);
                }
            }
            int[] out = new int[pts.size()];
            for (int i = 0; i < out.length; i++) {
                out[i] = pts.get(i);
            }
            legalPoints = out;
            boardView.setSelected(row, col);
            boardView.setLegalPoints(out);
            setStatus("已选中，点目标位置走子。");
            return;
        }
        clearSelect();
        boardView.setSelected(-1, -1);
        boardView.setLegalPoints(new int[0]);
    }

    private boolean isIn(int[] pts, int r, int c) {
        for (int p : pts) {
            if (p == r * 9 + c) {
                return true;
            }
        }
        return false;
    }

    private void clearSelect() {
        selectedRow = selectedCol = -1;
        legalPoints = new int[0];
        boardView.setSelected(-1, -1);
        boardView.setLegalPoints(new int[0]);
    }

    private void afterMove() {
        sync();
        if (board.gameOver()) {
            finishGame();
            return;
        }
        if (board.sideToMove() == ME) {
            setStatus("轮到你（红）。");
        } else {
            aiTurn();
        }
    }

    private String enginePath() {
        java.io.File f = NativeExe.prepare(this, "engine/eleeye", "libeleye.so");
        return f == null ? "" : f.getAbsolutePath();
    }

    /** ElephantEye 能不能用：真起一次进程握手，能正常回一手才算可用。 */
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
                    probe.setLevel(XiangqiAI.STRONG);
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

    /** 这一手交给谁算：引擎可用就走 ElephantEye（残局的强档是真强引擎），否则内置 AI。 */
    private XiangqiEngineApi engineForMove() {
        if (engineUsable()) {
            if (engine == null) engine = new XiangqiEngine(enginePath());
            engine.setLevel(diff);
            return engine;
        }
        ai.setLevel(diff);
        return ai;
    }

    private void closeEngine() {
        if (engine != null) {
            try {
                engine.close();
            } catch (Throwable ignore) {
            }
            engine = null;
        }
    }

    private void aiTurn() {
        if (board.gameOver()) {
            finishGame();
            return;
        }
        aiThinking = true;
        boardView.setInputEnabled(false);
        setStatus("电脑思考中…");
        final XiangqiBoard snapshot = board.copy();
        new Thread(new Runnable() {
            public void run() {
                int[] mv0;
                try {
                    mv0 = engineForMove().genmove(snapshot);
                } catch (RuntimeException e) {
                    ai.setLevel(diff);
                    mv0 = ai.genmove(snapshot);
                }
                final int[] mv = mv0;
                runOnUiThread(new Runnable() {
                    public void run() {
                        try {
                            aiThinking = false;
                            boardView.setInputEnabled(true);
                            if (mv == null) {
                                setStatus("电脑无棋可走。");
                                return;
                            }
                            board.play(mv[0], mv[1], mv[2], mv[3]);
                            boardView.setLastMove(mv[0] * 9 + mv[1], mv[2] * 9 + mv[3]);
                            afterMove();
                        } catch (Throwable t) {
                            PuzzleCrash.report(XiangqiPuzzleActivity.this, "象棋残局 电脑回手", t);
                        }
                    }
                });
            }
        }, "xiangqi-puzzle-ai").start();
    }

    private void finishGame() {
        boardView.setInputEnabled(false);
        if (board.winner() == ME) {
            setStatus("过关！" + board.resultString());
        } else if (board.winner() == CPU) {
            setStatus("电脑赢了，点「换一局」再来。" + board.resultString());
        } else {
            setStatus("和棋，点「换一局」再来。");
        }
    }

    private void sync() {
        if (boardView == null || board == null) {
            return;
        }
        boardView.setBoard(board);
        if (board.gameOver()) {
            finishGame();
        } else if (aiThinking) {
            setStatus("电脑思考中…");
        } else {
            setStatus("轮到你（红）。");
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
        // 与正常象棋对局页同一套尺寸：随屏幕自适应的正方形棋盘（9×10 画在里面）
        int side = dp(screen == null ? 340 : screen.boardSizeDp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(side, side);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
