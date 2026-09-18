package com.mcp_run.tools;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 短信发送工具 - 发送短信
 * 需要 SEND_SMS 权限
 */
public class SendSmsTool implements MCPTool {
    @Override
    public String getName() {
        return "send_sms";
    }

    @Override
    public String getDescription() {
        return "发送短信到指定号码。需要 SEND_SMS 权限。支持长短信自动分割。";
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

            // addresses: 多个收件人（可选，与 address 二选一）
            JSONObject addrsProp = new JSONObject();
            addrsProp.put("type", "array");
            addrsProp.put("description", "多个收件人号码数组（可选）");
            addrsProp.put("items", new JSONObject().put("type", "string"));
            props.put("addresses", addrsProp);

            schema.put("properties", props);
            schema.put("required", new JSONArray().put("address").put("message"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.SEND_SMS)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: SEND_SMS");
            }
        }

        String address = args.getString("address");
        String message = args.getString("message");
        JSONArray addressesArray = args.optJSONArray("addresses");

        // 获取所有收件人
        java.util.List<String> recipients = new java.util.ArrayList<>();
        recipients.add(address);
        if (addressesArray != null) {
            for (int i = 0; i < addressesArray.length(); i++) {
                recipients.add(addressesArray.getString(i));
            }
        }

        // 分割长短信
        SmsManager smsManager = SmsManager.getDefault();
        java.util.List<String> parts = smsManager.divideMessage(message);

        java.util.List<String> sentParts = new java.util.ArrayList<>();
        int totalSent = 0;

        for (String recipient : recipients) {
            // 确保 recipient 不为空
            if (recipient == null || recipient.trim().isEmpty()) {
                continue;
            }
            for (String part : parts) {
                try {
                    smsManager.sendTextMessage(recipient, null, part, null, null);
                    sentParts.add(part);
                    totalSent++;
                } catch (Exception e) {
                    // 记录失败的消息
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("success", totalSent > 0);
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
