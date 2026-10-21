/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * 数独页：9×9 棋盘 + 数字键盘 + 笔记 / 提示 / 撤销 / 计时。
 *
 * <p><b>屏幕适配</b>：所有尺寸都由 {@link Screen} 按当前屏幕算，
 * 布局全部用权重（棋盘吃掉剩余空间、按键等分宽度），竖屏上下排、横屏左右排，
 * 所以小屏 / 窄屏 / 矮屏 / 横屏都不会溢出或被裁。</p>
 *
 * <p>填写约束：同一行 / 同一列 / 同一宫都不能出现相同数字；重复时该格标红，
 * 但<b>允许填</b>（用户选的行为 A）。</p>
 */
public class SudokuActivity extends Activity {

    private SudokuGame game;
    private SudokuView view;
    private TextView infoView;
    private Button diffButton;
    private Button noteButton;
    private Screen screen;
    private Palette palette;
    private Handler handler;
    private boolean noteMode;
    private boolean solvedShown;
    private long startMs;
    private long elapsedMs;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            refreshInfo();
            if (!solvedShown && handler != null) {
                handler.postDelayed(this, 1000);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = Palette.of(UiTheme.apply(this));
        screen = Screen.read(this);
        handler = new Handler(Looper.getMainLooper());
        restore();
        buildUi();
        refreshInfo();
        startMs = System.currentTimeMillis() - elapsedMs;
        handler.postDelayed(tick, 1000);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("autogo", MODE_PRIVATE);
    }

    private void restore() {
        SharedPreferences p = prefs();
        int[] givens = SudokuGame.decode(p.getString("sudokuGivens", null));
        int[] board = SudokuGame.decode(p.getString("sudokuBoard", null));
        if (givens == null || board == null) {
            game = new SudokuGame(SudokuGame.NORMAL, System.nanoTime());
            return;
        }
        int[] notes = SudokuGame.decodeNotes(p.getString("sudokuNotes", null));
        game = SudokuGame.restore(givens, board, notes,
                p.getInt("sudokuDiff", SudokuGame.NORMAL), p.getLong("sudokuSeed", 0L));
        elapsedMs = p.getLong("sudokuElapsed", 0L);
    }

    private void save() {
        elapsedMs = Math.max(0L, System.currentTimeMillis() - startMs);
        prefs().edit()
                .putString("sudokuGivens", SudokuGame.encode(game.givenSnapshot()))
                .putString("sudokuBoard", SudokuGame.encode(game.snapshot()))
                .putString("sudokuNotes", SudokuGame.encodeNotes(game.noteSnapshot()))
                .putInt("sudokuDiff", game.difficulty())
                .putLong("sudokuSeed", game.seed())
                .putLong("sudokuElapsed", elapsedMs)
                .apply();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        recreate();
    }

    // ───────────────────────── 界面 ─────────────────────────

    private void buildUi() {
        boolean land = screen.landscape;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        int pad = screen.dp(screen.narrow ? 8 : 12);
        root.setPadding(pad, pad, pad, pad);

        root.addView(buildHeader(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        view = new SudokuView(this);
        view.setPalette(palette);
        view.setGame(game);
        view.setOnCellTap(new SudokuView.OnCellTap() {
            @Override
            public void onCellTap(int row, int col) {
                refreshInfo();
            }
        });

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(land ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER);

        if (land) {
            LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f);
            body.addView(view, boardLp);
            LinearLayout.LayoutParams padLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 2f);
            padLp.setMargins(screen.dp(10), 0, 0, 0);
            body.addView(buildPad(), padLp);
        } else {
            LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            boardLp.setMargins(0, screen.dp(6), 0, screen.dp(6));
            body.addView(view, boardLp);
            body.addView(buildPad(), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        Watermark.wrap(this, root, palette, 6);
    }

    private View buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        diffButton = smallButton("难度 " + SudokuGame.DIFF_NAMES[game.difficulty()]);
        diffButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleDifficulty();
            }
        });
        row.addView(diffButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, screen.dp(screen.shortScreen ? 36 : 40)));

        infoView = new TextView(this);
        infoView.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen.narrow ? 12 : 13);
        infoView.setTextColor(palette.text);
        infoView.setGravity(Gravity.CENTER);
        row.addView(infoView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button restart = smallButton("重开");
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmRestart();
            }
        });
        row.addView(restart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, screen.dp(screen.shortScreen ? 36 : 40)));
        return row;
    }

    private View buildPad() {
        LinearLayout pad = new LinearLayout(this);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.addView(numberRow(new int[]{1, 2, 3, 4, 5}), rowParams());
        pad.addView(numberRow(new int[]{6, 7, 8, 9, 0}), rowParams());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        noteButton = smallButton("笔记 关");
        noteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                noteMode = !noteMode;
                noteButton.setText(noteMode ? "笔记 开" : "笔记 关");
                noteButton.setBackgroundTintList(ColorStateList.valueOf(
                        noteMode ? accentColor() : palette.buttonBg));
            }
        });
        actions.addView(noteButton, cellLp());

        Button hint = smallButton("提示");
        hint.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                doHint();
            }
        });
        actions.addView(hint, cellLp());

        Button undo = smallButton("撤销");
        undo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                game.undo();
                refreshAll();
            }
        });
        actions.addView(undo, cellLp());

        pad.addView(actions, rowParams());
        return pad;
    }

    private View numberRow(int[] digits) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < digits.length; i++) {
            final int d = digits[i];
            Button b = smallButton(d == 0 ? "擦" : String.valueOf(d));
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp() + (d == 0 ? 0 : 3));
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onDigit(d);
                }
            });
            row.addView(b, cellLp());
        }
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, screen.dp(screen.shortScreen ? 40 : 46));
        lp.setMargins(0, screen.dp(4), 0, 0);
        return lp;
    }

    private LinearLayout.LayoutParams cellLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
        lp.setMargins(screen.dp(3), 0, screen.dp(3), 0);
        return lp;
    }

    private Button smallButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp());
        b.setTextColor(palette.buttonText);
        b.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
        b.setPadding(0, 0, 0, 0);
        b.setMinimumWidth(0);
        b.setMinimumHeight(0);
        return b;
    }

    private int accentColor() {
        return palette.dark ? 0xFF2F5D8C : 0xFF90CAF9;
    }

    // ───────────────────────── 操作 ─────────────────────────

    private void onDigit(int d) {
        int r = view.selectedRow();
        int c = view.selectedCol();
        if (r < 0 || c < 0) {
            refreshInfo();
            return;
        }
        if (noteMode && d != 0) {
            game.toggleNote(r, c, d);
        } else {
            game.set(r, c, d);
        }
        refreshAll();
    }

    private void doHint() {
        int r = view.selectedRow();
        int c = view.selectedCol();
        boolean ok = (r >= 0 && c >= 0) && game.hint(r, c);
        if (!ok) {
            game.hint();
        }
        refreshAll();
    }

    private void cycleDifficulty() {
        int next = (game.difficulty() + 1) % 3;
        game.newGame(next, System.nanoTime());
        diffButton.setText("难度 " + SudokuGame.DIFF_NAMES[next]);
        view.setGame(game);
        solvedShown = false;
        startMs = System.currentTimeMillis();
        elapsedMs = 0;
        refreshAll();
        save();
    }

    private void confirmRestart() {
        new AlertDialog.Builder(this)
                .setTitle("重开一局")
                .setMessage("当前进度会清空，按「" + SudokuGame.DIFF_NAMES[game.difficulty()] + "」重新出题。")
                .setPositiveButton("重开", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        game.newGame(game.difficulty(), System.nanoTime());
                        view.setGame(game);
                        solvedShown = false;
                        startMs = System.currentTimeMillis();
                        elapsedMs = 0;
                        refreshAll();
                        save();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void refreshAll() {
        view.invalidate();
        refreshInfo();
        save();
        if (game.solved() && !solvedShown) {
            solvedShown = true;
            showSolved();
        }
    }

    private void refreshInfo() {
        if (infoView == null) {
            return;
        }
        long ms = Math.max(0L, System.currentTimeMillis() - startMs);
        long sec = ms / 1000L;
        String time = String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60);
        int conflicts = game.conflictCount();
        String tail = conflicts > 0 ? "　冲突 " + conflicts : "";
        infoView.setText("剩 " + game.remaining() + " 格　" + time + tail);
    }

    private void showSolved() {
        long ms = Math.max(0L, System.currentTimeMillis() - startMs);
        long sec = ms / 1000L;
        new AlertDialog.Builder(this)
                .setTitle("完成")
                .setMessage("用时 " + (sec / 60) + " 分 " + (sec % 60) + " 秒，提示 "
                        + game.hintsUsed() + " 次。再来一局？")
                .setPositiveButton("再来", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        game.newGame(game.difficulty(), System.nanoTime());
                        view.setGame(game);
                        solvedShown = false;
                        startMs = System.currentTimeMillis();
                        elapsedMs = 0;
                        refreshAll();
                        save();
                    }
                })
                .setNegativeButton("看棋盘", null)
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
        if (handler != null) {
            handler.removeCallbacks(tick);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startMs = System.currentTimeMillis() - elapsedMs;
        if (handler != null) {
            handler.removeCallbacks(tick);
            handler.postDelayed(tick, 1000);
        }
    }
}
