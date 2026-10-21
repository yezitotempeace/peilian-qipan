/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * 象棋复盘页：结论 + 胜率条 + 胜率曲线 + 只读棋盘 + 失误排行 + 手顺列表。
 * 数据由对局页在 Intent 里传进来（口径见 {@link XiangqiReview}）；长度不对就不采用，只显示手顺。
 */
public class XiangqiReviewActivity extends Activity {

    public static final String EXTRA_MOVES = "xqreview.moves";
    public static final String EXTRA_WR = "xqreview.wr";
    public static final String EXTRA_LOSS = "xqreview.loss";
    public static final String EXTRA_BEST = "xqreview.best";
    public static final String EXTRA_BESTPT = "xqreview.bestpt";
    public static final String EXTRA_VERDICT = "xqreview.verdict";
    public static final String EXTRA_WINNER = "xqreview.winner";
    public static final String EXTRA_END = "xqreview.end";

    /** 每条手顺 4 个 int：fromRow/fromCol/toRow/toCol。 */
    private int[] rawMoves = new int[0];
    private int n;
    private double[] wr;
    private double[] loss;
    private String[] best;
    private int[] bestPoint;
    private String verdictText = "";
    private int winner;
    private String endText = "";

    private int sel;

    private Palette palette;
    private Screen screen;
    private boolean dark;

    private LinearLayout panel;
    private TextView positionText;
    private WinrateBarView bar;
    private WinrateChartView chart;
    private XiangqiView boardView;

    private final java.util.List<View> moveRows = new java.util.ArrayList<View>();
    private final java.util.List<View> blunderRows = new java.util.ArrayList<View>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = UiTheme.apply(this);
        palette = Palette.of(dark);
        screen = Screen.read(this);
        readIntent(getIntent());
        buildUi();
        select(n);
    }

    private void readIntent(Intent it) {
        if (it == null) {
            return;
        }
        int[] rm = it.getIntArrayExtra(EXTRA_MOVES);
        if (rm != null) {
            rawMoves = rm;
        }
        n = rawMoves.length / 4;
        String v = it.getStringExtra(EXTRA_VERDICT);
        if (v != null) {
            verdictText = v;
        }
        String e = it.getStringExtra(EXTRA_END);
        if (e != null) {
            endText = e;
        }
        winner = it.getIntExtra(EXTRA_WINNER, 0);
        double[] w = it.getDoubleArrayExtra(EXTRA_WR);
        double[] l = it.getDoubleArrayExtra(EXTRA_LOSS);
        String[] b = it.getStringArrayExtra(EXTRA_BEST);
        int[] bp = it.getIntArrayExtra(EXTRA_BESTPT);
        if (w == null || l == null || b == null || bp == null) {
            return;
        }
        if (w.length != n + 1 || l.length != n || b.length != n + 1 || bp.length != n + 1) {
            return;
        }
        wr = w;
        loss = l;
        best = b;
        bestPoint = bp;
    }

    private int moveColor(int index) {
        return (index % 2 == 0) ? XiangqiBoard.RED : XiangqiBoard.BLACK;
    }

    /** 红方胜率：行棋方胜率按轮次折算。 */
    private double[] redWinrates() {
        double[] out = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double w = wr == null ? 0.5 : wr[i];
            out[i] = (moveColor(i) == XiangqiBoard.RED) ? w : 1.0 - w;
        }
        return out;
    }

    private int[] moveAt(int index) {
        int[] mv = new int[4];
        for (int k = 0; k < 4; k++) {
            mv[k] = rawMoves[index * 4 + k];
        }
        return mv;
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        Button back = new Button(this);
        back.setText("返回");
        back.setAllCaps(false);
        back.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, screen.buttonTextSp());
        back.setTextColor(palette.buttonText);
        back.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.buttonBg));
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        top.addView(back, new LinearLayout.LayoutParams(dp(72), dp(40)));
        TextView title = new TextView(this);
        title.setText("象棋复盘");
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18f);
        title.setTextColor(palette.text);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp =
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        top.addView(title, titleLp);
        root.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView verdict = new TextView(this);
        verdict.setText(verdictText);
        verdict.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        verdict.setTextColor(palette.text);
        root.addView(verdict, textRow(8));

        boardView = new XiangqiView(this);
        boardView.setInputEnabled(false);
        boardView.setShowCoordinates(true);
        int side = dp(Math.min(Math.max(180, screen.widthDp - 32), 360));
        LinearLayout.LayoutParams boardLp = new LinearLayout.LayoutParams(side, side);
        boardLp.gravity = Gravity.CENTER_HORIZONTAL;
        boardLp.topMargin = dp(6);
        root.addView(boardView, boardLp);

        positionText = new TextView(this);
        positionText.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        positionText.setTextColor(palette.textDim);
        root.addView(positionText, textRow(6));

        bar = new WinrateBarView(this);
        bar.setPalette(palette);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        barLp.topMargin = dp(4);
        root.addView(bar, barLp);

        chart = new WinrateChartView(this);
        chart.setPalette(palette);
        LinearLayout.LayoutParams chartLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(screen.chartHeightDp()));
        chart.setData(wr == null ? new double[]{0.5} : redWinrates());
        chart.setOnSeek(new WinrateChartView.OnSeek() {
            @Override
            public void onSeek(int index) {
                select(index);
            }
        });
        root.addView(chart, chartLp);

        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        root.addView(panel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        blunderRows.clear();
        if (loss != null) {
            int shown = 0;
            for (int i = 0; i < n && shown < 5; i++) {
                if (loss[i] >= XiangqiReview.BLUNDER_MIN) {
                    final int target = i;
                    TextView row = new TextView(this);
                    row.setText("第 " + (i + 1) + " 手 " + XiangqiBoard.moveText(moveAt(i))
                            + " 亏 " + Math.round(loss[i] * 100) + "%"
                            + (best != null && best[i] != null && best[i].length() > 0
                            ? "（应下 " + best[i] + "）" : ""));
                    row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
                    row.setTextColor(palette.text);
                    row.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            select(target + 1);
                        }
                    });
                    panel.addView(row, textRow(2));
                    blunderRows.add(row);
                    shown++;
                }
            }
        }

        moveRows.clear();
        for (int i = 0; i < n; i++) {
            final int target = i + 1;
            TextView row = new TextView(this);
            row.setText(moveNumber(i) + ". " + XiangqiBoard.moveText(moveAt(i)));
            row.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
            row.setTextColor(palette.textDim);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(target);
                }
            });
            panel.addView(row, textRow(1));
            moveRows.add(row);
        }
        TextView endRow = new TextView(this);
        endRow.setText("终局　" + (endText.length() == 0 ? "（未知）" : endText));
        endRow.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f);
        endRow.setTextColor(palette.textDim);
        endRow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                select(n);
            }
        });
        panel.addView(endRow, textRow(1));
        moveRows.add(endRow);

        scroll.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Watermark.wrap(this, scroll, palette, 6);
    }

    private String moveNumber(int index) {
        return String.valueOf(index + 1);
    }

    private LinearLayout.LayoutParams textRow(int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(topDp);
        return lp;
    }

    /** 选中第 index 手（0 = 开局前，n = 终局）。 */
    public void select(int index) {
        if (index < 0) {
            index = 0;
        }
        if (index > n) {
            index = n;
        }
        sel = index;
        XiangqiBoard b = new XiangqiBoard();
        for (int i = 0; i < index; i++) {
            int[] mv = moveAt(i);
            b.play(mv[0], mv[1], mv[2], mv[3]);
        }
        boardView.setBoard(b);
        if (index > 0) {
            int[] lm = moveAt(index - 1);
            boardView.setLastMove(lm[0] * XiangqiBoard.COLS + lm[1], lm[2] * XiangqiBoard.COLS + lm[3]);
        }
        if (bestPoint != null && index < n && index < bestPoint.length
                && bestPoint[index] >= 0 && bestPoint[index] < XiangqiBoard.ROWS * XiangqiBoard.COLS) {
            boardView.setHint(bestPoint[index]);
        } else {
            boardView.setHint(-1);
        }
        double w = wr == null ? 0.5 : redWinrates()[index];
        bar.setBlackWinrate(w);
        bar.setLabel(index == 0 ? "开局前（红先）"
                : (index == n ? "终局" : "第 " + index + " 手走完"));
        chart.setSelected(index);
        String who = (index == n) ? "" : ("，轮" + (moveColor(index) == XiangqiBoard.RED ? "红" : "黑") + "走");
        String lossText = (index == 0 || loss == null) ? ""
                : ("　上一手亏 " + Math.round(loss[index - 1] * 100) + "%");
        positionText.setText("红方胜率 " + Math.round(w * 100) + "%　"
                + (index == 0 ? "开局" : "第 " + index + " 手") + who + lossText);
        for (int i = 0; i < moveRows.size(); i++) {
            moveRows.get(i).setBackgroundColor(i == index ? palette.surface : 0);
        }
        for (int i = 0; i < blunderRows.size(); i++) {
            blunderRows.get(i).setBackgroundColor(0);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
