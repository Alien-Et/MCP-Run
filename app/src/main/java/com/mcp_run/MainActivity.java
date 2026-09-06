package com.mcp_run;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Collections;
import java.util.Enumeration;
import java.net.InetAddress;
import java.net.NetworkInterface;

/**
 * MVP v1.18 - 主界面（修复通知栏停止同步）
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String PREFS_NAME = "mcp_config";
    private static final String KEY_PORT = "server_port";
    private static final String KEY_TIMEOUT = "server_timeout";
    
    private TextView statusText, ipText;
    private EditText portEdit, timeoutEdit;
    private Button toggleBtn, toolsBtn;
    private View statusIndicator;
    
    private boolean isRunning = false;
    private int currentPort = 1145;
    private boolean isStarting = false;
    
    // 用于接收服务停止通知的广播接收器
    private BroadcastReceiver stopReceiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        loadPreferences();
        updateServerStatus(MCPService.isRunning());
        
        // 注册广播接收器监听服务停止事件
        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (MCPService.ACTION_STOP_FROM_NOTIFICATION.equals(action)) {
                    Log.d(TAG, "Received stop notification from service");
                    // 更新UI状态
                    updateServerStatus(false);
                } else if (MCPService.ACTION_EXIT_APP.equals(action)) {
                    Log.d(TAG, "Received exit app notification - finishing activity");
                    // 退出应用
                    finishAffinity();
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(MCPService.ACTION_STOP_FROM_NOTIFICATION);
        filter.addAction(MCPService.ACTION_EXIT_APP);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopReceiver, filter);
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 每次回到界面时检查服务状态
        runOnUiThread(() -> updateServerStatus(MCPService.isRunning()));
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 注销广播接收器
        try {
            if (stopReceiver != null) {
                unregisterReceiver(stopReceiver);
                stopReceiver = null;
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to unregister receiver", e);
        }
    }

    private void initViews() {
        statusText = findViewById(R.id.statusText);
        ipText = findViewById(R.id.ipText);
        portEdit = findViewById(R.id.portInput);
        timeoutEdit = findViewById(R.id.timeoutInput);
        toggleBtn = findViewById(R.id.toggleBtn);
        toolsBtn = findViewById(R.id.toolsBtn);
        statusIndicator = findViewById(R.id.statusDot);
        
        toggleBtn.setOnClickListener(v -> {
            if (isStarting) {
                Toast.makeText(this, "服务器正在启动，请稍候...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isRunning) {
                stopServer();
            } else {
                startServer();
            }
        });
        
        toolsBtn.setOnClickListener(v -> {
            startActivity(new Intent(this, ToolListActivity.class));
        });
        
        ipText.setText("点击启动服务器");
    }

    private void loadPreferences() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentPort = prefs.getInt(KEY_PORT, 1145);
        portEdit.setText(String.valueOf(currentPort));
        int timeout = prefs.getInt(KEY_TIMEOUT, 60);
        timeoutEdit.setText(String.valueOf(timeout));
    }

    private void savePreferences() {
        android.content.SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int timeout = 60;
        try {
            timeout = Integer.parseInt(timeoutEdit.getText().toString().trim());
        } catch (NumberFormatException e) {
            timeout = 60;
        }
        prefs.edit()
                .putInt(KEY_PORT, currentPort)
                .putInt(KEY_TIMEOUT, timeout)
                .apply();
    }

    private void startServer() {
        if (isStarting) return;
        
        String portStr = portEdit.getText().toString().trim();
        if (portStr.isEmpty()) portStr = "1145";
        int port;
        try {
            port = Integer.parseInt(portStr);
            if (port < 1024 || port > 65535) {
                Toast.makeText(this, "端口范围: 1024-65535", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "端口格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        
        currentPort = port;
        savePreferences();
        
        isStarting = true;
        toggleBtn.setEnabled(false);
        portEdit.setEnabled(false);
        timeoutEdit.setEnabled(false);
        
        // 显示启动状态
        runOnUiThread(() -> {
            statusText.setText("启动中...");
            statusText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            ipText.setText("正在启动服务器...");
        });
        
        // 在子线程中启动服务
        new Thread(() -> {
            try {
                // 先停止旧服务（如果有）
                if (MCPService.isRunning()) {
                    stopService(new Intent(this, MCPService.class));
                    Thread.sleep(300);
                }
                
                // 创建意图
                Intent intent = new Intent(this, MCPService.class);
                intent.putExtra(MCPService.EXTRA_PORT, port);
                
                // 启动服务
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
                
                // 等待服务启动
                Thread.sleep(1500);
                
                // 更新UI
                runOnUiThread(() -> {
                    isStarting = false;
                    toggleBtn.setEnabled(true);
                    updateServerStatus(MCPService.isRunning());
                    
                    if (MCPService.isRunning()) {
                        updateIPDisplay(port);
                        Toast.makeText(this, 
                            "MCP 服务器已启动\n端口: " + port, 
                            Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, 
                            "服务器启动失败，请检查日志", 
                            Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                runOnUiThread(() -> {
                    isStarting = false;
                    toggleBtn.setEnabled(true);
                    portEdit.setEnabled(true);
                    timeoutEdit.setEnabled(true);
                    Toast.makeText(this, 
                        "启动失败: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private void updateIPDisplay(int port) {
        new Thread(() -> {
            try {
                String ip = getLocalIPAddress();
                final String finalIp = ip;
                runOnUiThread(() -> {
                    if (!"未知".equals(finalIp) && finalIp != null) {
                        ipText.setText("局域网: " + finalIp + ":" + port);
                        ipText.setTextColor(ContextCompat.getColor(this, R.color.status_green));
                    }
                });
            } catch (Exception e) {
                // 忽略
            }
        }).start();
    }
    
    /**
     * 获取本机局域网IP地址
     */
    private String getLocalIPAddress() {
        try {
            // 方法1: 通过WifiManager
            android.net.wifi.WifiManager wm = (android.net.wifi.WifiManager) getApplicationContext()
                    .getSystemService(WIFI_SERVICE);
            
            if (wm != null && wm.isWifiEnabled()) {
                android.net.wifi.WifiInfo wifiInfo = wm.getConnectionInfo();
                if (wifiInfo != null) {
                    int ip = wifiInfo.getIpAddress();
                    if (ip != 0) {
                        return intToIpAddress(ip);
                    }
                }
            }
            
            // 方法2: 通过ConnectivityManager (Android 6.0+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
                Network network = cm.getActiveNetwork();
                if (network != null) {
                    NetworkCapabilities nc = cm.getNetworkCapabilities(network);
                    if (nc != null) {
                        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                            android.net.LinkProperties lp = cm.getLinkProperties(network);
                            if (lp != null) {
                                for (android.net.LinkAddress addr : lp.getLinkAddresses()) {
                                    InetAddress inetAddr = addr.getAddress();
                                    if (inetAddr instanceof java.net.Inet4Address && 
                                        !inetAddr.isLoopbackAddress()) {
                                        return inetAddr.getHostAddress();
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // 方法3: 遍历网络接口
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                for (NetworkInterface ni : Collections.list(interfaces)) {
                    if (ni.isLoopback() || !ni.isUp()) continue;
                    for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                        if (addr instanceof java.net.Inet4Address && !addr.isLoopbackAddress()) {
                            return addr.getHostAddress();
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // 忽略
        }
        
        return "未知";
    }
    
    private String intToIpAddress(int ip) {
        return ((ip & 0xFF) + ".") +
               ((ip >> 8) & 0xFF) + "." +
               ((ip >> 16) & 0xFF) + "." +
               ((ip >> 24) & 0xFF);
    }

    private void stopServer() {
        Intent intent = new Intent(this, MCPService.class);
        intent.setAction(MCPService.ACTION_STOP);
        startService(intent);
        
        isRunning = false;
        updateServerStatus(false);
        portEdit.setEnabled(true);
        timeoutEdit.setEnabled(true);
        Toast.makeText(this, "MCP 服务器已停止", Toast.LENGTH_SHORT).show();
    }

    private void updateServerStatus(boolean running) {
        isRunning = running;
        runOnUiThread(() -> {
            if (running) {
                statusText.setText("运行中");
                statusText.setTextColor(ContextCompat.getColor(this, R.color.status_green));
                statusIndicator.setBackgroundResource(R.color.status_green);
                toggleBtn.setText("停止服务器");
                toggleBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF3B30));
                portEdit.setEnabled(false);
                timeoutEdit.setEnabled(false);
                updateIPDisplay(MCPService.getCurrentPort());
            } else {
                statusText.setText("已停止");
                statusText.setTextColor(ContextCompat.getColor(this, R.color.status_red));
                statusIndicator.setBackgroundResource(R.color.status_gray);
                toggleBtn.setText("启动服务器");
                toggleBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF328FFC));
                portEdit.setEnabled(true);
                timeoutEdit.setEnabled(true);
                ipText.setText("点击启动服务器");
                ipText.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
            }
        });
    }
}
