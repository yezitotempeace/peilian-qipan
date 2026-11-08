/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package android.content.res;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 桩测用的假 AssetManager。
 *
 * <p>默认「没有任何 asset」：{@code open()} 一律抛 IOException，
 * 于是 NativeExe 会退回 nativeLibraryDir（桩里也不存在）⇒ 引擎不可用 ⇒ 用内置 AI。
 * 需要测「asset 存在」的分支时，设置 {@link #root} 指向一个真实目录即可。
 */
public class AssetManager {

    /** 非 null 时按相对路径从这个目录读真实文件（给需要测释放流程的用例用）。 */
    public static File root;

    public InputStream open(String name) throws IOException {
        if (root == null) {
            throw new IOException("asset not found: " + name);
        }
        File f = new File(root, name);
        if (!f.isFile()) {
            throw new IOException("asset not found: " + name);
        }
        return new FileInputStream(f);
    }
}
