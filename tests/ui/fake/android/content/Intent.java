/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content;
public class Intent {
    public static final String ACTION_VIEW = "android.intent.action.VIEW";
    public final Class<?> target;
    public final java.util.Map<String, Object> extras = new java.util.HashMap<String, Object>();
    public Intent(Context ctx, Class<?> t) { target = t; android.os.FakeLog.log("new Intent:" + t.getName()); }
    private Intent put(String k, Object v) { extras.put(k, v); android.os.FakeLog.log("putExtra:" + k); return this; }
    public Intent putExtra(String k, int v) { return put(k, Integer.valueOf(v)); }
    public Intent putExtra(String k, boolean v) { return put(k, Boolean.valueOf(v)); }
    public Intent putExtra(String k, String v) { return put(k, v); }
    public Intent putExtra(String k, int[] v) { return put(k, v); }
    public Intent putExtra(String k, double[] v) { return put(k, v); }
    public Intent putExtra(String k, String[] v) { return put(k, v); }
    public static Intent last;
    public Intent() { target = null; }
    public Intent(String action) { target = null; this.action = action; }
    public Intent(String action, android.net.Uri uri) { target = null; this.action = action; }
    public String action;
    public Intent setPackage(String p) { return this; }
    public int getIntExtra(String k, int def) { Object v = extras.get(k); return v instanceof Integer ? (Integer) v : def; }
    public boolean getBooleanExtra(String k, boolean def) { Object v = extras.get(k); return v instanceof Boolean ? (Boolean) v : def; }
    public String getStringExtra(String k) { Object v = extras.get(k); return v instanceof String ? (String) v : null; }
    public int[] getIntArrayExtra(String k) { Object v = extras.get(k); return v instanceof int[] ? (int[]) v : null; }
    public double[] getDoubleArrayExtra(String k) { Object v = extras.get(k); return v instanceof double[] ? (double[]) v : null; }
    public String[] getStringArrayExtra(String k) { Object v = extras.get(k); return v instanceof String[] ? (String[]) v : null; }
}
