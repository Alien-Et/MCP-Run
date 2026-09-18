package com.mcp_run.tools;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * WiFi 扫描工具 - 扫描附近 WiFi 网络
 */
public class WifiScanTool implements MCPTool {
    @Override
    public String getName() {
        return "wifi_scan";
    }

    @Override
    public String getDescription() {
        return "扫描附近 WiFi 网络并返回详细信息（SSID、BSSID、信号强度等）。需要定位权限。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            schema.put("properties", new JSONObject());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        // 检查定位权限（Android 12+ 还需要 BLUETOOTH_SCAN 和 NEARBY_WIFI_DEVICES）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            boolean hasLocationPermission = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            boolean hasBluetoothPermission = context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED;
            boolean hasNearbyWifiPermission = context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED;
            
            if (!hasLocationPermission && !hasBluetoothPermission && !hasNearbyWifiPermission) {
                throw new Exception("缺少权限: ACCESS_FINE_LOCATION 或 BLUETOOTH_SCAN 或 NEARBY_WIFI_DEVICES");
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: ACCESS_FINE_LOCATION");
            }
        }

        WifiManager wifi = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);

        JSONObject result = new JSONObject();

        // 发起扫描
        boolean started = wifi.startScan();
        result.put("scan_started", started);

        // 获取扫描结果
        List<android.net.wifi.ScanResult> results = wifi.getScanResults();
        JSONArray networks = new JSONArray();

        if (results != null) {
            for (android.net.wifi.ScanResult scanResult : results) {
                JSONObject network = new JSONObject();
                network.put("ssid", scanResult.SSID);
                network.put("bssid", scanResult.BSSID);
                network.put("frequency_mhz", scanResult.frequency);
                network.put("signal_strength", scanResult.level);
                network.put("capabilities", scanResult.capabilities);

                // 计算信号质量
                int signal = scanResult.level;
                if (signal >= -50) {
                    network.put("signal_quality", "excellent");
                } else if (signal >= -60) {
                    network.put("signal_quality", "good");
                } else if (signal >= -70) {
                    network.put("signal_quality", "fair");
                } else {
                    network.put("signal_quality", "poor");
                }

                networks.put(network);
            }
        }

        result.put("network_count", networks.length());
        result.put("networks", networks);
        return result;
    }
}
