/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content.pm;

/** 假 SigningInfo：只实现 getApkContentsSigners()。 */
public class SigningInfo {
    private final Signature[] signers;

    public SigningInfo(Signature[] signers) {
        this.signers = signers == null ? new Signature[0] : signers;
    }

    public Signature[] getApkContentsSigners() {
        return signers;
    }
}
