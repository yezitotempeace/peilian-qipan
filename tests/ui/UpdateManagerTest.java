package com.autogo;

/** 更新 JSON 解析/版本比较（不联网）。 */
public class UpdateManagerTest {
    static int pass, fail;
    static void chk(String what, Object want, Object got) {
        if (String.valueOf(want).equals(String.valueOf(got))) pass++;
        else { fail++; System.out.println("FAIL " + what + " 期望 " + want + "，实际 " + got); }
    }
    public static void main(String[] args) {
        String json = "{\"tag_name\":\"v180\",\"body\":\"line1\\nline2\",\"assets\":[{\"browser_download_url\":\"https://example.com/a.apk\"}]}";
        UpdateManager.Info a = UpdateManager.parseJson(json, 179);
        chk("版本号解析", 180, a.versionCode);
        chk("当前 179 时有更新", false, a.upToDate);
        chk("下载 URL", "https://example.com/a.apk", a.apkUrl);
        chk("正文换行还原", true, a.notes.contains("\n"));
        UpdateManager.Info b = UpdateManager.parseJson(json, 180);
        chk("当前 180 已最新", true, b.upToDate);
        chk("tag 非法", 0, UpdateManager.versionCodeFromTag("nightly"));
        boolean threw = false;
        try { UpdateManager.parseJson("{\"tag_name\":\"v1\"}", 1); } catch (Exception e) { threw = true; }
        chk("没有 APK 资产要报错", true, threw);
        chk("安装回调 PendingIntent 是 mutable（系统才能回填状态）", true,
                (UpdateManager.STATUS_FLAGS & android.app.PendingIntent.FLAG_MUTABLE) != 0);
        chk("带查询串的 APK URL 也能解析", "https://example.com/a.apk?x=1",
                UpdateManager.parseJson("{\"tag_name\":\"v181\",\"body\":\"\",\"assets\":[{\"browser_download_url\":\"https://example.com/a.apk?x=1\"}]}", 180).apkUrl);
        android.content.Context ctx = new android.content.Context();
        UpdateManager.Info fresh = new UpdateManager.Info(181, "v181", "notes", "https://example.com/v181.apk", false);
        UpdateManager.recordCheck(ctx, fresh);
        long last = ctx.getSharedPreferences("autogo", android.content.Context.MODE_PRIVATE).getLong("lastUpdateCheck", 0L);
        chk("发现新版本也写 lastUpdateCheck", true, last > 0);
        chk("新版本写进待更新", "v181", ctx.getSharedPreferences("autogo", android.content.Context.MODE_PRIVATE).getString("availableUpdateVersion", ""));
        UpdateManager.Info same = new UpdateManager.Info(180, "v180", "", "", true);
        UpdateManager.recordCheck(ctx, same);
        chk("upToDate 清掉待更新", "", ctx.getSharedPreferences("autogo", android.content.Context.MODE_PRIVATE).getString("availableUpdateVersion", ""));
        long next = UpdateScheduler.nextTrigger(last, last + 1000L);
        chk("upToDate 后下一次约 2h（不是 60s）", true, next >= last + UpdateScheduler.INTERVAL_MS - 1000L);

        System.out.println("更新解析测试：通过 " + pass + " 项，失败 " + fail + " 项");
        if (fail > 0) System.exit(1);
    }
}
