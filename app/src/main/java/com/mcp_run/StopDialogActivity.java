package com.mcp_run;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

/**
 * 透明 Activity 用于处理通知栏停止按钮
 * 点击通知栏"关闭并退出"时启动此 Activity
 * 立即停止服务并退出应用
 */
public class StopDialogActivity extends Activity {
    private static final String TAG = "StopDialogActivity";
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "StopDialogActivity created");
        
        // 获取动作
        String action = getIntent().getStringExtra("action");
        Log.d(TAG, "Action: " + action);
        
        if ("stop".equals(action)) {
            // 停止服务器并退出
            stopServerAndExit();
        } else {
            finish();
        }
    }
    
    private void stopServerAndExit() {
        Log.d(TAG, "Stopping server and exiting app...");
        
        // 停止服务
        if (MCPService.isRunning()) {
            Intent intent = new Intent(this, MCPService.class);
            stopService(intent);
        }
        
        // 发送广播通知 MainActivity 退出（如果在后台）
        Intent exitIntent = new Intent("com.mcp_run.ACTION_EXIT_APP");
        exitIntent.setPackage(getPackageName());
        sendBroadcast(exitIntent);
        
        // 结束自己
        finish();
        
        // 退出应用
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }
    
    @Override
    public void onBackPressed() {
        // 禁用返回键
    }
}