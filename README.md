# Fitness · 独练

Android 本地健身训练应用（包名 `app.duolian.local`），自带安装包、源码工程与阶段文档。整理日期：2026-10-22。

## 当前版本

- 版本：0.4.1（versionCode 5），开发调试版。
- 包名：`app.duolian.local`；系统：Android 8.0（API 26）及以上。
- 安装包：[Duolian-0.4.1-debug.apk](Duolian-0.4.1-debug.apk)，11,074,026 字节。
- SHA-256：`a0ca2f7058ba558e40a4fd04e9b242ce73366e665da6bd7c60bfef00806278d3`。
- 与 0.3.0 使用同一调试签名，可直接覆盖升级并保留本地数据。

把 APK 发送到安卓手机，在文件管理器中打开并按系统提示安装。若提示签名冲突，请先导出备份再处理；卸载会删除本地数据。

## 0.4 新功能

- **训练方案**：常规组、递增金字塔、递减金字塔、递减组，开始训练时自动生成每组重量与次数。
- **组类型与 RPE**：正式 / 热身 / 递减 / AMRAP / 退让，可记录 RPE 5–10 与每组独立休息；热身组不计容量。
- **超级组**：两两超级组或整轮循环，组内只休息 15 秒。
- **1RM 与递进建议**：Epley 估算；历史页按上次表现给出 +2.5 kg / 维持 / 减重建议，可一键写入动作默认重量。
- **HIIT 间歇**：4 个内置模板 + 自定义，前台服务与通知栏倒计时，锁屏或切后台继续计时。
- **数据总览**：7/30/90 天训练负荷柱状图、连续天数、体重/体脂曲线、近 7 天宏量营养环、肌群分布、个人纪录。
- **备份 v2**：包含 HIIT 历史，兼容导入 v1；上限 8 MiB；每日首次打开自动备份并保留上一份。
- **动作库**：收藏与收藏筛选，可为动作添加本机照片（系统文件选择器持久授权）。

## 0.4.1 修复

- 30 天 / 90 天柱状图的日期标签原本每个刻度都参与排版，被挤成两行（如「9/」+「23」）；改为只排布抽样后的标签并均匀分布。
- 该问题由真机截图发现，属界面显示缺陷，不影响数据。

## 验证情况（0.4.1）

- JVM 单元测试 67 项全部通过（规则、方案、HIIT 状态机、统计、备份兼容）。
- Android Lint：0 错误、6 项警告。
- `:app:assembleDebug` 与 `:app:assembleDebugAndroidTest` 均编译通过。
- APK 签名（v2）与 zipalign 校验通过；证书与 0.3.0 相同。
- **真机渲染已验收**：在小米 22011211C（Android 14）上安装 0.4.1，逐页截图确认训练、工具、动作、历史、我的与计划表单正常渲染。
- **功能验收仍未完成**：通知权限与锁屏计时、相册选择、进程被杀后的恢复尚未在设备上验证。

## 目录

- `Duolian-0.4.1-debug.apk`：当前版本安装包。
- `android/`：Android 源码工程（Kotlin / Compose / Room），含构建脚本与单元测试。
- [CHANGELOG.md](CHANGELOG.md)：版本迭代摘要。
- [android/README.md](android/README.md)：源码结构、构建步骤与验证结果。
- `docs/`：阶段一至阶段四的原始 Markdown 记录、历史 README 与 CSV 清单。
- `history/`：0.2、0.3、0.4.0 历史安装包。
- `SHA256SUMS.txt`：本目录文件的 SHA-256 校验清单。

## 从源码构建

需要 JDK 17、Gradle 8.11.1、Android SDK 35；仓库不含签名私钥。

```powershell
cd android
Set-ExecutionPolicy -Scope Process Bypass
.\Build-Local.ps1 -JdkPath '<JDK>' -SdkPath '<SDK>' -GradlePath '<Gradle>'
```

产物在 `android/app/build/outputs/apk/debug/app-debug.apk`。

## 已知限制

未做完整功能验收；HIIT 记录不计入训练容量；不含动作演示视频与云端同步；备份上限 8 MiB。早期阶段文档保留当时状态，当前功能以本说明与 `android/README.md` 为准。
