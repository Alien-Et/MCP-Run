package com.mcp_run.tools;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import org.json.JSONObject;

/**
 * 网络连接信息工具 - 获取当前网络状态详情
 */
public class NetworkInfoTool implements MCPTool {
    @Override
    public String getName() {
        return "network_info";
    }

    @Override
    public String getDescription() {
        return "获取当前网络连接详细信息，包括WiFi和移动数据状态。";
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
        JSONObject result = new JSONObject();
        
        // WiFi信息
        WifiManager wifi = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
        
        JSONObject wifiInfo = new JSONObject();
        wifiInfo.put("enabled", wifi.isWifiEnabled());
        
        android.net.wifi.WifiInfo connectedWifi = wifi.getConnectionInfo();
        if (connectedWifi != null) {
            wifiInfo.put("connected", connectedWifi.getNetworkId() >= 0);
            wifiInfo.put("ssid", connectedWifi.getSSID().replace("\"", ""));
            wifiInfo.put("bssid", connectedWifi.getBSSID());
            wifiInfo.put("signal_strength", connectedWifi.getRssi());
            wifiInfo.put("ip_address", intToIpAddress(connectedWifi.getIpAddress()));
            wifiInfo.put("frequency_mhz", connectedWifi.getFrequency());
            wifiInfo.put("link_speed_mbps", connectedWifi.getLinkSpeed());
        }
        result.put("wifi", wifiInfo);
        
        // 移动数据信息
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager cm = 
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (cm != null) {
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                JSONObject mobileInfo = new JSONObject();
                
                if (activeNetwork != null && activeNetwork.isConnected()) {
                    mobileInfo.put("connected", true);
                    mobileInfo.put("type", activeNetwork.getTypeName().toLowerCase());
                    mobileInfo.put("subtype", activeNetwork.getSubtypeName().toLowerCase());
                    
                    if (activeNetwork.getType() == ConnectivityManager.TYPE_MOBILE) {
                        TelephonyManager tm = (TelephonyManager) 
                                context.getSystemService(Context.TELEPHONY_SERVICE);
                        if (tm != null) {
                            mobileInfo.put("network_operator", tm.getNetworkOperatorName());
                            mobileInfo.put("sim_operator", tm.getSimOperatorName());
                        }
                    }
                } else {
                    mobileInfo.put("connected", false);
                }
                result.put("mobile_data", mobileInfo);
            }
        }
        
        // 通用网络信息
        JSONObject generalInfo = new JSONObject();
        generalInfo.put("internet_access", isInternetAvailable(context));
        result.put("general", generalInfo);
        
        return result;
    }
    
    private String intToIpAddress(int ip) {
        if (ip == 0) return "0.0.0.0";
        return ((ip & 0xFF) + "." +
               ((ip >> 8) & 0xFF) + "." +
               ((ip >> 16) & 0xFF) + "." +
               ((ip >> 24) & 0xFF));
    }
    
    private boolean isInternetAvailable(Context context) {
        try {
            ConnectivityManager cm = 
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkInfo info = cm.getActiveNetworkInfo();
                return info != null && info.isConnected();
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }
}
