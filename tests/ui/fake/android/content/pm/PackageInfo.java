/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content.pm;
public class PackageInfo {
    public String versionName;
    public int versionCode;
    /** API 28+ 的签名信息（UpdateManager 验签用）。 */
    public SigningInfo signingInfo;
    /** API 26/27 的旧签名数组。 */
    public Signature[] signatures;
}
