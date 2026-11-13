/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 军棋对局页（暗棋）—— 完全重写。
 *
 * <p>流程分两个阶段：</p>
 * <ol>
 *   <li><b>布阵</b>：双方随机摆出合法阵型后，玩家可点选两枚己方棋子对调位置
 *       （军旗/地雷/炸弹的位置约束会自动校验），满意后点「开始对局」；</li>
 *   <li><b>对局</b>：玩家执红先行。点自己的棋子 → 高亮所有合法落点 → 点落点走子/吃子；
 *       电脑随后自动落子。交战后双方棋子身份亮出。</li>
 * </ol>
 *
 * <p>棋盘与规则见 {@link JunqiBoard}，AI 见 {@link JunqiAI}。本页只负责界面与交互。</p>
 */
public class JunqiActivity extends Activity {

    private JunqiBoard board;
    private final JunqiAI ai = new JunqiAI();
    private JunqiView boardView;
    private TextView status;
    private Button startBtn;
    private int level = JunqiAI.NORMAL;
    private boolean flipMode;

    private boolean aiThinking;
    private boolean alive = true;
    private int selected = -1;                 // row * COLS + col
    private List<int[]> legal = new ArrayList<int[]>();

    private Palette palette;
    private Screen screen;
    private boolean darkTheme;
    private ScrollView scroll;
    private LinearLayout topChrome;
    private LinearLayout bottomChrome;
    private int boardSideW, boardSideH;

    /** 经验库：记录电脑每一步的特征与最终胜负。 */
    private JunqiLearn learn;
    private final List<String> learnKeys =
            java.util.Collections.synchronizedList(new ArrayList<String>());
    private boolean learnRecorded;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        darkTheme = UiTheme.apply(this);
        palette = Palette.of(darkTheme);
        screen = Screen.read(this);
        learn = JunqiLearn.load(new File(getFilesDir(), JunqiLearn.FILE_NAME));
        newGame(false);
        buildUi();
    }

    private void newGame(boolean flip) {
        flipMode = flip;
        board = new JunqiBoard(System.currentTimeMillis(), flip);
        selected = -1;
        legal = new ArrayList<int[]>();
        aiThinking = false;
        learnRecorded = false;
        synchronized (learnKeys) {
            learnKeys.clear();
        }
        ai.setLevel(level);
        if (boardView != null) {
            boardView.setBoard(board);
        }
    }

    // ------------------------------------------------------------ 界面

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
        title.setText("军　棋");
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

        boardView = new JunqiView(this);
        boardView.setDark(palette.dark);
        boardView.setAnimationsEnabled(true);
        boardView.setBoard(board);
        boardView.setOnCellTap(new JunqiView.OnCellTap() {
            public void onTap(int row, int col) {
                onCellTap(row, col);
            }
        });
        root.addView(boardView, boardParams());

        bottomChrome = new LinearLayout(this);
        bottomChrome.setOrientation(LinearLayout.VERTICAL);

        // 布阵阶段专用按钮
        startBtn = new Button(this);
        startBtn.setText("开始对局");
        startBtn.setTextColor(palette.buttonText);
        startBtn.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
        startBtn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startBattle();
            }
        });
        bottomChrome.addView(startBtn, matchWrap());

        bottomChrome.addView(buttonRow(new String[]{"重新布阵", "认输"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                newGame(flipMode);
                                sync();
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                resign();
                            }
                        }}));
        bottomChrome.addView(buttonRow(new String[]{"难度", "模式", "返回"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                level = (level + 1) % 3;
                                ai.setLevel(level);
                                setStatus("难度：" + levelName());
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                newGame(!flipMode);
                                sync();
                                setStatus(flipMode
                                        ? "已切换为翻棋：50 子随机扣着铺满，翻牌算一步，翻到什么是什么。"
                                        : "已切换为暗棋：看不见对方棋子，交战后亮出。");
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                finish();
                            }
                        }}));
        root.addView(bottomChrome, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 规则参考
        TextView rules = new TextView(this);
        rules.setTextSize(11f);
        rules.setTextColor(palette.textDim);
        rules.setPadding(dp(12), dp(8), dp(12), dp(8));
        rules.setText(
                "【军衔】司令 > 军长 > 师长 > 旅长 > 团长 > 营长 > 连长 > 排长 > 工兵\n"
              + "【吃子】高吃低，同级同归于尽；炸弹与任何棋子同归于尽\n"
              + "【地雷】不能动；非工兵撞雷阵亡、雷保留；工兵挖雷\n"
              + "【军旗】在大本营，被扛走即输\n"
              + "【行营】安全区，里面的棋子不会被吃；四周可斜走\n"
              + "【铁路】可直线滑行多格；工兵可沿铁路拐弯\n"
              + "【山界】中线两侧不可通过，只能走三个前线口");
        root.addView(rules, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        sync();
        scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or2, int ob) {
                fitBoardToScreen();
            }
        });
    }

    private String levelName() {
        return level == JunqiAI.STRONG ? "强" : level == JunqiBoard.RED ? "普通" : "快";
    }

    private void startBattle() {
        if (board == null || !board.isSetupPhase()) {
            return;
        }
        board.beginBattle();
        selected = -1;
        legal = new ArrayList<int[]>();
        sync();
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams boardParams() {
        int h = boardSideH > 0 ? boardSideH : dp(screen.boardSizeDp(8));
        int w = boardSideW > 0 ? boardSideW
                : Math.max(dp(90), Math.round(h * 4.8f / 8.4f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    /** 按可用空间定棋盘宽高（实体军棋比例约 4.8:8.4）。 */
    private void fitBoardToScreen() {
        if (scroll == null || boardView == null || topChrome == null || bottomChrome == null) {
            return;
        }
        int availW = scroll.getWidth() - dp(16);
        int availH = scroll.getHeight() - topChrome.getHeight() - bottomChrome.getHeight() - dp(16);
        if (availW <= 0 || availH <= 0) {
            return;
        }
        float spanX = 4.8f, spanY = 8.4f;
        int h = Math.min(availH, Math.round(availW * spanY / spanX));
        int w = Math.round(h * spanX / spanY);
        int minW = Math.min(dp(90), Math.round(availH * spanX / spanY));
        if (w < minW) {
            w = minW;
            h = Math.round(w * spanY / spanX);
        }
        if (w == boardSideW && h == boardSideH) {
            return;
        }
        boardSideW = w;
        boardSideH = h;
        ViewGroup.LayoutParams lp = boardView.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            lp.width = w;
            lp.height = h;
            ((LinearLayout.LayoutParams) lp).gravity = Gravity.CENTER_HORIZONTAL;
            boardView.setLayoutParams(lp);
            boardView.requestLayout();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        if (UiTheme.dark(this) != darkTheme) {
            rebuildForTheme(UiTheme.dark(this));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            fitBoardToScreen();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (UiTheme.dark(this) != darkTheme) {
            rebuildForTheme(UiTheme.dark(this));
        }
    }

    private void rebuildForTheme(boolean now) {
        darkTheme = now;
        palette = Palette.of(now);
        setTheme(UiTheme.style(now));
        screen = Screen.read(this);
        buildUi();
    }

    @Override
    protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }

    // ------------------------------------------------------------ 交互

    private void onCellTap(int row, int col) {
        if (board == null || board.gameOver() || aiThinking || !alive) {
            return;
        }
        if (board.isSetupPhase()) {
            handleSetupTap(row, col);
            return;
        }
        if (board.sideToMove() != JunqiBoard.RED) {
            return;
        }
        handlePlayTap(row, col);
    }

    /** 布阵阶段：点两枚己方棋子对调位置。 */
    private void handleSetupTap(int row, int col) {
        int pt = JunqiBoard.idx(row, col);
        JunqiBoard.Pc p = board.at(row, col);
        if (selected < 0) {
            if (p != null && p.side == JunqiBoard.RED) {
                selected = pt;
                setStatus("已选中「" + JunqiBoard.typeName(p.type) + "」，再点另一枚对调；或点「开始对局」。");
            } else {
                setStatus("请点选你自己的棋子来调整阵型。");
            }
            sync();
            return;
        }
        int sr = selected / JunqiBoard.COLS, sc = selected % JunqiBoard.COLS;
        if (pt == selected) {
            selected = -1;
            setStatus("已取消选择。");
            sync();
            return;
        }
        if (board.swapOwn(JunqiBoard.RED, sr, sc, row, col)) {
            selected = -1;
            setStatus("阵型已调整。满意就点「开始对局」。");
        } else {
            setStatus("这两枚不能对调（军旗须在大本营、地雷限后两排、炸弹不进第一排）。");
        }
        sync();
    }

    /** 对局阶段：点己方棋子选落点，再点落点走子。 */
    private void handlePlayTap(int row, int col) {
        int pt = JunqiBoard.idx(row, col);
        // 翻棋：点扣着的棋子 = 翻开（算一步）
        if (flipMode) {
            JunqiBoard.Pc tapped = board.at(row, col);
            if (tapped != null && tapped.faceDown) {
                selected = -1;
                legal = new ArrayList<int[]>();
                int res = board.play(JunqiBoard.RED, row, col, row, col);
                boardView.setLastMove(row, col, row, col);
                setStatus(res >= 0 ? "翻出「" + JunqiBoard.typeName(board.typeAt(row, col)) + "」。" : "翻不了。");
                if (board.gameOver()) {
                    sync();
                    finishGame();
                    return;
                }
                sync();
                aiTurn();
                return;
            }
        }
        if (selected < 0) {
            JunqiBoard.Pc p = board.at(row, col);
            if (p == null || p.side != JunqiBoard.RED) {
                return;
            }
            selected = pt;
            legal = filterFrom(board.legalMoves(JunqiBoard.RED), row, col);
            if (legal.isEmpty()) {
                setStatus("这枚棋子目前无路可走，换一枚试试。");
                selected = -1;
            }
            sync();
            return;
        }
        int sr = selected / JunqiBoard.COLS, sc = selected % JunqiBoard.COLS;
        if (pt == selected) {
            selected = -1;
            legal = new ArrayList<int[]>();
            sync();
            return;
        }
        for (int[] m : legal) {
            if (m[2] == row && m[3] == col) {
                selected = -1;
                legal = new ArrayList<int[]>();
                playerMove(sr, sc, row, col);
                return;
            }
        }
        // 点的不是落点：改为重新选择
        JunqiBoard.Pc p = board.at(row, col);
        if (p != null && p.side == JunqiBoard.RED) {
            selected = pt;
            legal = filterFrom(board.legalMoves(JunqiBoard.RED), row, col);
            sync();
        }
    }

    private static List<int[]> filterFrom(List<int[]> all, int r, int c) {
        List<int[]> out = new ArrayList<int[]>();
        for (int[] m : all) {
            if (m[0] == r && m[1] == c) {
                out.add(m);
            }
        }
        return out;
    }

    private void playerMove(int fr, int fc, int tr, int tc) {
        int res = board.play(JunqiBoard.RED, fr, fc, tr, tc);
        boardView.setLastMove(fr, fc, tr, tc);
        showBattle(res);
        if (board.gameOver()) {
            sync();
            finishGame();
            return;
        }
        sync();
        aiTurn();
    }

    private void aiTurn() {
        if (board.gameOver() || !alive) {
            return;
        }
        aiThinking = true;
        setStatus("电脑思考中…");
        final JunqiBoard snapshot = board;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final int[] mv = ai.genmove(snapshot);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        aiThinking = false;
                        if (!alive || board != snapshot || board.gameOver()) {
                            return;
                        }
                        if (mv == null) {
                            setStatus("电脑无棋可走。");
                            return;
                        }
                        JunqiBoard.Pc mover = snapshot.at(mv[0], mv[1]);
                        int res = board.play(JunqiBoard.BLACK, mv[0], mv[1], mv[2], mv[3]);
                        boardView.setLastMove(mv[0], mv[1], mv[2], mv[3]);
                        if (mover != null) {
                            String key = "t" + mover.type + "z" + (mv[3] / 3)
                                    + "f" + (res == JunqiBoard.RES_WIN || res == JunqiBoard.RES_BOTH ? 1 : 0);
                            synchronized (learnKeys) {
                                learnKeys.add(key);
                            }
                        }
                        showBattle(res);
                        if (board.gameOver()) {
                            sync();
                            finishGame();
                            return;
                        }
                        sync();
                    }
                });
            }
        }, "junqi-ai");
        t.setDaemon(true);
        t.start();
    }

    private void showBattle(int res) {
        if (res == JunqiBoard.RES_WIN) {
            setStatus("你吃掉了对方棋子。");
        } else if (res == JunqiBoard.RES_LOSE) {
            setStatus("你的棋子被吃掉了。");
        } else if (res == JunqiBoard.RES_BOTH) {
            setStatus("同归于尽。");
        }
    }

    private void finishGame() {
        String text;
        boolean humanWon;
        if (board.result() == 1) {
            text = "你赢了！";
            humanWon = true;
        } else if (board.result() == 2) {
            text = "电脑获胜。";
            humanWon = false;
        } else {
            text = "和棋。";
            humanWon = false;
        }
        recordLearning(humanWon ? JunqiLearn.LOSS : JunqiLearn.WIN);
        AlertDialog.Builder dlg = new AlertDialog.Builder(this)
                .setTitle("对局结束")
                .setMessage(text + "\n" + learnInfo());
        if (humanWon && WinEgg.eggAllowed(level, JunqiAI.STRONG)) {
            dlg.setView(WinEgg.buildBody(this));
        }
        dlg
                .setPositiveButton("再来一局", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        newGame(flipMode);
                        sync();
                    }
                })
                .setNegativeButton("返回", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        finish();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private void resign() {
        if (board == null || board.gameOver()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("认输")
                .setMessage("确认认输？")
                .setPositiveButton("认输", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        recordLearning(JunqiLearn.WIN);
                        new AlertDialog.Builder(JunqiActivity.this)
                                .setTitle("对局结束")
                                .setMessage("你认输了。\n" + learnInfo())
                                .setPositiveButton("返回", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface d2, int w2) {
                                        finish();
                                    }
                                })
                                .setCancelable(false)
                                .show();
                    }
                })
                .setNegativeButton("继续下", null)
                .show();
    }

    private void recordLearning(int result) {
        if (learn == null || learnRecorded) {
            return;
        }
        learnRecorded = true;
        learn.recordGame(learnKeys, result);
        learn.save(new File(getFilesDir(), JunqiLearn.FILE_NAME));
    }

    private String learnInfo() {
        return learn == null ? "" : "（" + learn.describe() + "）";
    }

    // ------------------------------------------------------------ 状态同步

    private void sync() {
        if (boardView != null && board != null) {
            boardView.setBoard(board);
            boardView.setSelected(selected < 0 ? -1 : selected / JunqiBoard.COLS,
                    selected < 0 ? -1 : selected % JunqiBoard.COLS);
            int[] pts = new int[legal.size()];
            for (int i = 0; i < legal.size(); i++) {
                pts[i] = JunqiBoard.idx(legal.get(i)[2], legal.get(i)[3]);
            }
            boardView.setLegalPoints(pts);
            if (startBtn != null) {
                startBtn.setVisibility(board.isSetupPhase() ? View.VISIBLE : View.GONE);
            }
        }
        if (board != null && board.isSetupPhase()) {
            setStatus("布阵阶段：点两枚己方棋子对调位置，满意后点「开始对局」。");
        } else if (status != null && !aiThinking && board != null && !board.gameOver()
                && selected < 0) {
            setStatus("你执红先走。点己方棋子查看可走的落点。");
        }
    }

    private void setStatus(String s) {
        if (status != null) {
            status.setText(s);
        }
    }

    // ------------------------------------------------------------ 控件辅助

    private LinearLayout buttonRow(String[] names, View.OnClickListener[] ls) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            Button btn = new Button(this);
            btn.setText(names[i]);
            btn.setAllCaps(false);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
            btn.setTextColor(palette.buttonText);
            btn.setBackgroundTintList(ColorStateList.valueOf(palette.buttonBg));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            row.addView(btn, lp);
            final View.OnClickListener l = ls[i];
            btn.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    l.onClick(v);
                }
            });
        }
        return row;
    }

    private int dp(int v) {
        return Math.round(v * (screen != null ? screen.density : 1f));
    }
}
