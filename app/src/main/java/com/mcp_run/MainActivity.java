package com.mcp_run;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

/**
 * 主界面 - Material Design UI
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    
    private LinearLayout bottomNav, navHomeBtn, navSettingsBtn;
    private ImageView navHomeIcon, navSettingsIcon;
    private TextView navHomeLabel, navSettingsLabel;
    private androidx.fragment.app.FragmentManager fragmentManager;
    private Fragment currentFragment;
    private View fragmentContainer;
    
    // 用于接收服务停止通知的广播接收器
    private BroadcastReceiver stopReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        setupStatusBar();
        
        // 请求通知权限 (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }
        
        setupBottomNavigation();
        loadFragment(new HomeFragment());
        
        // 注册广播接收器监听服务停止事件
        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("com.mcp_run.ACTION_EXIT".equals(action)) {
                    Log.d(TAG, "Received exit app notification");
                    finishAffinity();
                }
            }
        };
        
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.mcp_run.ACTION_EXIT");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stopReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(stopReceiver, filter);
        }
    }
    
    /**
     * 设置沉浸式状态栏
     */
    private void setupStatusBar() {
        if (fragmentContainer == null) {
            Log.e(TAG, "fragmentContainer is null, skipping setupStatusBar");
            return;
        }
        
        // 启用 Edge-to-Edge (Android 15 强制要求)
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        
        // 状态栏和导航栏设为完全透明
        getWindow().setStatusBarColor(0x00000000);
        getWindow().setNavigationBarColor(0x00000000);
        
        // 获取 WindowInsetsController
        WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        
        // 浅色背景使用深色状态栏图标
        controller.setAppearanceLightStatusBars(true);
        controller.setAppearanceLightNavigationBars(true);
        
        // 允许内容延伸到系统栏下方
        ViewCompat.setOnApplyWindowInsetsListener(fragmentContainer, (view, insets) -> {
            int statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            view.setPadding(
                view.getPaddingLeft(),
                statusBarHeight,
                view.getPaddingRight(),
                navBarHeight
            );
            return insets;
        });
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // 确保当前Fragment能收到状态更新
        if (currentFragment instanceof HomeFragment) {
            ((HomeFragment) currentFragment).onResume();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
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
        bottomNav = findViewById(R.id.bottomNav);
        navHomeBtn = findViewById(R.id.navHomeBtn);
        navSettingsBtn = findViewById(R.id.navSettingsBtn);
        navHomeIcon = findViewById(R.id.navHomeIcon);
        navSettingsIcon = findViewById(R.id.navSettingsIcon);
        navHomeLabel = findViewById(R.id.navHomeLabel);
        navSettingsLabel = findViewById(R.id.navSettingsLabel);
        fragmentContainer = findViewById(R.id.fragmentContainer);
        fragmentManager = getSupportFragmentManager();
    }
    
    private void setupBottomNavigation() {
        navHomeBtn.setOnClickListener(v -> switchToTab(0));
        navSettingsBtn.setOnClickListener(v -> switchToTab(1));
        updateNavState(0);
    }
    
    private void switchToTab(int tabIndex) {
        if (currentFragment != null && 
            ((tabIndex == 0 && currentFragment instanceof HomeFragment) ||
             (tabIndex == 1 && currentFragment instanceof SettingsFragment))) {
            return;
        }
        if (tabIndex == 0) {
            loadFragment(new HomeFragment());
        } else {
            loadFragment(new SettingsFragment());
        }
        updateNavState(tabIndex);
    }
    
    private void loadFragment(Fragment fragment) {
        currentFragment = fragment;
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.replace(R.id.fragmentContainer, fragment);
        transaction.commit();
    }
    
    private void updateNavState(int activeTab) {
        int activeColor = ContextCompat.getColor(this, R.color.primary);
        int inactiveColor = ContextCompat.getColor(this, R.color.text_secondary);
        
        navHomeIcon.setColorFilter(activeTab == 0 ? activeColor : inactiveColor);
        navSettingsIcon.setColorFilter(activeTab == 1 ? activeColor : inactiveColor);
        
        // 更新标签颜色
        if (navHomeLabel != null) {
            navHomeLabel.setTextColor(activeTab == 0 ? activeColor : inactiveColor);
        }
        if (navSettingsLabel != null) {
            navSettingsLabel.setTextColor(activeTab == 1 ? activeColor : inactiveColor);
        }
    }
    
    public void showSettingsSaved() {
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
    }
}
