package com.mcp_run;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 通知栏操作接收器
 */
public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);
        
        if (MCPService.ACTION_STOP.equals(action)) {
            Log.d(TAG, "Stop button clicked");
            MCPService.handleStopClick(context);
        }
    }
}