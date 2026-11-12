/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Build;
import android.os.Looper;
import android.net.Uri;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 直连 GitHub 仓库检查 / 下载 / 安装更新。
 *
 * <p>只认仓库最新 Release 的 APK 资产；比较 versionCode（tag vNNN）。
 * 安装走 PackageInstaller（系统安装器），安装成功后由 {@link UpdateInstallReceiver}
 * 删掉缓存里的安装包；下次启动也会清理残留。</p>
 */
public final class UpdateManager {

    public static final String API = "https://api.github.com/repos/yezitotempeace/peilian-qipan/releases/latest";
    /** 国内可直连的 GitHub 代理；403/超时时改用，下载也走它。 */
    public static final String PROXY = "https://gh-proxy.com/";
    public static final String REPO_PAGE = "https://github.com/yezitotempeace/peilian-qipan/releases";
    /**
     * 本程序的签名证书指纹（SHA-256，小写无冒号）。
     * 下载回来的安装包必须由同一把证书签名才允许安装 —— 换过包的 APK 一律拒绝。
     */
    public static final String EXPECTED_CERT_SHA256 =
            "2a8de3ac837cabb945a4fb3bd952e7a28e555cb936c3bb064b4a4d2e08d90223";

    public static final String ACTION_INSTALL_STATUS = "com.autogo.UPDATE_INSTALL_STATUS";
    public static final String EXTRA_FILE = "update_file";

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    /** 系统安装器要回填 EXTRA_STATUS/EXTRA_INTENT，PendingIntent 必须是 mutable。 */
    public static final int STATUS_FLAGS = android.app.PendingIntent.FLAG_UPDATE_CURRENT
            | android.app.PendingIntent.FLAG_MUTABLE;

    public static class Info {
        public final int versionCode;
        public final String version;
        public final String notes;
        public final String apkUrl;
        public final boolean upToDate;
        Info(int versionCode, String version, String notes, String apkUrl, boolean upToDate) {
            this.versionCode = versionCode;
            this.version = version;
            this.notes = notes;
            this.apkUrl = apkUrl;
            this.upToDate = upToDate;
        }
    }

    public interface Callback {
        void onResult(Info info, String error);
    }

    private UpdateManager() {
    }

    public static void check(final Context ctx, final Callback cb) {
        final int current = currentVersionCode(ctx);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                Info info = null;
                HttpURLConnection conn = null;
                String[] endpoints = new String[]{PROXY + API, API};
                for (String endpoint : endpoints) {
                    try {
                        conn = (HttpURLConnection) new URL(endpoint).openConnection();
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(6000);
                        conn.setRequestProperty("Accept", "application/vnd.github+json");
                        conn.setRequestProperty("User-Agent", "PeilianQipan-Update");
                        int code = conn.getResponseCode();
                        if (code != 200) {
                            throw new IllegalStateException("HTTP " + code);
                        }
                        StringBuilder sb = new StringBuilder();
                        InputStream in = conn.getInputStream();
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            sb.append(new String(buf, 0, n, "UTF-8"));
                        }
                        in.close();
                        info = parseJson(sb.toString(), current);
                        error = null;
                        break;
                    } catch (Exception e) {
                        error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                    } finally {
                        if (conn != null) conn.disconnect();
                        conn = null;
                    }
                }
                final Info fi = info;
                final String fe = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        cb.onResult(fi, fe);
                    }
                });
            }
        }, "update-check").start();
    }

    /** 从 GitHub release JSON 里解析 tag/body/APK 资产；public 便于单测。 */
    public static Info parseJson(String json, int currentCode) {
        int code = versionCodeFromTag(first(json, "tag_name"));
        String version = first(json, "tag_name");
        String notes = unescape(first(json, "body"));
        String url = firstApkUrl(json);
        if (code <= 0 || url.length() == 0) {
            throw new IllegalStateException("release 里没有可用的 APK");
        }
        // 来源校验：不是本仓库的 release 资产，一律不认（防止被改成指向别人的仓库）
        if (!BuildInfo.Check.fromOurRepo(url)) {
            throw new IllegalStateException("更新来源不是官方仓库，已拒绝");
        }
        return new Info(code, version, notes, url, code <= currentCode);
    }

    public static int versionCodeFromTag(String tag) {
        if (tag == null) return 0;
        Matcher m = Pattern.compile("v?(\\d+)").matcher(tag);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static int currentVersionCode(Context ctx) {
        try {
            return ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (Exception e) {
            return -1;
        }
    }

    private static String first(String json, String key) {
        Matcher m = Pattern.compile("\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static String firstApkUrl(String json) {
        Matcher m = Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+?\\.apk(?:\\?[^\\\"]*)?)\\\"").matcher(json);
        return m.find() ? m.group(1) : "";
    }

    private static String unescape(String s) {
        return s.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    /** 无论有没有新版本/是否出错，都记一次检查时间；有新版本才写待更新信息。 */
    public static void recordCheck(Context ctx, Info info) {
        android.content.SharedPreferences.Editor e = ctx.getSharedPreferences("autogo", Context.MODE_PRIVATE).edit()
                .putLong("lastUpdateCheck", System.currentTimeMillis());
        if (info != null && !info.upToDate) {
            e.putString("availableUpdateVersion", info.version)
                    .putString("availableUpdateUrl", info.apkUrl)
                    .putString("availableUpdateNotes", info.notes);
        } else {
            e.remove("availableUpdateVersion")
                    .remove("availableUpdateUrl")
                    .remove("availableUpdateNotes");
        }
        e.apply();
    }

    /** 下载到缓存，再交给系统安装器；安装结果由 receiver 处理。 */
    public static void downloadAndInstall(final Activity activity, final Info info) {
        final ProgressDialog pd = new ProgressDialog(activity);
        pd.setMessage("正在下载 " + info.version + "…");
        pd.setCancelable(false);
        pd.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                File out = new File(activity.getCacheDir(), "update.apk");
                String error = null;
                String[] sources = info.apkUrl.startsWith(PROXY)
                        ? new String[]{info.apkUrl}
                        : new String[]{PROXY + info.apkUrl, info.apkUrl};
                for (String source : sources) {
                    HttpURLConnection conn = null;
                    try {
                        conn = (HttpURLConnection) new URL(source).openConnection();
                        conn.setConnectTimeout(15000);
                        conn.setReadTimeout(60000);
                        conn.setRequestProperty("User-Agent", "PeilianQipan-Update");
                        if (conn.getResponseCode() != 200) {
                            throw new IllegalStateException("HTTP " + conn.getResponseCode());
                        }
                        InputStream in = conn.getInputStream();
                        FileOutputStream fos = new FileOutputStream(out);
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) > 0) {
                            fos.write(buf, 0, n);
                        }
                        fos.close();
                        in.close();
                        error = null;
                        break;
                    } catch (Exception e) {
                        error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                        //noinspection ResultOfMethodCallIgnored
                        out.delete();
                    } finally {
                        if (conn != null) conn.disconnect();
                    }
                }
                final String fe = error;
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        pd.dismiss();
                        if (fe != null) {
                            Toast.makeText(activity, "下载失败：" + fe, Toast.LENGTH_LONG).show();
                            return;
                        }
                        install(activity, out);
                    }
                });
            }
        }, "update-download").start();
    }

    /**
     * 读出 APK 的签名证书 SHA-256（小写十六进制）。读不到返回空串。
     * API 28+ 用 getPackageArchiveInfo(GET_SIGNING_CERTIFICATES)，26/27 退回 GET_SIGNATURES。
     */
    static String certSha256(Activity activity, File apk) {
        try {
            android.content.pm.PackageManager pm = activity.getPackageManager();
            android.content.pm.PackageInfo pi;
            byte[] cert;
            if (Build.VERSION.SDK_INT >= 28) {
                pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                        android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES);
                if (pi == null || pi.signingInfo == null) {
                    return "";
                }
                android.content.pm.Signature[] sigs = pi.signingInfo.getApkContentsSigners();
                if (sigs == null || sigs.length == 0) {
                    return "";
                }
                cert = sigs[0].toByteArray();
            } else {
                pi = pm.getPackageArchiveInfo(apk.getAbsolutePath(),
                        android.content.pm.PackageManager.GET_SIGNATURES);
                if (pi == null || pi.signatures == null || pi.signatures.length == 0) {
                    return "";
                }
                cert = pi.signatures[0].toByteArray();
            }
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static void install(Activity activity, File apk) {
        try {
            // 先验签名：换过包的 APK（哪怕版本号更高）一律不装
            String cert = certSha256(activity, apk);
            if (!EXPECTED_CERT_SHA256.equalsIgnoreCase(cert)) {
                Toast.makeText(activity,
                        "安装包签名不符（不是官方版本），已拒绝安装", Toast.LENGTH_LONG).show();
                //noinspection ResultOfMethodCallIgnored
                apk.delete();
                return;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                    && !activity.getPackageManager().canRequestPackageInstalls()) {
                Toast.makeText(activity, "请先允许本应用安装未知应用，再点更新", Toast.LENGTH_LONG).show();
                try {
                    Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + activity.getPackageName()));
                    activity.startActivity(settings);
                } catch (Exception ignored) {
                }
                return;
            }
            PackageInstaller pi = activity.getPackageManager().getPackageInstaller();
            PackageInstaller.SessionParams params =
                    new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(activity.getPackageName());
            int id = pi.createSession(params);
            PackageInstaller.Session session = pi.openSession(id);
            InputStream in = new java.io.FileInputStream(apk);
            java.io.OutputStream out = session.openWrite("base.apk", 0, apk.length());
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            out.flush();
            session.fsync(out);
            out.close();
            in.close();
            Intent status = new Intent(ACTION_INSTALL_STATUS);
            status.setPackage(activity.getPackageName());
            status.putExtra(EXTRA_FILE, apk.getAbsolutePath());
            android.app.PendingIntent pending = android.app.PendingIntent.getBroadcast(
                    activity, 0, status,
                    STATUS_FLAGS);
            session.commit(pending.getIntentSender());
            session.close();
        } catch (Exception e) {
            Toast.makeText(activity, "无法启动安装：" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** 安装成功/下次启动清理缓存里的安装包。 */
    public static void cleanup(Context ctx) {
        try {
            File f = new File(ctx.getCacheDir(), "update.apk");
            if (f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (Exception ignored) {
        }
    }
}
