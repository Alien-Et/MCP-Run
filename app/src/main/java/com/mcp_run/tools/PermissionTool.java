package com.mcp_run.tools;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 权限检查工具 - 检查应用权限状态
 */
public class PermissionTool implements MCPTool {
    @Override
    public String getName() {
        return "check_permission";
    }

    @Override
    public String getDescription() {
        return "检查应用是否拥有指定权限，或列出所有已授权/未授权权限。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            // mode: "list" 列出所有权限，"check" 检查单个权限
            JSONObject modeProp = new JSONObject();
            modeProp.put("type", "string");
            modeProp.put("description", "操作模式: list(列出所有) 或 check(检查单个)");
            modeProp.put("default", "check");
            props.put("mode", modeProp);
            
            // permission: 要检查的权限名
            JSONObject permProp = new JSONObject();
            permProp.put("type", "string");
            permProp.put("description", "权限名称(仅check模式需要)，如 android.permission.ACCESS_FINE_LOCATION");
            props.put("permission", permProp);
            
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("mode"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String mode = args.optString("mode", "check");
        
        if ("list".equals(mode)) {
            return listAllPermissions(context);
        } else {
            return checkSinglePermission(context, args);
        }
    }
    
    private JSONObject listAllPermissions(Context context) throws Exception {
        android.content.pm.PackageManager pm = context.getPackageManager();
        String packageName = context.getPackageName();
        
        // 获取所有请求的权限
        android.content.pm.PackageInfo pkgInfo = pm.getPackageInfo(
            packageName, android.content.pm.PackageManager.GET_PERMISSIONS);
        
        if (pkgInfo.requestedPermissions == null) {
            JSONObject r = new JSONObject();
            r.put("total", 0);
            r.put("authorized", new JSONArray());
            r.put("denied", new JSONArray());
            return r;
        }
        
        JSONArray authorized = new JSONArray();
        JSONArray denied = new JSONArray();
        
        for (String perm : pkgInfo.requestedPermissions) {
            int granted = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                granted = pm.checkPermission(perm, packageName);
            }
            
            JSONObject p = new JSONObject();
            p.put("permission", perm);
            
            if (granted == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                p.put("status", "granted");
                authorized.put(p);
            } else {
                p.put("status", "denied");
                denied.put(p);
            }
        }
        
        JSONObject r = new JSONObject();
        r.put("total", pkgInfo.requestedPermissions.length);
        r.put("authorized_count", authorized.length());
        r.put("denied_count", denied.length());
        r.put("authorized", authorized);
        r.put("denied", denied);
        return r;
    }
    
    private JSONObject checkSinglePermission(Context context, JSONObject args) throws Exception {
        String permission = args.getString("permission");
        android.content.pm.PackageManager pm = context.getPackageManager();
        int granted = pm.checkPermission(permission, context.getPackageName());
        
        JSONObject r = new JSONObject();
        r.put("permission", permission);
        r.put("granted", granted == android.content.pm.PackageManager.PERMISSION_GRANTED);
        r.put("status", granted == android.content.pm.PackageManager.PERMISSION_GRANTED ? "已授权" : "未授权");
        return r;
    }
}
