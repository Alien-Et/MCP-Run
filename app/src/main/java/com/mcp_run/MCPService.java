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

import androidx.core.app.NotificationCompat;

import java.io.IOException;

/**
 * MVP v1.22 - MCP前台服务（简化版）
 */
public class MCPService extends Service {
    private static final String TAG = "MCPService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "mcp_server_channel";
    public static final String EXTRA_PORT = "extra_port";
    public static final String EXTRA_API_KEY = "extra_api_key";
    
    // 停止广播 action
    public static final String ACTION_STOP = "com.mcp_run.ACTION_STOP";
    // 退出应用广播 action
    public static final String ACTION_EXIT = "com.mcp_run.ACTION_EXIT";
    
    private MCPHttpServer server;
    private static MCPService instance;
    private static int currentPort = 1145;
    private static String currentApiKey = null;
    
    private NotificationManager notificationManager;
    
    public static boolean isRunning() {
        return instance != null && instance.server != null && instance.server.isRunning();
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
        Log.d(TAG, "onStartCommand");
        
        // 读取配置
        int port = intent != null ? intent.getIntExtra(EXTRA_PORT, 1145) : 1145;
        currentPort = port;
        String apiKey = intent != null ? intent.getStringExtra(EXTRA_API_KEY) : null;
        currentApiKey = apiKey;
        
        // 启动前台通知
        Notification notification = createNotification("启动中...", false);
        startForeground(NOTIFICATION_ID, notification);
        
        // 启动服务器
        new Thread(() -> {
            try {
                server = new MCPHttpServer(this, port, apiKey);
                server.start();
                updateNotification("运行中: " + server.getPort(), true);
            } catch (IOException e) {
                Log.e(TAG, "启动失败", e);
                updateNotification("启动失败: " + e.getMessage(), false);
            }
        }).start();
        
        return START_STICKY;
    }
    
    private void updateNotification(String text, boolean running) {
        Notification notification = createNotification(text, running);
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
    
    private Notification createNotification(String text, boolean isRunning) {
        // 点击打开APP
        Intent tapIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, tapIntent, PendingIntent.FLAG_IMMUTABLE);
        
        // 停止按钮 - 发送广播
        Intent stopIntent = new Intent(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this, 1, stopIntent, PendingIntent.FLAG_IMMUTABLE);
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(isRunning ? "MCP服务器运行中" : "MCP服务器已停止")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(isRunning)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止并退出", stopPendingIntent)
                .build();
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "MCP服务器", NotificationManager.IMPORTANCE_LOW);
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }
    
    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        if (server != null) {
            server.stop();
            server = null;
        }
        instance = null;
        super.onDestroy();
    }
    
    /**
     * 处理停止按钮点击
     */
    public static void handleStopClick(Context context) {
        Log.d(TAG, "Handling stop click");
        
        // 停止服务
        MCPService service = instance;
        if (service != null) {
            if (service.server != null) {
                service.server.stop();
                service.server = null;
            }
            service.stopSelf();
        }
        
        // 发送退出广播
        Intent exitIntent = new Intent(ACTION_EXIT);
        exitIntent.setPackage(context.getPackageName());
        context.sendBroadcast(exitIntent);
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}