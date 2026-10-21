/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.os;
/** 记录所有被我伪装的 Android API 调用，便于断言「谁被调了几次」。 */
public final class FakeLog {
    public static final java.util.List<String> EVENTS = new java.util.ArrayList<String>();
    public static void log(String s) { EVENTS.add(s); }
    public static int countPrefix(String p) { int n = 0; for (String e : EVENTS) if (e.startsWith(p)) n++; return n; }
    public static String findPrefix(String p) { for (String e : EVENTS) if (e.startsWith(p)) return e; return null; }
    public static void clear() { EVENTS.clear(); }
    private FakeLog() {}
}
