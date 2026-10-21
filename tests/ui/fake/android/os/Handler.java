/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.os;
public class Handler {
    public static final java.util.List<Runnable> POSTED = new java.util.ArrayList<Runnable>();
    /** 记下 postDelayed 的延时值。 */
    public static final java.util.List<Long> DELAYS = new java.util.ArrayList<Long>();
    public static int removed;
    public Handler(Looper l) {}
    public boolean post(Runnable r) { POSTED.add(r); r.run(); return true; }
    public boolean postDelayed(Runnable r, long d) { FakeLog.log("postDelayed:" + d); DELAYS.add(Long.valueOf(d)); POSTED.add(r); return true; }
    public void removeCallbacks(Runnable r) { removed++; FakeLog.log("removeCallbacks"); }
}
