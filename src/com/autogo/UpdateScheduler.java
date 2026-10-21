/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/** 一次性排下一次更新检查；检查完 receiver 再排下一次，避免进程重启后永久丢闹钟。 */
public final class UpdateScheduler {

    public static final long INTERVAL_MS = 2 * 3600 * 1000L;
    private static final int REQUEST = 1;

    private UpdateScheduler() {
    }

    /** 下一次的墙钟时间：距上次检查满 2 小时，至少 60 秒后。public 便于单测。 */
    public static long nextTrigger(long last, long now) {
        return Math.max(now + 60_000L, last + INTERVAL_MS);
    }

    public static void schedule(Context context) {
        try {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) {
                return;
            }
            long now = System.currentTimeMillis();
            long last = context.getSharedPreferences("autogo", Context.MODE_PRIVATE)
                    .getLong("lastUpdateCheck", 0L);
            long next = nextTrigger(last, now);
            Intent intent = new Intent("com.autogo.UPDATE_CHECK");
            intent.setPackage(context.getPackageName());
            PendingIntent pi = PendingIntent.getBroadcast(context, REQUEST, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            am.set(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + Math.max(60_000L, next - now), pi);
        } catch (Exception ignored) {
        }
    }
}
