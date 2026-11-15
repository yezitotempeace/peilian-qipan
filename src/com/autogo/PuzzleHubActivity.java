/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 残局选择页：五子棋 / 围棋 / 象棋 各有 弱 / 中 / 强 三档。
 *
 * <p>点哪一格就进哪一局的残局对局页。残局里没有彩蛋。</p>
 */
public class PuzzleHubActivity extends Activity {

    private Palette palette;
    private boolean darkTheme;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        darkTheme = UiTheme.apply(this);
        palette = Palette.of(darkTheme);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        int pad = dp(10);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("残　局");
        title.setTextSize(24f);
        title.setTextColor(palette.text);
        title.setGravity(Gravity.CENTER);
        root.addView(title, cell());

        TextView sub = new TextView(this);
        sub.setText("选一个棋、选一档难度，从给定局面开始，赢下电脑。\n对手强度跟着档位走，「强」用的是各自最强的引擎。");
        sub.setTextSize(13f);
        sub.setTextColor(palette.textDim);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(6), 0, dp(6));
        root.addView(sub, cell());

        int[] games = {EndgamePuzzles.GAME_GOMOKU, EndgamePuzzles.GAME_GO, EndgamePuzzles.GAME_XIANGQI};
        for (int g : games) {
            TextView head = new TextView(this);
            head.setText(EndgamePuzzles.GAME_NAMES[g]);
            head.setTextSize(17f);
            head.setTextColor(palette.text);
            head.setGravity(Gravity.CENTER);
            head.setPadding(0, dp(12), 0, dp(4));
            root.addView(head, cell());

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int d = 0; d < 3; d++) {
                final int game = g, diff = d;
                Button btn = new Button(this);
                btn.setText(EndgamePuzzles.DIFF_NAMES[d]);
                btn.setTextSize(15f);
                btn.setTextColor(palette.buttonText);
                btn.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
                btn.setAllCaps(false);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(dp(4), dp(4), dp(4), dp(4));
                btn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Intent it = new Intent(PuzzleHubActivity.this, playTarget(game));
                        it.putExtra("diff", diff);
                        startActivity(it);
                    }
                });
                row.addView(btn, lp);
            }
            root.addView(row, cell());
        }

        TextView note = new TextView(this);
        note.setText("残局没有彩蛋。");
        note.setTextSize(11f);
        note.setTextColor(palette.textDim);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(14), 0, 0);
        root.addView(note, cell());

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        RulesHelp.attach(this, palette,
                "【残局】从一道给定局面开始，你来走、电脑来守，赢下这一局就算过关。\n"
              + "【难度】弱 / 中 / 强三档：越强的对手越难对付，「强」用的是各棋最强的引擎。\n"
              + "【五子棋】黑先，连成五子即胜。\n"
              + "【围棋】黑先，终局数子，围地多者胜。\n"
              + "【象棋】红先，把黑将将死（或让对方无棋可走）即胜。\n"
              + "【提示】先看清局面里谁占优、有哪些威胁，再动手。");
        setContentView(scroll);
    }

    private Class<?> playTarget(int game) {
        if (game == EndgamePuzzles.GAME_GOMOKU) return GomokuPuzzleActivity.class;
        if (game == EndgamePuzzles.GAME_GO) return GoPuzzleActivity.class;
        return XiangqiPuzzleActivity.class;
    }

    private LinearLayout.LayoutParams cell() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
