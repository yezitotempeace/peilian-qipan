/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.app;
public class Activity extends android.content.Context {
    public static final java.util.List<Runnable> UI_QUEUE = new java.util.ArrayList<Runnable>();
    public static android.content.Intent lastStarted;
    public android.content.Intent intent = new android.content.Intent();
    public android.content.Intent getIntent() { return intent; }
    public int setThemeCount, setContentViewCount, recreateCount, lastTheme, onCreateCount;
    public boolean finishing, destroyed;
    public android.view.View content;
    protected void onCreate(android.os.Bundle b) { onCreateCount++; }
    protected void onResume() {}
    protected void onPause() {}
    protected void onDestroy() {}
    public void onBackPressed() {}
    public void onWindowFocusChanged(boolean hasFocus) { android.os.FakeLog.log("onWindowFocusChanged:" + hasFocus); }

    public void onConfigurationChanged(android.content.res.Configuration c) {}
    public void setTheme(int id) { setThemeCount++; lastTheme = id; }
    public void setContentView(android.view.View v) { setContentViewCount++; content = v; }
    public void recreate() { recreateCount++; }
    public void startActivity(android.content.Intent i) { lastStarted = i; android.os.FakeLog.log("startActivity:" + i.target.getName()); }
    public void finish() { finishing = true; android.os.FakeLog.log("finish"); }
    public boolean isFinishing() { return finishing; }
    public boolean isDestroyed() { return destroyed; }
    public void runOnUiThread(Runnable r) { UI_QUEUE.add(r); android.os.FakeLog.log("runOnUiThread"); }
    public static int drainUi() { int n = 0; while (!UI_QUEUE.isEmpty()) { UI_QUEUE.remove(0).run(); n++; } return n; }
}
