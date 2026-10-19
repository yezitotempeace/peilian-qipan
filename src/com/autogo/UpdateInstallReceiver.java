package com.autogo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import java.io.File;

/** 系统安装器的结果：待用户确认时拉起安装界面；成功后删掉缓存安装包。 */
public class UpdateInstallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) {
            return;
        }
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -1);
        String path = intent.getStringExtra(UpdateManager.EXTRA_FILE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirm != null) {
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(confirm);
            }
        } else if (status == PackageInstaller.STATUS_SUCCESS) {
            delete(path);
            Toast.makeText(context, "更新已安装", Toast.LENGTH_SHORT).show();
        } else if (status == PackageInstaller.STATUS_FAILURE
                || status == PackageInstaller.STATUS_FAILURE_ABORTED
                || status == PackageInstaller.STATUS_FAILURE_BLOCKED
                || status == PackageInstaller.STATUS_FAILURE_CONFLICT
                || status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
                || status == PackageInstaller.STATUS_FAILURE_INVALID
                || status == PackageInstaller.STATUS_FAILURE_STORAGE) {
            delete(path);
            Toast.makeText(context, "安装失败（错误码 " + status + "），请重试", Toast.LENGTH_LONG).show();
        }
    }

    private void delete(String path) {
        if (path == null) {
            return;
        }
        try {
            File f = new File(path);
            if (f.exists()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        } catch (Exception ignored) {
        }
    }
}
