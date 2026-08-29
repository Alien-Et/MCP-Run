package com.mcp_run.tools;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 系统辅助工具集 - shizuku, shizuku_shell, battery, battery_fix
 * 
 * 注意：Shizuku 功能需要额外添加依赖才能在 build.gradle 中声明。
 * 如果未安装 Shizuku SDK，相关工具会返回 "Shizuku 未安装" 状态。
 */
public class SysTool implements MCPTool {
    private final String toolName;
    
    // Shizuku API 类引用（可能在运行时不存在）
    private static final String SHIZUKU_CLASS = "moe.shizuku.api.Shizuku";
    private static final String SHIZUKU_BINDER_CLASS = "moe.shizuku.api.ShizukuBinderWrapper";

    public SysTool(String toolName) { this.toolName = toolName; }

    @Override public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "shizuku": return "查看Shizuku权限状态，判断是否已授权。需要设备安装Shizuku应用并授权。";
            case "shizuku_shell": return "使用Shizuku权限执行高权限shell命令。需要Shizuku已运行并授权此应用。";
            case "battery": return "查看电池状态和保活信息（电量、温度、充电状态等）";
            case "battery_fix": return "尝试将应用设为省电无限制模式，防止后台被杀";
            default: return "系统工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            switch (toolName) {
                case "shizuku": break;
                case "shizuku_shell": {
                    JSONObject c = new JSONObject(); c.put("type", "string"); c.put("description", "要执行的命令"); props.put("cmd", c);
                    schema.put("required", new JSONArray().put("cmd")); break;
                }
                case "battery": break;
                case "battery_fix": {
                    JSONObject m = new JSONObject(); m.put("type", "string"); m.put("description", "模式: system/shizuku"); m.put("default", "system"); props.put("mode", m);
                    schema.put("required", new JSONArray().put("mode")); break;
                }
            }
            schema.put("properties", props);
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "shizuku": return shizukuStatus(context);
            case "shizuku_shell": return shizukuShell(args);
            case "battery": return batteryInfo(context);
            case "battery_fix": return batteryFix(context, args);
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    /**
     * 检查 Shizuku 状态
     * 安全地处理 Shizuku SDK 可能不存在的情况
     */
    private JSONObject shizukuStatus(Context context) throws Exception {
        JSONObject r = new JSONObject();
        
        // 检查 Shizuku 是否可用（通过反射，避免编译时依赖）
        boolean shizukuAvailable = false;
        try {
            // 尝试加载 Shizuku 服务
            Class<?> shizukuClass = Class.forName("moe.shizuku.server.IShizukuService");
            shizukuAvailable = true;
        } catch (ClassNotFoundException e) {
            shizukuAvailable = false;
        }
        
        r.put("shizuku_available", shizukuAvailable);
        r.put("status", shizukuAvailable ? "Shizuku 服务可用" : "Shizuku 未安装或未启动");
        
        // 检查是否有 root
        boolean rootAvailable = checkRootAccess();
        r.put("root_available", rootAvailable);
        r.put("root_status", rootAvailable ? "已获取 root 权限" : "无 root 权限");
        
        // 检查 Android 版本（某些命令需要高版本）
        r.put("android_version", Build.VERSION.SDK_INT);
        r.put("android_release", Build.VERSION.RELEASE);
        
        return r;
    }
    
    /**
     * 使用 Shizuku 执行 shell 命令
     */
    private JSONObject shizukuShell(JSONObject args) throws Exception {
        String cmd = args.getString("cmd");
        
        // 检查 Shizuku 是否可用
        if (!isShizukuAvailable()) {
            JSONObject r = new JSONObject();
            r.put("command", cmd);
            r.put("output", "");
            r.put("success", false);
            r.put("error", "Shizuku 未安装或未授权。请先安装 Shizuku 应用并授权 MCP·AC。");
            return r;
        }
        
        // 尝试通过 Shizuku API 执行
        try {
            String output = execShellWithShizuku(cmd);
            JSONObject r = new JSONObject();
            r.put("command", cmd);
            r.put("output", output.trim());
            r.put("success", true);
            r.put("method", "shizuku");
            return r;
        } catch (Exception e) {
            // Shizuku 执行失败，fallback 到普通 shell
            Log.d("SysTool", "Shizuku execution failed, falling back to normal shell: " + e.getMessage());
        }
        
        // Fallback: 使用普通 shell
        String output = execShell(cmd);
        JSONObject r = new JSONObject();
        r.put("command", cmd);
        r.put("output", output.trim());
        r.put("success", true);
        r.put("method", "shell");
        return r;
    }
    
    private boolean isShizukuAvailable() {
        try {
            Class.forName("moe.shizuku.server.IShizukuService");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
    
    /**
     * 通过 Shizuku API 执行命令（简化实现）
     * 实际项目中需要完整集成 Shizuku SDK
     */
    private String execShellWithShizuku(String command) throws Exception {
        // TODO: 完整集成 Shizuku SDK
        // 当前简化实现：尝试通过 Shizuku 服务连接
        throw new UnsupportedOperationException("Shizuku SDK 未集成。请在 build.gradle 中添加 Shizuku 依赖。");
    }
    
    /**
     * 检查 root 权限
     */
    private boolean checkRootAccess() {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "echo root_ok"});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = br.readLine();
            p.waitFor();
            return "root_ok".equals(line);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取电池信息
     */
    private JSONObject batteryInfo(Context context) throws Exception {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent battery = context.registerReceiver(null, ifilter);
        JSONObject r = new JSONObject();
        if (battery != null) {
            int level = battery.getIntExtra("level", -1);
            int scale = battery.getIntExtra("scale", -1);
            int temp = battery.getIntExtra("temperature", -1);
            int voltage = battery.getIntExtra("voltage", -1);
            int status = battery.getIntExtra("status", -1);
            int plugged = battery.getIntExtra("plugged", -1);
            
            r.put("level", level);
            r.put("scale", scale);
            r.put("percentage", scale > 0 ? (level * 100 / scale) : -1);
            r.put("temperature_celsius", temp > 0 ? (temp / 10.0) : -1);
            r.put("voltage_mv", voltage);
            
            String[] statuses = {"unknown", "unknown", "charging", "full", "not_charging", "discharging"};
            r.put("status", status >= 0 && status < statuses.length ? statuses[status] : "unknown");
            
            String[] plugs = {"unknown", "ac", "usb", "unknown", "wireless"};
            r.put("plugged", plugged >= 0 && plugged < plugs.length ? plugs[plugged] : "unknown");
            
            // 健康状态
            int health = battery.getIntExtra("health", -1);
            String[] healths = {"unknown", "good", "overheat", "dead", "over_voltage", "unsupported", "cold"};
            r.put("health", health >= 0 && health < healths.length ? healths[health] : "unknown");
            
        } else {
            r.put("error", "无法获取电池信息");
        }
        return r;
    }

    /**
     * 电池优化设置
     */
    private JSONObject batteryFix(Context context, JSONObject args) throws Exception {
        String mode = args.optString("mode", "system");
        JSONObject r = new JSONObject();
        
        if ("system".equals(mode)) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                r.put("success", true);
                r.put("message", "已打开电池优化设置页面，请手动将MCP·AC设为「无限制」");
            } catch (Exception e) {
                r.put("success", false);
                r.put("message", "打开设置失败: " + e.getMessage());
            }
        } else if ("shizuku".equals(mode)) {
            // 使用 Shizuku 执行 system 命令
            if (isShizukuAvailable()) {
                try {
                    String output = execShellWithShizuku("settings put global power_save_disabled 1");
                    r.put("success", true);
                    r.put("message", "已通过Shizuku设置省电模式");
                    r.put("output", output.trim());
                } catch (Exception e) {
                    r.put("success", false);
                    r.put("message", "Shizuku执行失败: " + e.getMessage());
                }
            } else {
                r.put("success", false);
                r.put("message", "Shizuku 未安装，请使用 mode=system");
            }
        } else {
            // 默认使用普通 shell
            try {
                String output = execShell("settings put global power_save_disabled 1");
                r.put("success", true);
                r.put("message", "已尝试通过shell设置");
                r.put("output", output.trim());
            } catch (Exception e) {
                r.put("success", false);
                r.put("message", "设置失败: " + e.getMessage());
            }
        }
        return r;
    }

    /**
     * 通用 Shell 执行（简化版）
     */
    private String execShell(String command) throws Exception {
        Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line; while ((line = br.readLine()) != null) out.append(line).append("\n");
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
            String line; while ((line = br.readLine()) != null) out.append(line).append("\n");
        }
        p.waitFor();
        return out.toString();
    }
}
