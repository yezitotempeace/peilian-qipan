/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.widget;
public class TextView extends android.view.View {
    private CharSequence text = "";
    public TextView(android.content.Context c) {}
    public void setText(CharSequence t) { text = t; android.os.FakeLog.log("setText:" + t); }
    public CharSequence getText() { return text; }
    public void setTextSize(int unit, float size) { lastTextSize = size; android.os.FakeLog.log("setTextSize:" + size); }
    public float lastTextSize = -1f;
    public void setTextSize(float size) { lastTextSize = size; android.os.FakeLog.log("setTextSize:" + size); }
    public int lastTextColor = 0;
    public void setTextColor(int c) { lastTextColor = c; android.os.FakeLog.log("setTextColor:" + Integer.toHexString(c)); }
    public void setGravity(int g) {}
}
