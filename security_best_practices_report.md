# MCP·AC Android 安全审计报告

## 执行摘要

本次审计针对 `com.mcp_run` Android 应用的 MCP Streamable HTTP 服务器及其工具链进行端到端漏洞评估。该应用在设备上暴露一个 HTTP 服务器，默认绑定到所有网络接口（`0.0.0.0`），且**默认不启用 API Key 认证**。服务器注册了 70 余个可直接调用的 MCP 工具，覆盖 Shell 执行、文件读写、短信/联系人读取、应用管理、网络请求、系统控制等高敏感能力。

由于认证缺失、网络暴露和工具链缺乏访问控制，任何能够访问设备局域网端口的攻击者均可直接接管设备：执行任意 Shell、读取/写入任意文件、窃取短信/联系人/通话记录、安装 APK、截屏、操控系统设置等。**共确认 13 项中等及以上严重度漏洞，其中 5 项严重（Critical）、5 项高危（High）、3 项中危（Medium）。**

---

## 严重（Critical）

### C1: 默认无认证，局域网任意用户可直接调用全部 MCP 工具

- **攻击者画像**：同一局域网内的任意未认证外部用户（或其他被入侵的局域网设备）。
- **可控输入向量**：向 `http://<device-ip>:1145/mcp` 发送 MCP JSON-RPC 请求。
- **代码路径**：
  1. `MCPHttpServer.start()` 绑定到 `0.0.0.0`（[MCPHttpServer.java:104-106](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L104-L106)）。
  2. `MainActivity.startServer()` 启动 `MCPService` 时**未传递 `EXTRA_API_KEY`**（[MainActivity.java:200-208](file:///workspace/app/src/main/java/com/mcp_run/MainActivity.java#L200-L208)）。
  3. `MCPService.onStartCommand()` 读取到的 `apiKey` 为 `null`（[MCPService.java:70-71](file:///workspace/app/src/main/java/com/mcp_run/MCPService.java#L70-L71)）。
  4. `MCPHttpServer.handleStreamableHttp()` 仅在 `apiKey != null && !apiKey.isEmpty()` 时才校验 Authorization（[MCPHttpServer.java:235-242](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L235-L242)），否则直接放行。
- **造成影响**：完全绕过认证，攻击者无需任何凭证即可调用 `shell`、`read`、`write`、`get_sms`、`get_contacts`、`install_apk` 等全部工具。
- **修复建议**：
  - 强制生成或要求用户设置 API Key，服务启动前未配置则拒绝启动。
  - 将 `Authorization: Bearer <token>` 校验设为强制路径，不允许空 Key 绕过。
  - 默认仅绑定 `127.0.0.1`，局域网访问需用户显式开启并配置强密码。

### C2: `shell` 工具允许未认证执行任意 Shell 命令

- **攻击者画像**：同 C1，任何可访问 MCP 端口的攻击者。
- **可控输入向量**：MCP `tools/call` 请求，参数 `{"name":"shell","arguments":{"command":"<任意命令>"}}`。
- **代码路径**：
  1. `MCPHttpServer.handleToolsCall()` 解析工具名并调用 `tool.execute()`（[MCPHttpServer.java:357-368](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L357-L368)）。
  2. `ShellTool.execute()` 从 `args` 读取 `command` 后，直接构造 `ProcessBuilder("sh", "-c", command)`（[ShellTool.java:60-64](file:///workspace/app/src/main/java/com/mcp_run/tools/ShellTool.java#L60-L64)）。
- **造成影响**：完整远程代码执行（RCE）。可读取 `/data/data/*/shared_prefs/*`、植入木马、删除系统文件、开启反向 Shell、调用 `su` 尝试提权等。
- **修复建议**：
  - 删除该工具或至少要求用户每次调用时手动确认。
  - 若必须保留，实施严格的命令白名单与参数校验，禁止 `;`、`&`、`|`、`$()` 等元字符。
  - 启用沙箱用户/SELinux 域隔离，避免以应用 UID 执行高权限命令。

### C3: 文件工具存在路径遍历，可读写设备任意文件

- **攻击者画像**：同 C1。
- **可控输入向量**：`read`/`write`/`read_base64`/`write_base64`/`delete` 等工具中的 `path` 参数；`set_root`/`cd` 切换工作目录。
- **代码路径**：
  1. `FileSession.resolve()` 对绝对路径直接返回，不校验根目录边界（[FileSession.java:38-43](file:///workspace/app/src/main/java/com/mcp_run/tools/FileSession.java#L38-L43)）。
  2. `FileSystemTool.execCd()` 和 `execSetRoot()` 仅检查目录是否存在，不限制范围（[FileSystemTool.java:154-175](file:///workspace/app/src/main/java/com/mcp_run/tools/FileSystemTool.java#L154-L175)）。
  3. `FileContentTool.execRead()`/`execWrite()` 等直接读取/写入 `resolve()` 后的路径（[FileContentTool.java:121-134](file:///workspace/app/src/main/java/com/mcp_run/tools/FileContentTool.java#L121-L134)、[FileContentTool.java:229-241](file:///workspace/app/src/main/java/com/mcp_run/tools/FileContentTool.java#L229-L241)）。
- **造成影响**：可读取 `/data/data/com.mcp_run/shared_prefs/mcp_config.xml`、其他应用私有文件（需 UID 权限）、系统敏感文件；可写入启动脚本、替换配置文件，进而实现持久化或进一步提权。
- **修复建议**：
  - 在 `FileSession` 中强制实施根目录沙箱：`resolve()` 的结果必须位于 `rootDir` 之下。
  - 拒绝 `..`、符号链接逃逸及绝对路径越界。
  - 对写入操作增加二次确认或白名单扩展名限制。

### C4: `mcp_javascript` / `mcp_python` 工具允许未认证执行任意脚本

- **攻击者画像**：同 C1。
- **可控输入向量**：`tools/call` 中 `{"name":"mcp_javascript","arguments":{"code":"<JS>"}}` 或 `mcp_python` 的 `code`/`file_path`。
- **代码路径**：
  1. `ScriptTool.execJS()` 将用户输入的 `code` 直接拼接到 `echo '...' | d8` 和 `node -e '...'` 中执行（[ScriptTool.java:73-109](file:///workspace/app/src/main/java/com/mcp_run/tools/ScriptTool.java#L73-L109)）。
  2. `ScriptTool.execPython()` 将 `code` 写入临时 `.py` 文件后直接调用解释器（[ScriptTool.java:138-158](file:///workspace/app/src/main/java/com/mcp_run/tools/ScriptTool.java#L138-L158)）；`execute_file` 直接执行任意 `.py` 路径（[ScriptTool.java:126-137](file:///workspace/app/src/main/java/com/mcp_run/tools/ScriptTool.java#L126-L137)）。
- **造成影响**：等同于 C2 的任意代码执行，且脚本语言更易构造反向 Shell、文件遍历、网络扫描等攻击载荷。
- **修复建议**：
  - 移除脚本执行工具，或将其置于独立高权限模式，并要求每次运行前用户手动确认。
  - 对 `escapeForShell()` 的实现进行审计（当前仅替换单引号），避免 Shell 注入。
  - 脚本解释器查找路径中硬编码了 Termux 路径，易被替换，应校验解释器签名或哈希。

### C5: 服务默认监听 `0.0.0.0`，扩大攻击面至整个局域网

- **攻击者画像**：同一 Wi-Fi/局域网内的任意主机，包括被入侵的 IoT 设备、笔记本、手机等。
- **可控输入向量**：网络连接请求。
- **代码路径**：
  - `MCPHttpServer.start()` 使用 `InetAddress.getByName(ANY_INTERFACE)` 创建 `ServerSocket`（[MCPHttpServer.java:38-39、104-106](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L38-L39)）。
- **造成影响**：即使攻击者不在本机，只要能路由到设备 IP 的 1145 端口，即可触发 C1-C4 的全部漏洞。
- **修复建议**：
  - 默认绑定 `127.0.0.1`。
  - 提供显式“局域网模式”开关，开启时强制启用强认证（随机高强度 API Key）。

---

## 高危（High）

### H1: `http_get` / `download_file` 存在 SSRF 与任意文件落地

- **攻击者画像**：同 C1。
- **可控输入向量**：`url` 与 `output` 参数，例如 `"url":"file:///data/data/..."`、`"url":"http://169.254.169.254/"`、`"output":"/sdcard/Download/malware.apk"`。
- **代码路径**：
  1. `HttpTool.doRequest()` 直接将 `urlStr` 传给 `new URL(urlStr)`（[HttpTool.java:94](file:///workspace/app/src/main/java/com/mcp_run/tools/HttpTool.java#L94)）。
  2. `HttpTool.execDownloadFile()` 将下载内容写入用户指定的 `output` 路径（[HttpTool.java:177-200](file:///workspace/app/src/main/java/com/mcp_run/tools/HttpTool.java#L177-L200)）。
- **造成影响**：
  - SSRF：请求内网服务、本地端口、云服务元数据端点。
  - 落地恶意文件：下载 APK、脚本、SO 库等到可执行路径，配合 `install_apk` 或 `shell` 完成安装/执行。
- **修复建议**：
  - 对 `url` 实施白名单（仅允许公网 HTTPS），禁止 `file://`、内网 IP、回环地址。
  - `download_file` 的输出目录应限制在沙箱或用户明确授权的路径。
  - 对出站请求使用独立网络权限并记录审计日志。

### H2: 多个工具可直接窃取敏感 PII 与隐私数据

- **攻击者画像**：同 C1。
- **可控输入向量**：调用 `get_sms`、`get_call_log`、`get_contacts`、`clipboard`、`wifi_scan`、`screenshot`、`device_info` 等工具。
- **代码路径**：
  - 短信：`SmsTool.execute()` 查询 `Telephony.Sms.Inbox.CONTENT_URI`（[SmsTool.java:83-121](file:///workspace/app/src/main/java/com/mcp_run/tools/SmsTool.java#L83-L121)）。
  - 联系人：`ContactTool.execute()` 查询 `ContactsContract`（[ContactTool.java:69-112](file:///workspace/app/src/main/java/com/mcp_run/tools/ContactTool.java#L69-L112)）。
  - 通话记录：`CallLogTool.execute()` 查询 `CallLog.Calls.CONTENT_URI`（[CallLogTool.java:69-103](file:///workspace/app/src/main/java/com/mcp_run/tools/CallLogTool.java#L69-L103)）。
  - 剪贴板：`ClipboardTool.execute()` 读取 `ClipboardManager`（[ClipboardTool.java:58-75](file:///workspace/app/src/main/java/com/mcp_run/tools/ClipboardTool.java#L58-L75)）。
  - WiFi 扫描：`WifiScanTool.execute()` 返回附近 SSID/BSSID/信号强度（[WifiScanTool.java:58-99](file:///workspace/app/src/main/java/com/mcp_run/tools/WifiScanTool.java#L58-L99)）。
  - 截屏：`SystemControlTool.handleScreenshot()` / `SystemControlEnhancedTool.handleSnapshot()` 调用 `screencap`（[SystemControlTool.java:203-214](file:///workspace/app/src/main/java/com/mcp_run/tools/SystemControlTool.java#L203-L214)、[SystemControlEnhancedTool.java:478-490](file:///workspace/app/src/main/java/com/mcp_run/tools/SystemControlEnhancedTool.java#L478-L490)）。
  - 设备标识：`DeviceInfoTool.execute()` 返回 `ANDROID_ID`、系统版本、安全补丁等（[DeviceInfoTool.java:88-95](file:///workspace/app/src/main/java/com/mcp_run/tools/DeviceInfoTool.java#L88-L95)）。
- **造成影响**：大规模隐私泄露，包括短信验证码、联系人、通话记录、密码/URL（剪贴板）、物理位置指纹（BSSID）、屏幕内容、设备指纹等。
- **修复建议**：
  - 所有敏感数据工具必须在认证基础上增加显式用户授权/会话确认。
  - 对返回结果进行脱敏、截断或令牌化，避免原始 PII 直接外传。
  - 将敏感工具标记为“高权限”，默认禁用，需用户逐项启用。

### H3: `install_apk` / `app_manager` 支持未授权安装、停止、卸载任意应用

- **攻击者画像**：同 C1。
- **可控输入向量**：`install_apk` 的 `path`；`app_manager` 的 `action` 与 `package_name`。
- **代码路径**：
  - `ApkInstallTool.execute()` 构造 `pm install -g -r "<path>"` 并执行（[ApkInstallTool.java:59-89](file:///workspace/app/src/main/java/com/mcp_run/tools/ApkInstallTool.java#L59-L89)）。
  - `AppManagerTool.execute()` 的 `force_stop`、`kill_process`、`uninstall`、`launch` 分支直接调用 `am force-stop`、发送 `Intent.ACTION_DELETE` 或启动应用（[AppManagerTool.java:134-245](file:///workspace/app/src/main/java/com/mcp_run/tools/AppManagerTool.java#L134-L245)）。
- **造成影响**：可静默安装恶意 APK（配合下载）、卸载安全软件、终止反病毒/银行应用、启动钓鱼应用界面。
- **修复建议**：
  - 删除或高权限隔离这些工具。
  - 安装 APK 时必须启动系统安装器并由用户点击确认，禁止直接 `pm install`。
  - `force_stop`/`uninstall` 等破坏性操作需用户每次确认。

### H4: `NotificationReceiver` 被导出，任何应用可停止 MCP 服务

- **攻击者画像**：设备上安装的任何第三方应用（无需权限）。
- **可控输入向量**：发送广播 `com.mcp_run.ACTION_STOP`。
- **代码路径**：
  - `AndroidManifest.xml` 中 `NotificationReceiver` 的 `exported="true"`（[AndroidManifest.xml:153-159](file:///workspace/app/src/main/AndroidManifest.xml#L153-L159)）。
  - `NotificationReceiver.onReceive()` 调用 `MCPService.handleStopClick()`（[NotificationReceiver.java:14-23](file:///workspace/app/src/main/java/com/mcp_run/NotificationReceiver.java#L14-L23)）。
- **造成影响**：拒绝服务，恶意应用可持续发送该广播阻止 MCP 服务正常运行。
- **修复建议**：
  - 将 `exported` 设为 `false`，或使用 `android:permission` 限制只有本应用组件可发送该广播。
  - PendingIntent 应显式指向应用自身组件。

### H5: 发布签名密钥硬编码于 `build.gradle`

- **攻击者画像**：获得源码、APK 或构建产物的任何人。
- **可控输入向量**：读取 `app/build.gradle`。
- **代码路径**：
  - [app/build.gradle:18-27](file:///workspace/app/build.gradle#L18-L27) 中直接写入 `storePassword "mcp_run_2024"` 与 `keyPassword "mcp_run_2024"`，且 `storeFile file("keystore/mcp_run.keystore")`。
- **造成影响**：攻击者可使用相同密钥对恶意 APK 重签名，使系统/用户误以为是官方发布版本；若私钥泄露还可伪造应用更新。
- **修复建议**：
  - 立即撤销并更换该密钥。
  - 将密钥信息移至环境变量、CI 密钥管理或 `local.properties`，并对 `keystore/` 目录做 `.gitignore` 保护。
  - 使用 Gradle 的 `secrets-gradle-plugin` 或 Android Keystore 系统管理敏感构建配置。

---

## 中危（Medium）

### M1: 未认证 `/status`、`/`、`/tools`、`/history` 接口泄露服务器配置与可用工具

- **攻击者画像**：同 C1。
- **可控输入向量**：HTTP GET 请求。
- **代码路径**：
  - `MCPHttpServer.handleClient()` 对 `GET /`、`GET /status`、`GET /tools`、`GET /history` 直接响应，不经过 API Key 校验（[MCPHttpServer.java:212-221](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L212-L221)）。
  - 这些响应包含 `auth_enabled`、端口、绑定地址、`available_tools`、请求历史等（[MCPHttpServer.java:447-521](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L447-L521)）。
- **造成影响**：为攻击者提供侦察信息，包括是否存在认证、暴露端口、可用攻击工具列表。
- **修复建议**：
  - 所有端点统一纳入认证中间件保护。
  - 在 `auth_enabled=false` 时，对 `status`、`tools`、`history` 等敏感信息端点返回 401。

### M2: `android:allowBackup="true"` 与明文流量配置

- **攻击者画像**：可物理接触设备或获取 Google 备份数据的攻击者；中间人攻击者。
- **可控输入向量**：ADB 备份、Google 云端备份、网络嗅探。
- **代码路径**：
  - `AndroidManifest.xml` 设置 `android:allowBackup="true"`（[AndroidManifest.xml:115](file:///workspace/app/src/main/AndroidManifest.xml#L115)）。
  - `android:usesCleartextTraffic="true"`（[AndroidManifest.xml:121](file:///workspace/app/src/main/AndroidManifest.xml#L121)）。
  - `network_security_config.xml` 中 `cleartextTrafficPermitted="true"`（[network_security_config.xml:3](file:///workspace/app/src/main/res/xml/network_security_config.xml#L3)）。
- **造成影响**：
  - 应用私有数据（如 SharedPreferences 中的配置、数据库）可被 ADB 备份导出。
  - MCP 通信默认可被中间人明文窃听/篡改。
- **修复建议**：
  - 将 `allowBackup` 设为 `false`。
  - 仅在调试构建启用明文流量，发布构建强制 HTTPS；MCP 服务器若本地使用，应通过 TLS/自签名证书或 Unix Domain Socket 通信。

### M3: `BootReceiver` 导出且默认开机自启服务

- **攻击者画像**：设备上任何可发送广播的恶意应用（但受 `BOOT_COMPLETED` 触发条件限制）。
- **可控输入向量**：系统启动广播。
- **代码路径**：
  - `BootReceiver` 声明为 `exported="true"`（[AndroidManifest.xml:173-182](file:///workspace/app/src/main/AndroidManifest.xml#L173-L182)）。
  - `BootReceiver.onReceive()` 读取 `auto_start`（默认 `true`）后启动 `MCPService`（[BootReceiver.java:15-39](file:///workspace/app/src/main/java/com/mcp_run/BootReceiver.java#L15-L39)）。
- **造成影响**：设备重启后，若用户曾启用服务器，服务会自动重新暴露网络攻击面。
- **修复建议**：
  - 将 `BootReceiver` 的 `exported` 设为 `false`。
  - 自启前再次检查是否已配置强认证，未配置则拒绝启动并通知用户。

---

## 修复优先级总览

| 优先级 | 措施 |
|--------|------|
| P0 | 强制 API Key 认证；修复认证绕过；默认绑定 `127.0.0.1`；移除或沙箱化 `shell`、`mcp_javascript`、`mcp_python` 工具。 |
| P1 | 文件系统实施根目录沙箱与路径遍历防护；SSRF 过滤与下载路径限制；`NotificationReceiver` 不导出。 |
| P2 | 敏感数据工具增加用户逐项授权；安装/停止/卸载应用操作走系统确认流程；更换并保护签名密钥。 |
| P3 | 关闭 `allowBackup`、发布构建禁用明文流量、关闭 `BootReceiver` 导出。 |

---

*报告生成时间：2026-09-11*  
*审计范围：`/workspace/app/src/main/java/com/mcp_run` 及相关 Android 配置*
