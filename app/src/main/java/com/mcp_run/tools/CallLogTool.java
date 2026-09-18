package com.mcp_run.tools;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 通话记录读取工具 - 读取通话记录
 * 需要 READ_CALL_LOG 权限
 */
public class CallLogTool implements MCPTool {
    @Override
    public String getName() {
        return "get_call_log";
    }

    @Override
    public String getDescription() {
        return "读取设备通话记录。需要 READ_CALL_LOG 权限。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            // limit: 限制返回数量
            JSONObject limitProp = new JSONObject();
            limitProp.put("type", "number");
            limitProp.put("description", "限制返回数量（默认50）");
            limitProp.put("default", 50);
            props.put("limit", limitProp);
            
            // type: 通话类型 1=接入 2=拨出 3=未接
            JSONObject typeProp = new JSONObject();
            typeProp.put("type", "number");
            typeProp.put("description", "过滤通话类型: 1=接入 2=拨出 3=未接");
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
            if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) 
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: READ_CALL_LOG");
            }
        }
        
        int limit = args.optInt("limit", 50);
        int typeFilter = args.optInt("type", -1);
        
        android.content.ContentResolver cr = context.getContentResolver();
        java.util.ArrayList<JSONObject> result = new java.util.ArrayList<>();
        
        try (android.database.Cursor cursor = cr.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                new String[]{
                    android.provider.CallLog.Calls._ID,
                    android.provider.CallLog.Calls.NUMBER,
                    android.provider.CallLog.Calls.TYPE,
                    android.provider.CallLog.Calls.DATE,
                    android.provider.CallLog.Calls.DURATION,
                    android.provider.CallLog.Calls.CACHED_NAME
                },
                null, null, android.provider.CallLog.Calls.DATE + " DESC")) {
            
            if (cursor != null) {
                while (cursor.moveToNext() && result.size() < limit) {
                    int type = cursor.getInt(2);
                    
                    // 过滤
                    if (typeFilter >= 0 && type != typeFilter) continue;
                    
                    JSONObject call = new JSONObject();
                    call.put("id", cursor.getLong(0));
                    call.put("number", cursor.getString(1));
                    call.put("type", getTypeName(type));
                    call.put("date", cursor.getLong(3));
                    call.put("duration_seconds", cursor.getInt(4));
                    call.put("contact_name", cursor.getString(5));
                    
                    // 格式化日期
                    java.util.Date d = new java.util.Date(cursor.getLong(3));
                    call.put("date_formatted", d.toString());
                    
                    result.add(call);
                }
            }
        }
        
        JSONObject r = new JSONObject();
        r.put("total", result.size());
        r.put("calls", new JSONArray(result));
        return r;
    }
    
    private String getTypeName(int type) {
        switch (type) {
            case android.provider.CallLog.Calls.INCOMING_TYPE: return "incoming";
            case android.provider.CallLog.Calls.OUTGOING_TYPE: return "outgoing";
            case android.provider.CallLog.Calls.MISSED_TYPE: return "missed";
            default: return "unknown";
        }
    }
}
