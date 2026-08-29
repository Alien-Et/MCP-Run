package com.mcp_run.tools;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 截屏工具 - 通过对View绘制实现截图
 * 注意：需要Activity的Window作为根布局
 */
public class ScreenshotTool implements MCPTool {
    private static View rootView;
    
    /**
     * 设置根视图，用于截图
     */
    public static void setRootView(View view) {
        rootView = view;
    }

    @Override
    public String getName() {
        return "screenshot";
    }

    @Override
    public String getDescription() {
        return "截取设备屏幕并保存为PNG图片文件。需要Activity的根视图引用。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject pathProp = new JSONObject();
            pathProp.put("type", "string");
            pathProp.put("description", "保存路径（可选，默认保存在Pictures目录下）");
            props.put("save_path", pathProp);
            
            JSONObject qualityProp = new JSONObject();
            qualityProp.put("type", "number");
            qualityProp.put("description", "JPEG质量（1-100），默认100");
            qualityProp.put("default", 100);
            props.put("quality", qualityProp);
            
            schema.put("properties", props);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        if (rootView == null) {
            throw new Exception("根视图未设置，请先打开MCP·AC应用界面");
        }
        
        // 生成默认路径
        String savePath = args.optString("save_path", null);
        if (savePath == null || savePath.isEmpty()) {
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    .format(new Date());
            savePath = context.getExternalFilesDir("Pictures") + "/mcp_screenshot_" + timestamp + ".png";
        }
        
        // 截图
        rootView.setDrawingCacheEnabled(true);
        Bitmap bitmap = Bitmap.createBitmap(rootView.getDrawingCache());
        rootView.setDrawingCacheEnabled(false);
        
        // 保存文件
        File file = new File(savePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        
        FileOutputStream fos = new FileOutputStream(file);
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.flush();
        fos.close();
        bitmap.recycle();
        
        JSONObject result = new JSONObject();
        result.put("path", file.getAbsolutePath());
        result.put("size", file.length());
        result.put("width", bitmap.getWidth() > 0 ? bitmap.getWidth() : 
                context.getResources().getDisplayMetrics().widthPixels);
        result.put("height", bitmap.getHeight() > 0 ? bitmap.getHeight() : 
                context.getResources().getDisplayMetrics().heightPixels);
        result.put("format", "PNG");
        result.put("success", true);
        return result;
    }
}