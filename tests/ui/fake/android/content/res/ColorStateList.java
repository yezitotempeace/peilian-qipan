/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content.res;
public class ColorStateList {
    public final int color;
    private ColorStateList(int c) { color = c; }
    public static ColorStateList valueOf(int c) { return new ColorStateList(c); }
}
