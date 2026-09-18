package com.mcp_run.tools;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Telephony;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 短信删除工具 - 删除短信
 * 需要 READ_SMS 权限
 */
public class DeleteSmsTool implements MCPTool {
    @Override
    public String getName() {
        return "delete_sms";
    }

    @Override
    public String getDescription() {
        return "删除指定短信或按条件删除短信。支持按ID、号码、日期范围删除。需要 READ_SMS 权限。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            // id: 单条短信ID
            JSONObject idProp = new JSONObject();
            idProp.put("type", "number");
            idProp.put("description", "短信ID（与 ids/phone 二选一）");
            props.put("id", idProp);

            // ids: 多条短信ID数组
            JSONObject idsProp = new JSONObject();
            idsProp.put("type", "array");
            idsProp.put("description", "短信ID数组（与 id/phone 二选一）");
            idsProp.put("items", new JSONObject().put("type", "number"));
            props.put("ids", idsProp);

            // phone: 按号码删除所有短信
            JSONObject phoneProp = new JSONObject();
            phoneProp.put("type", "string");
            phoneProp.put("description", "按号码删除该联系人的所有短信");
            props.put("phone", phoneProp);

            // type: 短信类型 0=收件箱 1=发件箱
            JSONObject typeProp = new JSONObject();
            typeProp.put("type", "number");
            typeProp.put("description", "短信类型: 0=收件箱 1=发件箱");
            typeProp.put("default", 0);
            props.put("type", typeProp);

            // days_ago: 删除N天前的短信
            JSONObject daysProp = new JSONObject();
            daysProp.put("type", "number");
            daysProp.put("description", "删除N天前的短信（可选，配合 phone 使用）");
            props.put("days_ago", daysProp);

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

        android.content.ContentResolver cr = context.getContentResolver();
        int deletedCount = 0;

        // 方法1: 按ID删除
        if (args.has("id")) {
            long id = args.getLong("id");
            android.net.Uri uri = Telephony.Sms.CONTENT_URI;
            String selection = Telephony.Sms._ID + "=?";
            String[] selectionArgs = {String.valueOf(id)};
            deletedCount = cr.delete(uri, selection, selectionArgs);
        }
        // 方法2: 按ID数组删除
        else if (args.has("ids")) {
            JSONArray idsArray = args.getJSONArray("ids");
            for (int i = 0; i < idsArray.length(); i++) {
                long id = idsArray.getLong(i);
                android.net.Uri uri = Telephony.Sms.CONTENT_URI;
                String selection = Telephony.Sms._ID + "=?";
                String[] selectionArgs = {String.valueOf(id)};
                deletedCount += cr.delete(uri, selection, selectionArgs);
            }
        }
        // 方法3: 按号码删除
        else if (args.has("phone")) {
            String phone = args.getString("phone");
            int type = args.optInt("type", 0);
            long daysAgo = args.optLong("days_ago", 0);

            android.net.Uri uri = Telephony.Sms.CONTENT_URI;
            StringBuilder selection = new StringBuilder(
                Telephony.Sms.ADDRESS + " LIKE ? AND " + Telephony.Sms.TYPE + " = ?");
            java.util.ArrayList<String> selectionArgs = new java.util.ArrayList<>();
            selectionArgs.add("%" + phone + "%");
            selectionArgs.add(String.valueOf(type));

            if (daysAgo > 0) {
                long cutoffTime = System.currentTimeMillis() - (daysAgo * 24 * 60 * 60 * 1000L);
                selection.append(" AND " + Telephony.Sms.DATE + " < ?");
                selectionArgs.add(String.valueOf(cutoffTime));
            }

            deletedCount = cr.delete(uri, selection.toString(),
                    selectionArgs.toArray(new String[0]));
        }
        else {
            throw new Exception("请提供 id、ids 或 phone 参数");
        }

        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("deleted_count", deletedCount);
        result.put("message", "已删除 " + deletedCount + " 条短信");
        return result;
    }
}
