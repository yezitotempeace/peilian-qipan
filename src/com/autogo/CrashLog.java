/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

/**
 * 崩溃自记录。
 *
 * 为什么要这个：App 在手机上崩了，开发者这边拿不到 logcat（容器里的 logcat 缓冲区
 * 根本读不到这个应用的日志），用户也没法把日志导出来 —— 一轮排查就白跑。
 *
 * 这里把未捕获异常的栈写进 App 私有目录，下次启动时由界面弹出来，
 * 用户截个图或点「复制」就能反馈。
 */
public final class CrashLog {

    private static final String FILE = "last_crash.txt";
    private static final int MAX_CHARS = 8000;

    private CrashLog() {
    }

    /** 装一个全局未捕获异常处理器。多次调用无害。 */
    public static void install(final Context ctx) {
        final Context app = ctx.getApplicationContext();
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            try {
                write(app, t, e);
            } catch (Throwable ignored) {
                // 记录本身失败时不能再抛，否则会掩盖原始异常
            }
            // 交给原处理器，保持系统默认的崩溃行为（进程退出、系统提示）
            if (prev != null) {
                prev.uncaughtException(t, e);
            }
        });
    }

    private static void write(Context ctx, Thread t, Throwable e) throws IOException {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        pw.println("时间: " + new java.util.Date());
        pw.println("线程: " + t.getName());
        e.printStackTrace(pw);
        pw.flush();
        String text = sw.toString();
        if (text.length() > MAX_CHARS) {
            text = text.substring(0, MAX_CHARS) + "\n...(已截断)";
        }
        File f = new File(ctx.getFilesDir(), FILE);
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(text.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** 读取并清空上次的崩溃记录；没有则返回 null。 */
    public static String readAndClear(Context ctx) {
        File f = new File(ctx.getFilesDir(), FILE);
        if (!f.exists() || f.length() <= 0) {
            return null;
        }
        try {
            byte[] buf = new byte[(int) Math.min(f.length(), 1 << 20)];
            try (FileInputStream in = new FileInputStream(f)) {
                int n = in.read(buf);
                if (n <= 0) return null;
                return new String(buf, 0, n, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            return null;
        } finally {
            if (!f.delete()) {
                // 删不掉也无所谓，下次启动会再弹一次
            }
        }
    }
}
