/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.List;
import java.util.Random;

/** 军棋对局页：人执红、电脑执黑；开局可选明棋 / 暗棋。 */
public class JunqiActivity extends Activity {

    private static final long SEED = 20261017L;

    private JunqiBoard board;
    private JunqiBoard startBoard;
    private JunqiAI ai = new JunqiAI();
    private final boolean[] revealed = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
    private boolean dark = true;
    /** 翻棋模式：开局全扣，点击翻开，翻开后双方都能看到。 */
    private boolean flipMode;
    /** 翻棋模式下每个格子是否已被翻开（翻开后永久可见）。 */
    private final boolean[] flipped = new boolean[JunqiBoard.COLS * JunqiBoard.ROWS];
    /** 游戏模式：0=暗棋、1=明棋、2=翻棋。 */
    private int gameMode = 0;
    private int level = JunqiAI.NORMAL;
    private boolean aiThinking;
    private boolean resigned;
    private boolean alive = true;

    private int selected = -1;
    private int[] legal = new int[0];

    private Palette palette;
    private Screen screen;
    private boolean darkTheme;
    private TextView status;
    private JunqiView boardView;
    private ScrollView scroll;
    private LinearLayout topChrome;
    private LinearLayout bottomChrome;
    private int boardSideW, boardSideH;
    private JunqiLearn learn;
    private final java.util.List<String> learnKeys =
            java.util.Collections.synchronizedList(new java.util.ArrayList<String>());
    private boolean learnRecorded;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        darkTheme = UiTheme.apply(this);
        palette = Palette.of(darkTheme);
        screen = Screen.read(this);
        learn = JunqiLearn.load(new File(getFilesDir(), JunqiLearn.FILE_NAME));
        newGame();
        buildUi();
    }

    private void newGame() {
        board = new JunqiBoard();
        board.setupStandard(new Random(SEED));
        startBoard = board.copy();
        // 模式初始化
        dark = gameMode == 0;         // 暗棋
        flipMode = gameMode == 2;     // 翻棋
        if (flipMode) {
            // 翻棋：全部扣着，flipped 全 false
            java.util.Arrays.fill(flipped, false);
            JunqiReveal.reset(revealed, true);  // 全隐藏（等同暗棋初始）
        } else {
            JunqiReveal.reset(revealed, dark);
        }
        selected = -1;
        legal = new int[0];
        aiThinking = false;
        resigned = false;
        learnRecorded = false;
        synchronized (learnKeys) {
            learnKeys.clear();
        }
        ai.setLearn(learn);
        ai.setLevel(level);
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
        title.setText("军　棋");
        title.setTextSize(20f);
        title.setTextColor(palette.text);
        title.setGravity(Gravity.CENTER);
        topChrome.addView(title);
        status = new TextView(this);
        status.setTextColor(palette.textDim);
        status.setTextSize(13f);
        status.setGravity(Gravity.CENTER);
        topChrome.addView(status);
        root.addView(topChrome, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        boardView = new JunqiView(this);
        boardView.setDark(palette.dark);
        boardView.setAnimationsEnabled(true);
        boardView.setBoard(board);
        boardView.setHidden(hiddenArray());
        boardView.setOnCellTap(new JunqiView.OnCellTap() {
            public void onTap(int row, int col) {
                onCellTap(row, col);
            }
        });
        root.addView(boardView, boardParams());

        bottomChrome = new LinearLayout(this);
        bottomChrome.setOrientation(LinearLayout.VERTICAL);
        bottomChrome.addView(buttonRow(new String[]{"新局", "认输", "难度"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                newGame();
                                sync();
                                setStatus("新开一局。你执红先走。");
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                resign();
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                level = (level + 1) % 3;
                                ai.setLevel(level);
                                setStatus("难度：" + levelName() + "（下一手生效）");
                            }
                        }}));
        bottomChrome.addView(buttonRow(new String[]{"切换模式", "返回"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                gameMode = (gameMode + 1) % 3;
                                newGame();
                                sync();
                                String[] names = {"暗棋（只看不见对方）", "明棋（双方都看得到）", "翻棋（全部扣着，点击翻开）"};
                                setStatus("已切换为" + names[gameMode] + "。");
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                finish();
                            }
                        }}));
        root.addView(bottomChrome, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 规则参考面板：军衔克制关系 + 特殊规则
        TextView rules = new TextView(this);
        rules.setTextSize(11f);
        rules.setTextColor(palette.textDim);
        rules.setPadding(dp(12), dp(8), dp(12), dp(8));
        rules.setText(
                "【军衔大小】司令 > 军长 > 师长 > 旅长 > 团长 > 营长 > 连长 > 排长 > 工兵\n"
              + "【特殊规则】\n"
              + "· 炸弹：与任何棋子同归于尽（含地雷）\n"
              + "· 地雷：不能移动；除工兵外，谁碰谁死\n"
              + "· 工兵：最小军衔，但能挖地雷；铁路上可拐弯\n"
              + "· 军旗：不能移动；军旗被吃即输\n"
              + "· 行营：安全区，里面的棋子不能被吃\n"
              + "· 大本营：军旗必须放在大本营；棋子进入后不能移出\n"
              + "· 铁路：棋盘外圈和中线短铁路，棋子可沿铁路直行多格"
        );
        root.addView(rules, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        sync();
        setStatus(modeName() + "　你执红先走。");
        // 布局完成后按真实可用空间算棋盘（首帧、转屏、分屏都走这里；比 post 可靠）
        scroll.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                fitBoardToScreen();
            }
        });
    }

    private LinearLayout.LayoutParams boardParams() {
        int h = boardSideH > 0 ? boardSideH : dp(screen.boardSizeDp(8));
        int w = boardSideW > 0 ? boardSideW
                : Math.max(dp(90), Math.round(h * 4.8f / 8.4f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        return lp;
    }

    /** 按可用空间定棋盘宽高：实体军棋比例 4.8:8.4（约 1:1.75）。 */
    private void fitBoardToScreen() {
        if (scroll == null || boardView == null || topChrome == null || bottomChrome == null) {
            return;
        }
        int availW = scroll.getWidth() - dp(16);
        int availH = scroll.getHeight() - topChrome.getHeight() - bottomChrome.getHeight() - dp(16);
        if (availW <= 0 || availH <= 0) {
            return;
        }
        // 实体棋盘：列距 250、行距 150，外加左右 0.4 列、上下 1.5 行的边距
        float spanX = 4.8f;
        float spanY = 8.4f;
        int h = Math.min(availH, Math.round(availW * spanY / spanX));
        int w = Math.round(h * spanX / spanY);
        // 下限只到「不会把高度顶出可用空间」的程度：极小屏横屏宁可格子小，也不让棋盘藏到滚动区里
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
    public void onConfigurationChanged(android.content.res.Configuration cfg) {
        super.onConfigurationChanged(cfg);
        // onConfigurationChanged 返回后才 requestLayout（AOSP 顺序），量不到新尺寸；
        // 重算由 scroll 的 OnLayoutChangeListener 在布局完成后触发。
        if (UiTheme.dark(this) != darkTheme) {
            rebuildForTheme(UiTheme.dark(this));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            fitBoardToScreen();   // 兜底：个别机型转屏不重跑布局监听时也补一次
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (UiTheme.dark(this) != darkTheme) {
            rebuildForTheme(UiTheme.dark(this));
        }
    }

    /** 主题变了：原地重建控件（不 recreate）——棋局/暗棋 revealed/难度都在字段里，重建不会丢。 */
    private void rebuildForTheme(boolean now) {
        darkTheme = now;
        palette = Palette.of(now);
        setTheme(UiTheme.style(now));
        screen = Screen.read(this);
        buildUi();
        sync();
        refreshStatus();
    }

    private void refreshStatus() {
        if (board == null) {
            return;
        }
        if (board.gameOver()) {
            setStatus("对局已经结束，点「新局」再来一局。");
        } else if (aiThinking) {
            setStatus("电脑思考中…");
        } else {
            setStatus(modeName() + "　你执红先走。");
        }
    }

    private boolean[] hiddenArray() {
        boolean[] h = new boolean[revealed.length];
        for (int i = 0; i < revealed.length; i++) {
            if (flipMode) {
                // 翻棋：没翻开的棋子全部隐藏（不分敌我）
                h[i] = board.ownerAt(JunqiBoard.rowOf(i), JunqiBoard.colOf(i)) != JunqiBoard.EMPTY
                        && !flipped[i];
            } else {
                // 暗棋（标准口径）：只藏「对方」的棋子，己方始终看得见；明棋全可见。
                h[i] = dark
                        && board.ownerAt(JunqiBoard.rowOf(i), JunqiBoard.colOf(i)) == JunqiBoard.BLACK
                        && !revealed[i];
            }
        }
        return h;
    }

    private String levelName() {
        return level == JunqiAI.FAST ? "快" : level == JunqiAI.STRONG ? "强" : "普通";
    }

    private String modeName() {
        return flipMode ? "翻棋模式。" : dark ? "暗棋模式。" : "明棋模式。";
    }

    /** AI 一手真正落地（线程回调与探针共用这一条路径）。 */
    void finishAiMove(int[] mv) {
        JunqiBoard before = board.copy();
        boolean ok = board.play(mv[0], mv[1], mv[2], mv[3]);
        int fromIdx = JunqiBoard.idx(mv[0], mv[1]);
        int toIdx = JunqiBoard.idx(mv[2], mv[3]);
        // 翻棋模式：AI 走子后翻开状态跟着走
        if (flipMode) {
            flipped[toIdx] = flipped[fromIdx];
            flipped[fromIdx] = false;
        }
        JunqiReveal.resolve(revealed, before, new int[]{fromIdx, toIdx});
        sync();
        animateBattleReveal(mv[0], mv[1], mv[2], mv[3]);
        if (ok && board.gameOver()) {
            finishGame();
        } else {
            setStatus("轮到你走。");
        }
    }

    private void sync() {
        if (boardView == null) {
            return;
        }
        boardView.setBoard(board);
        boardView.setHidden(hiddenArray());
        if (selected >= 0) {
            boardView.setSelected(JunqiBoard.rowOf(selected), JunqiBoard.colOf(selected));
        } else {
            boardView.setSelected(-1, -1);
        }
        boardView.setLegalPoints(legal);
        List<int[]> hist = board.history();
        if (hist.isEmpty()) {
            boardView.setLastMove(-1, -1, -1, -1);
        } else {
            int[] m = hist.get(hist.size() - 1);
            boardView.setLastMove(m[0], m[1], m[2], m[3]);
        }
    }

    private void onCellTap(int row, int col) {
        if (aiThinking) {
            toast("电脑正在思考…");
            return;
        }
        if (board.gameOver()) {
            toast("对局已经结束，点「新局」再来一局。");
            return;
        }
        int p = JunqiBoard.idx(row, col);
        int owner = board.ownerAt(row, col);

        // 翻棋模式：点击未翻开的棋子 → 翻开（算一手）
        if (flipMode && owner != JunqiBoard.EMPTY && !flipped[p]) {
            flipPiece(row, col, p);
            return;
        }

        if (selected >= 0 && contains(legal, p)) {
            play(JunqiBoard.rowOf(selected), JunqiBoard.colOf(selected), row, col);
            return;
        }
        if (owner != JunqiBoard.RED) {
            selected = -1;
            legal = new int[0];
            sync();
            return;
        }
        // 翻棋模式下只有翻开的己方棋子才能选中走子
        if (flipMode && !flipped[p]) {
            return;
        }
        selected = p;
        legal = movesFrom(p);
        sync();
    }

    /** 翻棋模式：翻开一个棋子（算一手），然后换电脑走。 */
    private void flipPiece(int row, int col, int p) {
        flipped[p] = true;
        selected = -1;
        legal = new int[0];
        // 播放翻牌动画
        int owner = board.ownerAt(row, col);
        int type = board.at(row, col);
        boardView.revealFlip(row, col, owner, type);
        sync();
        setStatus("翻开了 " + (owner == JunqiBoard.RED ? "红方" : "黑方") + " " + JunqiBoard.typeName(type));
        // 翻牌后换电脑走
        if (!board.gameOver()) {
            startAiTurn();
        }
    }

    private int[] movesFrom(int from) {
        List<int[]> all = board.legalMoves(JunqiBoard.RED);
        int n = 0;
        for (int[] m : all) {
            if (m[0] == from) {
                n++;
            }
        }
        int[] out = new int[n];
        int i = 0;
        for (int[] m : all) {
            if (m[0] == from) {
                out[i++] = m[1];
            }
        }
        return out;
    }

    private static boolean contains(int[] a, int v) {
        for (int x : a) {
            if (x == v) {
                return true;
            }
        }
        return false;
    }

    private void play(int fr, int fc, int tr, int tc) {
        int[] mv = new int[]{JunqiBoard.idx(fr, fc), JunqiBoard.idx(tr, tc)};
        // 「谁被看到」统一走 JunqiReveal.resolve：它拿**走之前**的棋盘判断目标格原来有没有子。
        JunqiBoard before = board.copy();
        if (!board.play(fr, fc, tr, tc)) {
            toast("这一手不能走。");
            return;
        }
        // 翻棋模式：棋子移走后，翻开状态跟着走
        if (flipMode) {
            flipped[mv[1]] = flipped[mv[0]];
            flipped[mv[0]] = false;
        }
        JunqiReveal.resolve(revealed, before, mv);
        selected = -1;
        legal = new int[0];
        sync();
        animateBattleReveal(fr, fc, tr, tc);
        if (board.gameOver()) {
            finishGame();
        } else {
            startAiTurn();
        }
    }

    private void startAiTurn() {
        aiThinking = true;
        boardView.setInputEnabled(false);
        setStatus("电脑思考中…");

        if (flipMode) {
            // 翻棋模式下 AI 的回合：50% 概率翻一个未翻的子，50% 概率走一步已翻的子
            // 如果没有未翻的子就只走，没有可走的已翻子就只翻
            final boolean[] flipSnap = flipped.clone();
            final JunqiBoard snapshot = board.copy();
            final int count = board.history().size();
            Thread t = new Thread(new Runnable() {
                public void run() {
                    // 收集未翻开的子
                    java.util.List<Integer> unflipped = new java.util.ArrayList<Integer>();
                    for (int i = 0; i < flipSnap.length; i++) {
                        if (!flipSnap[i] && snapshot.ownerAt(JunqiBoard.rowOf(i), JunqiBoard.colOf(i)) != JunqiBoard.EMPTY) {
                            unflipped.add(i);
                        }
                    }
                    // 收集可走的已翻子（黑方）
                    java.util.List<int[]> moves = snapshot.legalMoves(JunqiBoard.BLACK);
                    java.util.List<int[]> flippedMoves = new java.util.ArrayList<int[]>();
                    for (int[] m : moves) {
                        if (flipSnap[m[0]]) {
                            flippedMoves.add(m);
                        }
                    }

                    final boolean doFlip;
                    if (unflipped.isEmpty()) {
                        doFlip = false;
                    } else if (flippedMoves.isEmpty()) {
                        doFlip = true;
                    } else {
                        doFlip = new Random().nextInt(3) == 0;  // 1/3 概率翻，2/3 概率走
                    }

                    if (doFlip) {
                        final int pick = unflipped.get(new Random().nextInt(unflipped.size()));
                        runOnUiThread(new Runnable() {
                            public void run() {
                                aiThinking = false;
                                if (!alive || board == null || board.gameOver()) return;
                                boardView.setInputEnabled(true);
                                int r = JunqiBoard.rowOf(pick), c = JunqiBoard.colOf(pick);
                                flipped[pick] = true;
                                int owner = board.ownerAt(r, c);
                                int type = board.at(r, c);
                                boardView.revealFlip(r, c, owner, type);
                                sync();
                                setStatus("电脑翻开了 " + (owner == JunqiBoard.RED ? "红方" : "黑方") + " " + JunqiBoard.typeName(type) + "。轮到你走。");
                            }
                        });
                    } else if (!flippedMoves.isEmpty()) {
                        final int[] mv = flippedMoves.get(new Random().nextInt(flippedMoves.size()));
                        runOnUiThread(new Runnable() {
                            public void run() {
                                aiThinking = false;
                                if (!alive || board == null || board.gameOver() || board.history().size() != count) return;
                                boardView.setInputEnabled(true);
                                int fr = mv[0] / JunqiBoard.COLS, fc = mv[0] % JunqiBoard.COLS;
                                int tr = mv[1] / JunqiBoard.COLS, tc = mv[1] % JunqiBoard.COLS;
                                // 吃子后被吃/吃掉的子也标记为翻开
                                flipped[mv[1]] = true;
                                finishAiMove(new int[]{fr, fc, tr, tc});
                            }
                        });
                    } else {
                        runOnUiThread(new Runnable() {
                            public void run() {
                                aiThinking = false;
                                if (!alive) return;
                                boardView.setInputEnabled(true);
                                setStatus("电脑无子可走。");
                                finishGame();
                            }
                        });
                    }
                }
            }, "junqi-ai-flip");
            t.setDaemon(true);
            t.start();
            return;
        }

        final JunqiBoard snapshot = board.copy();
        final boolean[] rev = revealed.clone();
        final int count = board.history().size();
        Thread t = new Thread(new Runnable() {
            public void run() {
                final int[] mv = ai.genmove(snapshot, JunqiBoard.BLACK, rev, learnKeys);
                runOnUiThread(new Runnable() {
                    public void run() {
                        aiThinking = false;
                        if (!alive || board == null || board.gameOver() || board.history().size() != count) {
                            return;
                        }
                        boardView.setInputEnabled(true);
                        if (mv == null) {
                            setStatus("电脑无子可走。");
                            finishGame();
                            return;
                        }
                        finishAiMove(mv);
                    }
                });
            }
        }, "junqi-ai");
        t.setDaemon(true);
        t.start();
    }

    private void resign() {
        if (board.gameOver()) {
            return;
        }
        resigned = true;
        // 人认输 = 电脑赢（学习库从电脑视角记 WIN，别记反）
        recordLearning(JunqiLearn.WIN);
        showEndDialog("你认输了。\n" + board.endReason() + "\n" + learnInfo());
    }

    private void finishGame() {
        String text;
        int result = JunqiLearn.DRAW;
        if (resigned) {
            text = "你认输了。";
            result = JunqiLearn.WIN;
        } else if (board.winner() == JunqiBoard.RED) {
            text = "你赢了！";
            result = JunqiLearn.LOSS;
        } else if (board.winner() == JunqiBoard.BLACK) {
            text = "电脑赢了。";
            result = JunqiLearn.WIN;
        } else {
            text = "和棋。";
        }
        recordLearning(result);
        showEndDialog(text + "\n" + board.endReason() + "\n" + learnInfo());
    }

    /** 学完这一盘后给用户看一眼（所有档位共用同一个学习库）。 */
    private String learnInfo() {
        return learn == null ? "" : "（" + learn.describe() + "）";
    }

    /** 一盘只记一次；记完立刻落盘。 */
    private void recordLearning(int result) {
        if (learnRecorded || learn == null) {
            return;
        }
        learnRecorded = true;
        java.util.List<String> copy;
        synchronized (learnKeys) {
            copy = new java.util.ArrayList<String>(learnKeys);
        }
        learn.recordGame(copy, result);
        learn.save(new File(getFilesDir(), JunqiLearn.FILE_NAME));
    }

    private void showEndDialog(String text) {
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle("对局结束")
                .setMessage(text)
                .setPositiveButton("再来一局", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        newGame();
                        sync();
                        setStatus("新开一局。你执红先走。");
                    }
                })
                .setNegativeButton("返回", new android.content.DialogInterface.OnClickListener() {
                    public void onClick(android.content.DialogInterface d, int w) {
                        d.dismiss();
                    }
                })
                .setCancelable(true);
        boolean humanWon = !resigned && board != null && board.winner() == JunqiBoard.RED;
        if (humanWon && WinEgg.eggAllowed(level, JunqiAI.FAST)) {
            b.setView(WinEgg.buildBody(this));
        }
        b.show();
    }

    private LinearLayout buttonRow(String[] names, View.OnClickListener[] clicks) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            Button b = new Button(this);
            b.setText(names[i]);
            b.setAllCaps(false);
            b.setTextSize(screen.buttonTextSp());
            b.setTextColor(palette.buttonText);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(palette.buttonBg));
            if (i < clicks.length) {
                b.setOnClickListener(clicks[i]);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(2), dp(6), dp(2), dp(6));
            row.addView(b, lp);
        }
        return row;
    }

    private void setStatus(String s) {
        if (status != null) {
            status.setText(s);
        }
    }

    /** 上一手若发生交战，把阵亡的棋子「背面→正面」翻过来给人看。 */
    private void animateBattleReveal(int fr, int fc, int tr, int tc) {
        if (board.lastDefType() == JunqiBoard.EMPTY) {
            return;   // 只是移动，不翻
        }
        int atkOwner = board.lastAttacker();
        int defOwner = JunqiBoard.opponent(atkOwner);
        int res = board.lastResult();
        if (res == atkOwner) {
            boardView.revealFlip(tr, tc, defOwner, board.lastDefType());   // 对方被吃
        } else if (res == defOwner) {
            boardView.revealFlip(fr, fc, atkOwner, board.lastAtkType());   // 自己被吃
        } else {
            boardView.revealFlip(tr, tc, defOwner, board.lastDefType());   // 同归于尽/炸弹：两个都翻
            boardView.revealFlip(fr, fc, atkOwner, board.lastAtkType());
        }
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        alive = false;
        super.onDestroy();
    }
}
