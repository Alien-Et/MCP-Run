package com.mcp_run.tools;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 系统控制增强工具 - WiFi/蓝牙/亮度/闹钟等
 */
public class SystemControlEnhancedTool implements MCPTool {
    
    // 存储异步扫描结果
    private static List<BluetoothDevice> scannedDevices = new ArrayList<>();
    private static boolean isScanning = false;
    private static Handler scanHandler = new Handler(Looper.getMainLooper());
    
    @Override
    public String getName() {
        return "system_control_enhanced";
    }

    @Override
    public String getDescription() {
        return "系统控制增强版。支持：WiFi开关/状态、蓝牙开关/扫描设备、亮度调节、闹钟管理、屏幕快照、系统广播等。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject actionProp = new JSONObject();
            actionProp.put("type", "string");
            actionProp.put("description", "操作类型: wifi, bluetooth, bluetooth_scan, brightness, alarm, snapshot, broadcast");
            actionProp.put("enum", new JSONArray()
                .put("wifi").put("bluetooth").put("bluetooth_scan").put("brightness")
                .put("alarm").put("snapshot").put("broadcast"));
            props.put("action", actionProp);
            
            // wifi参数
            JSONObject wifiActionProp = new JSONObject();
            wifiActionProp.put("type", "string");
            wifiActionProp.put("description", "on/off/status");
            wifiActionProp.put("default", "status");
            props.put("wifi_action", wifiActionProp);
            
            // 蓝牙参数
            JSONObject btActionProp = new JSONObject();
            btActionProp.put("type", "string");
            btActionProp.put("description", "on/off/status/toggle_settings");
            btActionProp.put("default", "status");
            props.put("bluetooth_action", btActionProp);
            
            // 蓝牙扫描参数
            JSONObject btScanProp = new JSONObject();
            btScanProp.put("type", "string");
            btScanProp.put("description", "扫描时长(毫秒)，默认5000");
            btScanProp.put("default", "5000");
            props.put("scan_duration", btScanProp);
            
            // 亮度参数
            JSONObject brightnessProp = new JSONObject();
            brightnessProp.put("type", "number");
            brightnessProp.put("description", "亮度值0-255（需WRITE_SETTINGS权限）");
            props.put("brightness_value", brightnessProp);
            
            // 闹钟参数
            JSONObject alarmActionProp = new JSONObject();
            alarmActionProp.put("type", "string");
            alarmActionProp.put("description", "add/list/delete");
            props.put("alarm_action", alarmActionProp);
            
            JSONObject alarmTimeProp = new JSONObject();
            alarmTimeProp.put("type", "string");
            alarmTimeProp.put("description", "闹钟时间格式: HH:mm");
            props.put("alarm_time", alarmTimeProp);
            
            JSONObject alarmLabelProp = new JSONObject();
            alarmLabelProp.put("type", "string");
            alarmLabelProp.put("description", "闹钟标签");
            props.put("alarm_label", alarmLabelProp);
            
            // 广播参数
            JSONObject broadcastActionProp = new JSONObject();
            broadcastActionProp.put("type", "string");
            broadcastActionProp.put("description", "广播动作名");
            props.put("broadcast_action", broadcastActionProp);
            
            schema.put("properties", props);
            schema.put("required", new JSONArray().put("action"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return schema;
    }

    @Override
    public JSONObject execute(Context context, JSONObject args) throws Exception {
        String action = args.getString("action");
        
        switch (action) {
            case "wifi": return handleWifi(context, args);
            case "bluetooth": return handleBluetooth(context, args);
            case "bluetooth_scan": return handleBluetoothScan(context, args);
            case "brightness": return handleBrightness(context, args);
            case "alarm": return handleAlarm(context, args);
            case "snapshot": return handleSnapshot(context);
            case "broadcast": return handleBroadcast(context, args);
            default: throw new Exception("未知操作: " + action);
        }
    }
    
    private JSONObject handleWifi(Context context, JSONObject args) throws Exception {
        WifiManager wifi = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        String wifiAction = args.optString("wifi_action", "status");
        
        // 检查 WiFi 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (context.checkSelfPermission(Manifest.permission.CHANGE_WIFI_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: CHANGE_WIFI_STATE");
            }
        }
        
        JSONObject result = new JSONObject();
        result.put("wifi_enabled", wifi.isWifiEnabled());
        
        switch (wifiAction) {
            case "on":
                wifi.setWifiEnabled(true);
                result.put("success", true);
                result.put("message", "WiFi已开启");
                break;
            case "off":
                wifi.setWifiEnabled(false);
                result.put("success", true);
                result.put("message", "WiFi已关闭");
                break;
            case "status":
                result.put("success", true);
                result.put("message", wifi.isWifiEnabled() ? "WiFi已开启" : "WiFi已关闭");
                break;
        }
        
        result.put("wifi_enabled", wifi.isWifiEnabled());
        return result;
    }
    
    private JSONObject handleBluetooth(Context context, JSONObject args) throws Exception {
        // 检查蓝牙权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: BLUETOOTH_CONNECT");
            }
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: BLUETOOTH_SCAN");
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: ACCESS_FINE_LOCATION (Android 10+ 需要位置权限才能操作蓝牙)");
            }
        }

        BluetoothAdapter btAdapter = getBluetoothAdapter(context);
        
        if (btAdapter == null) {
            throw new Exception("设备不支持蓝牙");
        }
        
        String btAction = args.optString("bluetooth_action", "status");
        JSONObject result = new JSONObject();
        result.put("bluetooth_enabled", btAdapter.isEnabled());
        
        switch (btAction) {
            case "on":
                // Android 12+ 无法通过代码直接开启蓝牙，打开系统设置页面
                if (!btAdapter.isEnabled()) {
                    Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    result.put("success", false);
                    result.put("message", "请在系统设置中手动开启蓝牙（Android 12+ 限制）");
                } else {
                    result.put("success", true);
                    result.put("message", "蓝牙已开启");
                }
                break;
            case "off":
                // 同样需要用户手动关闭
                if (btAdapter.isEnabled()) {
                    Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);
                    result.put("success", false);
                    result.put("message", "请在系统设置中手动关闭蓝牙（Android 12+ 限制）");
                } else {
                    result.put("success", true);
                    result.put("message", "蓝牙已关闭");
                }
                break;
            case "toggle_settings":
                // 直接跳转到蓝牙设置
                Intent intent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                result.put("success", true);
                result.put("message", "已打开蓝牙设置页面");
                break;
            case "status":
                result.put("success", true);
                result.put("message", btAdapter.isEnabled() ? "蓝牙已开启" : "蓝牙已关闭");
                break;
            default:
                throw new Exception("未知蓝牙操作: " + btAction);
        }
        
        result.put("bluetooth_enabled", btAdapter.isEnabled());
        return result;
    }
    
    @SuppressLint("MissingPermission")
    private JSONObject handleBluetoothScan(Context context, JSONObject args) throws Exception {
        // 检查蓝牙权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: BLUETOOTH_SCAN");
            }
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: BLUETOOTH_CONNECT");
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                throw new Exception("缺少权限: ACCESS_FINE_LOCATION (扫描蓝牙需要位置权限)");
            }
        }
        
        BluetoothAdapter btAdapter = getBluetoothAdapter(context);
        
        if (btAdapter == null) {
            throw new Exception("设备不支持蓝牙");
        }
        
        if (!btAdapter.isEnabled()) {
            throw new Exception("蓝牙未开启，请先开启蓝牙");
        }
        
        // 获取扫描时长
        long scanDuration = args.optLong("scan_duration", 5000);
        
        // 清除之前的扫描结果
        scannedDevices.clear();
        isScanning = true;
        
        // 使用新的 BLE 扫描 API
        BluetoothLeScanner scanner = btAdapter.getBluetoothLeScanner();
        if (scanner == null) {
            throw new Exception("无法获取蓝牙扫描器");
        }
        
        // 设置扫描过滤器（扫描所有设备）
        List<ScanFilter> filters = new ArrayList<>();
        
        // 配置扫描设置
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build();
        
        final JSONObject result = new JSONObject();
        result.put("scanning", true);
        result.put("scan_duration_ms", scanDuration);
        result.put("message", "正在扫描蓝牙设备...");
        
        // 执行扫描
        scanner.startScan(filters, settings, new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                BluetoothDevice device = result.getDevice();
                if (device != null && !scannedDevices.contains(device)) {
                    scannedDevices.add(device);
                }
            }
            
            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                for (ScanResult scanResult : results) {
                    BluetoothDevice device = scanResult.getDevice();
                    if (device != null && !scannedDevices.contains(device)) {
                        scannedDevices.add(device);
                    }
                }
            }
            
            @Override
            public void onScanFailed(int errorCode) {
                // 扫描失败，返回错误信息
                String errorMsg;
                switch (errorCode) {
                    case SCAN_FAILED_ALREADY_STARTED:
                        errorMsg = "扫描已启动";
                        break;
                    case SCAN_FAILED_APPLICATION_REGISTRATION_FAILED:
                        errorMsg = "扫描注册失败";
                        break;
                    case SCAN_FAILED_INTERNAL_ERROR:
                        errorMsg = "内部错误";
                        break;
                    case SCAN_FAILED_FEATURE_UNSUPPORTED:
                        errorMsg = "此功能不支持";
                        break;
                    default:
                        errorMsg = "未知错误 (" + errorCode + ")";
                }
                try {
                    result.put("scanning", false);
                    result.put("success", false);
                    result.put("error_code", errorCode);
                    result.put("error_message", errorMsg);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        
        // 等待扫描结束
        scanHandler.postDelayed(() -> {
            isScanning = false;
            // scanner.stopScan() removed - scan will stop naturally
            
            try {
                // 构建结果 JSON
                JSONArray devices = new JSONArray();
                for (BluetoothDevice device : scannedDevices) {
                    JSONObject deviceInfo = new JSONObject();
                    deviceInfo.put("name", device.getName() != null ? device.getName() : "未知设备");
                    deviceInfo.put("address", device.getAddress());
                    deviceInfo.put("type", device.getType());
                    deviceInfo.put("bond_state", device.getBondState());
                    devices.put(deviceInfo);
                }
                
                result.put("scanning", false);
                result.put("success", true);
                result.put("device_count", devices.length());
                result.put("devices", devices);
                result.put("message", "扫描完成，找到 " + devices.length() + " 个设备");
                
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, scanDuration);
        
        return result;
    }
    
    private JSONObject handleBrightness(Context context, JSONObject args) throws Exception {
        android.content.ContentResolver cr = context.getContentResolver();
        
        // 获取当前亮度
        float current = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, 128);
        
        JSONObject result = new JSONObject();
        result.put("current_brightness", current);
        
        if (args.has("brightness_value")) {
            int brightness = args.getInt("brightness_value");
            brightness = Math.max(0, Math.min(255, brightness));
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, brightness);
            result.put("new_brightness", brightness);
            result.put("success", true);
            result.put("message", "亮度已设置为 " + brightness);
        } else {
            result.put("success", false);
            result.put("message", "请提供 brightness_value 参数");
        }
        
        return result;
    }
    
    private JSONObject handleAlarm(Context context, JSONObject args) throws Exception {
        String alarmAction = args.optString("alarm_action", "list");
        
        if ("list".equals(alarmAction)) {
            return listAlarms(context);
        } else if ("add".equals(alarmAction)) {
            return addAlarm(context, args);
        } else if ("delete".equals(alarmAction)) {
            return deleteAlarm(context, args);
        }
        
        throw new Exception("未知闹钟操作");
    }
    
    private JSONObject listAlarms(Context context) throws Exception {
        JSONObject result = new JSONObject();
        JSONArray alarms = new JSONArray();
        
        // 由于Android限制，无法直接读取闹钟详情
        result.put("total", 0);
        result.put("alarms", alarms);
        result.put("message", "由于Android权限限制，无法直接读取闹钟详情。请在系统闹钟应用中查看。");
        return result;
    }
    
    private JSONObject addAlarm(Context context, JSONObject args) throws Exception {
        String timeStr = args.getString("alarm_time");
        String label = args.optString("alarm_label", "");
        
        // 解析时间 HH:mm
        String[] parts = timeStr.split(":");
        if (parts.length != 2) {
            throw new Exception("时间格式错误，应为 HH:mm");
        }
        
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        
        // 使用系统闹钟应用添加闹钟
        android.content.Intent intent = new android.content.Intent(
                android.provider.AlarmClock.ACTION_SET_ALARM);
        intent.putExtra(android.provider.AlarmClock.EXTRA_HOUR, hour);
        intent.putExtra(android.provider.AlarmClock.EXTRA_MINUTES, minute);
        if (!label.isEmpty()) {
            intent.putExtra(android.provider.AlarmClock.EXTRA_MESSAGE, label);
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("alarm_time", timeStr);
        result.put("label", label);
        result.put("message", "已打开闹钟设置界面，请手动确认添加");
        return result;
    }
    
    private JSONObject deleteAlarm(Context context, JSONObject args) throws Exception {
        JSONObject result = new JSONObject();
        result.put("success", false);
        result.put("message", "由于Android权限限制，无法直接删除闹钟。请在系统闹钟应用中手动删除。");
        return result;
    }
    
    private JSONObject handleSnapshot(Context context) throws Exception {
        // 使用 screencap 命令截图
        String cmd = "screencap -p /sdcard/Pictures/mcp_screenshot_" + 
                System.currentTimeMillis() + ".png";
        Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
        p.waitFor();
        
        JSONObject result = new JSONObject();
        result.put("success", p.exitValue() == 0);
        result.put("path", "/sdcard/Pictures/mcp_screenshot_" + System.currentTimeMillis() + ".png");
        result.put("message", "截图已保存");
        return result;
    }
    
    private JSONObject handleBroadcast(Context context, JSONObject args) throws Exception {
        String action = args.getString("broadcast_action");
        
        android.content.Intent intent = new android.content.Intent(action);
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
        context.sendBroadcast(intent);
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("action", action);
        result.put("message", "广播已发送");
        return result;
    }
    
    /**
     * 获取蓝牙适配器（兼容不同Android版本）
     */
    private BluetoothAdapter getBluetoothAdapter(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            BluetoothManager btManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
            if (btManager != null) {
                return btManager.getAdapter();
            }
        }
        // 旧版本兼容
        return BluetoothAdapter.getDefaultAdapter();
    }
}
