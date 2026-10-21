/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content.pm;
public class PackageManager {
    public static class NameNotFoundException extends Exception {}
    public static String VERSION_NAME = "1.20261015.86";
    public static int VERSION_CODE = 33;
    public static boolean THROW = false;
    public PackageInstaller getPackageInstaller() { return new PackageInstaller(); }
    public boolean canRequestPackageInstalls() { return true; }
    public PackageInfo getPackageInfo(String pkg, int flags) {
        if (THROW) throw new RuntimeException("fake PackageManager 故障");
        PackageInfo pi = new PackageInfo();
        pi.versionName = VERSION_NAME;
        pi.versionCode = VERSION_CODE;
        return pi;
    }
}
