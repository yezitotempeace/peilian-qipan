/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 构建元数据与运行期自检（内部辅助）。
 *
 * <p>这个类看起来只是「打包信息 + 一致性检查」，实际承担三件事：</p>
 * <ol>
 *   <li>给界面提供产品名、版本标签、仓库地址等展示用信息；</li>
 *   <li>给存档格式提供格式戳（{@link #stamp()}），写入与读取都靠它判断「是不是本程序写的」；</li>
 *   <li>给启动自检提供一组中性标记的期望值（{@link #tag()} / {@link #rev()} / {@link #sig()}），
 *       与 {@code res/values/strings.xml} 里的 {@code cfg_*} 比对。</li>
 * </ol>
 *
 * <p><b>为什么不写明文</b>：字面量全部是 Base64，运行时由 {@link #dec} 还原。
 * 这样在 APK 里 {@code strings} 出来的是编码串，不会一眼看到归属信息；
 * 但程序行为、以及仓库里的 {@code LICENSE} / {@code THIRD-PARTY-NOTICES.md} 完全不变，
 * 想确认开源许可与作者，看仓库即可。</p>
 *
 * <p><b>为什么删不掉</b>：本类被启动页、存档读写、崩溃日志、构建期脚本共同引用。
 * 删掉本类或删掉任一常量 ⇒ 编译不过；改了值不同步资源 ⇒ 构建期校验失败。
 * 这是「结构性依赖」，不是防破解的银弹 —— 开源代码谁都能改，详见本地交接文档。</p>
 */
public final class BuildInfo {

    // ---------------------------------------------------------------- 编码字面量
    // 解码见 dec()。改动任何一个都必须同步 res/values/strings.xml 与 tools/checks/verify_mark.sh。

    private static final String E_AUTHOR = "eWV6aXRvdGVtcGVhY2U=";
    private static final String E_YEAR = "MjAyNg==";
    private static final String E_LICENSE = "QUdQTC0zLjAtb25seQ==";
    private static final String E_REPO = "aHR0cHM6Ly9naXRodWIuY29tL3lleml0b3RlbXBlYWNlL3BlaWxpYW4tcWlwYW4=";
    private static final String E_MARK = "UEVJTElBTi1RSVBBTg==";
    private static final String E_TAG = "UFE=";
    private static final String E_REV = "MjY=";
    private static final String E_SIG = "YTMtcjc=";
    private static final String E_OWNER = "eWV6aXRvdGVtcGVhY2U=";
    private static final String E_NAME = "cGVpbGlhbi1xaXBhbg==";

    /**
     * 构建指纹。给 {@code tools/checks/verify_mark.sh} 核对；
     * 值本身是个看不出含义的短串，不同步脚本 ⇒ 构建失败。
     */
    public static final String BUILD_TAG = "q7-26-a3";

    /** 作者 / 仓库所有者。 */
    public static String author() {
        return dec(E_AUTHOR);
    }

    /** 起始年份。 */
    public static String year() {
        return dec(E_YEAR);
    }

    /** 许可标识。 */
    public static String license() {
        return dec(E_LICENSE);
    }

    /** 仓库地址。 */
    public static String repo() {
        return dec(E_REPO);
    }

    /** 仓库所有者（更新校验用）。 */
    public static String owner() {
        return dec(E_OWNER);
    }

    /** 仓库名（更新校验用）。 */
    public static String repoName() {
        return dec(E_NAME);
    }

    /** 内部识别码。 */
    public static String mark() {
        return dec(E_MARK);
    }

    /** 标记-标签段。 */
    public static String tag() {
        return dec(E_TAG);
    }

    /** 标记-版本段。 */
    public static String rev() {
        return dec(E_REV);
    }

    /** 标记-签名段。 */
    public static String sig() {
        return dec(E_SIG);
    }

    /** 存档格式戳：写入与读取都比对它。 */
    public static String stamp() {
        return mark() + "/" + license() + "/" + year() + "/" + author();
    }

    /** 存档头行。 */
    public static String header() {
        return "# " + stamp();
    }

    /** Base64 → 字符串。失败返回空串（不让自检把 App 弄崩）。 */
    private static String dec(String s) {
        try {
            return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
        } catch (RuntimeException e) {
            return "";
        }
    }

    private BuildInfo() {
    }

    /** 运行期一致性校验。 */
    public static final class Check {

        private Check() {
        }

        /**
         * 资源里的中性标记是否与代码期望值一致。
         *
         * @param tag 资源 {@code cfg_tag}
         * @param rev 资源 {@code cfg_rev}
         * @param sig 资源 {@code cfg_sig}
         * @param src 资源 {@code cfg_src}
         */
        public static boolean mark(String tag, String rev, String sig, String src) {
            return BuildInfo.tag().equals(trim(tag))
                    && BuildInfo.rev().equals(trim(rev))
                    && BuildInfo.sig().equals(trim(sig))
                    && src != null && src.contains(BuildInfo.author());
        }

        /** 存档头是否本程序所写。 */
        public static boolean stamp(String firstLine) {
            return firstLine != null && firstLine.trim().equals(BuildInfo.header());
        }

        /**
         * 更新来源是否为本仓库。
         *
         * @param url 待校验的下载地址
         * @return true = 指向本仓库的 release 资产
         */
        public static boolean fromOurRepo(String url) {
            if (url == null) {
                return false;
            }
            String expected = "https://github.com/" + BuildInfo.owner() + "/"
                    + BuildInfo.repoName() + "/releases/download/";
            return url.startsWith(expected);
        }

        private static String trim(String s) {
            return s == null ? "" : s.trim();
        }
    }
}
