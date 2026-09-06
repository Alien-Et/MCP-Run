package com.mcp_run;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.IOException;

/**
 * MVP v1.20 - MCP前台服务（全新通知栏架构）
 * 使用透明 Activity 处理停止按钮，确保可靠响应
 */
public class MCPService extends Service {
    private static final String TAG = "MCPService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "mcp_server_channel";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_API_KEY = "extra_api_key";
    
    private MCPHttpServer server;
    private static MCPService instance;
    private static int currentPort = 1145;
    private static String currentApiKey = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private NotificationManager notificationManager;
    
    public static boolean isRunning() {
        return instance != null && instance.server != null && instance.server.isRunning();
    }
    
    public static MCPService getInstance() {
        return instance;
    }
    
    public static int getCurrentPort() {
        return currentPort;
    }
    
    public static String getCurrentApiKey() {
        return currentApiKey;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        createNotificationChannel();
        Log.d(TAG, "Service created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand action=" + (intent != null ? intent.getAction() : "null"));
        
        // 读取端口
        int port = intent != null ? intent.getIntExtra(EXTRA_PORT, 1145) : 1145;
        currentPort = port;
        
        // 读取 API Key
        String apiKey = intent != null ? intent.getStringExtra(EXTRA_API_KEY) : null;
        currentApiKey = apiKey;
        
        // 启动前台通知
        Notification notification = createNotification("MCP服务器启动中...", false);
        try {
            startForeground(NOTIFICATION_ID, notification);
        } catch (Exception e) {
            Log.e(TAG, "startForeground failed", e);
        }
        
        // 在子线程中启动服务器
        new Thread(() -> {
            try {
                server = new MCPHttpServer(this, port, apiKey);
                server.setStatusListener(new MCPHttpServer.ServerStatusListener() {
                    @Override
                    public void onStatusChanged(String status, int p) {
                        Log.d(TAG, status);
                        updateNotification(status, server.isRunning());
                    }
                    
                    @Override
                    public void onRequest(String method, String path, int responseCode) {
                        Log.d(TAG, method + " " + path + " -> " + responseCode);
                    }
                    
                    @Override
                    public void onError(String error) {
                        Log.e(TAG, error);
                        updateNotification(error, false);
                    }
                });
                
                server.start();
                
                String notifyText = "MCP服务器运行中 端口:" + server.getPort()
                    + (apiKey != null ? " [认证已启用]" : "");
                updateNotification(notifyText, true);
                
            } catch (IOException e) {
                Log.e(TAG, "启动服务器失败", e);
                updateNotification("启动失败: " + e.getMessage(), false);
                
                mainHandler.post(() -> {
                    Toast.makeText(MCPService.this, 
                        "服务器启动失败: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Log.e(TAG, "未知错误", e);
                updateNotification("错误: " + e.getMessage(), false);
            }
        }, "MCP-Server-Start").start();
        
        return START_STICKY;
    }
    
    private void updateNotification(String text, boolean isRunning) {
        Notification notification = createNotification(text, isRunning);
        if (notificationManager != null) {
            notificationManager.notify(NOTIFICATION_ID, notification);
        }
    }
    
    private Notification createNotification(String text, boolean isRunning) {
        // 点击通知跳转到主界面
        Intent tapIntent = new Intent(this, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        // 停止按钮：启动透明 Activity 处理停止逻辑
        Intent stopIntent = new Intent(this, StopDialogActivity.class);
        stopIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        stopIntent.putExtra("action", "stop");
        PendingIntent stopPendingIntent = PendingIntent.getActivity(
                this, 999, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        String title = isRunning ? "MCP服务器运行中" : "MCP服务器已停止";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOngoing(isRunning)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭并退出", stopPendingIntent);
        
        return builder.build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MCP服务器",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("MCP服务器的运行状态通知");
            channel.setShowBadge(false);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed, server running: " + (server != null && server.isRunning()));
        if (server != null) {
            server.stop();
            server = null;
        }
        instance = null;
        super.onDestroy();
    }
    
    /**
     * 公开方法：停止服务器并退出应用
     */
    public void stopServerAndExit() {
        Log.d(TAG, "Stopping server and exiting app...");
        
        if (server != null) {
            server.stop();
            server = null;
        }
        
        mainHandler.post(() -> {
            Toast.makeText(this, "已停止服务并退出应用", Toast.LENGTH_SHORT).show();
        });
        
        stopSelf();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}