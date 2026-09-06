package com.mcp_run;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * 透明 Activity 用于处理通知栏停止按钮
 * 解决 PendingIntent.getService() 在某些 Android 版本上不触发的问题
 */
public class StopActivity extends Activity {
    private static final String TAG = "StopActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "StopActivity created from notification");
        
        // 停止服务
        stopService(new Intent(this, MCPService.class));
        
        // 退出应用
        finishAffinity();
        
        // 立即结束自己
        finish();
    }
}