package com.mcp_run;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * 停止服务并退出应用
 * 点击通知栏停止按钮时启动
 */
public class StopDialogActivity extends Activity {
    private static final String TAG = "StopDialogActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "StopDialogActivity created");
        
        // 1. 停止服务
        Intent stopIntent = new Intent(this, MCPService.class);
        stopService(stopIntent);
        
        // 2. 发送广播通知MainActivity退出
        Intent exitIntent = new Intent("com.mcp_run.ACTION_EXIT_APP");
        exitIntent.setPackage(getPackageName());
        sendBroadcast(exitIntent);
        
        // 3. 结束自己
        finish();
        
        // 4. 强制退出（如果MainActivity已经finish，这里会杀进程）
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
}