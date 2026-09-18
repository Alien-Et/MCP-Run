package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Python脚本执行工具 - 执行.py文件或Python代码片段
 * 需要设备上安装Python环境（如Termux中的python或系统python）
 */
public class PythonExecuteTool implements MCPTool {
    @Override
    public String getName() {
        return "execute_python";
    }

    @Override
    public String getDescription() {
        return "执行Python脚本或代码片段。支持：执行.py文件、执行代码字符串、列出可用Python解释器。需要设备上安装Python环境。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject actionProp = new JSONObject();
            actionProp.put("type", "string");
            actionProp.put("description", "操作类型: execute_file（执行.py文件）, execute_code（执行代码字符串）, find_python（查找解释器）");
            actionProp.put("enum", new JSONArray().put("execute_file").put("execute_code").put("find_python"));
            props.put("action", actionProp);
            
            JSONObject pathProp = new JSONObject();
            pathProp.put("type", "string");
            pathProp.put("description", "Python文件路径（execute_file时必填）");
            props.put("file_path", pathProp);
            
            JSONObject codeProp = new JSONObject();
            codeProp.put("type", "string");
            codeProp.put("description", "Python代码内容（execute_code时必填）");
            props.put("code", codeProp);
            
            JSONObject interpreterProp = new JSONObject();
            interpreterProp.put("type", "string");
            interpreterProp.put("description", "Python解释器路径，默认自动查找");
            props.put("interpreter", interpreterProp);
            
            JSONObject argsProp = new JSONObject();
            argsProp.put("type", "string");
            argsProp.put("description", "传递给脚本的命令行参数");
            props.put("args", argsProp);
            
            JSONObject timeoutProp = new JSONObject();
            timeoutProp.put("type", "number");
            timeoutProp.put("description", "超时时间（毫秒），默认60000");
            timeoutProp.put("default", 60000);
            props.put("timeout_ms", timeoutProp);
            
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
        
        switch (action) {
            case "find_python":
                return findPython();
            case "execute_file":
                return executeFile(context, args);
            case "execute_code":
                return executeCode(context, args);
            default:
                throw new Exception("未知操作: " + action);
        }
    }
    
    private JSONObject findPython() throws Exception {
        JSONArray found = new JSONArray();
        String[] candidates = {"python3", "python", "/data/data/com.termux/files/usr/bin/python3", "/data/data/com.termux/files/usr/bin/python"};
        
        for (String cmd : candidates) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd + " --version 2>&1"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String version = reader.readLine();
                p.waitFor();
                
                JSONObject py = new JSONObject();
                py.put("interpreter", cmd);
                py.put("version", version != null ? version.trim() : "unknown");
                py.put("available", p.exitValue() == 0);
                found.put(py);
            } catch (Exception e) {
                JSONObject py = new JSONObject();
                py.put("interpreter", cmd);
                py.put("available", false);
                py.put("error", e.getMessage());
                found.put(py);
            }
        }
        
        JSONObject result = new JSONObject();
        result.put("found", found);
        
        // 推荐最佳解释器
        for (int i = 0; i < found.length(); i++) {
            JSONObject py = found.getJSONObject(i);
            if (py.optBoolean("available", false)) {
                result.put("recommended", py.getString("interpreter"));
                break;
            }
        }
        if (!result.has("recommended")) {
            result.put("recommended", "无可用Python解释器");
            result.put("note", "请安装Termux并在其中安装python: pkg install python");
        }
        
        return result;
    }
    
    private JSONObject executeFile(Context context, JSONObject args) throws Exception {
        String filePath = args.getString("file_path");
        String interpreter = args.optString("interpreter", "");
        String scriptArgs = args.optString("args", "");
        long timeoutMs = args.optLong("timeout_ms", 60000);
        
        File file = new File(filePath);
        if (!file.exists()) {
            throw new Exception("文件不存在: " + filePath);
        }
        if (!filePath.endsWith(".py")) {
            throw new Exception("文件不是.py文件: " + filePath);
        }
        
        if (interpreter.isEmpty()) {
            interpreter = findBestInterpreter();
        }
        
        String cmd = interpreter + " \"" + filePath + "\"";
        if (!scriptArgs.isEmpty()) {
            cmd += " " + scriptArgs;
        }
        
        return runCommand(cmd, timeoutMs, file.getParent());
    }
    
    private JSONObject executeCode(Context context, JSONObject args) throws Exception {
        String code = args.getString("code");
        String interpreter = args.optString("interpreter", "");
        long timeoutMs = args.optLong("timeout_ms", 60000);
        
        if (interpreter.isEmpty()) {
            interpreter = findBestInterpreter();
        }
        
        // 将代码写入临时文件执行
        File tempDir = context.getCacheDir();
        File tempFile = new File(tempDir, "mcp_temp_" + System.currentTimeMillis() + ".py");
        tempFile.deleteOnExit();
        
        java.io.FileWriter fw = new java.io.FileWriter(tempFile);
        fw.write(code);
        fw.close();
        
        String cmd = interpreter + " \"" + tempFile.getAbsolutePath() + "\"";
        JSONObject result = runCommand(cmd, timeoutMs, tempDir.getAbsolutePath());
        tempFile.delete();
        return result;
    }
    
    private String findBestInterpreter() throws Exception {
        String[] candidates = {"python3", "python", "/data/data/com.termux/files/usr/bin/python3", "/data/data/com.termux/files/usr/bin/python"};
        for (String cmd : candidates) {
            try {
                Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", "which " + cmd + " 2>/dev/null"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
                String path = reader.readLine();
                p.waitFor();
                if (p.exitValue() == 0 && path != null && !path.isEmpty()) {
                    return cmd;
                }
            } catch (Exception ignored) {}
        }
        throw new Exception("未找到Python解释器，请安装Termux + python: pkg install python");
    }
    
    private JSONObject runCommand(String command, long timeoutMs, String workingDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("sh", "-c", command);
        if (workingDir != null) {
            pb.directory(new File(workingDir));
        }
        pb.redirectErrorStream(true);
        
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        
        Thread reader = new Thread(() -> {
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = br.readLine()) != null) {
                    output.append(line).append("\n");
                }
            } catch (Exception ignored) {}
        });
        reader.start();
        
        long start = System.currentTimeMillis();
        int exitCode = process.waitFor();
        long elapsed = System.currentTimeMillis() - start;
        reader.join(2000);
        
        JSONObject result = new JSONObject();
        result.put("command", command);
        result.put("exit_code", process.exitValue());
        result.put("stdout", output.toString().trim());
        result.put("elapsed_ms", elapsed);
        result.put("success", process.exitValue() == 0);
        return result;
    }
}