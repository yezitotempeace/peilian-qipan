package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 2048：4×4 数字合并，滑动手势。没有彩蛋，只做数字合并。 */
public class Game2048Activity extends Activity {

    private Game2048 game;
    private Game2048View view;
    private TextView scoreView, bestView, hint;
    private int best;
    private boolean winShown;
    private boolean overShown;
    private Palette palette;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        palette = Palette.of(UiTheme.apply(this));
        best = prefs().getInt("best2048", 0);
        game = new Game2048();
        restore();
        buildUi();
        refresh();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("autogo", MODE_PRIVATE);
    }

    private void restore() {
        String saved = prefs().getString("2048board", "");
        if (saved == null || saved.length() == 0) {
            return;
        }
        String[] parts = saved.split(",");
        if (parts.length != 16) {
            return;
        }
        int[] cells = new int[16];
        try {
            for (int i = 0; i < 16; i++) {
                cells[i] = Integer.parseInt(parts[i]);
            }
        } catch (NumberFormatException e) {
            return;
        }
        int score = prefs().getInt("2048score", 0);
        game.load(cells, score);
        winShown = prefs().getBoolean("2048winshown", false);
        overShown = prefs().getBoolean("2048overshown", false);
    }

    /** 主题/字号/转屏等配置变化时整页重铺，保证深色下棋盘和页面都不发白。 */
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        recreate();
    }

    private void save() {
        StringBuilder sb = new StringBuilder();
        int[] cells = game.snapshot();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(cells[i]);
        }
        prefs().edit().putString("2048board", sb.toString())
                .putInt("2048score", game.score())
                .putInt("best2048", best)
                .putBoolean("2048winshown", winShown)
                .putBoolean("2048overshown", overShown)
                .apply();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        int pad = dp(14);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("2048");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTextColor(palette.text);
        title.setGravity(Gravity.LEFT);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button restart = new Button(this);
        restart.setText("重开");
        restart.setAllCaps(false);
        restart.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        restart.setTextColor(palette.buttonText);
        restart.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                game.reset();
                winShown = false;
                overShown = false;
                view.setGame(game);
                refresh();
                save();
            }
        });
        top.addView(restart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)));
        root.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout scores = new LinearLayout(this);
        scores.setOrientation(LinearLayout.HORIZONTAL);
        scores.setGravity(Gravity.CENTER_VERTICAL);
        scoreView = scoreLabel("得分 0");
        bestView = scoreLabel("最高 0");
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        scores.addView(scoreView, slp);
        scores.addView(bestView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams scoresLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        scoresLp.setMargins(0, dp(6), 0, dp(6));
        root.addView(scores, scoresLp);

        view = new Game2048View(this);
        view.setDark(palette.dark);
        view.setGame(game);
        view.setOnSwipe(new Game2048View.OnSwipe() {
            @Override
            public void onSwipe(int dir) {
                move(dir);
            }
        });
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        boardLp.setMargins(0, dp(4), 0, dp(4));
        root.addView(view, boardLp);

        hint = new TextView(this);
        hint.setText("滑动合并相同数字；达到 2048 可继续。这个游戏没有彩蛋。");
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setTextColor(palette.textDim);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Watermark.wrap(this, root, palette, 6);
    }

    private TextView scoreLabel(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTextColor(palette.text);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void move(int dir) {
        boolean moved = game.move(dir);
        if (!moved) {
            if (game.over() && !overShown) {
                overShown = true;
                showOver();
                save();
            }
            return;
        }
        if (game.score() > best) {
            best = game.score();
        }
        view.onMoved();
        refresh();
        save();
        if (game.won() && !winShown) {
            winShown = true;
            save();
            showWin();
        } else if (game.over() && !overShown) {
            overShown = true;
            save();
            showOver();
        }
    }

    private void refresh() {
        scoreView.setText("得分 " + game.score());
        bestView.setText("最高 " + best);
    }

    private void showWin() {
        new AlertDialog.Builder(this)
                .setTitle("达成 2048")
                .setMessage("已经拼出 2048 方块。可以继续冲更高分，或者重开一局。")
                .setPositiveButton("继续", null)
                .setNegativeButton("重开", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        game.reset();
                        winShown = false;
                        overShown = false;
                        view.setGame(game);
                        refresh();
                        save();
                    }
                })
                .show();
    }

    private void showOver() {
        new AlertDialog.Builder(this)
                .setTitle("没有可走的了")
                .setMessage("得分 " + game.score() + "。再来一局？")
                .setPositiveButton("重开", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int w) {
                        game.reset();
                        winShown = false;
                        overShown = false;
                        view.setGame(game);
                        refresh();
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
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
