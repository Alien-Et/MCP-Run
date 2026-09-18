package com.mcp_run.tools;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 联系人读取工具 - 读取设备联系人信息
 * 需要 READ_CONTACTS 权限
 */
public class ContactTool implements MCPTool {
    @Override
    public String getName() {
        return "get_contacts";
    }

    @Override
    public String getDescription() {
        return "读取设备联系人列表。需要 READ_CONTACTS 权限。支持按姓名过滤。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            // filter: 按姓名过滤
            JSONObject filterProp = new JSONObject();
            filterProp.put("type", "string");
            filterProp.put("description", "按姓名关键字过滤（可选）");
            props.put("filter", filterProp);
            
            // limit: 限制返回数量
            JSONObject limitProp = new JSONObject();
            limitProp.put("type", "number");
            limitProp.put("description", "限制返回数量（默认50）");
            limitProp.put("default", 50);
            props.put("limit", limitProp);
            
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
            if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) 
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: READ_CONTACTS");
            }
        }
        
        String filter = args.optString("filter", "").toLowerCase();
        int limit = args.optInt("limit", 50);
        
        android.content.ContentResolver cr = context.getContentResolver();
        java.util.ArrayList<JSONObject> result = new java.util.ArrayList<>();
        
        android.net.Uri uri = android.provider.ContactsContract.Contacts.CONTENT_URI;
        String[] projection = {
            android.provider.ContactsContract.Contacts._ID,
            android.provider.ContactsContract.Contacts.DISPLAY_NAME,
            android.provider.ContactsContract.Contacts.HAS_PHONE_NUMBER
        };
        
        try (android.database.Cursor cursor = cr.query(uri, projection, null, null, null)) {
            if (cursor != null) {
                while (cursor.moveToNext() && result.size() < limit) {
                    String id = cursor.getString(cursor.getColumnIndexOrThrow(
                        android.provider.ContactsContract.Contacts._ID));
                    String name = cursor.getString(cursor.getColumnIndexOrThrow(
                        android.provider.ContactsContract.Contacts.DISPLAY_NAME));
                    
                    if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) {
                        continue;
                    }
                    
                    JSONObject contact = new JSONObject();
                    contact.put("id", id);
                    contact.put("name", name);
                    
                    // 获取电话
                    JSONArray phones = getPhones(context, id);
                    if (phones.length() > 0) {
                        contact.put("phones", phones);
                    }
                    
                    // 获取邮箱
                    JSONArray emails = getEmails(context, id);
                    if (emails.length() > 0) {
                        contact.put("emails", emails);
                    }
                    
                    result.add(contact);
                }
            }
        }
        
        JSONObject r = new JSONObject();
        r.put("total", result.size());
        r.put("contacts", new JSONArray(result));
        return r;
    }
    
    private JSONArray getPhones(Context context, String contactId) throws Exception {
        JSONArray phones = new JSONArray();
        android.content.ContentResolver cr = context.getContentResolver();
        android.net.Uri phoneUri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        
        try (android.database.Cursor cursor = cr.query(phoneUri, 
                new String[]{android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER,
                            android.provider.ContactsContract.CommonDataKinds.Phone.TYPE},
                android.provider.ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=?",
                new String[]{contactId}, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject phone = new JSONObject();
                    phone.put("number", cursor.getString(0));
                    int type = cursor.getInt(1);
                    phone.put("type", getTypeName(type));
                    phones.put(phone);
                }
            }
        }
        return phones;
    }
    
    private JSONArray getEmails(Context context, String contactId) throws Exception {
        JSONArray emails = new JSONArray();
        android.content.ContentResolver cr = context.getContentResolver();
        android.net.Uri emailUri = android.provider.ContactsContract.CommonDataKinds.Email.CONTENT_URI;
        
        try (android.database.Cursor cursor = cr.query(emailUri, 
                new String[]{android.provider.ContactsContract.CommonDataKinds.Email.ADDRESS,
                            android.provider.ContactsContract.CommonDataKinds.Email.TYPE},
                android.provider.ContactsContract.CommonDataKinds.Email.CONTACT_ID + "=?",
                new String[]{contactId}, null)) {
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    JSONObject email = new JSONObject();
                    email.put("address", cursor.getString(0));
                    int type = cursor.getInt(1);
                    email.put("type", getTypeName(type));
                    emails.put(email);
                }
            }
        }
        return emails;
    }
    
    private String getTypeName(int type) {
        switch (type) {
            case android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_HOME: return "home";
            case android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_WORK: return "work";
            case android.provider.ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE: return "mobile";
            default: return "other";
        }
    }
}
