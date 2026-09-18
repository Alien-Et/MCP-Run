package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 文本转换工具 - 大小写转换、去除空白等
 */
public class TextConvertTool implements MCPTool {
    @Override
    public String getName() {
        return "text_convert";
    }

    @Override
    public String getDescription() {
        return "文本格式转换。支持：upper（转大写）、lower（转小写）、trim（去除首尾空白）、trim_lines（每行去空白）、remove_empty_lines（删除空行）、normalize_newlines（统一换行符）、reverse（反转文本）、count（统计字符/单词/行数）。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            JSONObject actionProp = new JSONObject();
            actionProp.put("type", "string");
            actionProp.put("description", "转换操作类型");
            actionProp.put("enum", new JSONArray()
                    .put("upper").put("lower").put("trim")
                    .put("trim_lines").put("remove_empty_lines")
                    .put("normalize_newlines").put("reverse").put("count"));
            props.put("action", actionProp);

            JSONObject textProp = new JSONObject();
            textProp.put("type", "string");
            textProp.put("description", "要处理的文本内容");
            props.put("text", textProp);

            schema.put("properties", props);
            schema.put("required", new JSONArray().put("action").put("text"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String action = args.getString("action");
        String text = args.getString("text");

        String output;
        switch (action) {
            case "upper":
                output = text.toUpperCase();
                break;
            case "lower":
                output = text.toLowerCase();
                break;
            case "trim":
                output = text.trim();
                break;
            case "trim_lines": {
                StringBuilder sb = new StringBuilder();
                for (String line : text.split("\n")) {
                    sb.append(line.trim()).append("\n");
                }
                output = sb.toString().trim();
                break;
            }
            case "remove_empty_lines": {
                StringBuilder sb = new StringBuilder();
                for (String line : text.split("\n")) {
                    if (!line.trim().isEmpty()) {
                        sb.append(line).append("\n");
                    }
                }
                output = sb.toString().trim();
                break;
            }
            case "normalize_newlines":
                output = text.replace("\r\n", "\n").replace("\r", "\n");
                break;
            case "reverse":
                output = new StringBuilder(text).reverse().toString();
                break;
            case "count": {
                JSONObject counts = new JSONObject();
                counts.put("characters", text.length());
                counts.put("characters_no_space", text.replace(" ", "").replace("\n", "").replace("\r", "").replace("\t", "").length());
                counts.put("words", text.isEmpty() ? 0 : text.split("\\s+").length);
                counts.put("lines", text.isEmpty() ? 0 : text.split("\n").length);
                counts.put("bytes", text.getBytes("UTF-8").length);
                output = counts.toString(2);
                break;
            }
            default:
                throw new Exception("未知操作: " + action);
        }

        JSONObject result = new JSONObject();
        result.put("action", action);
        result.put("input_length", text.length());
        result.put("output_length", output.length());
        result.put("output", output);
        return result;
    }
}