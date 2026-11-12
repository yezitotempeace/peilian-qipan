/*
 * SPDX-License-Identifier: AGPL-3.0-only
 * Copyright (C) 2026 yezitotempeace
 */

package com.autogo;

/** 更新 JSON 解析/版本比较（不联网）。 */
public class UpdateManagerTest {
    static int pass, fail;
    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) pass++;
        else { fail++; System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got); }
    }
    public static void main(String[] args) {
        String json = "{\"tag_name\":\"v180\",\"body\":\"line1\\nline2\",\"assets\":[{\"browser_download_url\":\"https://github.com/yezitotempeace/peilian-qipan/releases/download/v180/a.apk\"}]}";
        UpdateManager.Info a = UpdateManager.parseJson(json, 179);
        chk("版本号解析", 180, a.versionCode);
        chk("当前 179 时有更新", false, a.upToDate);
        chk("下载 URL", "https://github.com/yezitotempeace/peilian-qipan/releases/download/v180/a.apk", a.apkUrl);
        chk("正文换行还原", true, a.notes.contains("\n"));
        UpdateManager.Info b = UpdateManager.parseJson(json, 180);
        chk("当前 180 已最新", true, b.upToDate);
        chk("tag 非法", 0, UpdateManager.versionCodeFromTag("nightly"));
        boolean threw = false;
        try { UpdateManager.parseJson("{\"tag_name\":\"v1\"}", 1); } catch (Exception e) { threw = true; }
        chk("没有 APK 资产要报错", true, threw);
        chk("安装回调 PendingIntent 是 mutable（系统才能回填状态）", true,
                (UpdateManager.STATUS_FLAGS & android.app.PendingIntent.FLAG_MUTABLE) != 0);
        chk("带查询串的 APK URL 也能解析", "https://github.com/yezitotempeace/peilian-qipan/releases/download/v181/a.apk?x=1",
                UpdateManager.parseJson("{\"tag_name\":\"v181\",\"body\":\"\",\"assets\":[{\"browser_download_url\":\"https://github.com/yezitotempeace/peilian-qipan/releases/download/v181/a.apk?x=1\"}]}", 180).apkUrl);
        android.content.Context ctx = new android.content.Context();
        UpdateManager.Info fresh = new UpdateManager.Info(181, "v181", "notes", "https://github.com/yezitotempeace/peilian-qipan/releases/download/v180/a.apk", false);
        UpdateManager.recordCheck(ctx, fresh);
        long last = ctx.getSharedPreferences("autogo", android.content.Context.MODE_PRIVATE).getLong("lastUpdateCheck", 0L);
        chk("发现新版本也写 lastUpdateCheck", true, last > 0);
        chk("新版本写进待更新", "v181", ctx.getSharedPreferences("autogo", android.content.Context.MODE_PRIVATE).getString("availableUpdateVersion", ""));
        UpdateManager.Info same = new UpdateManager.Info(180, "v180", "", "", true);
        UpdateManager.recordCheck(ctx, same);
        chk("upToDate 清掉待更新", "", ctx.getSharedPreferences("autogo", android.content.Context.MODE_PRIVATE).getString("availableUpdateVersion", ""));
        long next = UpdateScheduler.nextTrigger(last, last + 1000L);
        chk("upToDate 后下一次约 2h（不是 60s）", true, next >= last + UpdateScheduler.INTERVAL_MS - 1000L);

        chk("直连 API 仍是 GitHub", true, UpdateManager.API.startsWith("https://api.github.com/"));
        chk("国内镜像前缀是 gh-proxy.com", true, UpdateManager.PROXY.startsWith("https://gh-proxy.com/"));
        // 来源校验：不是官方仓库的 release 资产必须拒绝
        boolean rejected = false;
        try {
            UpdateManager.parseJson("{\"tag_name\":\"v199\",\"body\":\"\",\"assets\":[{\"browser_download_url\":\"https://github.com/someoneelse/peilian-qipan/releases/download/v199/a.apk\"}]}", 198);
        } catch (RuntimeException e) {
            rejected = true;
        }
        chk("非官方仓库来源被拒", true, rejected);
        boolean rejected2 = false;
        try {
            UpdateManager.parseJson("{\"tag_name\":\"v199\",\"body\":\"\",\"assets\":[{\"browser_download_url\":\"https://example.com/a.apk\"}]}", 198);
        } catch (RuntimeException e) {
            rejected2 = true;
        }
        chk("任意域名来源被拒", true, rejected2);

        // 签名指纹：必须是钉住的那一把
        chk("签名指纹钉死", "2a8de3ac837cabb945a4fb3bd952e7a28e555cb936c3bb064b4a4d2e08d90223",
                UpdateManager.EXPECTED_CERT_SHA256);
        // 读不到安装包信息时签名校验返回空串（→ 安装被拒绝，而不是放行）
        android.content.pm.PackageManager.ARCHIVE_INFO = null;
        chk("读不到签名返回空串", "", UpdateManager.certSha256(new android.app.Activity(), new java.io.File("/tmp/x.apk")));

        System.out.println("更新解析测试：通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
