/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content;
public interface DialogInterface {
    void dismiss();
    interface OnClickListener { void onClick(DialogInterface d, int which); }
}
