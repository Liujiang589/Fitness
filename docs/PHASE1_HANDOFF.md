# 第一阶段续接说明

先阅读同目录的 `Xunji_APK_Environment_Check_Report.md`。

第一阶段已经完成本地只读检查，原包版本为 7.0.302 / 1002，React Native + 纯文本 JS 包；未确认实际 JS 引擎。

3,637 个 APK 条目逐条 CRC32 和长度校验通过；手机、原始导出、ZIP 内 APK、既有分析副本 SHA-256 一致。正式密码学验签尚未完成。

读取 `../analysis/phase1-evidence.json`、`environment-evidence.json`、`device-apk-hashes.json`、`apk-entry-inventory.json` 恢复进度。

当前没有可用的 REA/JADX MCP，JDK/Android SDK/Android Studio 等在有限检查范围内未检出。不要说成全电脑均没有安装。

用户限制本轮只做阶段一。没有应用工程；app 目录为空。安装、全局配置或破坏性操作必须先确认。正式路径创建被拒，当前使用工作区下的 XunjiLab 目录。

不需要再次上传 260 MiB 的 APK 归档，不要将 APK 或原业务 JS 放进报告交付包。
