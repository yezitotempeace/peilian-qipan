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
 * 残局的兜底报告：任何一处出错（包括后台线程的未捕获异常）都不闪退，
 * 而是把完整堆栈写到应用外部目录 puzzle-crash.log 并弹窗显示，方便定位。
 */
public final class PuzzleCrash {

    private static Thread.UncaughtExceptionHandler prevHandler;
    private static Thread.UncaughtExceptionHandler installed;

    private PuzzleCrash() {
    }

    /** 在残局页存活期间接管未捕获异常：先记录/弹窗，再交给系统收尾。 */
    public static void install(final Activity a) {
        prevHandler = Thread.getDefaultUncaughtExceptionHandler();
        installed = new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
                report(a, "线程 " + t.getName(), e);
                if (prevHandler != null) {
                    prevHandler.uncaughtException(t, e);
                } else {
                    android.os.Process.killProcess(android.os.Process.myPid());
                }
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(installed);
    }

    /** 离开残局页时还原系统默认处理。 */
    public static void uninstall(Activity a) {
        if (installed != null && Thread.getDefaultUncaughtExceptionHandler() == installed) {
            Thread.setDefaultUncaughtExceptionHandler(prevHandler);
        }
        installed = null;
        prevHandler = null;
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

        try {
            File dir = a.getExternalFilesDir(null);
            if (dir != null) {
                FileOutputStream out = new FileOutputStream(new File(dir, "puzzle-crash.log"), true);
                out.write((text + "\n----\n").getBytes(StandardCharsets.UTF_8));
                out.close();
            }
        } catch (Throwable ignore) {
        }

        try {
            a.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        ScrollView sv = new ScrollView(a);
                        TextView tv = new TextView(a);
                        tv.setText(text);
                        tv.setPadding(24, 24, 24, 24);
                        tv.setTextSize(13f);
                        sv.addView(tv);
                        new AlertDialog.Builder(a)
                                .setTitle("残局出错（请把第一行发我）")
                                .setView(sv)
                                .setPositiveButton("知道了", null)
                                .show();
                    } catch (Throwable ignore) {
                    }
                }
            });
        } catch (Throwable ignore) {
        }
    }
}
