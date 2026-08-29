package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Shell命令执行工具 - Bug修复版
 * 修复: 输出流读取线程死锁问题
 */
public class ShellTool implements MCPTool {
    @Override
    public String getName() {
        return "shell";
    }

    @Override
    public String getDescription() {
        return "在设备上执行Shell命令（通过sh），返回命令输出。支持设置超时和工作目录。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject cmdProp = new JSONObject();
            cmdProp.put("type", "string");
            cmdProp.put("description", "要执行的Shell命令");
            props.put("command", cmdProp);
            
            JSONObject timeoutProp = new JSONObject();
            timeoutProp.put("type", "number");
            timeoutProp.put("description", "超时时间（毫秒），默认30000（30秒）");
            timeoutProp.put("default", 30000);
            props.put("timeout_ms", timeoutProp);
            
            JSONObject workDirProp = new JSONObject();
            workDirProp.put("type", "string");
            workDirProp.put("description", "工作目录，默认不设置");
            props.put("working_directory", workDirProp);
            
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("command"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String command = args.getString("command");
        long timeoutMs = args.optLong("timeout_ms", 30000);
        String workingDir = args.optString("working_directory", null);
        
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        if (workingDir != null && !workingDir.isEmpty()) {
            pb.directory(new java.io.File(workingDir));
        }
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        
        // Bug修复: 使用同步方式读取输出，避免线程死锁
        StringBuilder output = new StringBuilder();
        
        // 读取输出流（主线程）
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        
        // 等待进程完成
        boolean finished = process.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new java.util.concurrent.TimeoutException("命令执行超时 (" + timeoutMs + "ms): " + command);
        }
        
        int exitCode = process.exitValue();
        
        JSONObject result = new JSONObject();
        result.put("command", command);
        result.put("exit_code", exitCode);
        result.put("stdout", output.toString().trim());
        result.put("stderr", "");
        result.put("elapsed_ms", timeoutMs);
        result.put("success", exitCode == 0);
        return result;
    }
}
