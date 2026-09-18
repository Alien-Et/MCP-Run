package com.mcp_run.tools;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 短信读取工具 - 读取设备短信
 * 需要 READ_SMS 权限
 */
public class SmsTool implements MCPTool {
    @Override
    public String getName() {
        return "get_sms";
    }

    @Override
    public String getDescription() {
        return "读取设备短信记录。需要 READ_SMS 权限。支持按联系人/关键词过滤。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            // address: 按发件人过滤
            JSONObject addrProp = new JSONObject();
            addrProp.put("type", "string");
            addrProp.put("description", "按发件人号码过滤（可选）");
            props.put("address", addrProp);
            
            // body: 按内容关键词过滤
            JSONObject bodyProp = new JSONObject();
            bodyProp.put("type", "string");
            bodyProp.put("description", "按短信内容关键词过滤（可选）");
            props.put("body", bodyProp);
            
            // limit: 限制返回数量
            JSONObject limitProp = new JSONObject();
            limitProp.put("type", "number");
            limitProp.put("description", "限制返回数量（默认50）");
            limitProp.put("default", 50);
            props.put("limit", limitProp);
            
            // type: 短信类型 0=接收 1=发送
            JSONObject typeProp = new JSONObject();
            typeProp.put("type", "number");
            typeProp.put("description", "短信类型: 0=接收 1=发送");
            props.put("type", typeProp);
            
            schema.put("properties", props);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.READ_SMS) 
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: READ_SMS");
            }
        }
        
        String address = args.optString("address", "");
        String body = args.optString("body", "");
        int limit = args.optInt("limit", 50);
        int type = args.optInt("type", -1);
        
        android.content.ContentResolver cr = context.getContentResolver();
        java.util.ArrayList<JSONObject> result = new java.util.ArrayList<>();
        
        try (android.database.Cursor cursor = cr.query(
                android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                new String[]{
                    android.provider.Telephony.Sms._ID,
                    android.provider.Telephony.Sms.ADDRESS,
                    android.provider.Telephony.Sms.BODY,
                    android.provider.Telephony.Sms.DATE,
                    android.provider.Telephony.Sms.TYPE
                },
                null, null, android.provider.Telephony.Sms.DATE + " DESC")) {
            
            if (cursor != null) {
                while (cursor.moveToNext() && result.size() < limit) {
                    long id = cursor.getLong(0);
                    String addr = cursor.getString(1);
                    String msgBody = cursor.getString(2);
                    long date = cursor.getLong(3);
                    int msgType = cursor.getInt(4);
                    
                    // 过滤
                    if (!address.isEmpty() && !addr.contains(address)) continue;
                    if (!body.isEmpty() && !msgBody.contains(body)) continue;
                    if (type >= 0 && msgType != type) continue;
                    
                    JSONObject sms = new JSONObject();
                    sms.put("id", id);
                    sms.put("address", addr);
                    sms.put("body", msgBody);
                    sms.put("date", date);
                    sms.put("type", msgType == 1 ? "sent" : "received");
                    
                    // 解析日期
                    java.util.Date d = new java.util.Date(date);
                    sms.put("date_formatted", d.toString());
                    
                    result.add(sms);
                }
            }
        }
        
        JSONObject r = new JSONObject();
        r.put("total", result.size());
        r.put("sms", new JSONArray(result));
        return r;
    }
}
