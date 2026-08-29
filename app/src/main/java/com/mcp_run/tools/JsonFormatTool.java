package com.mcp_run.tools;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * JSON格式化工具 - 格式化、压缩、验证JSON字符串
 */
public class JsonFormatTool implements MCPTool {
 @Override
 public String getName() {
 return "json_format";
 }

 @Override
 public String getDescription() {
 return "格式化、压缩或验证JSON字符串。支持pretty（美化）、minify（压缩）、validate（验证）三种操作。";
 }

 @Override
 public JSONObject getInputSchema() {
 JSONObject schema = new JSONObject();
 try {
 schema.put("type", "object");
 JSONObject props = new JSONObject();

 JSONObject actionProp = new JSONObject();
 actionProp.put("type", "string");
 actionProp.put("description", "操作: pretty（美化格式化）, minify（压缩为一行）, validate（仅验证合法性）");
 actionProp.put("default", "pretty");
 props.put("action", actionProp);

 JSONObject textProp = new JSONObject();
 textProp.put("type", "string");
 textProp.put("description", "要处理的JSON字符串");
 props.put("text", textProp);

 schema.put("properties", props);
 schema.put("required", new JSONArray().put("text"));
 } catch (Exception e) {
 e.printStackTrace();
 }
 return schema;
 }

 @Override
 public JSONObject execute(Context context, JSONObject args) throws Exception {
 String text = args.getString("text");
 String action = args.optString("action", "pretty");

 JSONObject result = new JSONObject();
 result.put("input_length", text.length());

 // 先解析验证
 Object parsed;
 try {
 text = text.trim();
 if (text.startsWith("{")) {
 parsed = new JSONObject(text);
 } else if (text.startsWith("[")) {
 parsed = new JSONArray(text);
 } else {
 throw new Exception("不是有效的JSON: 必须以 { 或 [ 开头");
 }
 result.put("valid", true);
 } catch (Exception e) {
 result.put("valid", false);
 result.put("error", e.getMessage());
 result.put("output", text);
 return result;
 }

 switch (action) {
 case "pretty": {
 String formatted;
 if (parsed instanceof JSONObject) {
 formatted = ((JSONObject) parsed).toString(2);
 } else {
 formatted = ((JSONArray) parsed).toString(2);
 }
 result.put("output", formatted);
 result.put("output_length", formatted.length());
 result.put("action", "pretty");
 break;
 }
 case "minify": {
 String minified = parsed.toString();
 result.put("output", minified);
 result.put("output_length", minified.length());
 result.put("action", "minify");
 result.put("compression_ratio", String.format("%.1f%%",
 (1 - (double) minified.length() / text.length()) * 100));
 break;
 }
 case "validate": {
 result.put("output", " JSON 合法");
 result.put("action", "validate");
 result.put("type", parsed instanceof JSONObject ? "object" : "array");
 break;
 }
 default:
 throw new Exception("未知操作: " + action);
 }

 return result;
 }
}