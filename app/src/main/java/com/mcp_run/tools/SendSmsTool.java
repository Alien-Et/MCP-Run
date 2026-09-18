package com.mcp_run.tools;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 短信发送工具 - 发送短信
 * 需要 SEND_SMS 权限
 * 支持 Mock 模式（无需权限测试）
 */
public class SendSmsTool implements MCPTool {
    
    private static final String TAG = "SendSmsTool";
    private static final boolean MOCK_MODE = true; // 开启Mock模式

    @Override
    public String getName() {
        return "send_sms";
    }

    @Override
    public String getDescription() {
        return "发送短信到指定号码。支持Mock模式（无需权限测试）。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            // address: 收件人号码
            JSONObject addrProp = new JSONObject();
            addrProp.put("type", "string");
            addrProp.put("description", "收件人电话号码");
            props.put("address", addrProp);

            // message: 短信内容
            JSONObject msgProp = new JSONObject();
            msgProp.put("type", "string");
            msgProp.put("description", "短信内容");
            props.put("message", msgProp);

            // mock: 是否使用Mock模式（可选，默认false）
            JSONObject mockProp = new JSONObject();
            mockProp.put("type", "boolean");
            mockProp.put("description", "是否使用Mock模式（无需权限测试）");
            mockProp.put("default", MOCK_MODE);
            props.put("mock", mockProp);

            schema.put("properties", props);
            schema.put("required", new JSONArray().put("address").put("message"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        boolean useMock = args.optBoolean("mock", MOCK_MODE);
        
        // Mock模式：直接返回模拟结果
        if (useMock) {
            return buildMockResult(args);
        }
        
        // 真实模式：检查权限并发送
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: SEND_SMS。请开启Mock模式或授予权限。");
            }
        }

        return sendRealSms(context, args);
    }
    
    /**
     * Mock模式：模拟发送成功
     */
    private JSONObject buildMockResult(JSONObject args) throws Exception {
        String address = args.getString("address");
        String message = args.getString("message");
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("mode", "MOCK");
        result.put("recipient", address);
        result.put("message_preview", message.length() > 50 ? 
            message.substring(0, 50) + "..." : message);
        result.put("total_messages", 1);
        result.put("total_parts", 1);
        result.put("sent_at", System.currentTimeMillis());
        result.put("message", "Mock模式：短信已模拟发送（不会真正发送）");
        
        return result;
    }
    
    /**
     * 真实模式：发送短信
     */
    private JSONObject sendRealSms(Context context, JSONObject args) throws Exception {
        String address = args.getString("address");
        String message = args.getString("message");
        JSONArray addressesArray = args.optJSONArray("addresses");

        java.util.List<String> recipients = new java.util.ArrayList<>();
        recipients.add(address);
        if (addressesArray != null) {
            for (int i = 0; i < addressesArray.length(); i++) {
                recipients.add(addressesArray.getString(i));
            }
        }

        android.telephony.SmsManager smsManager = android.telephony.SmsManager.getDefault();
        java.util.List<String> parts = smsManager.divideMessage(message);

        java.util.List<String> sentParts = new java.util.ArrayList<>();
        int totalSent = 0;

        for (String recipient : recipients) {
            if (recipient == null || recipient.trim().isEmpty()) {
                continue;
            }
            for (String part : parts) {
                try {
                    smsManager.sendTextMessage(recipient, null, part, null, null);
                    sentParts.add(part);
                    totalSent++;
                } catch (Exception e) {
                    // 记录失败
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("success", totalSent > 0);
        result.put("mode", "REAL");
        result.put("total_messages", totalSent);
        result.put("total_parts", parts.size());
        result.put("recipients_count", recipients.size());
        result.put("sent_parts", new JSONArray(sentParts));

        if (totalSent > 0) {
            result.put("message", "短信已发送成功");
        } else {
            result.put("message", "短信发送失败");
        }

        return result;
    }
}
