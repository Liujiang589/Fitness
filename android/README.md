# 独练 Android 工程（0.4.0）

包名 `app.duolian.local`，Kotlin + Jetpack Compose + Room，支持 Android 8.0（API 26）及以上。

## 0.4.0 新增

- **训练方案**：常规组、递增金字塔、递减金字塔、递减组（drop set），开始训练时按方案自动生成每组的重量与次数。
- **组类型与 RPE**：正式 / 热身 / 递减 / AMRAP / 退让五类；每组可记录 RPE（5–10）与独立休息秒数，热身组不计容量。
- **超级组**：计划可设为两两超级组或整轮循环，超级组内只休息 15 秒，最后一组后恢复完整休息。
- **1RM 与递进建议**：Epley 公式估算，历史页按上次完成情况给出 +2.5 kg / 维持 / 减重建议，可一键写入动作默认重量。
- **HIIT 间歇**：内置 4 个模板，也可自定义训练/休息/轮数/准备时间；前台服务 + 通知栏倒计时，锁屏或切后台继续计时，阶段切换有提示音。
- **数据总览**：训练负荷柱状图（7/30/90 天）、连续训练天数、身体体重/体脂曲线、近 7 天宏量营养环、肌群容量分布、个人纪录。
- **备份 v2**：`duolian-native` v2 备份包含 HIIT 历史，导入兼容 v1；上限提升到 8 MiB；每日首次打开自动备份一次并保留上一份。
- **动作库**：动作可收藏、可按收藏筛选，可为动作选择本机照片（通过系统文件选择器持久授权）。

## 目录

```
app/src/main/java/app/duolian/local/
  domain/     Models.kt（实体与校验）Progression.kt（方案/1RM/建议）
              Hiit.kt（间歇状态机）Analytics.kt（统计聚合）
  data/       Database.kt（Room）Repository.kt（读写与快照）
  timer/      TimerService.kt（前台倒计时通知）
  *.kt        MainActivity / Pages / TrainingPage / ToolsPage / Charts / Design
app/src/test/          单元测试（JVM，无需设备）
app/src/androidTest/   设备测试（需要手机或模拟器）
```

数据层用「Room 表 + JSON 载荷」保存实体，Room 版本仍为 1，**没有数据库迁移**；新增字段都有默认值，旧载荷可直接反序列化。

## 构建

需要 JDK 17、Gradle 8.11.1、Android SDK 35（build-tools 35.0.0）。本仓库不含签名私钥；调试包使用工程内 `.local-signing/duolian-debug.keystore`（已被 git 忽略）。

```powershell
# 首次：生成/复制调试密钥并构建
.\Build-Local.ps1 -JdkPath '<JDK>' -SdkPath '<SDK>' -GradlePath '<Gradle>'

# 只构建 APK
.\Build-Local.ps1 -JdkPath '<JDK>' -SdkPath '<SDK>' -GradlePath '<Gradle>' -Tasks ':app:assembleDebug'
```

产物：`app/build/outputs/apk/debug/app-debug.apk`。

注意：

- `gradle.properties` 已包含 `android.overridePathCheck=true`，允许在含中文的用户目录下构建。
- 若 PowerShell 执行策略禁止运行脚本，先在当前会话执行 `Set-ExecutionPolicy -Scope Process Bypass`。
- 设备测试（`-DeviceTests`）会操作 `app.duolian.local` 的测试数据，必须在连接专用测试设备并明确同意后运行。

## 0.4.0 验证结果

| 项目 | 结果 |
|---|---|
| JVM 单元测试 | 67 项通过（Rules / Progression / Hiit / Analytics / Backup） |
| 编译 | `:app:assembleDebug` 通过 |
| 设备测试包 | `:app:assembleDebugAndroidTest` 通过编译 |
| 真机执行 | **未执行**（本次没有连接设备或模拟器） |
| 签名 | 调试签名，与 0.3.0 使用同一密钥，可覆盖升级 |

## 已知限制

- 未做真机验收：通知权限、锁屏计时、相册选择、进程被杀后的恢复都只在代码层面处理，未在设备上验证。
- HIIT 记录存在 metadata 表中（最多 200 条），不参与训练容量统计。
- 不含动作演示视频；照片由用户自行添加，应用不内置任何第三方媒体。
- 备份上限 8 MiB；超过时导出会提示先清理历史。
- 无云同步、无账号、无网络权限。
