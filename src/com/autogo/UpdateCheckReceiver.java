package com.autogo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 后台每 2 小时的更新检查：只记录新版本，不弹窗；下次打开菜单时「更新」按钮会亮。 */
public class UpdateCheckReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        final PendingResult pending = goAsync();
        UpdateManager.check(context, new UpdateManager.Callback() {
            @Override
            public void onResult(UpdateManager.Info info, String error) {
                UpdateManager.recordCheck(context, info);
                UpdateScheduler.schedule(context);
                pending.finish();
            }
        });
    }
}
