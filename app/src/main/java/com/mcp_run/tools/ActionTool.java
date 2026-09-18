package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 通用Action工具 - 通过名称、描述、参数Schema和执行函数创建MCP工具
 */
public class ActionTool implements MCPTool {
    private final String name;
    private final String description;
    private final JSONObject inputSchema;
    private final Executor executor;

    @FunctionalInterface
    public interface Executor {
        JSONObject execute(Context context, JSONObject args) throws Exception;
    }

    public ActionTool(String name, String description, JSONObject inputSchema, Executor executor) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema != null ? inputSchema : new JSONObject();
        this.executor = executor;
    }

    @Override
    public String getName() { return name; }

    @Override
    public String getDescription() { return description; }

    @Override
    public JSONObject getInputSchema() { return inputSchema; }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        return executor.execute(context, args);
    }

    /** 构建一个无参数的工具 */
    public static ActionTool noArgs(String name, String desc, Executor exec) {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", new JSONObject());
        } catch (Exception ignored) {}
        return new ActionTool(name, desc, schema, exec);
    }

    /** 构建带单个path参数的工具 */
    public static ActionTool withPath(String name, String desc, String pathDesc, Executor exec) {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            JSONObject p = new JSONObject();
            p.put("type", "string");
            p.put("description", pathDesc);
            props.put("path", p);
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("path"));
        } catch (Exception ignored) {}
        return new ActionTool(name, desc, schema, exec);
    }

    /** 构建带source/destination参数的工具 */
    public static ActionTool withSrcDest(String name, String desc, Executor exec) {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            JSONObject s = new JSONObject(); s.put("type", "string"); s.put("description", "源路径"); props.put("source", s);
            JSONObject d = new JSONObject(); d.put("type", "string"); d.put("description", "目标路径"); props.put("destination", d);
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("source").put("destination"));
        } catch (Exception ignored) {}
        return new ActionTool(name, desc, schema, exec);
    }
}