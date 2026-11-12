/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content.pm;

/** 假签名：只保存字节，够 UpdateManager 算 SHA-256。 */
public class Signature {
    private final byte[] bytes;

    public Signature(byte[] bytes) {
        this.bytes = bytes == null ? new byte[0] : bytes;
    }

    public byte[] toByteArray() {
        return bytes;
    }
}
