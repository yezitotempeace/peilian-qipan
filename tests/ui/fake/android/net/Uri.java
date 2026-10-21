/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.net;
public class Uri {
    public final String s;
    private Uri(String s) { this.s = s; }
    public static Uri parse(String s) { return new Uri(s); }
    public String toString() { return s; }
}
