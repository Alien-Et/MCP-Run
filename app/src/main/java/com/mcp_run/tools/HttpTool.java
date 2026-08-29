package com.mcp_run.tools;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 增强HTTP工具集 - http_get, http_post, http_put, http_delete, http_json, download_text, download_file
 */
public class HttpTool implements MCPTool {
    private final String toolName;

    public HttpTool(String toolName) { this.toolName = toolName; }

    @Override public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "http_get": return "发起HTTP GET请求，获取网络资源";
            case "http_post": return "发起HTTP POST请求，支持自定义请求体和Content-Type";
            case "http_put": return "发起HTTP PUT请求，用于上传/更新资源";
            case "http_delete": return "发起HTTP DELETE请求，用于删除资源";
            case "http_json": return "发起HTTP请求并自动解析JSON响应";
            case "download_text": return "下载文本内容并保存到本地文件";
            case "download_file": return "下载二进制文件并保存到本地文件";
            default: return "HTTP请求工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            JSONObject url = new JSONObject(); url.put("type", "string"); url.put("description", "请求URL"); props.put("url", url);

            switch (toolName) {
                case "http_get": {
                    JSONObject h = new JSONObject(); h.put("type", "object"); h.put("description", "可选请求头"); props.put("headers", h);
                    JSONObject ct = new JSONObject(); ct.put("type", "number"); ct.put("description", "连接超时毫秒"); ct.put("default", 15000); props.put("connect_timeout", ct);
                    JSONObject rt = new JSONObject(); rt.put("type", "number"); rt.put("description", "读取超时毫秒"); rt.put("default", 15000); props.put("read_timeout", rt);
                    schema.put("required", new JSONArray().put("url")); break;
                }
                case "http_post": case "http_put": {
                    JSONObject b = new JSONObject(); b.put("type", "string"); b.put("description", "请求体内容"); props.put("body", b);
                    JSONObject ct = new JSONObject(); ct.put("type", "string"); ct.put("description", "Content-Type"); ct.put("default", "application/json"); props.put("content_type", ct);
                    schema.put("required", new JSONArray().put("url")); break;
                }
                case "http_delete": {
                    schema.put("required", new JSONArray().put("url")); break;
                }
                case "http_json": {
                    JSONObject h = new JSONObject(); h.put("type", "object"); h.put("description", "可选请求头"); props.put("headers", h);
                    schema.put("required", new JSONArray().put("url")); break;
                }
                case "download_text": case "download_file": {
                    JSONObject o = new JSONObject(); o.put("type", "string"); o.put("description", "保存路径"); props.put("output", o);
                    schema.put("required", new JSONArray().put("url").put("output")); break;
                }
            }
            schema.put("properties", props);
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "http_get": return execGet(args);
            case "http_post": return execPost(args, "POST");
            case "http_put": return execPost(args, "PUT");
            case "http_delete": return execDelete(args);
            case "http_json": return execJson(args);
            case "download_text": return execDownloadText(args);
            case "download_file": return execDownloadFile(args);
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    private JSONObject doRequest(String urlStr, String method, String body, String contentType, int connectTimeout, int readTimeout) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(connectTimeout > 0 ? connectTimeout : 15000);
        conn.setReadTimeout(readTimeout > 0 ? readTimeout : 15000);
        conn.setRequestProperty("User-Agent", "MCP·AC/1.1 Android");
        conn.setInstanceFollowRedirects(true);

        if (body != null && !body.isEmpty()) {
            conn.setDoOutput(true);
            if (contentType != null) conn.setRequestProperty("Content-Type", contentType);
            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes("UTF-8"));
                os.flush();
            }
        }

        int code = conn.getResponseCode();
        String msg = conn.getResponseMessage();
        StringBuilder resp = new StringBuilder();
        try (java.io.BufferedReader br = new java.io.BufferedReader(
                new java.io.InputStreamReader(code >= 400 ? conn.getErrorStream() : conn.getInputStream(), "UTF-8"))) {
            String line; while ((line = br.readLine()) != null) resp.append(line).append("\n");
        }

        JSONObject r = new JSONObject();
        r.put("url", urlStr);
        r.put("method", method);
        r.put("status_code", code);
        r.put("status_message", msg);
        r.put("content_type", conn.getContentType());
        r.put("response", resp.toString().trim());
        r.put("success", code >= 200 && code < 300);
        conn.disconnect();
        return r;
    }

    private JSONObject execGet(JSONObject args) throws Exception {
        return doRequest(args.getString("url"), "GET", null, null,
                args.optInt("connect_timeout", 15000), args.optInt("read_timeout", 15000));
    }

    private JSONObject execPost(JSONObject args, String method) throws Exception {
        return doRequest(args.getString("url"), method, args.optString("body", ""),
                args.optString("content_type", "application/json"), 30000, 30000);
    }

    private JSONObject execDelete(JSONObject args) throws Exception {
        return doRequest(args.getString("url"), "DELETE", null, null, 15000, 15000);
    }

    private JSONObject execJson(JSONObject args) throws Exception {
        JSONObject resp = doRequest(args.getString("url"), "GET", null, null, 15000, 15000);
        String responseBody = resp.optString("response", "");
        try {
            if (responseBody.startsWith("{")) {
                resp.put("parsed", new JSONObject(responseBody));
            } else if (responseBody.startsWith("[")) {
                resp.put("parsed", new JSONArray(responseBody));
            }
        } catch (Exception e) {
            resp.put("parse_error", e.getMessage());
        }
        return resp;
    }

    private JSONObject execDownloadText(JSONObject args) throws Exception {
        String url = args.getString("url");
        String output = args.getString("output");
        JSONObject resp = execGet(args);
        String text = resp.optString("response", "");
        File f = new File(output);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        java.nio.file.Files.write(f.toPath(), text.getBytes("UTF-8"));
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("url", url);
        r.put("path", f.getAbsolutePath());
        r.put("size", f.length());
        r.put("message", "文本已下载保存");
        return r;
    }

    private JSONObject execDownloadFile(JSONObject args) throws Exception {
        String urlStr = args.getString("url");
        String output = args.getString("output");
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
        conn.setInstanceFollowRedirects(true);
        File f = new File(output);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (java.io.InputStream is = conn.getInputStream();
             java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
            byte[] buf = new byte[8192];
            int len; while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
        }
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("url", urlStr);
        r.put("path", f.getAbsolutePath());
        r.put("size", f.length());
        r.put("status_code", conn.getResponseCode());
        conn.disconnect();
        return r;
    }
}