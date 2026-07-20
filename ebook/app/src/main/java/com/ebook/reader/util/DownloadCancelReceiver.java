package com.ebook.reader.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DownloadCancelReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!UpdateManager.ACTION_CANCEL_DOWNLOAD.equals(intent.getAction())) {
            return;
        }
        int notificationId = intent.getIntExtra(UpdateManager.EXTRA_NOTIFICATION_ID, -1);
        if (notificationId != -1) {
            UpdateManager.cancelDownload(context, notificationId);
        }
    }
}
