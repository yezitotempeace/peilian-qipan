/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 外部引擎可执行文件的准备工具。
 *
 * <p><b>为什么不直接用 nativeLibraryDir</b>：把可执行文件打成 {@code lib/arm64-v8a/xxx.so}
 * 只会让文件带上可执行位，但 Android 10+ 对 {@code apk_data_file} 这类 SELinux 标签
 * 有 W^X / exec 限制（不同 ROM 策略不一致），真机上 {@code ProcessBuilder} 可能直接
 * 报 {@code EACCES / Permission denied}。{@code File.canExecute()} 只看权限位，
 * 测不出这层限制——所以「检查通过、运行时失败」正是最容易踩的坑。
 *
 * <p><b>可靠做法</b>：把二进制放进 {@code assets/}，首次运行时拷到 {@code filesDir}
 * （SELinux 标签 {@code app_data_file}，属 app 自己，允许 exec），再 {@code chmod 700}。
 * 这是 Android 上跑外部可执行程序的标准套路。
 *
 * <p>同时保留 nativeLibraryDir 的兼容路径：如果 {@code assets} 里没有（老安装包），
 * 仍尝试用 nativeLibraryDir 里那个文件。
 */
public final class NativeExe {

    /** 准备好一个可执行文件：优先从 assets 释放到 filesDir；失败则退回 nativeLibraryDir。 */
    public static File prepare(Context ctx, String assetName, String exeName) {
        if (ctx == null) {
            return null;
        }
        // ① assets 里有没有这个二进制（新安装包才有）
        File dst = new File(ctx.getFilesDir(), exeName);
        boolean hasAsset = hasAsset(ctx, assetName);
        if (hasAsset) {
            if (!isUsable(dst, assetName)) {
                try {
                    extract(ctx, assetName, dst);
                } catch (IOException e) {
                    // 释放失败：继续尝试 nativeLibraryDir（可能老包）
                }
            }
            if (isUsable(dst, assetName)) {
                return dst;
            }
        }
        // ② 退回 nativeLibraryDir（老安装包 / assets 缺失时）
        try {
            File lib = new File(ctx.getApplicationInfo().nativeLibraryDir, exeName);
            if (lib.isFile()) {
                return lib;
            }
        } catch (RuntimeException ignored) {
            // nativeLibraryDir 拿不到（桩测环境）
        }
        return null;
    }

    /** 文件存在、非空、并且真的可执行（权限位层面）。 */
    private static boolean isUsable(File f, String assetName) {
        if (f == null || !f.isFile() || f.length() <= 0) {
            return false;
        }
        if (!f.canExecute()) {
            // 可能是拷贝后权限丢了：补一次，补完还不行就算不可用
            f.setReadable(true, true);
            f.setWritable(false, true);
            f.setExecutable(true, true);
        }
        return f.canExecute();
    }

    private static boolean hasAsset(Context ctx, String assetName) {
        InputStream in = null;
        try {
            in = ctx.getAssets().open(assetName);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
    }

    /** 从 assets 拷到 filesDir，并设成仅本应用可执行。 */
    private static void extract(Context ctx, String assetName, File dst) throws IOException {
        File tmp = new File(dst.getParentFile(), dst.getName() + ".part");
        InputStream in = null;
        OutputStream out = null;
        try {
            in = ctx.getAssets().open(assetName);
            out = new FileOutputStream(tmp);
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        } finally {
            if (out != null) {
                try { out.close(); } catch (IOException ignored) { }
            }
            if (in != null) {
                try { in.close(); } catch (IOException ignored) { }
            }
        }
        if (dst.exists() && !dst.delete()) {
            // 删不掉也没关系，下面 rename 会失败再退化成覆盖
        }
        if (!tmp.renameTo(dst)) {
            // rename 失败：退化成直接覆盖
            InputStream in2 = null;
            OutputStream out2 = null;
            try {
                in2 = new java.io.FileInputStream(tmp);
                out2 = new FileOutputStream(dst);
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in2.read(buf)) > 0) {
                    out2.write(buf, 0, n);
                }
            } finally {
                if (out2 != null) {
                    try { out2.close(); } catch (IOException ignored) { }
                }
                if (in2 != null) {
                    try { in2.close(); } catch (IOException ignored) { }
                }
            }
            tmp.delete();
        }
        // 只给本应用执行权限（不要 777）
        dst.setReadable(true, true);
        dst.setWritable(false, true);
        dst.setExecutable(true, true);
    }

    private NativeExe() {
    }
}
