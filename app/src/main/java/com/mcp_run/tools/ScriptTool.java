package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * 脚本执行工具集 - Bug修复版
 * JavaScript: 使用内置 Rhino 引擎（Android 自带），fallback 到 d8/node
 * Python: 需要 Termux 环境或系统 python3
 * 
 * Bug修复:
 * 1. Rhino 引擎已确认在 Android 15 中移除，移除无效尝试
 * 2. Python 临时文件正确清理
 */
public class ScriptTool implements MCPTool {
    private final String toolName;

    public ScriptTool(String toolName) { this.toolName = toolName; }

    @Override public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "mcp_javascript": return "执行JavaScript代码，使用 d8/node 引擎（Rhino 已在 Android 15 移除），返回执行结果。";
            case "mcp_python": return "执行Python代码或.py脚本文件。需要 Termux 中安装 python: pkg install python";
            default: return "脚本执行工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            switch (toolName) {
                case "mcp_javascript": {
                    JSONObject c = new JSONObject(); c.put("type", "string"); c.put("description", "要执行的JavaScript代码"); props.put("code", c);
                    schema.put("required", new JSONArray().put("code")); break;
                }
                case "mcp_python": {
                    JSONObject action = new JSONObject(); action.put("type", "string"); action.put("description", "操作: execute_file/execute_code/find_python"); action.put("default", "execute_code"); props.put("action", action);
                    JSONObject code = new JSONObject(); code.put("type", "string"); code.put("description", "Python代码（execute_code时必填）"); props.put("code", code);
                    JSONObject path = new JSONObject(); path.put("type", "string"); path.put("description", "Python文件路径（execute_file时必填）"); props.put("file_path", path);
                    schema.put("required", new JSONArray().put("action")); break;
                }
            }
            schema.put("properties", props);
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "mcp_javascript": return execJS(context, args);
            case "mcp_python": return execPython(context, args);
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    // ===== JavaScript 执行 =====

    private JSONObject execJS(Context context, JSONObject args) throws Exception {
        String code = args.getString("code");
        long timeoutMs = args.optLong("timeout_ms", 10000);

        // Fallback: 尝试 d8
        try {
            String output = execShell("echo '" + escapeForShell(code) + "' | d8 2>&1", timeoutMs);
            if (!output.contains("not found") && !output.contains("No such file")) {
                JSONObject r = new JSONObject();
                r.put("engine", "d8");
                r.put("code", code);
                r.put("output", output.trim());
                r.put("success", true);
                return r;
            }
        } catch (Exception ignored) {}

        // Fallback: 尝试 node
        try {
            String output = execShell("node -e '" + escapeForShell(code) + "' 2>&1", timeoutMs);
            if (!output.contains("not found") && !output.contains("No such file")) {
                JSONObject r = new JSONObject();
                r.put("engine", "node");
                r.put("code", code);
                r.put("output", output.trim());
                r.put("success", true);
                return r;
            }
        } catch (Exception ignored) {}

        // 所有方式都失败
        JSONObject r = new JSONObject();
        r.put("code", code);
        r.put("output", "");
        r.put("success", false);
        r.put("note", "未找到JavaScript引擎。请在Termux中安装: pkg install nodejs 或 pkg install d8");
        return r;
    }

    // ===== Python 执行 =====

    private JSONObject execPython(Context context, JSONObject args) throws Exception {
        String action = args.optString("action", "execute_code");
        String interpreter = findPythonInterpreter();
        long timeoutMs = args.optLong("timeout_ms", 60000);

        switch (action) {
            case "find_python": {
                JSONObject r = new JSONObject();
                r.put("interpreter", interpreter);
                r.put("available", !interpreter.contains("未找到"));
                return r;
            }
            case "execute_file": {
                String filePath = args.getString("file_path");
                java.io.File f = new java.io.File(filePath);
                if (!f.exists()) throw new Exception("文件不存在: " + filePath);
                if (!filePath.endsWith(".py")) throw new Exception("不是.py文件");
                String output = execShell(interpreter + " \"" + filePath + "\" 2>&1", timeoutMs);
                JSONObject r = new JSONObject();
                r.put("file", filePath);
                r.put("output", output.trim());
                r.put("success", true);
                return r;
            }
            case "execute_code": {
                String code = args.getString("code");
                java.io.File temp = new java.io.File(context.getCacheDir(), "mcp_py_" + System.currentTimeMillis() + ".py");
                
                // Bug修复: 使用 delete() 而非 deleteOnExit()，确保文件立即清理
                java.io.FileWriter writer = null;
                try {
                    writer = new java.io.FileWriter(temp);
                    writer.write(code);
                } finally {
                    if (writer != null) writer.close();
                }
                
                String output = execShell(interpreter + " \"" + temp.getAbsolutePath() + "\" 2>&1", timeoutMs);
                temp.delete(); // 立即删除临时文件
                
                JSONObject r = new JSONObject();
                r.put("output", output.trim());
                r.put("success", true);
                return r;
            }
            default:
                throw new Exception("未知操作: " + action);
        }
    }

    private String findPythonInterpreter() throws Exception {
        String[] candidates = {"python3", "python", "/data/data/com.termux/files/usr/bin/python3", "/data/data/com.termux/files/usr/bin/python"};
        for (String cmd : candidates) {
            try {
                String output = execShell("which " + cmd + " 2>/dev/null", 3000);
                if (!output.trim().isEmpty()) return cmd;
            } catch (Exception ignored) {}
        }
        throw new Exception("未找到Python解释器，请在Termux中安装: pkg install python");
    }

    // ===== 通用 Shell 执行 =====

    private String execShell(String command) throws Exception {
        return execShell(command, 30000);
    }

    private String execShell(String command, long timeoutMs) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        pb.redirectErrorStream(true);
        Process p = pb.start();

        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) out.append(line).append("\n");
        }

        boolean finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        if (!finished) {
            p.destroyForcibly();
            throw new TimeoutException("命令执行超时 (" + timeoutMs + "ms): " + command.substring(0, Math.min(100, command.length())));
        }

        return out.toString();
    }

    /** Shell 字符串转义 */
    private String escapeForShell(String s) {
        return s.replace("'", "'\\''");
    }
}
