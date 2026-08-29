package com.mcp_run;

import android.content.Context;
import android.util.Log;

import com.mcp_run.tools.MCPTool;
import com.mcp_run.tools.NetworkUtils;
import com.mcp_run.tools.ToolRegistry;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeoutException;

/**
 * MVP v1.15 - MCP协议 Streamable HTTP 服务器
 * 修复: 端口被占用时抛出明确异常
 */
public class MCPHttpServer {
    private static final String TAG = "MCPHttpServer";
    private static final int DEFAULT_PORT = 1145;
    private static final String LOCALHOST = "127.0.0.1";
    private static final String ANY_INTERFACE = "0.0.0.0";
    private static final String MCP_VERSION = "2025-03-26";
    private static final int MAX_LOG_ENTRIES = 500;
    
    private final Context context;
    private final ToolRegistry toolRegistry;
    private final int port;
    private final String apiKey;
    private ServerSocket serverSocket;
    private ExecutorService threadPool;
    private boolean running = false;
    
    private ServerStatusListener statusListener;
    
    private final LinkedBlockingQueue<String> requestLog = new LinkedBlockingQueue<String>(MAX_LOG_ENTRIES);
    
    // 本机 WiFi IP（用于显示）
    private String localIP = "未知";
    
    public interface ServerStatusListener {
        void onStatusChanged(String status, int port);
        void onRequest(String method, String path, int responseCode);
        void onError(String error);
    }
    
    public MCPHttpServer(Context context, int port) {
        this(context, port, null);
    }
    
    public MCPHttpServer(Context context, int port, String apiKey) {
        this.context = context;
        this.toolRegistry = new ToolRegistry(context);
        this.port = port > 0 ? port : DEFAULT_PORT;
        this.apiKey = apiKey;
    }
    
    public MCPHttpServer(Context context) {
        this(context, DEFAULT_PORT, null);
    }
    
    public void setStatusListener(ServerStatusListener listener) {
        this.statusListener = listener;
    }
    
    public int getPort() { return port; }
    public boolean isRunning() { return running; }
    public ToolRegistry getToolRegistry() { return toolRegistry; }
    public List<String> getRequestHistory() { 
        return new java.util.ArrayList<String>(requestLog); 
    }
    
    /**
     * 获取本机局域网 IP
     */
    public String getLocalIP() {
        return localIP;
    }
    
    public void start() throws IOException {
        if (running) return;
        
        // 获取本机 WiFi IP 用于显示
        localIP = NetworkUtils.getLocalIPAddress(context);
        
        try {
            // 绑定到 0.0.0.0 所有网络接口，支持局域网访问
            InetAddress bindAddr = InetAddress.getByName(ANY_INTERFACE);
            serverSocket = new ServerSocket(port, 50, bindAddr);
        } catch (IOException e) {
            // 端口被占用，抛出更明确的异常
            throw new IOException("端口 " + port + " 已被占用: " + e.getMessage());
        }
        
        threadPool = Executors.newFixedThreadPool(10);
        running = true;
        
        String displayIP = localIP;
        String statusText = "MCP Streamable HTTP 服务器已启动 (" + displayIP + ":" + port + ")";
        if ("未知".equals(localIP)) {
            statusText += " [请在设置中查看IP]";
        } else {
            statusText += " [局域网可用]";
        }
        notifyStatus(statusText);
        
        new Thread(() -> {
            while (running && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    threadPool.execute(() -> handleClient(client));
                } catch (IOException e) {
                    if (running) notifyError("接受连接失败: " + e.getMessage());
                }
            }
        }, "MCP-Accept").start();
    }
    
    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            Log.w(TAG, "关闭ServerSocket异常", e);
        }
        if (threadPool != null) {
            threadPool.shutdownNow();
        }
        notifyStatus("MCP服务器已停止");
    }
    
    /**
     * 获取可用的 endpoint URL（用于显示和返回）
     */
    public String getEndpointURL() {
        return "http://" + localIP + ":" + port + "/mcp";
    }
    
    private void handleClient(Socket client) {
        try {
            client.setSoTimeout(30000);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out = client.getOutputStream();
            
            String requestLine = reader.readLine();
            if (requestLine == null || requestLine.isEmpty()) {
                client.close();
                return;
            }
            
            String[] parts = requestLine.split(" ", 3);
            if (parts.length < 2) {
                sendError(out, 400, "Bad Request");
                client.close();
                return;
            }
            
            String method = parts[0];
            String rawPath = parts[1];
            String path = URLDecoder.decode(rawPath.split("\\?")[0], "UTF-8");
            
            Map<String, String> headers = new HashMap<String, String>();
            String line;
            int contentLength = 0;
            while ((line = reader.readLine()) != null && !line.isEmpty()) {
                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String key = line.substring(0, colonIdx).trim().toLowerCase();
                    String value = line.substring(colonIdx + 1).trim();
                    headers.put(key, value);
                    if ("content-length".equals(key)) {
                        try { contentLength = Integer.parseInt(value); } catch (NumberFormatException ignored) {}
                    }
                }
            }
            
            String body = "";
            if (contentLength > 0) {
                char[] buf = new char[contentLength];
                int read = reader.read(buf, 0, contentLength);
                if (read > 0) body = new String(buf, 0, read);
            }
            
            if (method.equals("OPTIONS")) {
                sendOptions(out);
                client.close();
                return;
            }
            
            if (method.equals("POST") && (path.equals("/mcp") || path.equals("/"))) {
                handleStreamableHttp(out, body, headers);
            } else if (method.equals("GET") && (path.equals("/") || path.equals("/status"))) {
                handleStatus(out);
            } else if (method.equals("GET") && path.equals("/tools")) {
                handleListTools(out);
            } else if (method.equals("GET") && path.equals("/history")) {
                handleHistory(out);
            } else {
                sendJson(out, 404, createErrorResponse(null, -32601, "Not Found: " + path));
            }
            
            client.close();
        } catch (Exception e) {
            Log.e(TAG, "处理请求异常", e);
            notifyError("请求处理错误: " + e.getMessage());
            try { client.close(); } catch (IOException ignored) {}
        }
    }
    
    private void handleStreamableHttp(OutputStream out, String body, Map<String, String> headers) throws IOException {
        String mcpVersion = headers.getOrDefault("mcp-version", MCP_VERSION);
        String callbackUrl = headers.get("callback-url");
        String authHeader = headers.get("authorization");
        
        if (apiKey != null && !apiKey.isEmpty()) {
            String expectedAuth = "Bearer " + apiKey;
            if (!expectedAuth.equals(authHeader)) {
                sendJson(out, 401, createErrorResponse(null, -32000, "Unauthorized: Invalid API Key"));
                notifyRequest("POST", "/mcp", 401);
                return;
            }
        }
        
        notifyStatus("Streamable HTTP 请求 (MCP-Version: " + mcpVersion 
            + (callbackUrl != null ? ", callback: " + callbackUrl : "") + ")");
        
        if (body == null || body.trim().isEmpty()) {
            JSONObject info = new JSONObject();
            try {
                info.put("server", "MCP·AC Android Server");
                info.put("version", "1.2.0");
                info.put("protocol", "Streamable HTTP");
                info.put("mcp_version", MCP_VERSION);
                info.put("tools_count", toolRegistry.getToolCount());
                info.put("endpoint", getEndpointURL());
                info.put("bind_address", "0.0.0.0");
                info.put("local_ip", localIP);
                info.put("auth_enabled", apiKey != null && !apiKey.isEmpty());
                if (callbackUrl != null) {
                    info.put("callback_url", callbackUrl);
                }
            } catch (Exception e) { Log.e(TAG, "info error", e); }
            sendJson(out, 200, info);
            return;
        }
        
        JSONObject response;
        try {
            JSONObject request = new JSONObject(body);
            String rpcMethod = request.optString("method", "");
            JSONObject params = request.optJSONObject("params");
            if (params == null) params = new JSONObject();
            Object id = request.has("id") ? request.get("id") : null;
            
            Log.d(TAG, "MCP Streamable HTTP: " + rpcMethod + " id=" + id);
            notifyStatus("MCP请求: " + rpcMethod);
            
            switch (rpcMethod) {
                case "initialize":
                    response = handleInitialize(params, id);
                    break;
                case "ping":
                    JSONObject pong = new JSONObject();
                    pong.put("pong", true);
                    response = createSuccessResult(id, pong);
                    break;
                case "tools/list":
                    response = handleToolsList(id);
                    break;
                case "tools/call":
                    response = handleToolsCall(params, id);
                    break;
                case "resources/list":
                    response = handleResourcesList(id);
                    break;
                case "resources/read":
                    response = handleResourcesRead(params, id);
                    break;
                case "notifications/initialized":
                    sendJsonWithVersion(out, 202, new JSONObject(), mcpVersion);
                    return;
                default:
                    response = createErrorResponse(id, -32601, "Method not found: " + rpcMethod);
            }
        } catch (Exception e) {
            Log.e(TAG, "消息解析失败", e);
            response = createErrorResponse(null, -32700, "Parse error: " + e.getMessage());
        }
        
        sendJsonWithVersion(out, 200, response, mcpVersion);
        notifyRequest("POST", "/mcp", 200);
    }
    
    private JSONObject handleInitialize(JSONObject params, Object id) throws Exception {
        JSONObject capabilities = new JSONObject();
        
        JSONObject toolsCap = new JSONObject();
        toolsCap.put("listChanged", false);
        capabilities.put("tools", toolsCap);
        
        JSONObject resourcesCap = new JSONObject();
        resourcesCap.put("subscribe", false);
        resourcesCap.put("listChanged", false);
        capabilities.put("resources", resourcesCap);
        
        JSONObject streamingCap = new JSONObject();
        streamingCap.put("supported", true);
        streamingCap.put("resumable", false);
        capabilities.put("streamableHttp", streamingCap);
        
        JSONObject result = new JSONObject();
        result.put("protocolVersion", MCP_VERSION);
        result.put("capabilities", capabilities);
        result.put("serverInfo", new JSONObject()
            .put("name", "MCP·AC")
            .put("version", "1.2.0")
            .put("description", "Android MCP Server with built-in tools"));
        
        return createSuccessResult(id, result);
    }
    
    private JSONObject handleToolsList(Object id) throws Exception {
        JSONArray toolsArray = new JSONArray();
        for (MCPTool tool : toolRegistry.getAllTools()) {
            JSONObject toolObj = new JSONObject();
            toolObj.put("name", tool.getName());
            toolObj.put("description", tool.getDescription());
            toolObj.put("inputSchema", tool.getInputSchema());
            toolsArray.put(toolObj);
        }
        
        JSONObject result = new JSONObject();
        result.put("tools", toolsArray);
        return createSuccessResult(id, result);
    }
    
    private JSONObject handleToolsCall(JSONObject params, Object id) throws Exception {
        String toolName = params.getString("name");
        JSONObject arguments = params.optJSONObject("arguments");
        if (arguments == null) arguments = new JSONObject();
        
        MCPTool tool = toolRegistry.getTool(toolName);
        if (tool == null) {
            return createErrorResponse(id, -32602, "Unknown tool: " + toolName);
        }
        
        try {
            JSONObject result = tool.execute(context, arguments);
            JSONObject content = new JSONObject();
            content.put("type", "text");
            content.put("text", result.toString(2));
            
            JSONArray contentArray = new JSONArray();
            contentArray.put(content);
            
            JSONObject response = new JSONObject();
            response.put("content", contentArray);
            response.put("isError", false);
            
            return createSuccessResult(id, response);
        } catch (Exception e) {
            Log.e(TAG, "工具执行失败: " + toolName, e);
            JSONObject errorContent = new JSONObject();
            errorContent.put("type", "text");
            errorContent.put("text", "Error: " + e.getMessage());
            
            JSONArray contentArray = new JSONArray();
            contentArray.put(errorContent);
            
            JSONObject response = new JSONObject();
            response.put("content", contentArray);
            response.put("isError", true);
            return createSuccessResult(id, response);
        }
    }
    
    private JSONObject handleResourcesList(Object id) throws Exception {
        JSONArray resources = new JSONArray();
        
        JSONObject devInfo = new JSONObject();
        devInfo.put("uri", "mcp://device/info");
        devInfo.put("name", "Device Info");
        devInfo.put("description", "Android device information");
        devInfo.put("mimeType", "application/json");
        resources.put(devInfo);
        
        JSONObject storageInfo = new JSONObject();
        storageInfo.put("uri", "mcp://device/storage");
        storageInfo.put("name", "Storage Info");
        storageInfo.put("description", "Device storage usage");
        storageInfo.put("mimeType", "application/json");
        resources.put(storageInfo);
        
        JSONObject result = new JSONObject();
        result.put("resources", resources);
        return createSuccessResult(id, result);
    }
    
    private JSONObject handleResourcesRead(JSONObject params, Object id) throws Exception {
        String uri = params.getString("uri");
        JSONObject content = new JSONObject();
        content.put("uri", uri);
        content.put("mimeType", "application/json");
        
        switch (uri) {
            case "mcp://device/info":
                content.put("text", toolRegistry.getTool("device_info")
                    .execute(context, new JSONObject()).toString(2));
                break;
            case "mcp://device/storage":
                JSONObject info = toolRegistry.getTool("device_info")
                    .execute(context, new JSONObject());
                content.put("text", info.getJSONObject("storage").toString(2));
                break;
            default:
                return createErrorResponse(id, -32602, "Unknown resource: " + uri);
        }
        
        JSONArray contents = new JSONArray();
        contents.put(content);
        
        JSONObject result = new JSONObject();
        result.put("contents", contents);
        return createSuccessResult(id, result);
    }
    
    private void handleStatus(OutputStream out) throws IOException {
        JSONObject status = new JSONObject();
        try {
            status.put("server", "MCP·AC Android Server");
            status.put("version", "1.2.0");
            status.put("protocol", "Streamable HTTP (MCP " + MCP_VERSION + ")");
            status.put("bind_address", "0.0.0.0:" + port);
            status.put("local_ip", localIP);
            status.put("port", port);
            status.put("running", running);
            status.put("tools_count", toolRegistry.getToolCount());
            status.put("endpoint", getEndpointURL());
            status.put("auth_enabled", apiKey != null && !apiKey.isEmpty());
            
            JSONArray toolsList = new JSONArray();
            for (MCPTool tool : toolRegistry.getAllTools()) {
                toolsList.put(tool.getName());
            }
            status.put("available_tools", toolsList);
            
        } catch (Exception e) {
            Log.e(TAG, "状态生成失败", e);
        }
        sendJson(out, 200, status);
    }
    
    private void handleHistory(OutputStream out) throws IOException, org.json.JSONException {
        JSONArray history = new JSONArray();
        for (String entry : requestLog) {
            history.put(entry);
        }
        
        JSONObject result = new JSONObject();
        result.put("total", history.length());
        result.put("history", history);
        sendJson(out, 200, result);
    }
    
    private void handleListTools(OutputStream out) throws IOException {
        StringBuilder html = new StringBuilder();
        String displayIP = localIP;
        html.append("<html><head><title>MCP·AC Tools</title>")
            .append("<style>body{font-family:sans-serif;margin:20px;background:#1a1a2e;color:#eee;}")
            .append("h1{color:#00d2ff;}.tool{border:1px solid #333;padding:12px;margin:8px 0;border-radius:8px;background:#16213e;}")
            .append(".tool h3{margin:0 0 5px 0;color:#00d2ff;}")
            .append(".desc{color:#aaa;font-size:14px;}")
            .append(".cat{color:#e94560;font-size:12px;}")
            .append(".auth{color:#FBBF24;font-size:12px;margin:10px 0;}</style></head><body>")
            .append("<h1> MCP·AC Tools</h1>")
            .append("<p>Streamable HTTP | ").append(displayIP).append(":")
            .append(port).append(" | ").append(toolRegistry.getToolCount()).append(" tools")
            .append(apiKey != null ? " | <span class='auth'>认证已启用</span>" : " | <span class='auth'>无认证</span>")
            .append("</p>");
        
        for (String category : toolRegistry.getAllCategories()) {
            html.append("<h2>").append(ToolRegistry.getCategoryIcon(category))
                .append(" ").append(category).append("</h2>");
            for (MCPTool tool : toolRegistry.getToolsByCategory(category)) {
                html.append("<div class='tool'")
                    .append("<h3>").append(ToolRegistry.getToolIcon(tool.getName()))
                    .append(" ").append(tool.getName()).append("</h3>")
                    .append("<div class='desc'>").append(tool.getDescription()).append("</div>")
                    .append("</div>");
            }
        }
        html.append("</body></html>");
        
        String resp = "HTTP/1.1 200 OK\r\n" +
            "Content-Type: text/html; charset=utf-8\r\n" +
            "Content-Length: " + html.toString().getBytes(StandardCharsets.UTF_8).length + "\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "\r\n" + html.toString();
        out.write(resp.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
    
    private void sendOptions(OutputStream out) throws IOException {
        String resp = "HTTP/1.1 204 No Content\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Content-Type, MCP-Version, Callback-URL, Authorization\r\n" +
            "Access-Control-Max-Age: 86400\r\n" +
            "\r\n";
        out.write(resp.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
    
    private void sendJson(OutputStream out, int statusCode, JSONObject json) throws IOException {
        sendJsonWithVersion(out, statusCode, json, MCP_VERSION);
    }
    
    private void sendJsonWithVersion(OutputStream out, int statusCode, JSONObject json, String mcpVersion) throws IOException {
        String jsonStr = json.toString();
        String resp = "HTTP/1.1 " + statusCode + " " + getStatusText(statusCode) + "\r\n" +
            "Content-Type: application/json; charset=utf-8\r\n" +
            "Content-Length: " + jsonStr.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
            "MCP-Version: " + mcpVersion + "\r\n" +
            "Access-Control-Allow-Origin: *\r\n" +
            "Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n" +
            "Access-Control-Allow-Headers: Content-Type, MCP-Version, Callback-URL, Authorization\r\n" +
            "Connection: close\r\n" +
            "\r\n" + jsonStr;
        out.write(resp.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }
    
    private void sendError(OutputStream out, int statusCode, String message) throws IOException {
        JSONObject error = createErrorResponse(null, statusCode, message);
        sendJson(out, statusCode, error);
    }
    
    private JSONObject createSuccessResult(Object id, Object result) {
        JSONObject response = new JSONObject();
        try {
            response.put("jsonrpc", "2.0");
            response.put("id", id != null ? id : JSONObject.NULL);
            response.put("result", result);
        } catch (Exception e) {
            Log.e(TAG, "创建成功响应失败", e);
        }
        return response;
    }
    
    private JSONObject createErrorResponse(Object id, int code, String message) {
        JSONObject response = new JSONObject();
        try {
            response.put("jsonrpc", "2.0");
            response.put("id", id != null ? id : JSONObject.NULL);
            JSONObject error = new JSONObject();
            error.put("code", code);
            error.put("message", message);
            response.put("error", error);
        } catch (Exception e) {
            Log.e(TAG, "创建错误响应失败", e);
        }
        return response;
    }
    
    private String getStatusText(int code) {
        switch (code) {
            case 200: return "OK";
            case 201: return "Created";
            case 202: return "Accepted";
            case 204: return "No Content";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 404: return "Not Found";
            case 500: return "Internal Server Error";
            default: return "Unknown";
        }
    }
    
    private void notifyStatus(String status) {
        Log.d(TAG, status);
        if (statusListener != null) statusListener.onStatusChanged(status, port);
    }
    
    private void notifyRequest(String method, String path, int code) {
        if (statusListener != null) statusListener.onRequest(method, path, code);
        String entry = java.time.LocalTime.now().toString()
            + " " + method + " " + path + " -> " + code;
        if (requestLog.size() >= MAX_LOG_ENTRIES) {
            requestLog.poll();
        }
        requestLog.add(entry);
    }
    
    private void notifyError(String error) {
        Log.e(TAG, error);
        if (statusListener != null) statusListener.onError(error);
    }
}
