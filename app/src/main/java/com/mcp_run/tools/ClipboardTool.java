package com.mcp_run.tools;

import android.content.Context;
import android.content.ClipboardManager;
import android.content.ClipData;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 剪贴板工具
 */
public class ClipboardTool implements MCPTool {
    @Override
    public String getName() {
        return "clipboard";
    }

    @Override
    public String getDescription() {
        return "读取或写入设备系统剪贴板。支持文本内容的读写操作。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject actionProp = new JSONObject();
            actionProp.put("type", "string");
            actionProp.put("description", "操作类型：read（读取）或 write（写入）");
            actionProp.put("enum", new JSONArray().put("read").put("write"));
            props.put("action", actionProp);
            
            JSONObject textProp = new JSONObject();
            textProp.put("type", "string");
            textProp.put("description", "要写入的文本（action为write时必填）");
            props.put("text", textProp);
            
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("action"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        ClipboardManager clipboard = (ClipboardManager) 
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        
        String action = args.getString("action");
        JSONObject result = new JSONObject();
        
        if ("read".equals(action)) {
            if (clipboard.hasPrimaryClip()) {
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).getText();
                    result.put("success", true);
                    result.put("content", text != null ? text.toString() : "");
                    result.put("has_content", text != null);
                } else {
                    result.put("success", true);
                    result.put("content", "");
                    result.put("has_content", false);
                }
            } else {
                result.put("success", true);
                result.put("content", "");
                result.put("has_content", false);
            }
        } else if ("write".equals(action)) {
            String text = args.getString("text");
            ClipData clip = ClipData.newPlainText("mcp_text", text);
            clipboard.setPrimaryClip(clip);
            result.put("success", true);
            result.put("length", text.length());
            result.put("message", "已写入剪贴板");
        } else {
            throw new Exception("未知操作: " + action + "，仅支持 read/write");
        }
        
        return result;
    }
}