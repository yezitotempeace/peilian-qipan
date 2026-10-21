/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.app;
public class PendingIntent {
    public static final int FLAG_UPDATE_CURRENT = 0x08000000;
    public static final int FLAG_IMMUTABLE = 0x04000000;
    public static final int FLAG_MUTABLE = 0x02000000;
    public static PendingIntent getBroadcast(android.content.Context c, int r, android.content.Intent i, int f) { return new PendingIntent(); }
    public android.content.IntentSender getIntentSender() { return new android.content.IntentSender(); }
}
