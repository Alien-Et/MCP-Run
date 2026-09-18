package com.mcp_run.tools;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.Build;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Enumeration;

/**
 * 网络工具类 - 获取本机 IP 地址
 */
public class NetworkUtils {

    /**
     * 获取本机 WiFi IP 地址（优先返回局域网 IP）
     * 如果无法获取，返回 "未知" 提示而非 "0.0.0.0"
     */
    public static String getLocalIPAddress(Context context) {
        // 方法1: 通过 WifiManager (需要 WiFi 已连接)
        try {
            WifiManager wm = (WifiManager) context.getApplicationContext()
                .getSystemService(Context.WIFI_SERVICE);
            
            if (wm != null && wm.isWifiEnabled()) {
                android.net.wifi.WifiInfo wifiInfo = wm.getConnectionInfo();
                if (wifiInfo != null) {
                    int ip = wifiInfo.getIpAddress();
                    if (ip != 0) {
                        return intToIpAddress(ip);
                    }
                }
            }
        } catch (Exception e) {
            // 继续尝试其他方法
        }

        // 方法2: 通过 ConnectivityManager 获取当前网络
        try {
            ConnectivityManager cm = (ConnectivityManager) 
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
            
            if (cm != null) {
                Network network = null;
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    network = cm.getActiveNetwork();
                    if (network != null) {
                        NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                        if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                            android.net.LinkProperties linkProps = cm.getLinkProperties(network);
                            if (linkProps != null) {
                                for (android.net.LinkAddress addr : linkProps.getLinkAddresses()) {
                                    InetAddress inetAddr = addr.getAddress();
                                    if (inetAddr instanceof Inet4Address && !inetAddr.isLoopbackAddress()) {
                                        return inetAddr.getHostAddress();
                                    }
                                }
                            }
                        }
                    }
                }
                
                // 如果没有 WiFi 网络，尝试任何可用网络
                if (network == null) {
                    android.net.Network[] networks = cm.getAllNetworks();
                    if (networks != null) {
                        for (android.net.Network net : networks) {
                            NetworkCapabilities nc = cm.getNetworkCapabilities(net);
                            if (nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
                                               nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) {
                                android.net.LinkProperties linkProps = cm.getLinkProperties(net);
                                if (linkProps != null) {
                                    for (android.net.LinkAddress addr : linkProps.getLinkAddresses()) {
                                        InetAddress inetAddr = addr.getAddress();
                                        if (inetAddr instanceof Inet4Address && !inetAddr.isLoopbackAddress()) {
                                            return inetAddr.getHostAddress();
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 继续尝试其他方法
        }

        // 方法3: 遍历所有网络接口
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface ni : Collections.list(interfaces)) {
                    // 优先选择 wlan0
                    if (ni.getName().equals("wlan0") || ni.getName().equals("eth0")) {
                        for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                            if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                                return addr.getHostAddress();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 继续尝试其他方法
        }

        // 兜底: 遍历所有非回环 IPv4
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface ni : Collections.list(interfaces)) {
                    if (ni.isLoopback() || !ni.isUp()) continue;
                    for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                        if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 最后返回未知提示
        }

        return "未知";
    }

    /**
     * 检查 WiFi 是否已连接
     */
    public static boolean isWifiConnected(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) 
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return false;
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                    return nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI);
                }
            }
            
            android.net.NetworkInfo wifi = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            return wifi != null && wifi.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 将 int IP 转为字符串
     */
    public static String intToIpAddress(int ip) {
        return ((ip & 0xFF) + ".") +
               ((ip >> 8) & 0xFF) + "." +
               ((ip >> 16) & 0xFF) + "." +
               ((ip >> 24) & 0xFF);
    }

    /**
     * 判断 IP 是否为局域网 IP
     */
    public static boolean isLocalIPAddress(String ip) {
        if (ip == null || ip.equals("0.0.0.0") || ip.equals("未知")) return false;
        return ip.startsWith("10.") ||
               ip.startsWith("192.168.") ||
               ip.startsWith("172.16.") ||
               ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") ||
               ip.startsWith("172.19.") ||
               ip.startsWith("172.2") ||
               ip.startsWith("172.3");
    }
}