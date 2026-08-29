package com.mcp_run.tools;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.List;

/**
 * 增强版应用管理工具 - 启动/停止/强制停止/列出进程/获取前台Activity
 */
public class AppManagerTool implements MCPTool {
    @Override
    public String getName() {
        return "app_manager";
    }

    @Override
    public String getDescription() {
        return "高级应用管理。支持：列出已安装应用、获取应用详情、启动应用、强制停止应用、列出运行进程、获取前台Activity、打开应用设置、卸载应用。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject actionProp = new JSONObject();
            actionProp.put("type", "string");
            actionProp.put("description", "操作类型: list, info, launch, force_stop, list_running, top_activity, app_settings, uninstall, kill_process");
            actionProp.put("enum", new JSONArray().put("list").put("info").put("launch").put("force_stop").put("list_running").put("top_activity").put("app_settings").put("uninstall").put("kill_process"));
            props.put("action", actionProp);
            
            JSONObject pkgProp = new JSONObject();
            pkgProp.put("type", "string");
            pkgProp.put("description", "应用包名（info/launch/force_stop/app_settings/uninstall/kill_process时必填）");
            props.put("package_name", pkgProp);
            
            JSONObject filterProp = new JSONObject();
            filterProp.put("type", "string");
            filterProp.put("description", "过滤关键字（list时可选，按包名或应用名过滤）");
            props.put("filter", filterProp);
            
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("action"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String action = args.getString("action");
        PackageManager pm = context.getPackageManager();
        
        switch (action) {
            case "list": {
                String filter = args.optString("filter", "").toLowerCase();
                JSONArray apps = new JSONArray();
                
                List<PackageInfo> packages = pm.getInstalledPackages(
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);
                
                for (PackageInfo pkg : packages) {
                    String packageName = pkg.packageName;
                    String appName = pm.getApplicationLabel(pkg.applicationInfo).toString();
                    
                    if (!filter.isEmpty() && !packageName.contains(filter) 
                            && !appName.toLowerCase().contains(filter)) {
                        continue;
                    }
                    
                    JSONObject app = new JSONObject();
                    app.put("package_name", packageName);
                    app.put("app_name", appName);
                    app.put("version_name", pkg.versionName);
                    app.put("version_code", pkg.versionCode);
                    app.put("is_system", (pkg.applicationInfo.flags & 
                            ApplicationInfo.FLAG_SYSTEM) != 0);
                    app.put("uid", pkg.applicationInfo.uid);
                    app.put("install_time", pkg.firstInstallTime);
                    app.put("last_update_time", pkg.lastUpdateTime);
                    apps.put(app);
                }
                
                JSONObject result = new JSONObject();
                result.put("total", apps.length());
                result.put("apps", apps);
                return result;
            }
            
            case "info": {
                String packageName = args.getString("package_name");
                PackageInfo pkg = pm.getPackageInfo(packageName, 
                        PackageManager.GET_ACTIVITIES | PackageManager.GET_META_DATA);
                
                JSONObject result = new JSONObject();
                result.put("package_name", pkg.packageName);
                result.put("app_name", pm.getApplicationLabel(pkg.applicationInfo).toString());
                result.put("version_name", pkg.versionName);
                result.put("version_code", pkg.versionCode);
                result.put("is_system", (pkg.applicationInfo.flags & 
                        ApplicationInfo.FLAG_SYSTEM) != 0);
                result.put("uid", pkg.applicationInfo.uid);
                result.put("source_dir", pkg.applicationInfo.sourceDir);
                
                // 权限
                if (pkg.requestedPermissions != null) {
                    JSONArray perms = new JSONArray();
                    for (String perm : pkg.requestedPermissions) {
                        perms.put(perm);
                    }
                    result.put("requested_permissions", perms);
                }
                
                return result;
            }
            
            case "launch": {
                String packageName = args.getString("package_name");
                Intent intent = pm.getLaunchIntentForPackage(packageName);
                if (intent == null) {
                    throw new Exception("应用 " + packageName + " 没有可启动的入口Activity");
                }
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("package_name", packageName);
                result.put("message", "已启动应用");
                return result;
            }
            
            case "force_stop": {
                String packageName = args.getString("package_name");
                String cmd = "am force-stop " + packageName;
                execShell(cmd);
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("package_name", packageName);
                result.put("message", "已强制停止应用 " + packageName);
                return result;
            }
            
            case "list_running": {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.RunningAppProcessInfo> processes = am.getRunningAppProcesses();
                
                JSONArray processList = new JSONArray();
                if (processes != null) {
                    for (ActivityManager.RunningAppProcessInfo proc : processes) {
                        JSONObject p = new JSONObject();
                        p.put("pid", proc.pid);
                        p.put("process_name", proc.processName);
                        p.put("importance", proc.importance);
                        p.put("package_name", proc.pkgList != null ? new JSONArray(proc.pkgList) : new JSONArray());
                        processList.put(p);
                    }
                }
                
                JSONObject result = new JSONObject();
                result.put("total", processList.length());
                result.put("processes", processList);
                return result;
            }
            
            case "top_activity": {
                String output = execShell("dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp'");
                JSONObject result = new JSONObject();
                result.put("raw_output", output.trim());
                
                // 尝试解析
                if (output.contains("mCurrentFocus")) {
                    int start = output.indexOf("mCurrentFocus") + 14;
                    int end = output.indexOf("\n", start);
                    if (end > start) {
                        result.put("current_focus", output.substring(start, end).trim());
                    }
                }
                if (output.contains("mFocusedApp")) {
                    int start = output.indexOf("mFocusedApp") + 13;
                    int end = output.indexOf("\n", start);
                    if (end > start) {
                        result.put("focused_app", output.substring(start, end).trim());
                    }
                }
                return result;
            }
            
            case "app_settings": {
                String packageName = args.getString("package_name");
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + packageName));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("package_name", packageName);
                result.put("message", "已打开应用设置界面");
                return result;
            }
            
            case "uninstall": {
                String packageName = args.getString("package_name");
                Uri packageUri = Uri.parse("package:" + packageName);
                Intent intent = new Intent(Intent.ACTION_DELETE, packageUri);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("package_name", packageName);
                result.put("message", "已打开卸载界面");
                return result;
            }
            
            case "kill_process": {
                String packageName = args.getString("package_name");
                String cmd = "am force-stop " + packageName;
                execShell(cmd);
                
                JSONObject result = new JSONObject();
                result.put("success", true);
                result.put("package_name", packageName);
                result.put("message", "已通过am force-stop终止 " + packageName);
                return result;
            }
            
            default:
                throw new Exception("未知操作: " + action);
        }
    }
    
    private String execShell(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        process.waitFor();
        return output.toString();
    }
}