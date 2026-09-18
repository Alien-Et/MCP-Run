package com.mcp_run.tools;

import android.content.Context;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import androidx.core.app.NotificationCompat;

/**
 * 通知发送工具
 */
public class NotificationTool implements MCPTool {
    private static final String CHANNEL_ID = "mcp_notifications";
    private static final String CHANNEL_NAME = "MCP通知";
    
    @Override
    public String getName() {
        return "send_notification";
    }

    @Override
    public String getDescription() {
        return "在设备上发送一条系统通知。支持设置标题、内容、优先级等。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject titleProp = new JSONObject();
            titleProp.put("type", "string");
            titleProp.put("description", "通知标题");
            props.put("title", titleProp);
            
            JSONObject contentProp = new JSONObject();
            contentProp.put("type", "string");
            contentProp.put("description", "通知内容");
            props.put("content", contentProp);
            
            JSONObject priorityProp = new JSONObject();
            priorityProp.put("type", "string");
            priorityProp.put("description", "通知优先级: low, default, high, urgent");
            priorityProp.put("default", "default");
            props.put("priority", priorityProp);
            
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("title").put("content"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String title = args.getString("title");
        String content = args.getString("content");
        String priority = args.optString("priority", "default");
        
        NotificationManager notificationManager = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        // 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
        
        // 映射优先级
        int priorityCompat;
        int importance;
        switch (priority) {
            case "low":
                priorityCompat = NotificationCompat.PRIORITY_LOW;
                importance = NotificationManager.IMPORTANCE_LOW;
                break;
            case "high":
                priorityCompat = NotificationCompat.PRIORITY_HIGH;
                importance = NotificationManager.IMPORTANCE_HIGH;
                break;
            case "urgent":
                priorityCompat = NotificationCompat.PRIORITY_MAX;
                importance = NotificationManager.IMPORTANCE_MAX;
                break;
            default:
                priorityCompat = NotificationCompat.PRIORITY_DEFAULT;
                importance = NotificationManager.IMPORTANCE_DEFAULT;
        }
        
        // 更新渠道重要性
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_NAME, importance);
            notificationManager.createNotificationChannel(channel);
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(priorityCompat)
                .setAutoCancel(true);
        
        int notificationId = (int) System.currentTimeMillis();
        notificationManager.notify(notificationId, builder.build());
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("notification_id", notificationId);
        result.put("title", title);
        result.put("content", content);
        result.put("priority", priority);
        return result;
    }
}