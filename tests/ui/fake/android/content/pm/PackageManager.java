/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content.pm;
public class PackageManager {
    public static final int GET_SIGNATURES = 0x00000040;
    public static final int GET_SIGNING_CERTIFICATES = 0x08000000;

    public static class NameNotFoundException extends Exception {}

    /** 桩测用：getPackageArchiveInfo 返回什么（null = 读不到安装包）。 */
    public static PackageInfo ARCHIVE_INFO = null;
    public static String VERSION_NAME = "1.20261015.86";
    public static int VERSION_CODE = 33;
    public static boolean THROW = false;
    public PackageInstaller getPackageInstaller() { return new PackageInstaller(); }
    public boolean canRequestPackageInstalls() { return true; }
    public PackageInfo getPackageArchiveInfo(String path, int flags) {
        return ARCHIVE_INFO;
    }

    public PackageInfo getPackageInfo(String pkg, int flags) {
        if (THROW) throw new RuntimeException("fake PackageManager 故障");
        PackageInfo pi = new PackageInfo();
        pi.versionName = VERSION_NAME;
        pi.versionCode = VERSION_CODE;
        return pi;
    }
}
