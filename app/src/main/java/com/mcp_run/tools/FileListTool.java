package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * 文件列表工具
 */
public class FileListTool implements MCPTool {
    @Override
    public String getName() {
        return "list_files";
    }

    @Override
    public String getDescription() {
        return "列出指定目录下的文件和子目录，支持递归搜索和过滤。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject pathProp = new JSONObject();
            pathProp.put("type", "string");
            pathProp.put("description", "目录路径");
            props.put("path", pathProp);
            
            JSONObject recursiveProp = new JSONObject();
            recursiveProp.put("type", "boolean");
            recursiveProp.put("description", "是否递归列出子目录");
            recursiveProp.put("default", false);
            props.put("recursive", recursiveProp);
            
            JSONObject filterProp = new JSONObject();
            filterProp.put("type", "string");
            filterProp.put("description", "文件名过滤关键字");
            props.put("filter", filterProp);
            
            JSONObject maxDepthProp = new JSONObject();
            maxDepthProp.put("type", "number");
            maxDepthProp.put("description", "递归最大深度，默认-1（无限制）");
            maxDepthProp.put("default", -1);
            props.put("max_depth", maxDepthProp);
            
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
        boolean recursive = args.optBoolean("recursive", false);
        String filter = args.optString("filter", "").toLowerCase();
        int maxDepth = args.optInt("max_depth", -1);
        
        File dir = new File(path);
        if (!dir.exists()) {
            throw new Exception("目录不存在: " + path);
        }
        if (!dir.isDirectory()) {
            throw new Exception("路径不是目录: " + path);
        }
        
        JSONArray files = new JSONArray();
        listFiles(dir, files, filter, recursive, maxDepth, 0);
        
        JSONObject result = new JSONObject();
        result.put("path", dir.getAbsolutePath());
        result.put("total", files.length());
        result.put("files", files);
        return result;
    }
    
    private void listFiles(File dir, JSONArray result, String filter, 
                          boolean recursive, int maxDepth, int depth) {
        File[] entries = dir.listFiles();
        if (entries == null) return;
        
        for (File entry : entries) {
            String name = entry.getName();
            if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) {
                if (recursive && entry.isDirectory()) {
                    // 即使目录名不匹配，仍然递归搜索里面的文件
                    if (maxDepth < 0 || depth < maxDepth) {
                        listFiles(entry, result, filter, recursive, maxDepth, depth + 1);
                    }
                }
                continue;
            }
            
            JSONObject fileInfo = new JSONObject();
            try {
                fileInfo.put("name", name);
                fileInfo.put("path", entry.getAbsolutePath());
                fileInfo.put("is_directory", entry.isDirectory());
                fileInfo.put("is_file", entry.isFile());
                fileInfo.put("size", entry.length());
                fileInfo.put("last_modified", entry.lastModified());
                fileInfo.put("can_read", entry.canRead());
                fileInfo.put("can_write", entry.canWrite());
                fileInfo.put("depth", depth);
                result.put(fileInfo);
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            if (recursive && entry.isDirectory()) {
                if (maxDepth < 0 || depth < maxDepth) {
                    listFiles(entry, result, filter, recursive, maxDepth, depth + 1);
                }
            }
        }
    }
}