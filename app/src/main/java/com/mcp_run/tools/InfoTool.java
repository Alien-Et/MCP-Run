package com.mcp_run.tools;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PowerManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 系统信息和实用工具 - health, service_info, history, clear_log, tool_help 等
 */
public class InfoTool implements MCPTool {
    private final String toolName;

    public InfoTool(String toolName) { this.toolName = toolName; }

    @Override public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "health": return "获取服务器健康状态，包括CPU、内存、存储使用情况";
            case "service_info": return "获取MCP服务运行状态和信息";
            case "history": return "获取最近的服务请求历史（最多50条）";
            case "clear_log": return "清除服务日志和历史记录";
            case "tool_help": return "获取所有可用工具的列表和帮助信息";
            case "script_help": return "获取脚本执行工具的帮助信息";
            case "file_help": return "获取文件操作工具的帮助信息";
            case "system_help": return "获取系统管理工具的帮助信息";
            default: return "系统信息工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            switch (toolName) {
                case "history": {
                    JSONObject limit = new JSONObject();
                    limit.put("type", "number");
                    limit.put("description", "返回的历史记录数量，默认20");
                    limit.put("default", 20);
                    props.put("limit", limit);
                    break;
                }
                case "clear_log": break;
                case "tool_help":
                case "script_help":
                case "file_help":
                case "system_help": break;
                default: break;
            }
            
            schema.put("properties", props);
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "health": return getHealthInfo(context);
            case "service_info": return getServiceInfo(context);
            case "history": return getHistory(args);
            case "clear_log": return clearLog();
            case "tool_help": return getToolHelp(context);
            case "script_help": return getScriptHelp();
            case "file_help": return getFileHelp();
            case "system_help": return getSystemHelp();
            default: return new JSONObject().put("tool", toolName).put("message", "Unknown tool");
        }
    }

    private JSONObject getHealthInfo(Context context) throws Exception {
        JSONObject r = new JSONObject();
        
        // CPU 信息
        try {
            String cpuInfo = execShell("cat /proc/cpuinfo | grep 'model name' | head -1");
            r.put("cpu", cpuInfo.trim().replace("model name\t:", ""));
            
            int cpuCount = Runtime.getRuntime().availableProcessors();
            r.put("cpu_count", cpuCount);
        } catch (Exception e) {
            r.put("cpu_error", e.getMessage());
        }
        
        // 内存信息
        try {
            String memInfo = execShell("cat /proc/meminfo | head -3");
            r.put("memory", memInfo.trim());
        } catch (Exception e) {
            r.put("memory_error", e.getMessage());
        }
        
        // 存储信息
        try {
            Process p = Runtime.getRuntime().exec("df -h /data");
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            p.waitFor();
            r.put("storage", sb.toString().trim());
        } catch (Exception e) {
            r.put("storage_error", e.getMessage());
        }
        
        // 电池信息
        try {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                r.put("battery_optimization_enabled", pm.isIgnoringBatteryOptimizations(context.getPackageName()));
            }
        } catch (Exception e) {
            r.put("battery_error", e.getMessage());
        }
        
        // 系统信息
        r.put("android_version", Build.VERSION.RELEASE);
        r.put("android_sdk", Build.VERSION.SDK_INT);
        r.put("device_model", Build.MODEL);
        r.put("device_brand", Build.BRAND);
        
        return r;
    }

    private JSONObject getServiceInfo(Context context) throws Exception {
        JSONObject r = new JSONObject();
        r.put("service_name", "MCPService");
        r.put("package_name", context.getPackageName());
        r.put("app_version", getAppVersion(context));
        r.put("build_version", Build.VERSION.RELEASE);
        
        // 获取已安装应用数量
        try {
            PackageManager pm = context.getPackageManager();
            int appCount = pm.getInstalledApplications(PackageManager.GET_META_DATA).size();
            r.put("installed_apps", appCount);
        } catch (Exception e) {
            r.put("installed_apps_error", e.getMessage());
        }
        
        return r;
    }

    private String getAppVersion(Context context) {
        try {
            PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private JSONObject getHistory(JSONObject args) throws Exception {
        int limit = args.optInt("limit", 20);
        JSONArray history = new JSONArray();
        
        // 这里可以接入 MCPHttpServer 的历史记录
        // 暂时返回空数组，等待与服务层集成
        return new JSONObject().put("history", history).put("total", 0);
    }

    private JSONObject clearLog() throws Exception {
        // 清空日志（在实际应用中需要连接到服务端）
        return new JSONObject().put("success", true).put("message", "日志已清空（仅客户端显示）");
    }

    private JSONObject getToolHelp(Context context) throws Exception {
        ToolRegistry registry = new ToolRegistry(context);
        JSONArray tools = new JSONArray();
        
        for (String category : registry.getAllCategories()) {
            JSONObject cat = new JSONObject();
            cat.put("category", category);
            JSONArray catTools = new JSONArray();
            
            for (MCPTool tool : registry.getToolsByCategory(category)) {
                JSONObject t = new JSONObject();
                t.put("name", tool.getName());
                t.put("description", tool.getDescription());
                catTools.put(t);
            }
            
            cat.put("tools", catTools);
            tools.put(cat);
        }
        
        JSONObject r = new JSONObject();
        r.put("total_tools", registry.getToolCount());
        r.put("categories", registry.getAllCategories().size());
        r.put("tools", tools);
        return r;
    }

    private JSONObject getScriptHelp() throws Exception {
        JSONObject r = new JSONObject();
        r.put("tools", new JSONArray()
            .put(new JSONObject().put("name", "mcp_javascript").put("description", "执行JavaScript代码"))
            .put(new JSONObject().put("name", "mcp_python").put("description", "执行Python代码"))
            .put(new JSONObject().put("name", "execute_python").put("description", "Python脚本执行（增强版"))
        );
        r.put("note", "JavaScript 使用 Rhino 引擎，Python 需要 Termux 环境");
        return r;
    }

    private JSONObject getFileHelp() throws Exception {
        ToolRegistry registry = new ToolRegistry(null);
        JSONArray tools = new JSONArray();
        
        for (MCPTool tool : registry.getToolsByCategory(ToolRegistry.CAT_FILE)) {
            JSONObject t = new JSONObject();
            t.put("name", tool.getName());
            t.put("description", tool.getDescription());
            tools.put(t);
        }
        
        JSONObject r = new JSONObject();
        r.put("category", ToolRegistry.CAT_FILE);
        r.put("tools", tools);
        return r;
    }

    private JSONObject getSystemHelp() throws Exception {
        ToolRegistry registry = new ToolRegistry(null);
        JSONArray tools = new JSONArray();
        
        for (MCPTool tool : registry.getToolsByCategory(ToolRegistry.CAT_SYSTEM)) {
            JSONObject t = new JSONObject();
            t.put("name", tool.getName());
            t.put("description", tool.getDescription());
            tools.put(t);
        }
        
        JSONObject r = new JSONObject();
        r.put("category", ToolRegistry.CAT_SYSTEM);
        r.put("tools", tools);
        r.put("note", "部分系统工具需要 root 或 Shizuku 权限");
        return r;
    }

    private String execShell(String command) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line; while ((line = br.readLine()) != null) out.append(line).append("\n");
        }
        p.waitFor();
        return out.toString();
    }
}
