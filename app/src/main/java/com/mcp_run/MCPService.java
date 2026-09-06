package com.mcp_run;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;

/**
 * MVP v1.21 - MCP前台服务（参考 OpenList 实现）
 * 使用 PendingIntent.getBroadcast() + 内部 BroadcastReceiver
 */
public class MCPService extends Service {
    private static final String TAG = "MCPService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "mcp_server_channel";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_API_KEY = "extra_api_key";
    
    // Action constants
    public static final String ACTION_STOP = "com.mcp_run.ACTION_STOP";
    
    private MCPHttpServer server;
    private static MCPService instance;
    private static int currentPort = 1145;
    private static String currentApiKey = null;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    
    private NotificationManager notificationManager;
    private NotificationActionReceiver notificationReceiver;
    
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
        
        // 注册通知操作接收器
        notificationReceiver = new NotificationActionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_STOP);
        registerReceiver(notificationReceiver, filter);
        
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
        
        // 停止按钮 - 使用 getBroadcast 触发内部 Receiver
        Intent stopIntent = new Intent(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 0, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        
        String title = isRunning ? "MCP服务器运行中" : "MCP服务器已停止";
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isRunning)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭并退出", stopPendingIntent);
        
        return builder.build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "MCP服务器",
                    NotificationManager.IMPORTANCE_LOW
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
        
        // 注销广播接收器
        try {
            if (notificationReceiver != null) {
                unregisterReceiver(notificationReceiver);
                notificationReceiver = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister receiver", e);
        }
        
        if (server != null) {
            server.stop();
            server = null;
        }
        instance = null;
        super.onDestroy();
    }
    
    /**
     * 通知栏操作接收器（参考 OpenList 实现）
     */
    class NotificationActionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "NotificationActionReceiver: action=" + action);
            
            if (ACTION_STOP.equals(action)) {
                // 停止服务并退出应用
                stopServerAndExit();
            }
        }
    }
    
    private void stopServerAndExit() {
        Log.d(TAG, "Stopping server and exiting app...");
        
        if (server != null) {
            server.stop();
            server = null;
        }
        
        // 发送广播通知 MainActivity 退出
        Intent exitIntent = new Intent("com.mcp_run.ACTION_EXIT_APP");
        exitIntent.setPackage(getPackageName());
        sendBroadcast(exitIntent);
        
        // 停止服务
        stopSelf();
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}