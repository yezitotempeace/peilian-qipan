/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content;
public class SharedPreferences {
    public final java.util.HashMap<String, Integer> ints = new java.util.HashMap<String, Integer>();
    public final java.util.HashMap<String, String> strings = new java.util.HashMap<String, String>();
    public final java.util.HashMap<String, Boolean> bools = new java.util.HashMap<String, Boolean>();
    public final java.util.HashMap<String, Long> longs = new java.util.HashMap<String, Long>();
    public int getInt(String k, int def) { Integer v = ints.get(k); return v == null ? def : v.intValue(); }
    public long getLong(String k, long def) { Long v = longs.get(k); return v == null ? def : v.longValue(); }
    public String getString(String k, String def) { String v = strings.get(k); return v == null ? def : v; }
    public boolean getBoolean(String k, boolean def) { Boolean v = bools.get(k); return v == null ? def : v.booleanValue(); }

    public Editor edit() { return new Editor(this); }

    public static class Editor {
        private final SharedPreferences p;
        Editor(SharedPreferences p) { this.p = p; }
        public Editor remove(String k) { p.strings.remove(k); p.ints.remove(k); p.longs.remove(k); p.bools.remove(k); return this; }
        public Editor putLong(String k, long v) { p.longs.put(k, Long.valueOf(v)); return this; }
        public Editor putInt(String k, int v) { p.ints.put(k, Integer.valueOf(v)); return this; }
        public Editor putString(String k, String v) { p.strings.put(k, v); return this; }
        public Editor putBoolean(String k, boolean v) { p.bools.put(k, Boolean.valueOf(v)); return this; }
        public void apply() {}
        public boolean commit() { return true; }
    }
}
