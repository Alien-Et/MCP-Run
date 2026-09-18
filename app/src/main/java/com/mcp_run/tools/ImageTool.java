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

/**
 * 图片处理工具集 - image_info, image_resize, image_convert, image_crop, image_rotate, image_to_base64, base64_to_image
 */
public class ImageTool implements MCPTool {
    private final String toolName;

    public ImageTool(String toolName) { this.toolName = toolName; }

    @Override public String getName() { return toolName; }

    @Override
    public String getDescription() {
        switch (toolName) {
            case "image_info": return "查看图片基本信息（尺寸、格式、文件大小、颜色深度等）";
            case "image_resize": return "缩放图片到指定宽高，保持比例或强制拉伸";
            case "image_convert": return "转换图片格式（如PNG转JPEG、WebP等）";
            case "image_crop": return "裁剪图片指定区域（x, y, width, height）";
            case "image_rotate": return "旋转图片指定角度（90, 180, 270等）";
            case "image_to_base64": return "将图片文件转为Base64编码字符串";
            case "base64_to_image": return "将Base64编码数据写回为图片文件";
            default: return "图片处理工具";
        }
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            JSONObject path = new JSONObject(); path.put("type", "string"); path.put("description", "图片路径"); props.put("path", path);

            switch (toolName) {
                case "image_info":
                    schema.put("required", new JSONArray().put("path")); break;
                case "image_resize": {
                    JSONObject w = new JSONObject(); w.put("type", "number"); w.put("description", "目标宽度"); props.put("width", w);
                    JSONObject h = new JSONObject(); h.put("type", "number"); h.put("description", "目标高度"); props.put("height", h);
                    JSONObject o = new JSONObject(); o.put("type", "string"); o.put("description", "输出路径"); props.put("output", o);
                    schema.put("required", new JSONArray().put("path").put("output").put("width").put("height")); break;
                }
                case "image_convert": {
                    JSONObject o = new JSONObject(); o.put("type", "string"); o.put("description", "输出路径（后缀决定格式如 .jpg .png .webp）"); props.put("output", o);
                    schema.put("required", new JSONArray().put("path").put("output")); break;
                }
                case "image_crop": {
                    JSONObject x = new JSONObject(); x.put("type", "number"); x.put("description", "起始X坐标"); props.put("x", x);
                    JSONObject y = new JSONObject(); y.put("type", "number"); y.put("description", "起始Y坐标"); props.put("y", y);
                    JSONObject w = new JSONObject(); w.put("type", "number"); w.put("description", "裁剪宽度"); props.put("width", w);
                    JSONObject h = new JSONObject(); h.put("type", "number"); h.put("description", "裁剪高度"); props.put("height", h);
                    JSONObject o = new JSONObject(); o.put("type", "string"); o.put("description", "输出路径"); props.put("output", o);
                    schema.put("required", new JSONArray().put("path").put("output").put("x").put("y").put("width").put("height")); break;
                }
                case "image_rotate": {
                    JSONObject d = new JSONObject(); d.put("type", "number"); d.put("description", "旋转角度（90, 180, 270）"); props.put("degrees", d);
                    JSONObject o = new JSONObject(); o.put("type", "string"); o.put("description", "输出路径"); props.put("output", o);
                    schema.put("required", new JSONArray().put("path").put("output").put("degrees")); break;
                }
                case "image_to_base64":
                    schema.put("required", new JSONArray().put("path")); break;
                case "base64_to_image": {
                    JSONObject c = new JSONObject(); c.put("type", "string"); c.put("description", "Base64内容"); props.put("content", c);
                    JSONObject o = new JSONObject(); o.put("type", "string"); o.put("description", "输出路径"); props.put("path", o);
                    schema.put("required", new JSONArray().put("path").put("content")); break;
                }
            }
            schema.put("properties", props);
        } catch (Exception ignored) {}
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        switch (toolName) {
            case "image_info": return info(args);
            case "image_resize": return resize(args);
            case "image_convert": return convert(args);
            case "image_crop": return crop(args);
            case "image_rotate": return rotate(args);
            case "image_to_base64": return toBase64(args);
            case "base64_to_image": return fromBase64(args);
            default: throw new Exception("未知操作: " + toolName);
        }
    }

    private Bitmap loadBitmap(String path) throws Exception {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bm = BitmapFactory.decodeFile(path, opts);
        if (bm == null) throw new Exception("无法解码图片: " + path);
        return bm;
    }

    private void saveBitmap(Bitmap bm, String path) throws Exception {
        String lower = path.toLowerCase();
        Bitmap.CompressFormat fmt;
        if (lower.endsWith(".png")) fmt = Bitmap.CompressFormat.PNG;
        else if (lower.endsWith(".webp")) fmt = Bitmap.CompressFormat.WEBP;
        else fmt = Bitmap.CompressFormat.JPEG;
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(f)) {
            bm.compress(fmt, 90, fos);
        }
    }

    private JSONObject info(JSONObject args) throws Exception {
        String path = args.getString("path");
        File f = new File(path);
        if (!f.exists()) throw new Exception("文件不存在: " + path);
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, opts);
        JSONObject r = new JSONObject();
        r.put("path", f.getAbsolutePath());
        r.put("name", f.getName());
        r.put("file_size", f.length());
        r.put("width", opts.outWidth);
        r.put("height", opts.outHeight);
        r.put("mime_type", opts.outMimeType);
        r.put("format", opts.outMimeType != null ? opts.outMimeType.replace("image/", "").toUpperCase() : "unknown");
        r.put("bit_depth", opts.outConfig != null ? opts.outConfig.toString() : "unknown");
        return r;
    }

    private JSONObject resize(JSONObject args) throws Exception {
        String path = args.getString("path");
        int w = args.getInt("width");
        int h = args.getInt("height");
        String output = args.getString("output");
        Bitmap bm = loadBitmap(path);
        Bitmap resized = Bitmap.createScaledBitmap(bm, w, h, true);
        saveBitmap(resized, output);
        bm.recycle(); resized.recycle();
        JSONObject r = new JSONObject(); r.put("success", true); r.put("output", output); r.put("width", w); r.put("height", h); return r;
    }

    private JSONObject convert(JSONObject args) throws Exception {
        String path = args.getString("path");
        String output = args.getString("output");
        Bitmap bm = loadBitmap(path);
        saveBitmap(bm, output);
        bm.recycle();
        JSONObject r = new JSONObject(); r.put("success", true); r.put("output", output); return r;
    }

    private JSONObject crop(JSONObject args) throws Exception {
        String path = args.getString("path");
        int x = args.getInt("x"); int y = args.getInt("y");
        int w = args.getInt("width"); int h = args.getInt("height");
        String output = args.getString("output");
        Bitmap bm = loadBitmap(path);
        Bitmap cropped = Bitmap.createBitmap(bm, x, y, w, h);
        saveBitmap(cropped, output);
        bm.recycle(); cropped.recycle();
        JSONObject r = new JSONObject(); r.put("success", true); r.put("output", output); return r;
    }

    private JSONObject rotate(JSONObject args) throws Exception {
        String path = args.getString("path");
        float degrees = (float) args.getDouble("degrees");
        String output = args.getString("output");
        Bitmap bm = loadBitmap(path);
        android.graphics.Matrix matrix = new android.graphics.Matrix();
        matrix.postRotate(degrees);
        Bitmap rotated = Bitmap.createBitmap(bm, 0, 0, bm.getWidth(), bm.getHeight(), matrix, true);
        saveBitmap(rotated, output);
        bm.recycle(); rotated.recycle();
        JSONObject r = new JSONObject(); r.put("success", true); r.put("output", output); r.put("degrees", degrees); return r;
    }

    private JSONObject toBase64(JSONObject args) throws Exception {
        String path = args.getString("path");
        File f = new File(path);
        if (!f.exists()) throw new Exception("文件不存在: " + path);
        byte[] data = java.nio.file.Files.readAllBytes(f.toPath());
        String base64 = Base64.encodeToString(data, Base64.NO_WRAP);
        JSONObject r = new JSONObject();
        r.put("path", f.getAbsolutePath());
        r.put("size", data.length);
        r.put("base64", base64);
        return r;
    }

    private JSONObject fromBase64(JSONObject args) throws Exception {
        String path = args.getString("path");
        String content = args.getString("content");
        byte[] data = Base64.decode(content, Base64.NO_WRAP);
        File f = new File(path);
        if (f.getParentFile() != null) f.getParentFile().mkdirs();
        java.nio.file.Files.write(f.toPath(), data);
        JSONObject r = new JSONObject();
        r.put("success", true);
        r.put("path", f.getAbsolutePath());
        r.put("size", data.length);
        return r;
    }
}