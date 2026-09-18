# MCP Run

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android%208.0%2B-blue" alt="Platform">
  <img src="https://img.shields.io/badge/SDK-26--34-green" alt="SDK">
  <img src="https://img.shields.io/badge/Language-Java-orange" alt="Language">
  <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
  <img src="https://img.shields.io/badge/Tools-75%2B-success" alt="Tools">
</p>

**MCP Run** 是一款在 Android 设备上运行的 **MCP (Model Context Protocol) 服务器**，将手机变成一个功能丰富的 AI 工具平台。内置 75+ 工具，覆盖文件管理、系统控制、应用管理、脚本执行等方方面面。

---

## ✨ 核心功能

### 🔧 MCP 服务器
- **前台常驻服务**：确保 MCP 服务器持续运行
- **透明状态栏 & 导航栏**：沉浸式全屏体验，与 APP 底色融为一体
- **小米 UI 风格**：简洁的表单式设置界面
- **通知栏控制**：从通知栏一键停止服务并退出

### 🛠 75+ 工具集（10 大分类）

| 分类 | 工具数量 | 主要功能 |
|------|---------|---------|
| 📁 **文件操作** | ~40 | 读写文件、搜索、压缩、加密、批量重命名、EXIF 重命名等 |
| ⚙️ **系统管理** | 5+ | Shell 命令执行、系统控制、亮度/音量调节、锁屏、开关 WiFi/蓝牙 |
| 📱 **设备信息** | 8+ | 设备详情、截图、图片处理（裁剪/旋转/缩放/格式转换） |
| 📦 **应用管理** | 10+ | 应用列表、安装/卸载 APK、APK 反编译/重编译/签名/搜索 |
| 📜 **脚本执行** | 2 | JavaScript / Python 代码执行 |
| 💬 **通讯交互** | 2 | 剪贴板读写、系统通知发送 |
| 🌐 **网络请求** | 8+ | HTTP GET/POST/PUT/DELETE、JSON 解析、文件/文本下载 |
| 🔧 **实用工具** | 10+ | JSON 格式化、文本转换、计算器、日志查看 |
| 🔐 **权限管理** | 2 | 权限检查、权限列表查询 |
| 🎵 **媒体与健康** | 12+ | 联系人、短信收发、通话记录、步数/距离/卡路里/心率/睡眠数据 |

### 📋 文件操作工具详情
```
pwd / cd / set_root / exists / stat
ls / list_all / tree / find / grep
mkdir / touch / empty / read / write / append
head / tail / read_lines / batch_read
copy / rename / delete
du / hash / edit / compare_files
chmod / chmod_batch
zip / unzip / tar / gzip
rename_batch / delete_batch / copy_batch
file_count / file_size_top / file_duplicate / file_empty
file_search_content / file_search_regex / file_search_recent
file_watch / file_tail_live
file_encrypt / file_decrypt
file_checksum
rename_exif / rename_video
```

### ⚙️ 系统管理工具详情
```
shell              # 执行 Shell 命令
system_control     # 音量/WiFi/截图/静音/锁屏/重启SystemUI
system_control_enhanced  # 增强版：蓝牙扫描、亮度、闹钟、广播
shizuku            # Shizuku 权限状态
battery            # 电池状态
battery_fix        # 省电无限制模式
```

### 📦 应用管理工具详情
```
app_manager        # 应用列表/详情/启动/停止/卸载
install_apk        # 安装 APK
apk_decompile      # 反编译 APK
apk_recompile      # 回编译 APK
apk_info           # APK 详细信息
apk_list_files     # 列出 APK 文件
apk_extract        # 解压 APK
apk_sign           # 签名 APK
apk_search         # 在 APK 中搜索
apk_permission     # 提取 APK 权限列表
```

---

## 🏗 项目结构

```
MCP-Run/
├── app/src/main/
│   ├── java/com/mcp_run/
│   │   ├── MainActivity.java          # 主界面（透明状态栏/导航栏）
│   │   ├── HomeFragment.java          # 首页 - 服务状态控制
│   │   ├── SettingsFragment.java      # 设置页 - 表单模式
│   │   ├── ToolListActivity.java      # 工具集列表
│   │   ├── MCPService.java            # MCP 前台服务
│   │   ├── MCPHttpServer.java         # HTTP 服务器核心
│   │   ├── MCPFNotificationListener.java  # 通知监听服务
│   │   ├── BootReceiver.java          # 开机自启广播
│   │   ├── NotificationReceiver.java  # 通知栏停止按钮
│   │   └── tools/                     # 75+ 工具实现
│   │       ├── MCPTool.java           # 工具基类
│   │       ├── ToolRegistry.java      # 工具注册表
│   │       ├── FileSystemTool.java    # 文件操作
│   │       ├── ShellTool.java         # Shell 执行
│   │       ├── AppManagerTool.java    # 应用管理
│   │       ├── HttpTool.java          # 网络请求
│   │       └── ... (更多工具)
│   ├── res/
│   │   ├── layout/                    # XML 布局
│   │   ├── values/                    # 颜色/主题/字符串
│   │   └── drawable/                  # 图标/背景
│   └── AndroidManifest.xml
├── build.gradle                       # 项目级构建配置
├── app/build.gradle                   # 模块级构建配置
├── gradle.properties                  # Gradle 属性
└── local.properties                   # 本地 SDK 路径
```

---

## 🚀 快速开始

### 环境要求
- Android 8.0+ (API 26)
- JDK 8+
- Android SDK 34
- Gradle 8.x

### 构建

```bash
# 配置 SDK 路径
echo "sdk.dir=/path/to/android-sdk" > local.properties

# Debug 包
./gradlew assembleDebug

# Release 包（需要签名配置）
./gradlew assembleRelease
```

### 输出位置
```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

---

## 🔑 权限说明

### 必需权限
| 权限 | 用途 |
|------|------|
| `INTERNET` | MCP HTTP 服务器通信 |
| `FOREGROUND_SERVICE` | 前台服务运行 |
| `POST_NOTIFICATIONS` | 通知栏显示 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |

### 可选权限（按需开启）
| 权限 | 用途 |
|------|------|
| `READ_CONTACTS` | 联系人查询 |
| `READ_SMS` / `SEND_SMS` | 短信收发 |
| `READ_CALL_LOG` | 通话记录 |
| `CAMERA` | 截图功能 |
| `RECORD_AUDIO` | 录音功能 |
| `ACCESS_FINE_LOCATION` | WiFi 扫描 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 省电白名单 |

---

## 🎨 UI 特性

- **透明状态栏 & 导航栏**：全屏沉浸体验，系统栏与 APP 背景融为一体
- **小米 UI 风格**：干净的卡片布局、表单式设置界面
- **底部导航栏**：简洁的双 Tab 设计（主页 / 设置）
- **深色模式支持**：自动适配系统夜间模式

---

## 📄 许可证

本项目采用 MIT 许可证。

---

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

1. Fork 本仓库
2. 创建特性分支 (`git checkout -b feature/xxx`)
3. 提交更改 (`git commit -m 'Add xxx'`)
4. 推送到分支 (`git push origin feature/xxx`)
5. 开启 Pull Request

---

<p align="center">
  Made with ❤️ on Android
</p>
