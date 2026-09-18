package com.mcp_run;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class SettingsFragment extends Fragment {

    private static final String PREFS_NAME = "mcp_config";
    private static final String KEY_PORT = "server_port";
    private static final String KEY_TIMEOUT = "server_timeout";
    
    private EditText portEdit, timeoutEdit;
    private Button toolsBtn, saveBtn;
    
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        portEdit = view.findViewById(R.id.portInput);
        timeoutEdit = view.findViewById(R.id.timeoutInput);
        toolsBtn = view.findViewById(R.id.toolsBtn);
        saveBtn = view.findViewById(R.id.saveBtn);
        
        loadPreferences();
        
        saveBtn.setOnClickListener(v -> savePreferences());
        
        toolsBtn.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), ToolListActivity.class));
        });
    }
    
    private void loadPreferences() {
        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        int port = prefs.getInt(KEY_PORT, 1145);
        int timeout = prefs.getInt(KEY_TIMEOUT, 60);
        portEdit.setText(String.valueOf(port));
        timeoutEdit.setText(String.valueOf(timeout));
    }
    
    private void savePreferences() {
        int port = 1145;
        int timeout = 60;
        
        try {
            port = Integer.parseInt(portEdit.getText().toString().trim());
            if (port < 1024 || port > 65535) {
                Toast.makeText(requireContext(), "端口范围: 1024-65535", Toast.LENGTH_SHORT).show();
                return;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(requireContext(), "端口格式错误", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            timeout = Integer.parseInt(timeoutEdit.getText().toString().trim());
            if (timeout < 1) timeout = 60;
        } catch (NumberFormatException e) {
            timeout = 60;
        }
        
        android.content.SharedPreferences prefs = requireContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putInt(KEY_PORT, port)
                .putInt(KEY_TIMEOUT, timeout)
                .apply();
        
        Toast.makeText(requireContext(), "设置已保存", Toast.LENGTH_SHORT).show();
        
        // 通知MainActivity更新标题
        if (getActivity() instanceof MainActivity) {
        }
    }
}