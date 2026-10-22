# Fitness · 独练

Android 本地健身训练应用（包名 `app.duolian.local`），自带安装包、源码工程与阶段文档。整理日期：2026-10-22。

## 当前版本

- 版本：0.4.0（versionCode 4），开发调试版。
- 包名：`app.duolian.local`；系统：Android 8.0（API 26）及以上。
- 安装包：[Duolian-0.4.0-debug.apk](Duolian-0.4.0-debug.apk)，11,074,026 字节。
- SHA-256：`7f693718e5352fc49190d54c763ed21a1c8322e8e2c06da1de9501357a7bb1f5`。
- 与 0.3.0 使用同一调试签名，可直接覆盖升级并保留本地数据。

把 APK 发送到安卓手机，在文件管理器中打开并按系统提示安装。若提示签名冲突，请先导出备份再处理；卸载会删除本地数据。

## 0.4.0 新增

- **训练方案**：常规组、递增金字塔、递减金字塔、递减组，开始训练时自动生成每组重量与次数。
- **组类型与 RPE**：正式 / 热身 / 递减 / AMRAP / 退让，可记录 RPE 5–10 与每组独立休息；热身组不计容量。
- **超级组**：两两超级组或整轮循环，组内只休息 15 秒。
- **1RM 与递进建议**：Epley 估算；历史页按上次表现给出 +2.5 kg / 维持 / 减重建议，可一键写入动作默认重量。
- **HIIT 间歇**：4 个内置模板 + 自定义，前台服务与通知栏倒计时，锁屏或切后台继续计时。
- **数据总览**：7/30/90 天训练负荷柱状图、连续天数、体重/体脂曲线、近 7 天宏量营养环、肌群分布、个人纪录。
- **备份 v2**：包含 HIIT 历史，兼容导入 v1；上限 8 MiB；每日首次打开自动备份并保留上一份。
- **动作库**：收藏与收藏筛选，可为动作添加本机照片（系统文件选择器持久授权）。

## 验证情况（0.4.0）

- JVM 单元测试 67 项全部通过（规则、方案、HIIT 状态机、统计、备份兼容）。
- Android Lint：0 错误、6 项警告。
- `:app:assembleDebug` 与 `:app:assembleDebugAndroidTest` 均编译通过。
- APK 签名（v2）与 zipalign 校验通过；证书与 0.3.0 相同。
- **未执行真机测试**：本次没有连接手机或模拟器，通知权限、锁屏计时、相册选择、进程被杀后的恢复等仍需在设备上验收。

## 目录

- `Duolian-0.4.0-debug.apk`：当前版本安装包。
- `android/`：Android 源码工程（Kotlin / Compose / Room），含构建脚本与单元测试。
- [CHANGELOG.md](CHANGELOG.md)：版本迭代摘要。
- [android/README.md](android/README.md)：源码结构、构建步骤与验证结果。
- `docs/`：阶段一至阶段四的原始 Markdown 记录、历史 README 与 CSV 清单。
- `history/`：0.2、0.3 历史安装包。
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

未做真机验收；HIIT 记录不计入训练容量；不含动作演示视频与云端同步；备份上限 8 MiB。早期阶段文档保留当时状态，当前功能以本说明与 `android/README.md` 为准。
