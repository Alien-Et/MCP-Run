# MCP·AC Android 应用安全审计报告

## 执行摘要

本次审计针对 `/workspace` 下的 Android 项目 `com.mcp_run`（MCP·AC）。该应用是一个基于 MCP 协议的本地 HTTP 服务器，把手机上的大量敏感/高权限能力（Shell、文件系统、短信、联系人、通话记录、应用管理、APK 安装、系统控制等）以 JSON-RPC 形式暴露出来。

**核心风险：应用在默认配置下把 HTTP 服务器绑定到 `0.0.0.0`（所有网络接口）且未启用任何认证。** 这意味着同一局域网内的任意设备、或设备上任何能连接 `localhost` 的进程，都可以直接调用全部高权限工具，造成设备完全失控、隐私数据批量泄露、恶意软件安装等严重后果。

共确认 **1 项严重、4 项高危、2 项中危** 漏洞，均具备清晰的端到端利用路径。

---

## 严重（Critical）

### CRIT-1：HTTP 服务器默认监听 0.0.0.0 且无认证，任意网络可达攻击者可完全控制设备

- **位置**：
  - [MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L103-L106) 绑定 `0.0.0.0`
  - [MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L235-L242) 仅在 `apiKey` 非空时校验 `Authorization`
  - [MainActivity.java](file:///workspace/app/src/main/java/com/mcp_run/MainActivity.java#L151-L159) 启动服务时未传入 `EXTRA_API_KEY`
  - [MCPService.java](file:///workspace/app/src/main/java/com/mcp_run/MCPService.java#L70-L76) 默认 `apiKey = null`
  - [BootReceiver.java](file:///workspace/app/src/main/java/com/mcp_run/BootReceiver.java#L24-L37) 开机自启服务，同样未设置 API Key

- **攻击者画像**：同一 Wi-Fi/局域网的外部攻击者；设备上其他应用（通过 `127.0.0.1` 或本机 IP）；恶意网页（配合 `Access-Control-Allow-Origin: *`）。

- **可控输入**：向 `http://<device-ip>:1145/mcp` 发送任意 JSON-RPC `tools/call` 请求，例如：
  ```json
  {
    "jsonrpc": "2.0",
    "method": "tools/call",
    "params": {
      "name": "shell",
      "arguments": { "command": "id && ls /data/data" }
    },
    "id": 1
  }
  ```

- **完整代码路径**：
  1. `BootReceiver` 开机后或用户点击“启动服务器”后，`MCPService` 被启动；
  2. `MCPService.onStartCommand()` 没有拿到 `EXTRA_API_KEY`，`apiKey` 保持 `null`；
  3. `MCPHttpServer` 构造函数保存 `apiKey = null`；
  4. `MCPHttpServer.start()` 使用 `InetAddress.getByName("0.0.0.0")` 创建 `ServerSocket`，接受所有网卡连接；
  5. 请求进入 `handleClient()` → `handleStreamableHttp()`；
  6. 由于 `apiKey` 为空，`if (apiKey != null && !apiKey.isEmpty())` 分支不执行，认证被跳过；
  7. 请求体被解析后进入 `handleToolsCall()`，按 `params.name` 取出工具并直接执行 `tool.execute(context, arguments)`。

- **造成影响**：
  - **远程代码执行**：`ShellTool` 直接执行 `sh -c <command>`（[ShellTool.java:64](file:///workspace/app/src/main/java/com/mcp_run/tools/ShellTool.java#L64)）。
  - **批量隐私泄露**：`ContactTool`、`SmsTool`、`CallLogTool` 读取联系人、短信、通话记录。
  - **任意文件读写**：`FileSystemTool` / `FileContentTool` 配合 `MANAGE_EXTERNAL_STORAGE` 可读写外部存储几乎所有文件。
  - **应用控制与恶意安装**：`AppManagerTool`、`ApkInstallTool` 可强制停止、卸载、安装 APK。
  - **系统控制/拒绝服务**：`SystemControlTool` 可锁屏、重启 SystemUI、修改系统设置等。

- **修复建议**：
  1. 默认仅绑定 `127.0.0.1`；如需局域网访问，必须在用户明确开启且完成认证配置后才允许 `0.0.0.0`。
  2. 强制启用认证：生成随机高强度 API Key 并在 UI 中展示/扫码，每次 RPC 校验 `Authorization: Bearer <key>`。
  3. 移除或严格限制 `BootReceiver` 的自动启动行为，未配置认证时不应启动服务。
  4. 使用 HTTPS（TLS）或至少支持 TLS 的隧道，避免明文传输控制指令。
  5. 移除 `Access-Control-Allow-Origin: *`，或限制为可信源。

---

## 高危（High）

### HIGH-1：文件系统工具根目录限制失效，存在路径遍历

- **位置**：
  - [FileSession.java](file:///workspace/app/src/main/java/com/mcp_run/tools/FileSession.java#L38-L42) `resolve()` 对绝对路径直接放行
  - [FileContentTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/FileContentTool.java#L119-L122) 调用 `session.resolve()` 后读取文件
  - [FileSystemTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/FileSystemTool.java#L178-L179) 同样使用 `session.resolve()`

- **攻击者画像**：任何能调用文件工具的攻击者（在 CRIT-1 的前提下为任意网络可达者）。

- **可控输入**：`read`/`write`/`delete` 等工具的 `path` 参数传入绝对路径，例如 `/storage/emulated/0/Android/data/<其他应用>/files/xxx` 或 `/data/data/com.mcp_run/shared_prefs/mcp_config.xml`。

- **利用路径**：`FileSession.resolve()` 中 `if (f.isAbsolute()) return f.getAbsolutePath();` 直接返回绝对路径，`set_root` 设置的根目录不会被校验。应用还声明了 `MANAGE_EXTERNAL_STORAGE`，因此可以遍历、读取、写入、删除外部存储上任意文件。

- **造成影响**：
  - 读取其他应用在外部存储的私有文件、用户照片/文档；
  - 篡改或删除系统/应用关键文件；
  - 窃取本应用 `SharedPreferences` 等配置。

- **修复建议**：
  1. 在 `resolve()` 中做根目录白名单校验，使用 `getCanonicalPath()` 后检查是否以 `rootDir` 开头。
  2. 删除不必要的 `MANAGE_EXTERNAL_STORAGE` 权限，改用 Storage Access Framework 或 MediaStore。
  3. 对 `write`/`delete` 增加二次确认或仅允许操作限定的沙箱目录。

### HIGH-2：APK 安装工具存在 Shell 命令注入

- **位置**：[ApkInstallTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/ApkInstallTool.java#L59-L78)

- **攻击者画像**：能调用 `install_apk` 的攻击者。

- **可控输入**：`path` 参数。

- **利用路径**：`execute()` 拼接命令：
  ```java
  StringBuilder cmd = new StringBuilder("pm install ");
  if (grantAll) cmd.append("-g ");
  if (replace) cmd.append("-r ");
  cmd.append("\"").append(path).append("\"");
  ```
  `path` 未做转义。若攻击者先通过文件写入工具创建名为 `evil"; id; #.apk` 的文件，再调用 `install_apk` 并传入该路径，最终 shell 命令会被截断并执行注入的 `id` 等任意命令。

- **造成影响**：以应用 UID 执行任意 shell 命令，可配合文件工具实现提权/持久化，或安装恶意 APK。

- **修复建议**：
  1. 使用 `ProcessBuilder` 或 `Runtime.exec(String[])` 传递参数，避免字符串拼接。
  2. 对 `path` 做白名单校验：必须为 `.apk`、不能包含 `"`、`$`、反引号等 shell 元字符。
  3. 优先使用 `PackageManager` / `Intent.ACTION_INSTALL_PACKAGE` 等系统 API，而非 `pm install`。

### HIGH-3：Python 脚本执行存在命令注入

- **位置**：[ScriptTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/ScriptTool.java#L126-L136)

- **攻击者画像**：能调用 `mcp_python` 工具 `execute_file` 的攻击者。

- **可控输入**：`file_path` 参数。

- **利用路径**：`execute_file` 拼接 `interpreter + " \"" + filePath + "\" 2>&1"`。若 `file_path` 包含双引号或 shell 元字符，可破坏命令结构并注入任意命令。

- **造成影响**：任意 shell 命令执行（与 HIGH-2 类似）。

- **修复建议**：使用 `ProcessBuilder(Arrays.asList(interpreter, filePath))`；校验路径字符；禁止路径穿越。

### HIGH-4：发布签名密钥密码硬编码在构建脚本中

- **位置**：[app/build.gradle](file:///workspace/app/build.gradle#L18-L25)

- **攻击者画像**：能够访问代码仓库或构建产物的内部/外部人员。

- **可控输入**：无需运行时输入，密码直接写在 Gradle 配置里。

- **利用路径**：
  1. `build.gradle` 中 `storePassword` 与 `keyPassword` 均为 `"mcp_run_2024"`；
  2. 密钥库路径指向 `app/keystore/mcp_run.keystore`；
  3. 若攻击者同时获取该密钥库文件（如 CI 产物、备份、文件泄露），即可使用硬编码密码导出私钥并签名任意 APK。

- **造成影响**：
  - 可发布冒用该应用签名的恶意更新包；
  - 破坏应用升级签名链的信任基础。

- **修复建议**：
  1. 立即从仓库中删除密码，改用环境变量或 `local.properties`（加入 `.gitignore`）。
  2. 密钥库文件不得加入版本控制。
  3. 启用 Google Play App Signing 等托管签名方案，降低私钥泄露影响。

---

## 中危（Medium）

### MED-1：HTTP 工具存在 SSRF（服务器端请求伪造）

- **位置**：[HttpTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/HttpTool.java#L93-L128)

- **攻击者画像**：能调用 `http_get`/`http_post`/`download_file` 等工具的攻击者。

- **可控输入**：`url` 参数。

- **利用路径**：`doRequest()` 直接使用 `new URL(urlStr).openConnection()`，未校验目标地址，且 `setInstanceFollowRedirects(true)`。攻击者可传入 `http://127.0.0.1:<port>/...`、`http://10.0.0.1/...` 或云环境元数据地址（如 `http://169.254.169.254/`），让应用代为发起请求并返回结果。

- **造成影响**：
  - 探测/攻击设备本地服务（包括 MCP 服务自身）；
  - 在公有云/模拟器环境下读取实例元数据（可能泄露临时凭证）；
  - 作为跳板对内部网络进行端口扫描。

- **修复建议**：
  1. 对 URL 做白名单校验：仅允许 `http`/`https`，禁止私有地址段、回环地址、链路本地地址。
  2. 禁用自动跳转，或跳转后重新校验目标地址。
  3. 限制请求端口和协议，避免 `file://`、`ftp://` 等 scheme。

### MED-2：明文传输与过度开放的 CORS

- **位置**：
  - [AndroidManifest.xml](file:///workspace/app/src/main/AndroidManifest.xml#L121) `android:usesCleartextTraffic="true"`
  - [network_security_config.xml](file:///workspace/app/src/main/res/xml/network_security_config.xml#L3) `cleartextTrafficPermitted="true"`
  - [MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L544) `Access-Control-Allow-Origin: *`

- **攻击者画像**：本地网络中的被动监听者或能诱使用户访问恶意网页的攻击者。

- **利用路径**：
  1. 由于服务器使用明文 HTTP，局域网内的嗅探者可直接读取请求/响应内容；
  2. `Access-Control-Allow-Origin: *` 允许任意网页通过浏览器 `fetch`/`XHR` 向设备发起跨域请求，配合 CRIT-1 的无认证状态，可直接从网页端触发设备控制命令。

- **造成影响**：命令与数据可被窃听/篡改；恶意网页可在用户访问时直接操作设备。

- **修复建议**：
  1. 服务器启用 HTTPS，使用本地自签名证书或用户导入证书；`usesCleartextTraffic` 不应全局开启。
  2. 移除 `Access-Control-Allow-Origin: *`，改为仅允许可信源，或禁用浏览器跨域访问。

---

## 未纳入报告的低风险/按设计行为说明

- 部分工具（如 `ShellTool`、`get_sms`、`get_contacts`）本身是高权限能力，若在有强认证、仅本地回环、HTTPS 的前提下使用，可视为应用功能而非独立漏洞。本次报告关注的是**因访问控制缺失、注入点、凭证泄露、明文传输等导致这些能力被未授权利用**的路径。
- `BootReceiver` 的 `exported="true"` 本身受 `BOOT_COMPLETED` 等受保护广播限制，第三方应用通常无法直接触发，但其自动启动无认证服务的行为已在 CRIT-1 中体现。
- 未发现 SQL 注入、模板注入、ContentProvider 注入或源代码中硬编码的第三方 API 密钥/PII 日志。

---

## 修复优先级建议

1. **立即处理**：修复 CRIT-1（默认绑定 `0.0.0.0` + 无认证）；这是所有后续利用的前提。
2. **本周内处理**：HIGH-1（路径遍历）、HIGH-2（APK 安装命令注入）、HIGH-3（Python 命令注入）、HIGH-4（签名密钥密码硬编码）。
3. **短期内处理**：MED-1（SSRF）、MED-2（明文传输与 CORS）。

---

*报告生成时间：2026-08-30*
