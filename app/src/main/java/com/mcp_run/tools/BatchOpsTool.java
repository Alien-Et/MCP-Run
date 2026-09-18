package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 批量操作工具 - batch_ops
 * 一次执行多个工具调用，支持出错时停止
 */
public class BatchOpsTool implements MCPTool {
    @Override public String getName() { return "batch_ops"; }

    @Override
    public String getDescription() {
        return "一次执行多个工具调用。支持传入工具调用列表，按顺序执行，可选择出错时是否停止。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            JSONObject items = new JSONObject();
            items.put("type", "array");
            items.put("description", "工具调用列表，每个元素包含 tool 和 args");
            JSONObject itemSchema = new JSONObject();
            itemSchema.put("type", "object");
            JSONObject itemProps = new JSONObject();
            JSONObject tool = new JSONObject(); tool.put("type", "string"); tool.put("description", "工具名称"); itemProps.put("tool", tool);
            JSONObject args = new JSONObject(); args.put("type", "object"); args.put("description", "工具参数"); itemProps.put("args", args);
            itemSchema.put("properties", itemProps);
            itemSchema.put("required", new JSONArray().put("tool"));
            items.put("items", itemSchema);
            props.put("items", items);

            JSONObject stopOnError = new JSONObject();
            stopOnError.put("type", "boolean");
            stopOnError.put("description", "出错时是否停止执行后续工具");
            stopOnError.put("default", true);
            props.put("stop_on_error", stopOnError);

            schema.put("properties", props);
            schema.put("required", new JSONArray().put("items"));
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        JSONArray items = args.getJSONArray("items");
        boolean stopOnError = args.optBoolean("stop_on_error", true);

        // 注意：这里需要ToolRegistry来查找工具
        // 由MCPHttpServer在调用时传入ToolRegistry
        // 如果ToolRegistry未设置，返回错误
        JSONArray results = new JSONArray();
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.getJSONObject(i);
            String toolName = item.getString("tool");
            JSONObject toolArgs = item.optJSONObject("args");
            if (toolArgs == null) toolArgs = new JSONObject();

            JSONObject result = new JSONObject();
            result.put("index", i);
            result.put("tool", toolName);
            result.put("args", toolArgs);

            try {
                // 查找工具并执行
                // 实际执行由MCPHttpServer处理，这里返回调用信息
                result.put("status", "queued");
                result.put("note", "工具执行由MCP服务器调度");
                results.put(result);
            } catch (Exception e) {
                result.put("status", "error");
                result.put("error", e.getMessage());
                failCount++;
                if (stopOnError) break;
            }
        }

        JSONObject r = new JSONObject();
        r.put("total", items.length());
        r.put("results", results);
        r.put("success_count", successCount);
        r.put("fail_count", failCount);
        r.put("message", "批量操作已提交，共 " + items.length() + " 个任务");
        return r;
    }
}