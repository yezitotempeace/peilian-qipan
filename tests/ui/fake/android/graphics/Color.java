/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.graphics;
public class Color {
    public static final int BLACK = 0xFF000000, WHITE = 0xFFFFFFFF, TRANSPARENT = 0, RED = 0xFFFF0000;
    public static int rgb(int r, int g, int b) { return 0xFF000000 | (r << 16) | (g << 8) | b; }
    public static int argb(int a, int r, int g, int b) { return (a << 24) | (r << 16) | (g << 8) | b; }
}
