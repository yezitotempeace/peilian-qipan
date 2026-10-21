/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 五子棋复盘页：胜率曲线 + 失误排行 + 逐步手顺（含每手的推荐点）。
 *
 * <p>数据由对局页（{@link GomokuActivity}）算好后用 Intent 数组带过来 —— 复盘页自己不碰引擎，
 * 所以这一页只负责画。数组口径与围棋复盘（{@code GameReview} / {@code ReviewActivity}）一致：
 * {@code wrToMove} / {@code bestMove} / {@code bestPoint} 长度 n+1（下标 = 第 i 手落子前的位置），
 * {@code loss} 长度 n（下标 i = 第 i+1 手的亏损）。
 *
 * <p>主题与字号跟着系统走（{@link UiTheme}），与对局页、选择页同一套：进页面先定窗口主题，
 * 清单声明了配置变化由本页处理，所以 {@code onConfigurationChanged} 里自己重判重铺，
 * 自动档还要靠每分钟一次的 {@code themeTick} 盯过点。
 */
public class GomokuReviewActivity extends Activity {

    public static final String EXTRA_SIZE = "gomokuSize";
    public static final String EXTRA_MOVES = "gomokuMoves";
    public static final String EXTRA_FORBID = "gomokuForbid";
    public static final String EXTRA_FIRST = "gomokuFirst";
    public static final String EXTRA_WR = "gomokuWr";
    public static final String EXTRA_LOSS = "gomokuLoss";
    public static final String EXTRA_BEST = "gomokuBest";
    public static final String EXTRA_BESTPT = "gomokuBestPt";
    public static final String EXTRA_VERDICT = "gomokuVerdict";
    public static final String EXTRA_WINNER = "gomokuWinner";
    public static final String EXTRA_END = "gomokuEnd";

    private int size = 15;
    private boolean forbid = false;
    private int firstColor = GomokuBoard.BLACK;
    private final List<int[]> moves = new ArrayList<int[]>();
    private double[] wrToMove;
    private double[] loss;
    private String[] bestMove;
    private int[] bestPoint;
    private String verdictLine = "";
    private String endText = "";

    private Palette palette;
    private Screen screen;
    private boolean dark;
    /** 当前选中的位置：0 = 开局前，n = 终局。 */
    private int sel = 0;

    private ScrollView scroll;
    private LinearLayout root;
    private TextView positionText;
    private GomokuView boardView;
    private WinrateBarView bar;
    private WinrateChartView chart;
    private final List<TextView> rows = new ArrayList<TextView>();
    private final List<TextView> blunders = new ArrayList<TextView>();

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable themeTick = new Runnable() {
        @Override
        public void run() {
            maybeAutoSwitchTheme();
            ui.postDelayed(this, 60_000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dark = UiTheme.apply(this);
        palette = Palette.of(dark);
        screen = Screen.read(this);
        readIntent(getIntent());
        buildUi();
        select(moves.size());
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshTheme(newConfig);
    }

    @Override
    protected void onResume() {
        super.onResume();
        maybeAutoSwitchTheme();
        ui.removeCallbacks(themeTick);
        ui.postDelayed(themeTick, 60_000L);
        boolean now = UiTheme.dark(this);
        if (now != dark) {
            dark = now;
            palette = Palette.of(now);
            setTheme(UiTheme.style(now));
            screen = Screen.read(this);
            buildUi();
            select(sel);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(themeTick);
    }

    /** 自动档：过了 19:00 / 07:00 就换色（与选择页、对局页同一套）。 */
    private void maybeAutoSwitchTheme() {
        if (UiTheme.mode(this) != Theme.AUTO) return;
        boolean now = UiTheme.dark(this);
        if (now == dark) return;
        dark = now;
        palette = Palette.of(now);
        setTheme(UiTheme.style(now));
        screen = Screen.read(this);
        buildUi();
        select(sel);
    }

    /** 清单声明配置变化由本页处理：明暗变了才换窗口主题，界面一律重铺（字号/尺寸才跟得上）。 */
    private void refreshTheme(android.content.res.Configuration cfg) {
        boolean now = UiTheme.dark(UiTheme.mode(this), cfg);
        if (now != dark) {
            dark = now;
            setTheme(UiTheme.style(now));
        }
        palette = Palette.of(dark);
        screen = Screen.read(this);
        buildUi();
        select(sel);
    }

    /** 把对局页带过来的数组接住。长度对不上就不采用（宁可显示空曲线，也不画错数据）。 */
    private void readIntent(Intent it) {
        if (it == null) return;
        size = it.getIntExtra(EXTRA_SIZE, 15);
        forbid = it.getBooleanExtra(EXTRA_FORBID, false);
        firstColor = it.getIntExtra(EXTRA_FIRST, GomokuBoard.BLACK);
        int[] raw = it.getIntArrayExtra(EXTRA_MOVES);
        if (raw != null) {
            for (int i = 0; i + 1 < raw.length; i += 2) moves.add(new int[]{raw[i], raw[i + 1]});
        }
        int n = moves.size();
        double[] wr = it.getDoubleArrayExtra(EXTRA_WR);
        double[] ls = it.getDoubleArrayExtra(EXTRA_LOSS);
        String[] bm = it.getStringArrayExtra(EXTRA_BEST);
        int[] bp = it.getIntArrayExtra(EXTRA_BESTPT);
        String vd = it.getStringExtra(EXTRA_VERDICT);
        if (vd != null) verdictLine = vd;
        if (wr == null || ls == null || bm == null || bp == null) return;
        if (wr.length != n + 1 || ls.length != n || bm.length != n + 1 || bp.length != n + 1) return;
        wrToMove = wr;
        loss = ls;
        bestMove = bm;
        bestPoint = bp;
        // 终局文字直接用对局页带过来的结果（不在这一页重放，省一遍重算）
        String end = it.getStringExtra(EXTRA_END);
        int winner = it.getIntExtra(EXTRA_WINNER, GomokuBoard.EMPTY);
        if (end != null && !end.isEmpty()) endText = end;
        else if (moves.isEmpty()) endText = "（空棋谱）";
        else endText = (winner == GomokuBoard.EMPTY) ? "和棋（下满未分胜负）"
                : (winner == GomokuBoard.BLACK ? "黑胜" : "白胜");
    }

    private int colorAt(int i) {   // 第 i 手（0 基）是谁走的
        return (i % 2 == 0) ? firstColor : GomokuBoard.opponent(firstColor);
    }

    // —————————————————————————— 界面 ——————————————————————————

    private void buildUi() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        int pad = screen.dp(10);
        root.setPadding(pad, pad, pad, pad);

        // 标题条：标题 + 返回
        LinearLayout titleBar = new LinearLayout(this);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("五子棋复盘");
        title.setTextSize(screen.buttonTextSp() + 3);
        title.setTextColor(palette.text);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleBar.addView(title, tlp);
        Button back = makeButton("返回");
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        titleBar.addView(back, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, screen.dp(40)));
        root.addView(titleBar);

        TextView verdict = new TextView(this);
        verdict.setText(verdictLine);
        verdict.setTextSize(screen.buttonTextSp());
        verdict.setTextColor(palette.text);
        verdict.setPadding(0, screen.dp(6), 0, 0);
        root.addView(verdict);

        positionText = new TextView(this);
        positionText.setTextSize(screen.buttonTextSp() - 1);
        positionText.setTextColor(palette.textDim);
        positionText.setPadding(0, screen.dp(4), 0, screen.dp(4));
        root.addView(positionText);

        boardView = new GomokuView(this);
        boardView.setInputEnabled(false);   // 复盘只看不下
        boardView.setShowNumbers(true);     // 手顺数字：一眼看出哪手在哪里
        root.addView(boardView, boardParams());

        bar = new WinrateBarView(this);
        bar.setPalette(palette);
        root.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, screen.dp(44)));

        chart = new WinrateChartView(this);
        chart.setPalette(palette);
        chart.setData(blackWinrates());
        chart.setOnSeek(new WinrateChartView.OnSeek() {
            @Override
            public void onSeek(int index) {
                select(index);
            }
        });
        root.addView(chart, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, screen.dp(screen.chartHeightDp())));

        // 失误排行
        TextView blunderTitle = new TextView(this);
        blunderTitle.setText("失误排行");
        blunderTitle.setTextSize(screen.buttonTextSp());
        blunderTitle.setTextColor(palette.text);
        blunderTitle.setPadding(0, screen.dp(8), 0, screen.dp(2));
        root.addView(blunderTitle);
        blunders.clear();
        List<Integer> worst = worstIndexes(5);
        if (worst.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("全部手顺的亏损都在 " + pct(GomokuReview.BLUNDER_MIN) + " 以下，没有明显失误。");
            none.setTextSize(screen.buttonTextSp() - 1);
            none.setTextColor(palette.textDim);
            root.addView(none);
        } else {
            for (Integer idx : worst) {
                TextView row = new TextView(this);
                row.setText("第 " + (idx + 1) + " 手　" + colorName(colorAt(idx)) + " "
                        + name(idx) + "　亏 " + pct(loss[idx])
                        + (bestMove != null && bestMove[idx] != null && !bestMove[idx].isEmpty()
                        ? "　应下 " + bestMove[idx] : ""));
                row.setTextSize(screen.buttonTextSp() - 1);
                row.setTextColor(palette.text);
                row.setPadding(screen.dp(4), screen.dp(4), screen.dp(4), screen.dp(4));
                final int target = idx + 1;
                row.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        select(target);
                    }
                });
                root.addView(row);
                blunders.add(row);
            }
        }

        // 手顺
        TextView moveTitle = new TextView(this);
        moveTitle.setText("手顺（点一行跳到那一步）");
        moveTitle.setTextSize(screen.buttonTextSp());
        moveTitle.setTextColor(palette.text);
        moveTitle.setPadding(0, screen.dp(8), 0, screen.dp(2));
        root.addView(moveTitle);

        ScrollView list = new ScrollView(this);
        LinearLayout listBody = new LinearLayout(this);
        listBody.setOrientation(LinearLayout.VERTICAL);
        rows.clear();
        for (int i = 0; i < moves.size(); i++) {
            TextView row = new TextView(this);
            row.setText(moveLine(i));
            row.setTextSize(screen.buttonTextSp() - 1);
            row.setTextColor(palette.text);
            row.setPadding(screen.dp(4), screen.dp(3), screen.dp(4), screen.dp(3));
            final int target = i + 1;
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(target);
                }
            });
            listBody.addView(row);
            rows.add(row);
        }
        TextView terminal = new TextView(this);
        terminal.setText("终局　" + (endText.isEmpty() ? "（结果未记录）" : endText));
        terminal.setTextSize(screen.buttonTextSp() - 1);
        terminal.setTextColor(palette.textDim);
        terminal.setPadding(screen.dp(4), screen.dp(3), screen.dp(4), screen.dp(3));
        terminal.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                select(moves.size());
            }
        });
        listBody.addView(terminal);
        rows.add(terminal);
        list.addView(listBody);
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        scroll = new ScrollView(this);
        
        scroll.addView(root);
        select(sel);   // 首屏就把「当前选中的那一手」摆上棋盘
        Watermark.wrap(this, scroll, palette, 6);
    }

    private Button makeButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(screen.buttonTextSp());
        b.setPadding(screen.dp(2), screen.dp(6), screen.dp(2), screen.dp(6));
        b.setMinimumWidth(0);
        b.setTextColor(palette.buttonText);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.buttonBg));
        return b;
    }

    private String moveLine(int i) {
        String s = "第 " + (i + 1) + " 手　" + colorName(colorAt(i)) + " " + name(i);
        double l = loss == null ? 0 : loss[i];
        s += "　亏 " + pct(l);
        if (bestMove != null && bestMove[i] != null && !bestMove[i].isEmpty()
                && !bestMove[i].equals(name(i))) {
            s += "　应下 " + bestMove[i];
        }
        return s;
    }

    private String name(int i) {
        if (i < 0 || i >= moves.size()) return "—";
        int[] m = moves.get(i);
        return GomokuBoard.pointToName(size, m[1]);
    }

    private String colorName(int color) {
        return color == GomokuBoard.BLACK ? "黑" : "白";
    }

    /** 失误排行：最大的几手（只列真正算失误的）。 */
    private List<Integer> worstIndexes(int count) {
        List<Integer> out = new ArrayList<Integer>();
        if (loss == null) return out;
        boolean[] used = new boolean[loss.length];
        for (int k = 0; k < count; k++) {
            int best = -1;
            for (int i = 0; i < loss.length; i++) {
                if (used[i] || loss[i] < GomokuReview.BLUNDER_MIN) continue;
                if (best < 0 || loss[i] > loss[best]) best = i;
            }
            if (best < 0) break;
            used[best] = true;
            out.add(best);
        }
        return out;
    }

    private double[] blackWinrates() {
        int n = moves.size();
        double[] out = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            double wr = (wrToMove == null || i >= wrToMove.length) ? 0.5 : wrToMove[i];
            if (Double.isNaN(wr)) wr = 0.5;
            int toMove = colorAt(i);
            out[i] = (toMove == GomokuBoard.BLACK) ? wr : 1.0 - wr;
        }
        return out;
    }

    /** 选中第 index 步（0 = 开局前）：同步曲线、胜率条、位置文字与手顺高亮。 */
    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private LinearLayout.LayoutParams boardParams() {
        int px = dp(Math.min(screen.widthDp - 24, 400));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(px, px);
        p.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        p.setMargins(dp(4), dp(4), dp(4), dp(4));
        return p;
    }

    /** 把「走到第 index 手」的局面摆到棋盘上（index = 0 是空盘）。 */
    private void syncBoard() {
        if (boardView == null) return;
        GomokuBoard b = new GomokuBoard(size, false);
        for (int i = 0; i < sel && i < moves.size(); i++) {
            int[] mv = moves.get(i);
            if (mv != null && mv.length >= 2 && b.isLegal(mv[0], mv[1])) b.play(mv[0], mv[1]);
        }
        boardView.setBoard(b);
        boardView.setLastMove(sel > 0 && sel - 1 < moves.size() ? moves.get(sel - 1)[1] : -1);
    }

    private void select(int index) {
        int n = moves.size();
        if (index < 0) index = 0;
        if (index > n) index = n;
        sel = index;
        double[] bw = blackWinrates();
        if (bar != null) {
            bar.setBlackWinrate(bw[index]);
            bar.setLabel(index == 0
                    ? "开局前（黑先）"
                    : "第 " + index + " 手走完" + (index == n ? "：终局" : ""));
        }
        if (chart != null) chart.setSelected(index);
        if (positionText != null) {
            String who = (index == 0) ? "轮黑走" : (index == n ? "" : "轮" + colorName(colorAt(index)) + "走");
            String beat = "";
            if (index > 0 && loss != null && index - 1 < loss.length) {
                beat = "　上一手亏 " + pct(loss[index - 1]);
            }
            positionText.setText("黑棋胜率 " + pct(bw[index]) + "　" + (n == 0 ? "空棋谱" : (index == 0 ? "开局" : "第 " + index + " 手"))
                    + (who.isEmpty() ? "" : "，" + who) + beat);
        }
        syncBoard();
        for (int i = 0; i < rows.size(); i++) {
            TextView row = rows.get(i);
            // 第 i 个手顺行对应第 i+1 手；终局档（index == n）高亮终局行；开局前不高亮。
            boolean on = (index == n) ? (i == n) : (index > 0 && i == index - 1);
            row.setBackgroundColor(on ? palette.surface : 0x00000000);
        }
    }

    private String pct(double v) {
        if (Double.isNaN(v)) return "—";
        return String.format(java.util.Locale.US, "%.0f%%", v * 100.0);
    }
}
