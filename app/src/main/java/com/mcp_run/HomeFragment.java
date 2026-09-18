package com.mcp_run;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import java.util.Collections;
import java.util.Enumeration;
import java.net.InetAddress;
import java.net.NetworkInterface;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private static final String PREFS_NAME = "mcp_config";
    
    private TextView statusText, ipText, portDisplay;
    private MaterialButton toggleBtn;
    private View statusIndicator;
    
    private boolean isRunning = false;
    private boolean isStarting = false;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        statusText = view.findViewById(R.id.statusText);
        ipText = view.findViewById(R.id.ipText);
        portDisplay = view.findViewById(R.id.portDisplay);
        toggleBtn = view.findViewById(R.id.toggleBtn);
        statusIndicator = view.findViewById(R.id.statusDot);
        
        toggleBtn.setOnClickListener(v -> {
            if (isStarting) {
                Toast.makeText(requireContext(), "服务器正在启动，请稍候...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (isRunning) {
                stopServer();
            } else {
                startServer();
            }
        });
        
        updateServerStatus(MCPService.isRunning());
        
        // 定期刷新IP显示
        startStatusRefresh();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        updateServerStatus(MCPService.isRunning());
    }
    
    private void startStatusRefresh() {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isResumed()) {
                    boolean running = MCPService.isRunning();
                    if (running != isRunning) {
                        updateServerStatus(running);
                    }
                    if (running) {
                        updateIPDisplay(MCPService.getCurrentPort());
                    }
                    startStatusRefresh();
                }
            }
        }, 2000);
    }
    
    private void updateServerStatus(boolean running) {
        isRunning = running;
        if (requireActivity().isDestroyed()) return;
        
        requireActivity().runOnUiThread(() -> {
            int primaryColor = ContextCompat.getColor(requireContext(), R.color.primary);
            int errorColor = ContextCompat.getColor(requireContext(), R.color.status_red);
            int greenColor = ContextCompat.getColor(requireContext(), R.color.status_green);
            int grayColor = ContextCompat.getColor(requireContext(), R.color.status_gray);
            
            if (running) {
                statusText.setText("运行中");
                statusText.setTextColor(greenColor);
                statusIndicator.setBackgroundColor(greenColor);
                toggleBtn.setText("停止服务器");
                toggleBtn.setBackgroundColor(errorColor);
                portDisplay.setText(String.valueOf(MCPService.getCurrentPort()));
                updateIPDisplay(MCPService.getCurrentPort());
            } else {
                statusText.setText("已停止");
                statusText.setTextColor(errorColor);
                statusIndicator.setBackgroundColor(grayColor);
                toggleBtn.setText("启动服务器");
                toggleBtn.setBackgroundColor(primaryColor);
                portDisplay.setText("1145");
                ipText.setText("点击启动服务器");
                ipText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            }
        });
    }
    
    private void updateIPDisplay(int port) {
        new Thread(() -> {
            try {
                String ip = getLocalIPAddress();
                final String finalIp = ip;
                requireActivity().runOnUiThread(() -> {
                    if (!"未知".equals(finalIp) && finalIp != null) {
                        ipText.setText(finalIp + ":" + port);
                        ipText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_green));
                    }
                });
            } catch (Exception e) {
                // 忽略
            }
        }).start();
    }
    
    private String getLocalIPAddress() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                ConnectivityManager cm = (ConnectivityManager) requireContext()
                        .getSystemService(Context.CONNECTIVITY_SERVICE);
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
    
    private void startServer() {
        if (isStarting) return;
        
        int port = 1145;
        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        port = prefs.getInt("server_port", 1145);
        
        isStarting = true;
        requireActivity().runOnUiThread(() -> {
            toggleBtn.setEnabled(false);
            statusText.setText("启动中...");
            statusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
            ipText.setText("正在启动服务器...");
        });
        
        final int finalPort = port;
        new Thread(() -> {
            try {
                if (MCPService.isRunning()) {
                    requireActivity().stopService(new Intent(requireContext(), MCPService.class));
                    Thread.sleep(300);
                }
                
                Intent intent = new Intent(requireContext(), MCPService.class);
                intent.putExtra(MCPService.EXTRA_PORT, finalPort);
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    requireActivity().startForegroundService(intent);
                } else {
                    requireActivity().startService(intent);
                }
                
                Thread.sleep(1500);
                
                requireActivity().runOnUiThread(() -> {
                    isStarting = false;
                    toggleBtn.setEnabled(true);
                    updateServerStatus(MCPService.isRunning());
                    
                    if (MCPService.isRunning()) {
                        updateIPDisplay(finalPort);
                        Toast.makeText(requireContext(), 
                            "MCP 服务器已启动\n端口: " + finalPort, 
                            Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), 
                            "服务器启动失败，请检查日志", 
                            Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                requireActivity().runOnUiThread(() -> {
                    isStarting = false;
                    toggleBtn.setEnabled(true);
                    Toast.makeText(requireContext(), 
                        "启动失败: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }
    
    private void stopServer() {
        if (MCPService.isRunning()) {
            requireActivity().stopService(new Intent(requireContext(), MCPService.class));
        }
        isRunning = false;
        updateServerStatus(false);
        Toast.makeText(requireContext(), "MCP 服务器已停止", Toast.LENGTH_SHORT).show();
    }
}
