package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
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
        board = new JunqiBoard();
        board.setupStandard(new Random(SEED));
        startBoard = board.copy();
        // 明棋：整盘都看得见（连空格也算「已暴露」，避免界面把空格画成背面）；
        // 暗棋：一开始全部不可见，交战过的格子才露出来（JunqiReveal.resolve）。
        // 初始「已暴露」表交给 JunqiReveal 生成：页面里不出现任何 revealed[...] 的赋值
        JunqiReveal.reset(revealed, dark);
        selected = -1;
        legal = new int[0];
        aiThinking = false;
        resigned = false;
        ai.setLevel(level);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(palette.background);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(palette.background);
        int pad = dp(8);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("军　棋");
        title.setTextSize(20f);
        title.setTextColor(palette.text);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        status = new TextView(this);
        status.setTextColor(palette.textDim);
        status.setTextSize(13f);
        status.setGravity(Gravity.CENTER);
        root.addView(status);

        boardView = new JunqiView(this);
        boardView.setBoard(board);
        boardView.setHidden(hiddenArray());
        boardView.setOnCellTap(new JunqiView.OnCellTap() {
            public void onTap(int row, int col) {
                onCellTap(row, col);
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(screen.boardSizeDp(8)));
        root.addView(boardView, bp);

        root.addView(buttonRow(new String[]{"新局", "认输", "难度"},
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
        root.addView(buttonRow(new String[]{"暗棋 / 明棋", "返回"},
                new View.OnClickListener[]{
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                dark = !dark;
                                newGame();
                                sync();
                                setStatus(dark ? "已换成暗棋（双方看不清对方兵种）。" : "已换成明棋（双方都看得到兵种）。");
                            }
                        },
                        new View.OnClickListener() {
                            public void onClick(View v) {
                                finish();
                            }
                        }}));

        scroll.addView(root);
        Watermark.wrap(this, scroll, palette, 6);
        sync();
        setStatus(dark ? "你执红先走。当前是暗棋。" : "你执红先走。当前是明棋。");
    }

    private boolean[] hiddenArray() {
        boolean[] h = new boolean[revealed.length];
        for (int i = 0; i < revealed.length; i++) {
            h[i] = !revealed[i];
        }
        return h;
    }

    private String levelName() {
        return level == JunqiAI.FAST ? "快" : level == JunqiAI.STRONG ? "强" : "普通";
    }

    /** AI 一手真正落地（线程回调与探针共用这一条路径）。 */
    void finishAiMove(int[] mv) {
        JunqiBoard before = board.copy();
        boolean ok = board.play(mv[0], mv[1], mv[2], mv[3]);
        JunqiReveal.resolve(revealed, before,
                new int[]{JunqiBoard.idx(mv[0], mv[1]), JunqiBoard.idx(mv[2], mv[3])});
        sync();
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
        selected = p;
        legal = movesFrom(p);
        sync();
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
        JunqiReveal.resolve(revealed, before, mv);
        selected = -1;
        legal = new int[0];
        sync();
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
        final JunqiBoard snapshot = board.copy();
        final boolean[] rev = revealed.clone();
        final int count = board.history().size();
        Thread t = new Thread(new Runnable() {
            public void run() {
                final int[] mv = ai.genmove(snapshot, JunqiBoard.BLACK, rev);
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
        showEndDialog("你认输了。\n" + board.endReason());
    }

    private void finishGame() {
        String text;
        if (resigned) {
            text = "你认输了。";
        } else if (board.winner() == JunqiBoard.RED) {
            text = "你赢了！";
        } else if (board.winner() == JunqiBoard.BLACK) {
            text = "电脑赢了。";
        } else {
            text = "和棋。";
        }
        showEndDialog(text + "\n" + board.endReason());
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
