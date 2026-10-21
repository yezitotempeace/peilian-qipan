/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.widget.Toast;

/**
 * 赢棋彩蛋：赢了之后把 707380438 显示出来，点它就复制到剪贴板。
 * 围棋、五子棋、以及以后新加的棋种都走这一处，保证口径一致。
 */
public final class WinEgg {

    /** 彩蛋数字（用户指定，可点即复制）。 */
    public static final String CODE = "707380438";

    private static final String TIP = "点下面这串数字，就能复制带走。";


    /** 围棋结果串（B+R / W+3.5 / 黑胜…）算不算「人赢了」。 */
    public static boolean goHumanWon(String result, int humanColor) {
        if (result == null || result.isEmpty()) {
            return false;
        }
        boolean blackWon = result.startsWith("B") || result.startsWith("黑");
        boolean whiteWon = result.startsWith("W") || result.startsWith("白");
        if (!blackWon && !whiteWon) {
            return false;
        }
        return blackWon == (humanColor == Board.BLACK);
    }

    /** 彩蛋只给「普通 / 强」两档（快档不显示）；各棋种用各自的 FAST 常量传进来。 */
    public static boolean eggAllowed(int level, int fastLevel) {
        return level != fastLevel;
    }

    /** 围棋的三个网络档位映射成 0=快 / 1=普通 / 2=强；未知按普通算。 */
    public static int goLevel(String modelAlias) {
        if ("fast".equals(modelAlias)) {
            return 0;
        }
        if ("strong".equals(modelAlias)) {
            return 2;
        }
        return 1;
    }

    /** 围棋档位的中文名（状态条 / 彩蛋判定提示用）。 */
    public static String goLevelName(String modelAlias) {
        int lv = goLevel(modelAlias);
        return lv == 0 ? "快" : (lv == 1 ? "普通" : "强");
    }

    /** 人认输时围棋结果串：赢家永远是人的对手（与「轮到谁」无关）。 */
    public static String resignResult(int humanColor) {
        return humanColor == Board.BLACK ? "W+R" : "B+R";
    }

    /** 把彩蛋数字复制到剪贴板，并给一句提示。 */
    public static boolean copy(Activity a) {
        if (a == null) {
            return false;
        }
        try {
            ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) {
                return false;
            }
            cm.setPrimaryClip(ClipData.newPlainText("号码", CODE));
            Toast.makeText(a, "已复制：" + CODE, Toast.LENGTH_SHORT).show();
            return true;
        } catch (RuntimeException e) {
            Toast.makeText(a, "复制失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * 弹窗主体：提示 + 一个又大又能点的「707380438」。
     * 用自定义视图而不是按钮，是为了在弹窗里同时保住「再来一局 / 复盘 / 返回」三个按钮。
     */
    public static android.view.View buildBody(Activity a) {
        android.widget.LinearLayout box = new android.widget.LinearLayout(a);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int pad = Math.round(16 * a.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad, pad, pad);
        android.widget.TextView tip = new android.widget.TextView(a);
        tip.setText(TIP);
        box.addView(tip);
        android.widget.TextView code = new android.widget.TextView(a);
        code.setText(CODE);
        code.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 26f);
        // 彩蛋弹出：先小、透明，再放大淡入（只在真的显示彩蛋时走这里）
        code.setAlpha(0f);
        code.setScaleX(0.6f);
        code.setScaleY(0.6f);
        code.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(AnimMath.EGG_MS).start();
        code.setGravity(android.view.Gravity.CENTER);
        code.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                copy(a);
            }
        });
        android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, pad / 2, 0, 0);
        box.addView(code, lp);
        return box;
    }

    /** 赢棋后的弹窗：中间一个可点的「707380438」。 */
    public static void showWin(Activity a) {
        if (a == null || a.isFinishing()) {
            return;
        }
        new AlertDialog.Builder(a)
                .setTitle("你赢了！")
                .setView(buildBody(a))
                .setPositiveButton("知道了", null)
                .setCancelable(true)
                .show();
    }

    private WinEgg() {
    }
}
