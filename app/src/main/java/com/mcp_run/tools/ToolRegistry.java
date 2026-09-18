package com.mcp_run.tools;

import android.content.Context;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表 - 管理所有可用的MCP工具
 * 75+ 个工具，10个分类
 * 
 * Bug 修复:
 * 1. getCategoryIcon() 和 getToolIcon() 返回正确的图标
 * 2. 媒体工具类已正确注册
 */
public class ToolRegistry {
    private final Map<String, MCPTool> tools = new HashMap<>();
    private final Map<String, String> toolCategories = new HashMap<>();
    private final FileSession session;

    public static final String CAT_FILE = "文件操作";
    public static final String CAT_SYSTEM = "系统管理";
    public static final String CAT_DEVICE = "设备信息";
    public static final String CAT_APP = "应用管理";
    public static final String CAT_SCRIPT = "脚本执行";
    public static final String CAT_COMMUNICATION = "通讯交互";
    public static final String CAT_NETWORK = "网络请求";
    public static final String CAT_UTILITY = "实用工具";
    public static final String CAT_PERMISSION = "权限管理";
    public static final String CAT_MEDIA = "媒体信息";

    // 工具图标映射
    private static final Map<String, String> TOOL_ICONS = new HashMap<>();
    static {
        // 文件工具
        TOOL_ICONS.put("pwd", "📁");
        TOOL_ICONS.put("cd", "📂");
        TOOL_ICONS.put("mkdir", "📁");
        TOOL_ICONS.put("touch", "📄");
        TOOL_ICONS.put("copy", "📋");
        TOOL_ICONS.put("delete", "🗑️");
        TOOL_ICONS.put("read", "📖");
        TOOL_ICONS.put("du", "📊");
        TOOL_ICONS.put("hash", "🔑");
        TOOL_ICONS.put("write", "✏️");
        TOOL_ICONS.put("ls", "📋");
        // 系统工具
        TOOL_ICONS.put("shell", "⚙️");
        TOOL_ICONS.put("system_control", "🔧");
        // 设备工具
        TOOL_ICONS.put("device_info", "📱");
        TOOL_ICONS.put("screenshot", "📸");
        // 应用工具
        TOOL_ICONS.put("app_manager", "📦");
        TOOL_ICONS.put("install_apk", "📲");
        TOOL_ICONS.put("apk_decompile", "🔍");
        TOOL_ICONS.put("apk_recompile", "🔧");
        TOOL_ICONS.put("apk_info", "ℹ️");
        TOOL_ICONS.put("apk_list_files", "📋");
        TOOL_ICONS.put("apk_extract", "📂");
        TOOL_ICONS.put("apk_sign", "✍️");
        TOOL_ICONS.put("apk_search", "🔎");
        TOOL_ICONS.put("apk_permission", "🔐");
        TOOL_ICONS.put("apk_install", "📲");
        // 脚本工具
        TOOL_ICONS.put("mcp_javascript", "📜");
        TOOL_ICONS.put("mcp_python", "🐍");
        // 通讯工具
        TOOL_ICONS.put("clipboard", "📋");
        TOOL_ICONS.put("notification", "🔔");
        // 网络工具
        TOOL_ICONS.put("http_get", "🌐");
        TOOL_ICONS.put("http_post", "🌐");
        // 媒体工具
        TOOL_ICONS.put("contact", "👤");
        TOOL_ICONS.put("sms", "💬");
        TOOL_ICONS.put("call_log", "📞");
        TOOL_ICONS.put("send_sms", "📤");
        TOOL_ICONS.put("delete_sms", "🗑️");
        TOOL_ICONS.put("get_step_count", "🏃");
        TOOL_ICONS.put("get_distance", "📏");
        TOOL_ICONS.put("get_calories", "🔥");
        TOOL_ICONS.put("get_heart_rate", "❤️");
        TOOL_ICONS.put("get_sleep", "😴");
        TOOL_ICONS.put("get_activity", "💪");
        TOOL_ICONS.put("chmod", "🔐");
        TOOL_ICONS.put("chmod_batch", "🔐");
        TOOL_ICONS.put("zip", "📦");
        TOOL_ICONS.put("unzip", "📂");
        TOOL_ICONS.put("tar", "📦");
        TOOL_ICONS.put("gzip", "🗜️");
        TOOL_ICONS.put("rename_batch", "🔄");
        TOOL_ICONS.put("delete_batch", "🗑️");
        TOOL_ICONS.put("copy_batch", "📋");
        TOOL_ICONS.put("file_count", "📊");
        TOOL_ICONS.put("file_size_top", "🏆");
        TOOL_ICONS.put("file_duplicate", "🔍");
        TOOL_ICONS.put("file_empty", "🔍");
        TOOL_ICONS.put("file_search_content", "🔎");
        TOOL_ICONS.put("file_search_regex", "🔎");
        TOOL_ICONS.put("file_search_recent", "🆕");
        TOOL_ICONS.put("file_watch", "👁️");
        TOOL_ICONS.put("file_tail_live", "📡");
        TOOL_ICONS.put("file_encrypt", "🔒");
        TOOL_ICONS.put("file_decrypt", "🔓");
        TOOL_ICONS.put("file_checksum", "✅");
        TOOL_ICONS.put("rename_exif", "📅");
        TOOL_ICONS.put("rename_video", "🎬");
    }

    public ToolRegistry(Context context) {
        this.session = new FileSession();
        registerAll(context);
    }

    public FileSession getSession() { return session; }

    private void registerAll(Context context) {
        // ===== 文件系统管理 =====
        register(new FileSystemTool("pwd", session), CAT_FILE);
        register(new FileSystemTool("cd", session), CAT_FILE);
        register(new FileSystemTool("set_root", session), CAT_FILE);
        register(new FileSystemTool("exists", session), CAT_FILE);
        register(new FileSystemTool("stat", session), CAT_FILE);
        register(new FileSystemTool("ls", session), CAT_FILE);
        register(new FileSystemTool("list_all", session), CAT_FILE);
        register(new FileSystemTool("tree", session), CAT_FILE);
        register(new FileSystemTool("find", session), CAT_FILE);
        register(new FileSystemTool("grep", session), CAT_FILE);
        register(new FileSystemTool("mkdir", session), CAT_FILE);
        register(new FileSystemTool("touch", session), CAT_FILE);
        register(new FileSystemTool("empty", session), CAT_FILE);
        register(new FileSystemTool("copy", session), CAT_FILE);
        register(new FileSystemTool("rename", session), CAT_FILE);
        register(new FileSystemTool("delete", session), CAT_FILE);
        register(new FileSystemTool("edit", session), CAT_FILE);
        register(new FileSystemTool("du", session), CAT_FILE);
        register(new FileSystemTool("hash", session), CAT_FILE);

        // ===== 文件内容读写 =====
        register(new FileContentTool("read", session), CAT_FILE);
        register(new FileContentTool("head", session), CAT_FILE);
        register(new FileContentTool("tail", session), CAT_FILE);
        register(new FileContentTool("read_lines", session), CAT_FILE);
        register(new FileContentTool("batch_read", session), CAT_FILE);
        register(new FileContentTool("read_base64", session), CAT_FILE);
        register(new FileContentTool("write", session), CAT_FILE);
        register(new FileContentTool("append", session), CAT_FILE);
        register(new FileContentTool("write_base64", session), CAT_FILE);
        register(new FileContentTool("compare_files", session), CAT_FILE);

        // ===== 文件扩展工具 =====
        register(new FileExtraTool("chmod", session), CAT_FILE);
        register(new FileExtraTool("chmod_batch", session), CAT_FILE);
        register(new FileExtraTool("zip", session), CAT_FILE);
        register(new FileExtraTool("unzip", session), CAT_FILE);
        register(new FileExtraTool("tar", session), CAT_FILE);
        register(new FileExtraTool("gzip", session), CAT_FILE);
        register(new FileExtraTool("rename_batch", session), CAT_FILE);
        register(new FileExtraTool("delete_batch", session), CAT_FILE);
        register(new FileExtraTool("copy_batch", session), CAT_FILE);
        register(new FileExtraTool("file_count", session), CAT_FILE);
        register(new FileExtraTool("file_size_top", session), CAT_FILE);
        register(new FileExtraTool("file_duplicate", session), CAT_FILE);
        register(new FileExtraTool("file_empty", session), CAT_FILE);
        register(new FileExtraTool("file_search_content", session), CAT_FILE);
        register(new FileExtraTool("file_search_regex", session), CAT_FILE);
        register(new FileExtraTool("file_search_recent", session), CAT_FILE);
        register(new FileExtraTool("file_watch", session), CAT_FILE);
        register(new FileExtraTool("file_tail_live", session), CAT_FILE);
        register(new FileExtraTool("file_encrypt", session), CAT_FILE);
        register(new FileExtraTool("file_decrypt", session), CAT_FILE);
        register(new FileExtraTool("file_checksum", session), CAT_FILE);
        register(new FileExtraTool("rename_exif", session), CAT_FILE);
        register(new FileExtraTool("rename_video", session), CAT_FILE);

        // ===== 系统管理 =====
        register(new ShellTool(), CAT_SYSTEM);
        register(new SystemControlTool(), CAT_SYSTEM);
        register(new SystemControlEnhancedTool(), CAT_SYSTEM);
        
        // Shizuku 工具
        register(new SysTool("shizuku"), CAT_SYSTEM);
        register(new SysTool("battery"), CAT_SYSTEM);
        register(new SysTool("battery_fix"), CAT_SYSTEM);

        // ===== 设备信息 =====
        register(new DeviceInfoTool(), CAT_DEVICE);
        register(new ScreenshotTool(), CAT_DEVICE);
        register(new ImageTool("image_info"), CAT_DEVICE);
        register(new ImageTool("image_resize"), CAT_DEVICE);
        register(new ImageTool("image_convert"), CAT_DEVICE);
        register(new ImageTool("image_crop"), CAT_DEVICE);
        register(new ImageTool("image_rotate"), CAT_DEVICE);
        register(new ImageTool("image_to_base64"), CAT_DEVICE);
        register(new ImageTool("base64_to_image"), CAT_DEVICE);

        // ===== 应用管理 =====
        register(new AppManagerTool(), CAT_APP);
        register(new ApkInstallTool(), CAT_APP);
        register(new ApkTool("apk_decompile", context), CAT_APP);
        register(new ApkTool("apk_recompile", context), CAT_APP);
        register(new ApkTool("apk_info", context), CAT_APP);
        register(new ApkTool("apk_list_files", context), CAT_APP);
        register(new ApkTool("apk_extract", context), CAT_APP);
        register(new ApkTool("apk_sign", context), CAT_APP);
        register(new ApkTool("apk_search", context), CAT_APP);
        register(new ApkTool("apk_permission", context), CAT_APP);

        // ===== 脚本执行 =====
        register(new ScriptTool("mcp_javascript"), CAT_SCRIPT);
        register(new ScriptTool("mcp_python"), CAT_SCRIPT);

        // ===== 通讯交互 =====
        register(new ClipboardTool(), CAT_COMMUNICATION);
        register(new NotificationTool(), CAT_COMMUNICATION);

        // ===== 网络请求 =====
        register(new HttpTool("http_get"), CAT_NETWORK);
        register(new HttpTool("http_post"), CAT_NETWORK);
        register(new HttpTool("http_put"), CAT_NETWORK);
        register(new HttpTool("http_delete"), CAT_NETWORK);
        register(new HttpTool("http_json"), CAT_NETWORK);
        register(new HttpTool("download_text"), CAT_NETWORK);
        register(new HttpTool("download_file"), CAT_NETWORK);
        register(new NetworkInfoTool(), CAT_NETWORK);
        register(new WifiScanTool(), CAT_NETWORK);

        // ===== 权限管理 =====
        register(new PermissionTool(), CAT_PERMISSION);

        // ===== 媒体信息 =====
        register(new ContactTool(), CAT_MEDIA);
        register(new SmsTool(), CAT_MEDIA);
        register(new CallLogTool(), CAT_MEDIA);
        register(new SendSmsTool(), CAT_MEDIA);
        register(new DeleteSmsTool(), CAT_MEDIA);

        // ===== 运动健康 =====
        register(new HealthTool("get_step_count"), CAT_MEDIA);
        register(new HealthTool("get_distance"), CAT_MEDIA);
        register(new HealthTool("get_calories"), CAT_MEDIA);
        register(new HealthTool("get_heart_rate"), CAT_MEDIA);
        register(new HealthTool("get_sleep"), CAT_MEDIA);
        register(new HealthTool("get_activity"), CAT_MEDIA);

        // ===== 实用工具 =====
        register(new JsonFormatTool(), CAT_UTILITY);
        register(new TextConvertTool(), CAT_UTILITY);
        register(new InfoTool("get_time_info"), CAT_UTILITY);
        register(new InfoTool("health"), CAT_UTILITY);
        register(new InfoTool("service_info"), CAT_UTILITY);
        register(new InfoTool("history"), CAT_UTILITY);
        register(new InfoTool("clear_log"), CAT_UTILITY);
        register(new InfoTool("tool_help"), CAT_UTILITY);
        register(new InfoTool("script_help"), CAT_UTILITY);
        register(new InfoTool("file_help"), CAT_UTILITY);
        register(new InfoTool("system_help"), CAT_UTILITY);
        register(new BatchOpsTool(), CAT_UTILITY);
    }

    private void register(MCPTool tool, String category) {
        tools.put(tool.getName(), tool);
        toolCategories.put(tool.getName(), category);
    }

    public MCPTool getTool(String name) { return tools.get(name); }
    public List<MCPTool> getAllTools() { return new ArrayList<>(tools.values()); }
    public int getToolCount() { return tools.size(); }

    public String getCategory(String toolName) {
        return toolCategories.getOrDefault(toolName, "其他");
    }

    public List<String> getAllCategories() {
        List<String> cats = new ArrayList<>();
        cats.add(CAT_FILE); cats.add(CAT_SYSTEM); cats.add(CAT_DEVICE);
        cats.add(CAT_APP); cats.add(CAT_SCRIPT); cats.add(CAT_COMMUNICATION);
        cats.add(CAT_NETWORK); cats.add(CAT_UTILITY);
        cats.add(CAT_PERMISSION); cats.add(CAT_MEDIA);
        return cats;
    }

    public List<MCPTool> getToolsByCategory(String category) {
        List<MCPTool> result = new ArrayList<>();
        for (MCPTool tool : tools.values()) {
            if (category.equals(toolCategories.get(tool.getName()))) result.add(tool);
        }
        return result;
    }

    // Bug 修复: 返回正确的分类图标
    public static String getCategoryIcon(String category) {
        switch (category) {
            case CAT_FILE: return "📁";
            case CAT_SYSTEM: return "⚙️";
            case CAT_DEVICE: return "📱";
            case CAT_APP: return "📦";
            case CAT_SCRIPT: return "📜";
            case CAT_COMMUNICATION: return "💬";
            case CAT_NETWORK: return "🌐";
            case CAT_UTILITY: return "🔧";
            case CAT_PERMISSION: return "🔐";
            case CAT_MEDIA: return "🎵";
            default: return "📌";
        }
    }

    // Bug 修复: 返回正确的工具图标
    public static String getToolIcon(String toolName) {
        return TOOL_ICONS.getOrDefault(toolName, "🔹");
    }
}
