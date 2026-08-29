package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * MCP工具接口 - 所有可被AI调用的工具都需要实现此接口
 */
public interface MCPTool {
    /** 获取工具名称 */
    String getName();
    
    /** 获取工具描述 */
    String getDescription();
    
    /** 获取工具的JSON Schema参数定义 */
    JSONObject getInputSchema();
    
    /**
     * 执行工具调用
     * @param context Android上下文
     * @param args JSON参数对象
     * @return JSON格式的结果
     */
    JSONObject execute(Context context, JSONObject args) throws Exception;
}