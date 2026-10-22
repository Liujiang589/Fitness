# Fitness · 多练

本目录整理了 Fitness 项目的 Android 安装包、说明及版本迭代文档，便于后续上传 GitHub。整理日期：2026-10-22。

## 当前版本

- 版本：0.3.0-dev（versionCode 3），开发调试版。
- 包名：app.duolian.local。
- 系统：Android 8.0（API 26）及以上。
- 安装包：[Duolian-0.3.0-debug.apk](Duolian-0.3.0-debug.apk)，11,360,527 字节。
- SHA-256：67da857f042d4cb5f4b71c01464f8580822bff3af204e2fef7f928ac4c629336。

把 APK 发送到安卓手机，在文件管理器中打开并按系统提示安装。同签名版本可覆盖升级；若提示签名冲突，请先备份训练数据再处理，卸载会删除本地数据。

## 功能与验证情况

当前支持本地训练计划、40 个基础动作与自定义动作、训练组记录与保存、历史统计、身体/饮食/饮水记录和 JSON 备份入口。0.3 更新了首页、动作库、训练和历史页面，并增加计划复制、身体/饮食记录编辑与 1RM 估算等功能。

依据原项目记录，0.3 的 15 项单元测试及一次完整的 5 项小米真机测试通过。部分新增功能、备份系统选择器、后台与设备重启路径尚未完整验收；Lint 记录为 0 错误、4 项警告。此次仅整理和校验文件，未重新构建或进行真机测试。

复杂周期计划、超级组/递减组、原生 HIIT、后台通知、完整动作视频与挑战等仍待完善。

## 目录

- `Duolian-0.3.0-debug.apk`：当前版本安装包。
- [CHANGELOG.md](CHANGELOG.md)：根据原始记录整理的版本迭代摘要。
- [docs/UI03_HANDOFF.md](docs/UI03_HANDOFF.md)：0.3 界面、功能与验收记录，优先阅读。
- [docs/PHASE4_HANDOFF.md](docs/PHASE4_HANDOFF.md)：原生开发、构建与真机测试过程，末尾追加了 0.3 状态。
- `docs/`：阶段一至阶段四的原始 Markdown 记录、历史 README 及相关 CSV 清单。
- `history/Duolian-0.2.0-debug.apk`：0.2 历史安装包。
- `android/`：Android 源码工程（Kotlin / Jetpack Compose / Room），可用 JDK 17 + Gradle 8.11.1 + Android SDK 35 重建 APK；不含签名私钥。
- `SHA256SUMS.txt`：本目录文件校验清单。

原始阶段文档按当时状态保留，早期“未构建/未安装”等描述是历史记录；当前版本以本说明与 UI03_HANDOFF.md 为准。部分原文链接指向原工程的源码、分析证据或预览文件，这些文件未包含在本次整理范围内。

本目录包含安装包、阶段文档与 `android/` 源码工程；不含签名私钥。源码可用于本地重建与继续开发。
