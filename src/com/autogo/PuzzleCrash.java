/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * 残局的兜底报告：任何一处出错都不闪退，而是把完整堆栈
 * 写到应用外部目录（puzzle-crash.log）并弹窗显示，方便定位。
 */
public final class PuzzleCrash {

    private PuzzleCrash() {
    }

    public static void report(Activity a, String where, Throwable t) {
        StringBuilder sb = new StringBuilder();
        sb.append("位置：").append(where).append('\n');
        sb.append(t.getClass().getName()).append(": ").append(t.getMessage()).append('\n');
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(14, st.length); i++) {
            sb.append("  at ").append(st[i]).append('\n');
        }
        Throwable cause = t.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(cause).append('\n');
        }
        String text = sb.toString();

        // 落到外部目录（无需权限），方便拿日志
        try {
            File dir = a.getExternalFilesDir(null);
            if (dir != null) {
                FileOutputStream out = new FileOutputStream(new File(dir, "puzzle-crash.log"), true);
                out.write((text + "\n----\n").getBytes(StandardCharsets.UTF_8));
                out.close();
            }
        } catch (Throwable ignore) {
        }

        // 弹窗让人能直接念出错误的第一行
        try {
            ScrollView sv = new ScrollView(a);
            TextView tv = new TextView(a);
            tv.setText(text);
            tv.setPadding(24, 24, 24, 24);
            tv.setTextSize(13f);
            sv.addView(tv);
            new AlertDialog.Builder(a)
                    .setTitle("残局出错（请把这段发我）")
                    .setView(sv)
                    .setPositiveButton("知道了", null)
                    .show();
        } catch (Throwable ignore) {
        }
    }
}
