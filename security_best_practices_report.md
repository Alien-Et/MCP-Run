# MCP·AC Android 安全审计报告

## 执行摘要

本次审计针对仓库 `/workspace` 中的 Android 应用 `com.mcp_run`（MCP·AC）。该应用实现了一个基于 MCP 协议的 HTTP 服务器，绑定到所有网络接口，并对外暴露 70 余个高权限工具（Shell 执行、文件读写、APK 安装、短信/联系人/通话记录读取、系统控制等）。

**核心风险：应用在默认配置下不启用任何身份认证**。任何能够与设备端口 1145（默认）连通的网络攻击者，均可直接调用这些工具，实现完整的设备接管。即使在用户手动启用 API Key 后，仍有多个 GET 端点、文件路径遍历、命令注入、明文传输等问题可被利用。

本次审计共确认 **6 个中等及以上严重度漏洞**，其中 **2 个严重、1 个高危、3 个中危**。

---

## 严重度说明

| 严重度 | 定义 |
|--------|------|
| 严重 (Critical) | 可导致远程完整设备接管、敏感数据大规模泄露或关键功能被完全控制 |
| 高危 (High) | 可导致敏感数据泄露、权限提升、任意文件写入或严重服务破坏 |
| 中危 (Medium) | 可导致信息泄露、有限命令执行、服务拒绝或凭证泄露风险 |

---

## 严重 (Critical)

### CRIT-1：默认无认证的局域网远程设备接管

**一句话影响**：同一网络内的任意攻击者无需凭证即可通过 HTTP 调用任意 MCP 工具，执行 Shell、读写文件、读取短信/联系人、安装 APK 等，完全控制设备。

#### 攻击者画像
外部网络用户（同一 Wi-Fi / 局域网内），无需任何设备权限或事先认证。

#### 可控输入向量
- TCP 连接到 `0.0.0.0:1145`（默认端口）。
- HTTP POST `/mcp`，Body 为 JSON-RPC，例如调用 `tools/call`。

#### 代码路径
1. 服务器绑定到所有接口：[MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L37-L106) 中 `ANY_INTERFACE = "0.0.0.0"`，并在 `start()` 中使用该地址创建 `ServerSocket`。
2. 默认不启用认证：
   - [MainActivity.java](file:///workspace/app/src/main/java/com/mcp_run/MainActivity.java#L200-L208) 启动 `MCPService` 时只传递 `EXTRA_PORT`，未传递 `EXTRA_API_KEY`。
   - [MCPService.java](file:///workspace/app/src/main/java/com/mcp_run/MCPService.java#L64-L80) 从 Intent 读取 `apiKey` 并传给 `MCPHttpServer`。
   - [MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L235-L241) 仅在 `apiKey != null && !apiKey.isEmpty()` 时校验 `Authorization` 头，否则直接放行。
3. 请求路由到工具执行：[MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L357-L368) 将 `tools/call` 分派到 `ToolRegistry.getTool(name).execute(...)`。
4. 危险工具集合：[ToolRegistry.java](file:///workspace/app/src/main/java/com/mcp_run/tools/ToolRegistry.java#L78-L175) 注册了 `ShellTool`、`FileSystemTool`、`FileContentTool`、`ApkInstallTool`、`SmsTool`、`ContactTool`、`CallLogTool`、`HttpTool` 等。

#### 造成的影响
- 远程任意 Shell 执行（`shell` 工具）。
- 任意文件读/写/删除（`read`/`write`/`delete` 等文件工具）。
- 读取短信、联系人、通话记录（`get_sms`、`get_contacts`、`get_call_log`）。
- 下载并安装 APK（`download_file` + `install_apk`）。
- 通过 `system_control` 锁屏、开关 Wi-Fi/蓝牙、发送按键事件、重启 SystemUI 等。
- [BootReceiver.java](file:///workspace/app/src/main/java/com/mcp_run/BootReceiver.java#L15-L38) 在开机后自动以默认无认证配置启动服务，使漏洞持续暴露。

#### 修复建议
1. **强制启用认证**：启动服务器时必须生成或要求用户设置强随机 API Key；无 Key 时拒绝启动或仅监听回环地址。
2. **默认仅监听回环**：将默认绑定地址改为 `127.0.0.1`，并仅在用户显式开启“局域网访问”时绑定 `0.0.0.0`。
3. **为所有端点实施认证**：不仅 POST `/mcp`，GET `/status`、`/tools`、`/history` 等也应要求相同的 `Authorization` 头。
4. **使用 HTTPS**：为 LAN 访问生成/导入 TLS 证书，避免 API Key 和请求体明文传输。
5. **降低工具默认权限**：对 Shell、安装 APK 等高危工具增加二次确认或独立授权机制。

---

### CRIT-2：文件工具路径遍历导致任意文件读写

**一句话影响**：文件系统工具未将请求路径限制在工作根目录内，攻击者可通过绝对路径读取、写入或删除设备上任何可访问的文件。

#### 攻击者画像
已能访问 MCP 服务器的攻击者（见 CRIT-1），或经认证后的恶意 MCP 客户端。

#### 可控输入向量
`FileSystemTool` / `FileContentTool` 中所有接受 `path`、`source`、`destination` 的参数，例如 `"path": "/data/data/com.mcp_run/shared_prefs/mcp_config.xml"`。

#### 代码路径
1. 路径解析未做沙箱限制：[FileSession.java](file:///workspace/app/src/main/java/com/mcp_run/tools/FileSession.java#L38-L43) 的 `resolve()` 对绝对路径直接返回，不做根目录校验。
2. 文件工具直接使用解析结果：
   - [FileSystemTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/FileSystemTool.java#L178-L480) 在 `exists`、`stat`、`ls`、`find`、`grep`、`mkdir`、`touch`、`empty`、`copy`、`rename`、`delete`、`edit` 中均调用 `session.resolve(path)`。
   - [FileContentTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/FileContentTool.java#L119-L306) 在 `read`、`write`、`append`、`read_base64`、`write_base64`、`compare_files` 中同样调用 `session.resolve(...)`。
3. 绝对路径可直达系统或应用私有目录：`/storage/emulated/0/...`、`/data/data/com.mcp_run/...` 等。

#### 造成的影响
- 读取任意文本/二进制文件（短信数据库、联系人、应用私有 SharedPreferences 等）。
- 写入/覆盖任意文件，植入恶意脚本、替换 APK、篡改配置文件。
- 递归删除任意目录（`delete` 工具 `recursive=true`）。
- 绕过所谓“工作区/根目录”的安全边界。

#### 修复建议
1. 在 `FileSession.resolve()` 中实施**强制根目录校验**：将解析结果规范化为绝对路径后，必须位于 `rootDir` 之下；否则抛出安全异常。
2. 禁止 `..` 穿越；对 `source`、`destination`、`path` 等参数统一做校验。
3. 为不同工具设置最小权限原则：例如只读工具不应能调用写入/删除操作。
4. 对敏感目录（`/data/data/`、系统目录）实施显式黑名单或沙箱隔离。

---

## 高危 (High)

### HIGH-1：HttpTool 任意文件下载与写入 / SSRF

**一句话影响**：`download_file` / `download_text` 允许攻击者指定任意 URL 和本地保存路径，并跟随重定向，可导致设备被用作内网探测跳板，并向任意路径写入文件。

#### 攻击者画像
已能访问 MCP 服务器的攻击者。

#### 可控输入向量
- `url`：任意 HTTP(S) URL，包括 `http://127.0.0.1:xxx/...` 或指向内网服务。
- `output`：任意本地文件路径（包括绝对路径）。

#### 代码路径
1. [HttpTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/HttpTool.java#L177-L199) 的 `execDownloadFile` 直接用 `new File(output)` 创建目标文件，未调用 `FileSession.resolve()` 或校验路径。
2. 使用 `HttpURLConnection` 并启用重定向：[HttpTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/HttpTool.java#L94-L129) 调用 `conn.setInstanceFollowRedirects(true)`。
3. `execDownloadText` 同样从任意 URL 下载并写入用户指定路径：[HttpTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/HttpTool.java#L160-L175)。

#### 造成的影响
- **SSRF**：攻击者让设备请求内网服务（如路由器管理页、本地 HTTP 服务），探测内网拓扑、读取内部接口。
- **任意文件写入**：将攻击者控制的内容写入 `/sdcard/Download/malware.apk` 或应用可写的任意路径，为后续安装或覆盖文件做准备。
- 结合 CRIT-1，可在无认证设备上实现“下载→安装”完整利用链。

#### 修复建议
1. 对 `output` 路径强制经过 `FileSession.resolve()` 并限制在允许的根目录内。
2. 对 `url` 实施白名单或黑名单：禁止 `127.0.0.1`、`localhost`、私有网段、非 HTTP/HTTPS 协议；限制重定向次数并校验重定向后的地址。
3. 对下载文件大小、类型进行限制，并存储到隔离的临时目录。

---

## 中危 (Medium)

### MED-1：硬编码发布签名密钥库密码

**一句话影响**：发布签名配置中硬编码了密钥库文件路径、库密码和密钥密码，任何能访问代码的人均可能利用该凭证伪造应用更新包。

#### 攻击者画像
拥有仓库访问权限的内部人员、CI/CD 日志读取者或代码泄露后的外部人员。

#### 可控输入向量
仓库源码本身。

#### 代码路径
- [app/build.gradle](file:///workspace/app/build.gradle#L18-L27) 的 `signingConfigs.release` 中明文写出：
  - `storeFile file("keystore/mcp_run.keystore")`
  - `storePassword "mcp_run_2024"`
  - `keyAlias "mcp_run"`
  - `keyPassword "mcp_run_2024"`
- 该签名配置同时被 `release` 和 `debug` 构建类型使用。
- [README.md](file:///workspace/README.md#L136-L139) 也重复泄露了相同凭证。

#### 造成的影响
- 若攻击者同时获取到 `keystore/mcp_run.keystore` 文件，可使用这些密码对恶意 APK 进行签名，使其与官方应用拥有相同签名。
- 相同签名的恶意更新可能绕过系统更新校验，诱导用户覆盖安装。

#### 修复建议
1. 立即从 `build.gradle` 和 `README.md` 中移除硬编码密码；将 `storePassword` / `keyPassword` 替换为环境变量或本地 `local.properties` / CI 密钥管理服务读取。
2.  rotating 签名密钥，并重新发布使用新密钥签名的版本。
3. 不在版本控制中存放密钥库文件；在 `.gitignore` 中明确排除 `*.keystore`、`*.jks`。

---

### MED-2：明文流量全局放行导致凭证与数据可被嗅探

**一句话影响**：应用全局允许明文 HTTP 传输，若用户启用 API Key，攻击者可在同网络内通过 MITM 窃取 Key 及所有工具请求/响应内容。

#### 攻击者画像
同一局域网内的中间人攻击者，或控制设备所连 Wi-Fi 热点者。

#### 可控输入向量
网络流量；无需向应用发送特定输入。

#### 代码路径
- [AndroidManifest.xml](file:///workspace/app/src/main/AndroidManifest.xml#L121) 在 `<application>` 上设置 `android:usesCleartextTraffic="true"`。
- [res/xml/network_security_config.xml](file:///workspace/app/src/main/res/xml/network_security_config.xml#L3) 的 `<base-config cleartextTrafficPermitted="true">` 将明文传输策略应用到所有域名。
- [MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L155) 的 endpoint URL 使用 `http://`。

#### 造成的影响
- API Key（如果启用）以 `Authorization: Bearer <key>` 明文传输，可被网络嗅探获取。
- 工具请求体（包含 shell 命令、文件路径、短信/联系人读取结果）和响应全部明文，泄露敏感信息。

#### 修复建议
1. 移除全局 `usesCleartextTraffic="true"` 和 `cleartextTrafficPermitted="true"`；如确需明文，仅对特定可信域名配置 `<domain-config>`。
2. 为 MCP HTTP 服务器启用 TLS：使用自签名证书、Let’s Encrypt 或局域网 CA 签发的证书。
3. 在 UI 中提示用户当前连接未加密的风险。

---

### MED-3：多处工具将用户输入拼接到 Shell 命令（命令注入）

**一句话影响**：ApkInstallTool、AppManagerTool、SystemControlTool 等未对用户输入做转义或参数化，攻击者可通过特殊字符注入额外 Shell 命令。

#### 攻击者画像
已能调用 MCP 工具的攻击者。

#### 可控输入向量
- `install_apk` 的 `path` 参数。
- `app_manager` 的 `package_name` 参数。
- `system_control` 的 `property`、`key_code` 参数。

#### 代码路径
1. [ApkInstallTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/ApkInstallTool.java#L73-L78) 构造命令：
   ```java
   StringBuilder cmd = new StringBuilder("pm install ");
   ...
   cmd.append("\"").append(path).append("\"");
   String output = execShell(cmd.toString() + " 2>&1");
   ```
   输入中的 `"` 或 `;` 可闭合引号并追加命令。
2. [AppManagerTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/AppManagerTool.java#L150-L160) 和 [AppManagerTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/AppManagerTool.java#L235-L245) 直接将 `package_name` 拼接到 `am force-stop ` 后执行。
3. [SystemControlTool.java](file:///workspace/app/src/main/java/com/mcp_run/tools/SystemControlTool.java#L258-L278) 将 `property` 拼接到 `getprop `、将 `key_code` 拼接到 `input keyevent ` 后执行。

#### 造成的影响
- 在相关工具调用上下文中执行预期之外的 Shell 命令。
- 例如通过 `install_apk` 的 `path` 注入 `; rm -rf /sdcard/... ;`。
- 虽然当前 `shell` 工具已提供任意命令执行能力，但这些注入点属于独立的输入校验缺陷，未来若对 `shell` 工具做权限拆分，仍会被利用。

#### 修复建议
1. 避免拼接命令字符串；使用 `ProcessBuilder` 并传递参数数组，使 Shell 不会解释用户输入中的元字符。
2. 若必须拼接，则对输入进行严格白名单校验（例如包名仅允许 `[a-zA-Z0-9._]`，文件路径必须以 `.apk` 结尾且位于允许目录）。
3. 对 `property`、`key_code` 等枚举值使用白名单匹配。

---

### MED-4：GET 端点绕过 API Key 造成信息泄露

**一句话影响**：即使启用了 API Key，攻击者仍可直接访问 `/status`、`/tools`、`/history`，获取工具列表、服务器配置和请求历史。

#### 攻击者画像
知道服务器 IP 和端口的网络攻击者。

#### 可控输入向量
HTTP GET 请求到 `/status`、`/tools` 或 `/history`。

#### 代码路径
1. [MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L212-L217) 在 `handleClient` 中直接处理这些 GET 路径，未进入 `handleStreamableHttp` 的认证检查。
2. 认证检查仅位于 [MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L235-L241) 的 `handleStreamableHttp` 中。
3. `handleStatus`（[MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L447-L483)）会返回 `auth_enabled`、`available_tools`、`endpoint`、`bind_address` 等敏感信息。
4. `handleHistory`（[MCPHttpServer.java](file:///workspace/app/src/main/java/com/mcp_run/MCPHttpServer.java#L473-L483)）返回最近 500 条请求日志，包含方法、路径和响应码。

#### 造成的影响
- 攻击者可枚举所有可用工具及其名称，为后续调用做准备。
- 可确认认证是否开启、服务器绑定的 IP/端口。
- 请求历史可能泄露用户操作模式和部分敏感路径。

#### 修复建议
1. 将 GET `/status`、`/tools`、`/history` 统一纳入 `handleStreamableHttp` 处理，或在这些 handler 开头复用相同的 API Key 校验逻辑。
2. 限制 `/history` 内容，不对外暴露内部请求日志；如必须暴露，增加访问控制和审计。

---

### MED-5：导出的 NotificationReceiver 可被任意应用停止服务

**一句话影响**：`NotificationReceiver` 未设置权限且 `exported="true"`，设备上任何应用都能发送 `com.mcp_run.ACTION_STOP` 广播停止 MCP 服务。

#### 攻击者画像
已安装在设备上的其他恶意应用（无需网络权限）。

#### 可控输入向量
发送 Action 为 `com.mcp_run.ACTION_STOP` 的广播 Intent。

#### 代码路径
1. [AndroidManifest.xml](file:///workspace/app/src/main/AndroidManifest.xml#L153-L159) 声明：
   ```xml
   <receiver android:name=".NotificationReceiver" android:exported="true">
       <intent-filter>
           <action android:name="com.mcp_run.ACTION_STOP" />
       </intent-filter>
   </receiver>
   ```
2. [NotificationReceiver.java](file:///workspace/app/src/main/java/com/mcp_run/NotificationReceiver.java#L15-L22) 收到该 Action 后直接调用 `MCPService.handleStopClick(context)`，停止服务器并退出应用。

#### 造成的影响
- 恶意应用可反复停止 MCP 服务，造成拒绝服务。
- 可配合社会工程学让用户误以为服务异常，从而诱导用户重新启动并触发其他攻击。

#### 修复建议
1. 将 `NotificationReceiver` 的 `exported` 设为 `false`，或使用 `android:permission` 限制只有系统/本应用可发送该广播。
2. 内部广播 Action 使用动态注册并设置 `RECEIVER_NOT_EXPORTED`（参考 [MainActivity.java](file:///workspace/app/src/main/java/com/mcp_run/MainActivity.java#L80-L84) 中的做法）。

---

## 修复优先级总结

| 优先级 | 漏洞 ID | 修复动作 |
|--------|---------|----------|
| P0 | CRIT-1 | 强制默认认证、默认回环监听、为所有端点加认证、启用 TLS |
| P0 | CRIT-2 | 在 `FileSession.resolve()` 中实施根目录沙箱校验 |
| P1 | HIGH-1 | 校验下载 URL、限制重定向、将 `output` 限制在允许目录 |
| P2 | MED-1 | 从源码中移除签名密码，迁移到环境变量/CI 密钥管理并轮换密钥 |
| P2 | MED-2 | 移除全局明文流量配置，为 MCP 服务启用 HTTPS |
| P2 | MED-3 | 使用 `ProcessBuilder` 参数数组或严格白名单，消除命令拼接 |
| P2 | MED-4 | 为 GET `/status`、`/tools`、`/history` 增加 API Key 校验 |
| P2 | MED-5 | 将 `NotificationReceiver` 改为非导出或使用权限保护 |

---

## 未发现的更高危问题

- 未发现 SQL 注入：`SmsTool`、`ContactTool`、`CallLogTool` 均使用硬编码 projection/selection，未将用户输入放入 `ContentResolver.query()` 的 `selection` 参数。
- 未发现 WebView 相关漏洞：项目中未使用 WebView。
- 未发现硬编码第三方 API Key/Token（除签名密码外）。
