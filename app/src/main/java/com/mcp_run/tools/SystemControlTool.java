package com.mcp_run.tools;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * 系统控制工具 - 设备系统级别的控制功能
 */
public class SystemControlTool implements MCPTool {
    @Override
    public String getName() {
        return "system_control";
    }

    @Override
    public String getDescription() {
        return "设备系统控制。支持：调节音量、开关WiFi/蓝牙/蓝牙、切换静音模式、锁屏、重启SystemUI、打开开发者选项、获取系统属性、执行按键模拟等。";
    }

    @Override
    public JSONObject getInputSchema() {
        JSONObject schema = new JSONObject();
        try {
            schema.put("type", "object");
            JSONObject props = new JSONObject();
            
            JSONObject actionProp = new JSONObject();
            actionProp.put("type", "string");
            actionProp.put("description", "操作类型: volume, wifi, screenshot_system, silent_mode, lock_screen, restart_systemui, system_property, key_event, open_developer, open_accessibility, battery_info");
            actionProp.put("enum", new JSONArray()
                    .put("volume").put("wifi").put("screenshot_system")
                    .put("silent_mode").put("lock_screen").put("restart_systemui")
                    .put("system_property").put("key_event").put("open_developer")
                    .put("open_accessibility").put("battery_info"));
            props.put("action", actionProp);
            
            // volume参数
            JSONObject streamTypeProp = new JSONObject();
            streamTypeProp.put("type", "string");
            streamTypeProp.put("description", "音量类型: music, ring, notification, alarm, system, call (volume操作时使用)");
            streamTypeProp.put("default", "music");
            props.put("stream_type", streamTypeProp);
            
            JSONObject levelProp = new JSONObject();
            levelProp.put("type", "number");
            levelProp.put("description", "音量值0-100，或使用up/down/mute (volume操作时使用)");
            props.put("level", levelProp);
            
            // wifi参数
            JSONObject wifiActionProp = new JSONObject();
            wifiActionProp.put("type", "string");
            wifiActionProp.put("description", "on/off/status (wifi操作时使用)");
            wifiActionProp.put("default", "status");
            props.put("wifi_action", wifiActionProp);
            
            // 按键事件参数
            JSONObject keyProp = new JSONObject();
            keyProp.put("type", "string");
            keyProp.put("description", "按键名称: KEYCODE_HOME, KEYCODE_BACK, KEYCODE_APP_SWITCH, KEYCODE_VOLUME_UP, KEYCODE_VOLUME_DOWN, KEYCODE_POWER, KEYCODE_MENU");
            props.put("key_code", keyProp);
            
            // 系统属性
            JSONObject propProp = new JSONObject();
            propProp.put("type", "string");
            propProp.put("description", "系统属性名，如 ro.build.display.id, persist.sys.timezone");
            props.put("property", propProp);
            
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
            case "volume":
                return handleVolume(context, args);
            case "wifi":
                return handleWifi(context, args);
            case "screenshot_system":
                return handleScreenshot(context);
            case "silent_mode":
                return handleSilentMode(context, args);
            case "lock_screen":
                return handleLockScreen(context);
            case "restart_systemui":
                return handleRestartSystemUI();
            case "system_property":
                return handleSystemProperty(args);
            case "key_event":
                return handleKeyEvent(args);
            case "open_developer":
                return openDeveloperSettings(context);
            case "open_accessibility":
                return openAccessibilitySettings(context);
            case "battery_info":
                return getBatteryInfo(context);
            default:
                throw new Exception("未知操作: " + action);
        }
    }
    
    private JSONObject handleVolume(Context context, JSONObject args) throws Exception {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        String streamType = args.optString("stream_type", "music");
        int stream;
        switch (streamType) {
            case "ring": stream = AudioManager.STREAM_RING; break;
            case "notification": stream = AudioManager.STREAM_NOTIFICATION; break;
            case "alarm": stream = AudioManager.STREAM_ALARM; break;
            case "system": stream = AudioManager.STREAM_SYSTEM; break;
            case "call": stream = AudioManager.STREAM_VOICE_CALL; break;
            default: stream = AudioManager.STREAM_MUSIC;
        }
        
        int maxVol = audio.getStreamMaxVolume(stream);
        int currentVol = audio.getStreamVolume(stream);
        
        JSONObject result = new JSONObject();
        result.put("stream_type", streamType);
        result.put("max_volume", maxVol);
        result.put("current_volume", currentVol);
        
        if (args.has("level")) {
            Object level = args.get("level");
            if (level instanceof Number) {
                int vol = ((Number) level).intValue();
                vol = Math.max(0, Math.min(vol, maxVol));
                audio.setStreamVolume(stream, vol, AudioManager.FLAG_SHOW_UI);
                result.put("new_volume", vol);
                result.put("message", "音量已设置为 " + vol);
            } else if (level instanceof String) {
                String s = (String) level;
                switch (s) {
                    case "up":
                        audio.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI);
                        result.put("message", "音量已增加");
                        break;
                    case "down":
                        audio.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                        result.put("message", "音量已减少");
                        break;
                    case "mute":
                        audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI);
                        result.put("message", "已静音");
                        break;
                    case "unmute":
                        audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, AudioManager.FLAG_SHOW_UI);
                        result.put("message", "已取消静音");
                        break;
                }
            }
            result.put("new_volume", audio.getStreamVolume(stream));
        }
        
        return result;
    }
    
    private JSONObject handleWifi(Context context, JSONObject args) throws Exception {
        WifiManager wifi = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        String wifiAction = args.optString("wifi_action", "status");
        
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
    
    private JSONObject handleScreenshot(Context context) throws Exception {
        // 使用系统截图 (需要 root 或系统权限，这里尝试通过 shell 执行)
        String cmd = "screencap -p /sdcard/Pictures/mcp_screenshot_" + 
                System.currentTimeMillis() + ".png";
        String output = execShell(cmd + " 2>&1");
        
        JSONObject result = new JSONObject();
        result.put("command", cmd);
        result.put("output", output.trim());
        result.put("success", output.isEmpty() || output.contains("null"));
        return result;
    }
    
    private JSONObject handleSilentMode(Context context, JSONObject args) throws Exception {
        AudioManager audio = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        int ringerMode = audio.getRingerMode();
        
        JSONObject result = new JSONObject();
        result.put("current_mode", ringerModeToString(ringerMode));
        
        // 切换模式
        if (ringerMode == AudioManager.RINGER_MODE_NORMAL) {
            audio.setRingerMode(AudioManager.RINGER_MODE_SILENT);
            result.put("new_mode", "silent");
            result.put("message", "已切换为静音模式");
        } else {
            audio.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            result.put("new_mode", "normal");
            result.put("message", "已切换为标准模式");
        }
        
        return result;
    }
    
    private JSONObject handleLockScreen(Context context) throws Exception {
        // 尝试锁屏 (需要系统权限)
        execShell("input keyevent 26 2>&1"); // KEYCODE_POWER
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("message", "已发送锁屏指令");
        return result;
    }
    
    private JSONObject handleRestartSystemUI() throws Exception {
        String output = execShell("pkill -f com.android.systemui 2>&1");
        
        JSONObject result = new JSONObject();
        result.put("command", "pkill -f com.android.systemui");
        result.put("output", output.trim());
        result.put("success", true);
        result.put("message", "已尝试重启SystemUI");
        return result;
    }
    
    private JSONObject handleSystemProperty(JSONObject args) throws Exception {
        String property = args.optString("property", "ro.build.display.id");
        String output = execShell("getprop " + property + " 2>&1");
        
        JSONObject result = new JSONObject();
        result.put("property", property);
        result.put("value", output.trim());
        return result;
    }
    
    private JSONObject handleKeyEvent(JSONObject args) throws Exception {
        String keyCode = args.optString("key_code", "KEYCODE_HOME");
        String output = execShell("input keyevent " + keyCode + " 2>&1");
        
        JSONObject result = new JSONObject();
        result.put("key_code", keyCode);
        result.put("output", output.trim());
        result.put("success", true);
        result.put("message", "已发送按键事件: " + keyCode);
        return result;
    }
    
    private JSONObject openDeveloperSettings(Context context) throws Exception {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("message", "已打开开发者选项");
        return result;
    }
    
    private JSONObject openAccessibilitySettings(Context context) throws Exception {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        
        JSONObject result = new JSONObject();
        result.put("success", true);
        result.put("message", "已打开无障碍设置");
        return result;
    }
    
    private JSONObject getBatteryInfo(Context context) throws Exception {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        
        JSONObject result = new JSONObject();
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra("level", -1);
            int scale = batteryStatus.getIntExtra("scale", -1);
            int temperature = batteryStatus.getIntExtra("temperature", -1);
            int voltage = batteryStatus.getIntExtra("voltage", -1);
            int status = batteryStatus.getIntExtra("status", -1);
            int plugged = batteryStatus.getIntExtra("plugged", -1);
            
            result.put("level", level);
            result.put("scale", scale);
            result.put("percentage", scale > 0 ? (level * 100 / scale) : -1);
            result.put("temperature_celsius", temperature > 0 ? (temperature / 10.0) : -1);
            result.put("voltage_mv", voltage);
            
            String statusStr;
            switch (status) {
                case 2: statusStr = "充电中"; break;
                case 3: statusStr = "已充满"; break;
                case 4: statusStr = "未充电"; break;
                case 5: statusStr = "放电中"; break;
                default: statusStr = "未知";
            }
            result.put("status", statusStr);
            
            String pluggedStr;
            switch (plugged) {
                case 1: pluggedStr = "交流电"; break;
                case 2: pluggedStr = "USB"; break;
                case 4: pluggedStr = "无线充电"; break;
                default: pluggedStr = "未充电";
            }
            result.put("plugged", pluggedStr);
        }
        
        return result;
    }
    
    private String ringerModeToString(int mode) {
        switch (mode) {
            case AudioManager.RINGER_MODE_NORMAL: return "normal";
            case AudioManager.RINGER_MODE_SILENT: return "silent";
            case AudioManager.RINGER_MODE_VIBRATE: return "vibrate";
            default: return "unknown";
        }
    }
    
    private String execShell(String command) throws Exception {
        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", command});
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        process.waitFor();
        return output.toString();
    }
}