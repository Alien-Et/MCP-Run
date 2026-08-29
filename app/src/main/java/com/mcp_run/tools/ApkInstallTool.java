package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * APK安装工具 - 通过Shell命令安装APK文件
 */
public class ApkInstallTool implements MCPTool {
    @Override
    public String getName() {
        return "install_apk";
    }

    @Override
    public String getDescription() {
        return "通过Shell命令安装APK文件。支持安装指定路径的APK文件，返回安装结果。需要系统授权或root。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            JSONObject pathProp = new JSONObject();
            pathProp.put("type", "string");
            pathProp.put("description", "APK文件路径");
            props.put("path", pathProp);

            JSONObject grantAllProp = new JSONObject();
            grantAllProp.put("type", "boolean");
            grantAllProp.put("description", "是否授予所有运行时权限，默认true");
            grantAllProp.put("default", true);
            props.put("grant_all", grantAllProp);

            JSONObject replaceProp = new JSONObject();
            replaceProp.put("type", "boolean");
            replaceProp.put("description", "是否覆盖安装已存在的应用，默认true");
            replaceProp.put("default", true);
            props.put("replace", replaceProp);

            schema.put("properties", props);
            schema.put("required", new JSONArray().put("path"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String path = args.getString("path");
        boolean grantAll = args.optBoolean("grant_all", true);
        boolean replace = args.optBoolean("replace", true);

        File apkFile = new File(path);
        if (!apkFile.exists()) {
            throw new Exception("APK文件不存在: " + path);
        }
        if (!path.endsWith(".apk")) {
            throw new Exception("文件不是APK格式: " + path);
        }

        // 构建 pm install 命令
        StringBuilder cmd = new StringBuilder("pm install ");
        if (grantAll) cmd.append("-g ");
        if (replace) cmd.append("-r ");
        cmd.append("\"").append(path).append("\"");

        String output = execShell(cmd.toString() + " 2>&1");

        JSONObject result = new JSONObject();
        result.put("apk_path", path);
        result.put("apk_size", apkFile.length());
        result.put("command", cmd.toString());
        result.put("output", output.trim());
        result.put("success", output.contains("Success") || output.contains("Successfully"));
        result.put("message", output.trim().isEmpty() ? "安装完成" : output.trim());

        return result;
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
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        process.waitFor();
        return output.toString();
    }
}