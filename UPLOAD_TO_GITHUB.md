# 上传到 GitHub

代码已经准备好，需要手动推送。

## 推送步骤

```bash
cd /workspace/MCP-Run
git push origin main
```

如果需要认证，请使用以下方式之一：

### 方式1: Personal Access Token (推荐)
```bash
# 在GitHub上创建PAT: Settings -> Developer settings -> Personal access tokens
git remote set-url origin https://<TOKEN>@github.com/Alien-Et/MCP-Run.git
git push origin main
```

### 方式2: SSH Key
```bash
git remote set-url origin git@github.com:Alien-Et/MCP-Run.git
git push origin main
```

## 当前状态

- **分支**: main
- **本地提交**: 1 commit ahead of origin/main
- **远程仓库**: https://github.com/Alien-Et/MCP-Run.git
- **提交信息**: "UI重构: Material Design 3 + MUI液态玻璃风格"

## 修改内容摘要

### UI重构
- Material Design 3 主题系统
- 自动适配暗黑/亮色模式
- 统一按钮样式 (48dp高度, 12dp圆角)
- 统一输入框样式 (52dp高度)
- 悬浮圆角矩形导航栏 (MUI液态玻璃风格)

### Bug修复
- 工具详情点击崩溃 → 改用 AlertDialog.Builder
- 输入框数字显示不全 → 增加高度到52dp
- 导航栏返回按钮多余 → 已删除

### 持久化保存
- SharedPreferences 正确保存端口和超时设置
- 启动时自动读取保存的配置
- 键名一致: server_port, server_timeout

### APK信息
- 路径: /workspace/MCP-Run/app/build/outputs/apk/debug/app-debug.apk
- 大小: 7.98 MB
