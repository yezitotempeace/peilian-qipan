/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content.res;
public class Configuration {
    public static final int UI_MODE_NIGHT_MASK = 0x30;
    public static final int UI_MODE_NIGHT_NO = 0x10;
    public static final int UI_MODE_NIGHT_YES = 0x20;
    public static final int ORIENTATION_PORTRAIT = 1;
    public static final int ORIENTATION_LANDSCAPE = 2;
    public int uiMode;
    public int screenWidthDp;
    public int screenHeightDp;
    public int smallestScreenWidthDp;
    public int orientation = ORIENTATION_PORTRAIT;
    public int densityDpi = 160;
    public float fontScale = 1f;
    public Configuration() {}
}
