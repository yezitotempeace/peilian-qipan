/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content;

/** 桩测用的假 ClipData。 */
public class ClipData {
    public String text;

    public static ClipData newPlainText(String label, String text) {
        ClipData c = new ClipData();
        c.text = text;
        return c;
    }
}
