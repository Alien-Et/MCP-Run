package com.mcp_run.tools;

import android.content.Context;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 文件内容读写工具集
 * 包含: read, head, tail, read_lines, batch_read, read_base64, write, append, write_base64, compare_files
 */
public class FileContentTool implements MCPTool {
    private final String toolName;
    private final FileSession session;

    public FileContentTool(String toolName, FileSession session) {
        this.toolName = toolName;
        this.session = session;
    }

    @Override public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "read": return "读取文本文件的全部内容，返回文件内容和元数据";
            case "head": return "读取文件开头指定行数";
            case "tail": return "读取文件末尾指定行数";
            case "read_lines": return "读取文件指定行范围（从start行到end行）";
            case "batch_read": return "批量读取多个文件的内容";
            case "read_base64": return "以Base64编码读取二进制文件（如图片、APK等）";
            case "write": return "写入文本文件（覆盖模式），如文件不存在则创建";
            case "append": return "追加内容到文本文件末尾";
            case "write_base64": return "将Base64编码内容写入二进制文件";
            case "compare_files": return "对比两个文件的差异，返回差异行";
            default: return "文件内容操作";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            switch (toolName) {
                case "read": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "文件路径"); props.put("path", p);
                    schema.put("required", new JSONArray().put("path")); break;
                }
                case "head": case "tail": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "文件路径"); props.put("path", p);
                    JSONObject n = new JSONObject(); n.put("type", "number"); n.put("description", "行数"); n.put("default", 10); props.put("lines", n);
                    schema.put("required", new JSONArray().put("path")); break;
                }
                case "read_lines": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "文件路径"); props.put("path", p);
                    JSONObject s = new JSONObject(); s.put("type", "number"); s.put("description", "起始行号（从1开始）"); props.put("start", s);
                    JSONObject e = new JSONObject(); e.put("type", "number"); e.put("description", "结束行号"); props.put("end", e);
                    schema.put("required", new JSONArray().put("path").put("start").put("end")); break;
                }
                case "batch_read": {
                    JSONObject f = new JSONObject(); f.put("type", "array"); f.put("description", "文件路径数组"); f.put("items", new JSONObject().put("type", "string")); props.put("files", f);
                    schema.put("required", new JSONArray().put("files")); break;
                }
                case "read_base64": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "文件路径"); props.put("path", p);
                    schema.put("required", new JSONArray().put("path")); break;
                }
                case "write": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "文件路径"); props.put("path", p);
                    JSONObject c = new JSONObject(); c.put("type", "string"); c.put("description", "写入内容"); props.put("content", c);
                    schema.put("required", new JSONArray().put("path").put("content")); break;
                }
                case "append": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "文件路径"); props.put("path", p);
                    JSONObject c = new JSONObject(); c.put("type", "string"); c.put("description", "追加内容"); props.put("content", c);
                    schema.put("required", new JSONArray().put("path").put("content")); break;
                }
                case "write_base64": {
                    JSONObject p = new JSONObject(); p.put("type", "string"); p.put("description", "输出文件路径"); props.put("path", p);
                    JSONObject c = new JSONObject(); c.put("type", "string"); c.put("description", "Base64编码内容"); props.put("content", c);
                    schema.put("required", new JSONArray().put("path").put("content")); break;
                }
                case "compare_files": {
                    JSONObject l = new JSONObject(); l.put("type", "string"); l.put("description", "左文件路径"); props.put("left", l);
                    JSONObject r = new JSONObject(); r.put("type", "string"); r.put("description", "右文件路径"); props.put("right", r);
                    schema.put("required", new JSONArray().put("left").put("right")); break;
                }
            }
            schema.put("properties", props);
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "read": return execRead(args);
            case "head": return execHead(args);
            case "tail": return execTail(args);
            case "read_lines": return execReadLines(args);
            case "batch_read": return execBatchRead(args);
            case "read_base64": return execReadBase64(args);
            case "write": return execWrite(args);
            case "append": return execAppend(args);
            case "write_base64": return execWriteBase64(args);
            case "compare_files": return execCompareFiles(args);
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    private String resolve(String path) { return session.resolve(path); }

    private JSONObject execRead(JSONObject args) throws Exception {
        String path = resolve(args.getString("path"));
        File f = new File(path);
        if (!f.exists()) throw new Exception("文件不存在: " + path);

        // 大文件使用流式读取，避免 OOM
        StringBuilder contentBuilder = new StringBuilder();
        long fileSize = f.length();
        if (fileSize > 10 * 1024 * 1024) { // 超过 10MB 只返回前 10MB
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    contentBuilder.append(line).append("\n");
                    if (contentBuilder.length() > 10 * 1024 * 1024) break;
                }
            }
            contentBuilder.append("\n... [文件过大，仅显示前 10MB]");
        } else {
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    contentBuilder.append(line).append("\n");
                }
            }
        }
        String content = contentBuilder.toString();

        JSONObject r = new JSONObject();
        r.put("path", f.getAbsolutePath());
        r.put("name", f.getName());
        r.put("size", f.length());
        r.put("content", content);
        r.put("line_count", content.isEmpty() ? 0 : content.split("\n", -1).length);
        r.put("last_modified", f.lastModified());
        return r;
    }

    private JSONObject execHead(JSONObject args) throws Exception {
        String path = resolve(args.getString("path"));
        int lines = args.optInt("lines", 10);
        File f = new File(path);
        if (!f.exists()) throw new Exception("文件不存在: " + path);

        StringBuilder sb = new StringBuilder();
        int count = 0;
        int totalLines = 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                totalLines++;
                if (count < lines) {
                    sb.append(line).append("\n");
                    count++;
                }
            }
        }

        JSONObject r = new JSONObject();
        r.put("path", f.getAbsolutePath());
        r.put("total_lines", totalLines);
        r.put("lines_read", count);
        r.put("content", sb.toString());
        return r;
    }

    private JSONObject execTail(JSONObject args) throws Exception {
        String path = resolve(args.getString("path"));
        int lines = args.optInt("lines", 10);
        File f = new File(path);
        if (!f.exists()) throw new Exception("文件不存在: " + path);

        // 使用双端队列存储最后 N 行
        java.util.ArrayDeque<String> queue = new java.util.ArrayDeque<>();
        int totalLines = 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                totalLines++;
                if (queue.size() >= lines) {
                    queue.pollFirst();
                }
                queue.addLast(line);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (String line : queue) {
            sb.append(line).append("\n");
        }

        JSONObject r = new JSONObject();
        r.put("path", f.getAbsolutePath());
        r.put("total_lines", totalLines);
        r.put("lines_read", queue.size());
        r.put("content", sb.toString());
        return r;
    }

    private JSONObject execReadLines(JSONObject args) throws Exception {
        String path = resolve(args.getString("path"));
        int start = args.getInt("start");
        int end = args.getInt("end");
        File f = new File(path);
        if (!f.exists()) throw new Exception("文件不存在: " + path);

        StringBuilder sb = new StringBuilder();
        int lineNum = 0;
        int totalLines = 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                totalLines++;
                lineNum++;
                if (lineNum >= start && lineNum <= end) {
                    sb.append(line).append("\n");
                }
                if (lineNum > end) break;
            }
        }

        if (start < 1) start = 1;
        if (end > totalLines) end = totalLines;

        JSONObject r = new JSONObject();
        r.put("path", f.getAbsolutePath());
        r.put("total_lines", totalLines);
        r.put("start", start);
        r.put("end", end);
        r.put("content", sb.toString());
        return r;
    }

    private JSONObject execBatchRead(JSONObject args) throws Exception {
            JSONArray files = args.optJSONArray("files");
            JSONArray results = new JSONArray();
            for (int i = 0; i < files.length(); i++) {
                String path = resolve(files.getString(i));
                File f = new File(path);
                JSONObject item = new JSONObject();
                item.put("path", path);
                if (f.exists() && f.isFile()) {
                    // 大文件使用流式读取
                    StringBuilder contentBuilder = new StringBuilder();
                    long fileSize = f.length();
                    if (fileSize > 5 * 1024 * 1024) { // 超过 5MB 只返回前 5MB
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                contentBuilder.append(line).append("\n");
                                if (contentBuilder.length() > 5 * 1024 * 1024) break;
                            }
                        }
                        contentBuilder.append("\n... [文件过大，仅显示前 5MB]");
                    } else {
                        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.FileReader(f))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                contentBuilder.append(line).append("\n");
                            }
                        }
                    }
                    item.put("exists", true);
                    item.put("size", f.length());
                    item.put("content", contentBuilder.toString());
                } else {
                    item.put("exists", false);
                    item.put("error", "文件不存在");
                }
                results.put(item);
            }
            JSONObject r = new JSONObject();
            r.put("total", files.length());
            r.put("files", results);
            return r;
        }

    private JSONObject execReadBase64(JSONObject args) throws Exception {
        String path = resolve(args.getString("path"));
        File f = new File(path);
        if (!f.exists()) throw new Exception("文件不存在: " + path);

        // 大文件使用流式读取，避免 OOM
        long fileSize = f.length();
        if (fileSize > 50 * 1024 * 1024) { // 超过 50MB 返回错误
            throw new Exception("文件过大 (" + fileSize + " bytes)，Base64 编码可能超出内存限制");
        }

        byte[] data = new byte[(int) fileSize];
        try (java.io.InputStream is = new java.io.FileInputStream(f)) {
            int offset = 0;
            int bytesRead;
            while ((bytesRead = is.read(data, offset, (int) fileSize - offset)) != -1) {
                offset += bytesRead;
                if (offset >= fileSize) break;
            }
        }
        String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
        JSONObject r = new JSONObject();
        r.put("path", f.getAbsolutePath());
        r.put("name", f.getName());
        r.put("size", data.length);
        r.put("base64", base64);
        return r;
    }

    private JSONObject execWrite(JSONObject args) throws Exception {
        String path = resolve(args.getString("path"));
        String content = args.getString("content");
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (java.io.FileWriter writer = new java.io.FileWriter(f)) {
            writer.write(content);
        }
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("path", f.getAbsolutePath());
        r.put("size", f.length());
        r.put("message", "文件已写入");
        return r;
    }

    private JSONObject execAppend(JSONObject args) throws Exception {
        String path = resolve(args.getString("path"));
        String content = args.getString("content");
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (java.io.FileWriter writer = new java.io.FileWriter(f, true)) {
            writer.write(content);
        }
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("path", f.getAbsolutePath());
        r.put("size", f.length());
        r.put("message", "内容已追加");
        return r;
    }

    private JSONObject execWriteBase64(JSONObject args) throws Exception {
        String path = resolve(args.getString("path"));
        String base64 = args.getString("content");
        byte[] data = Base64.decode(base64, Base64.NO_WRAP);
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (java.io.FileOutputStream fos = new java.io.FileOutputStream(f)) {
            fos.write(data);
        }
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("path", f.getAbsolutePath());
        r.put("size", data.length);
        r.put("message", "二进制文件已写入");
        return r;
    }

    private JSONObject execCompareFiles(JSONObject args) throws Exception {
        String leftPath = resolve(args.getString("left"));
        String rightPath = resolve(args.getString("right"));
        File left = new File(leftPath);
        File right = new File(rightPath);
        if (!left.exists()) throw new Exception("左文件不存在: " + leftPath);
        if (!right.exists()) throw new Exception("右文件不存在: " + rightPath);

        // 使用流式读取，逐行对比
        JSONArray diffs = new JSONArray();
        int leftLines = 0;
        int rightLines = 0;
        int lineNum = 0;

        try (java.io.BufferedReader leftBr = new java.io.BufferedReader(new java.io.FileReader(left));
             java.io.BufferedReader rightBr = new java.io.BufferedReader(new java.io.FileReader(right))) {

            String leftLine;
            String rightLine;
            while ((leftLine = leftBr.readLine()) != null | (rightLine = rightBr.readLine()) != null) {
                lineNum++;
                if (leftLine != null) leftLines++;
                if (rightLine != null) rightLines++;

                if (!java.util.Objects.equals(leftLine, rightLine)) {
                    JSONObject diff = new JSONObject();
                    diff.put("line", lineNum);
                    diff.put("left", leftLine != null ? leftLine : "(EOF)");
                    diff.put("right", rightLine != null ? rightLine : "(EOF)");
                    diffs.put(diff);
                }
            }
        }

        JSONObject result = new JSONObject();
        result.put("left_file", left.getAbsolutePath());
        result.put("right_file", right.getAbsolutePath());
        result.put("left_lines", leftLines);
        result.put("right_lines", rightLines);
        result.put("differences", diffs.length());
        result.put("diffs", diffs);
        return result;
    }
}