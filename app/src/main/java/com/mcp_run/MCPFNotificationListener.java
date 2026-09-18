package com.mcp_run;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/**
 * 通知监听服务 - 监听系统通知
 */
public class MCPFNotificationListener extends NotificationListenerService {
    private static final String TAG = "MCPFNotificationListener";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        Log.d(TAG, "收到新通知: " + sbn.getPackageName());
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        Log.d(TAG, "通知已移除: " + sbn.getPackageName());
    }
}
