package com.mcp_run.tools;

import android.content.Context;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;

/**
 * 图片信息工具 - 获取图片的尺寸、格式、大小等信息
 */
public class ImageInfoTool implements MCPTool {
    @Override
    public String getName() {
        return "image_info";
    }

    @Override
    public String getDescription() {
        return "获取图片文件的详细信息，包括尺寸、格式、文件大小、DPI等。支持JPEG、PNG、WebP、GIF、BMP等格式。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();

            JSONObject pathProp = new JSONObject();
            pathProp.put("type", "string");
            pathProp.put("description", "图片文件路径");
            props.put("path", pathProp);

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
        File file = new File(path);

        if (!file.exists()) {
            throw new Exception("文件不存在: " + path);
        }
        if (!file.isFile()) {
            throw new Exception("路径不是文件: " + path);
        }

        // 读取图片尺寸
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (FileInputStream fis = new FileInputStream(file)) {
            BitmapFactory.decodeStream(fis, null, options);
        }

        String mimeType = options.outMimeType != null ? options.outMimeType : "unknown";
        String format = mimeType.replace("image/", "").toUpperCase();

        JSONObject result = new JSONObject();
        result.put("path", file.getAbsolutePath());
        result.put("name", file.getName());
        result.put("file_size", file.length());
        result.put("file_size_display", formatFileSize(file.length()));
        result.put("width", options.outWidth);
        result.put("height", options.outHeight);
        result.put("mime_type", mimeType);
        result.put("format", format);
        result.put("bit_depth", options.outConfig != null ? options.outConfig.toString() : "unknown");
        result.put("last_modified", file.lastModified());

        if (options.outWidth > 0 && options.outHeight > 0) {
            result.put("aspect_ratio", String.format("%.2f", (float) options.outWidth / options.outHeight));
            result.put("megapixels", String.format("%.1f", (options.outWidth * options.outHeight) / 1000000.0));
        }

        return result;
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}