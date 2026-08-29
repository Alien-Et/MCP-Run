# MCP Run

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-blue" alt="Platform">
  <img src="https://img.shields.io/badge/SDK-26--34-green" alt="SDK">
  <img src="https://img.shields.io/badge/Java-8-orange" alt="Java">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
</p>

MCP Run 是一款基于 Android 平台的 **MCP (Model Context Protocol)** 服务管理工具，提供后台服务运行、通知监听、系统权限管理等核心功能。

---

## 📱 功能特性

| 功能 | 说明 |
|------|------|
| **MCP 服务** | 前台常驻服务，支持数据同步 |
| **通知监听** | 实时监听系统通知并处理 |
| **工具集** | 集成多种实用工具模块 |
| **开机自启** | 支持系统启动后自动运行 |
| **包监听** | 监控应用的安装与卸载事件 |
| **后台优化** | 支持忽略电池优化限制 |

---

## 🛠 技术栈

```
语言：Java 8
构建：Gradle 7.x
最小 SDK：26 (Android 8.0)
目标 SDK：34 (Android 14)
编译 SDK：34
```

### 核心依赖

```gradle
implementation 'androidx.appcompat:appcompat:1.6.1'
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.constraintlayout:constraintlayout:2.1.4'
implementation 'androidx.preference:preference:1.2.1'
```

---

## 🚀 快速开始

### 构建环境

```bash
# 配置 Android SDK 路径（local.properties）
sdk.dir=/path/to/android-sdk
```

### 编译命令

```bash
# 使用 Gradle Wrapper
./gradlew assembleDebug      # Debug 包
./gradlew assembleRelease    # Release 包

# 或直接在终端运行
chmod +x gradlew
./gradlew build
```

### 输出位置

```
app/build/outputs/apk/
├── debug/app-debug.apk
└── release/app-release.apk
```

---

## 📋 权限说明

应用需要以下权限以实现核心功能：

### 必需权限
| 权限 | 用途 |
|------|------|
| `INTERNET` | 网络通信 |
| `FOREGROUND_SERVICE` | 前台服务运行 |
| `POST_NOTIFICATIONS` | 通知显示 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |

### 可选权限
| 权限 | 用途 |
|------|------|
| `CAMERA` | 相机功能 |
| `RECORD_AUDIO` | 录音功能 |
| `READ_CONTACTS` / `WRITE_CONTACTS` | 联系人管理 |
| `ACCESS_FINE_LOCATION` | 定位服务 |
| `READ_PHONE_STATE` | 设备信息读取 |

---

## 🏗 项目结构

```
MCP_Run_project/
├── app/
│   ├── src/main/
│   │   ├── java/com/mcp_run/
│   │   │   ├── MainActivity.kt          # 主界面
│   │   │   ├── ToolListActivity.kt      # 工具集列表
│   │   │   ├── MCPService.kt            # MCP 前台服务
│   │   │   ├── MCPFNotificationListener.kt  # 通知监听
│   │   │   ├── BootReceiver.kt          # 开机广播
│   │   │   └── PackageReceiver.kt       # 包监听
│   │   ├── res/
│   │   │   ├── layout/                  # 布局文件
│   │   │   ├── values/                  # 资源定义
│   │   │   └── xml/                     # XML 配置
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── gradle/                              # Gradle 配置
├── build.gradle                         # 项目级构建
├── settings.gradle                      # 模块设置
└── local.properties                     # 本地配置
```

---

## 📦 签名配置

Release 版本使用以下签名配置：

```groovy
signingConfigs {
    release {
        storeFile file("keystore/mcp_run.keystore")
        storePassword "mcp_run_2024"
        keyAlias "mcp_run"
        keyPassword "mcp_run_2024"
    }
}
```

---

## 🔧 开发指南

### 添加新功能

1. 在 `app/src/main/java/com/mcp_run/` 创建新的 Activity/Service
2. 在 `AndroidManifest.xml` 中注册组件
3. 更新 `res/layout/` 添加 UI 布局

### 权限申请

推荐使用 [Activity Results API](https://developer.android.com/training/permissions/requesting)：

```kotlin
val permissionLauncher = registerForActivityResult(
    ActivityResultContracts.RequestPermission()
) { isGranted ->
    // 处理权限结果
}
permissionLauncher.launch(Manifest.permission.CAMERA)
```

---

## 📄 许可证

本项目采用 MIT 许可证 - 详见 [LICENSE](LICENSE) 文件

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 开启 Pull Request

---

## 📞 联系方式

- **作者**: Alien-Et
- **仓库**: https://github.com/Alien-Et/MCP-Run

---

<p align="center">
  Made with ❤️ using Android Studio & Termux
</p>
