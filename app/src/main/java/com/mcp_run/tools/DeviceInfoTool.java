package com.mcp_run.tools;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;
import android.telephony.TelephonyManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;

/**
 * 设备信息工具
 */
public class DeviceInfoTool implements MCPTool {
    @Override
    public String getName() {
        return "device_info";
    }

    @Override
    public String getDescription() {
        return "获取Android设备的详细信息，包括硬件、系统、存储、网络等信息。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", new JSONObject());
            schema.put("description", "无需参数，直接返回设备信息");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        JSONObject result = new JSONObject();
        
        // 系统信息
        JSONObject system = new JSONObject();
        system.put("android_version", Build.VERSION.RELEASE);
        system.put("api_level", Build.VERSION.SDK_INT);
        system.put("build_id", Build.DISPLAY);
        system.put("build_type", Build.TYPE);
        system.put("build_time", Build.TIME);
        system.put("security_patch", Build.VERSION.SECURITY_PATCH);
        result.put("system", system);
        
        // 硬件信息
        JSONObject hardware = new JSONObject();
        hardware.put("brand", Build.BRAND);
        hardware.put("manufacturer", Build.MANUFACTURER);
        hardware.put("model", Build.MODEL);
        hardware.put("device", Build.DEVICE);
        hardware.put("product", Build.PRODUCT);
        hardware.put("board", Build.BOARD);
        hardware.put("hardware", Build.HARDWARE);
        hardware.put("cpu_abi", Build.CPU_ABI);
        hardware.put("cpu_abi2", Build.CPU_ABI2);
        hardware.put("supported_abis", new JSONArray(Build.SUPPORTED_ABIS));
        result.put("hardware", hardware);
        
        // 存储信息
        JSONObject storage = new JSONObject();
        File dataDir = context.getDataDir();
        if (dataDir != null) {
            storage.put("data_path", dataDir.getAbsolutePath());
            storage.put("data_total", new File(dataDir.getAbsolutePath()).getTotalSpace());
            storage.put("data_free", new File(dataDir.getAbsolutePath()).getFreeSpace());
            storage.put("data_usable", new File(dataDir.getAbsolutePath()).getUsableSpace());
        }
        File externalDir = context.getExternalFilesDir(null);
        if (externalDir != null) {
            storage.put("external_path", externalDir.getAbsolutePath());
            storage.put("external_total", externalDir.getTotalSpace());
            storage.put("external_free", externalDir.getFreeSpace());
            storage.put("external_usable", externalDir.getUsableSpace());
        }
        storage.put("internal_storage_total", new File("/data").getTotalSpace());
        storage.put("internal_storage_free", new File("/data").getFreeSpace());
        result.put("storage", storage);
        
        // 设备标识
        JSONObject identity = new JSONObject();
        identity.put("device_id", Settings.Secure.getString(
                context.getContentResolver(), Settings.Secure.ANDROID_ID));
        identity.put("package_name", context.getPackageName());
        identity.put("app_version", context.getPackageManager()
                .getPackageInfo(context.getPackageName(), 0).versionName);
        result.put("identity", identity);
        
        // 显示信息
        JSONObject display = new JSONObject();
        display.put("density", context.getResources().getDisplayMetrics().densityDpi);
        display.put("width_pixels", context.getResources().getDisplayMetrics().widthPixels);
        display.put("height_pixels", context.getResources().getDisplayMetrics().heightPixels);
        display.put("scaled_density", context.getResources().getDisplayMetrics().scaledDensity);
        result.put("display", display);
        
        return result;
    }
}