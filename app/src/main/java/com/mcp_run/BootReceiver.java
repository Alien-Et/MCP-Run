package com.mcp_run;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启接收器 - 开机后自动启动MCP服务
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "收到广播: " + action);
        
        if (action != null && (
            Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action) ||
            "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
        )) {
            // 检查是否应该自动启动（可通过设置控制）
            android.content.SharedPreferences prefs = 
                context.getSharedPreferences("mcp_config", Context.MODE_PRIVATE);
            boolean autoStart = prefs.getBoolean("auto_start", true);
            
            if (autoStart) {
                Log.d(TAG, "自动启动MCP服务");
                Intent serviceIntent = new Intent(context, MCPService.class);
                serviceIntent.putExtra(MCPService.EXTRA_PORT, 1145);
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
            }
        }
    }
}
