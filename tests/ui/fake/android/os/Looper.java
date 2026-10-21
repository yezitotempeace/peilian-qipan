/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.os;
public class Looper {
    private static final Looper MAIN = new Looper();
    public static Looper getMainLooper() { return MAIN; }
}
