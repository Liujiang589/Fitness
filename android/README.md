# 独练 · Android 工程

Kotlin + Jetpack Compose + Room。包名 `app.duolian.local`，`minSdk 26`、`compileSdk/targetSdk 35`，
当前版本 `versionName 1.19 / versionCode 33`。

## 技术栈

| 项 | 版本 |
|---|---|
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin / Compose 编译器 | 2.0.21 |
| KSP | 2.0.21-1.0.28 |
| JDK | 17 |

Release 变体开启 `isMinifyEnabled` 与 `isShrinkResources`（R8），未开启 `debuggable`。

## 目录结构

```
app/src/main/java/app/duolian/local/
├── domain/      纯 Kotlin 业务：模型、规则、进阶建议、HIIT、统计、诊断、模板、
│                营养估算、动作示范、体表网格、户外记录、周回顾、更新策略
├── data/        Room 数据库与仓储、更新下载、户外轨迹存储
├── timer/       训练/组间休息的前台服务
├── outdoor/     户外记录前台服务
└── *.kt         29 个界面与视图模型文件（Compose 单 Activity）
app/src/test/          22 个单元测试文件（239 项测试）
app/src/androidTest/   14 个仪器测试文件（需真机或模拟器）
app/src/debug/         仅调试源集：户外预览 Activity，不进入 Release 清单
```

界面不拆模块：`MainActivity` 承载 Compose 树，页面按功能分文件（`Pages.kt`、`TrainingPage.kt`、
`OutdoorPage.kt`、`ToolsPage.kt` 等），设计令牌集中在 `Design.kt` 与 `PremiumDesign.kt`。

## 构建

需要 JDK 17、Android SDK 35（build-tools 35.0.0）、Gradle 8.11.1。

```powershell
cd android
Set-ExecutionPolicy -Scope Process Bypass
.\Build-Local.ps1 -JdkPath '<JDK>' -SdkPath '<SDK>' -GradlePath '<Gradle>'
```

默认执行 `testDebugUnitTest`、`lintDebug`、`assembleDebug`、`assembleDebugAndroidTest`。
其他用法：

```powershell
# 只构建发布包
.\Build-Local.ps1 ... -Tasks ':app:assembleRelease'
# 只校验环境
.\Build-Local.ps1 ... -ConfigureOnly
# 追加设备测试（需要已连接的手机）
.\Build-Local.ps1 ... -DeviceTests
```

产物：`app/build/outputs/apk/debug/app-debug.apk`、`app/build/outputs/apk/release/app-release.apk`。

## 签名与升级

- 签名文件在 `android/.local-signing/duolian-debug.keystore`，**已被 Git 忽略**，请自行备份。
- 所有版本使用同一证书，因此可以直接覆盖升级；证书一旦更换，用户必须先卸载，数据会丢失。
- Release 与 Debug 共用该证书，这是内部测试的取舍，不代表可以上架。

## 高德地图 Key

Key 从 `android/amap.local.properties`（`AMAP_ANDROID_KEY=<32 位十六进制>`）或环境变量
`DUOLIAN_AMAP_ANDROID_KEY` 读取，通过 `manifestPlaceholders` 注入。示例见
`amap.local.properties.example`。**Key 不写入仓库**；未配置时应用可正常构建运行，地图页不可用，
`BuildConfig.AMAP_CONFIGURED` 为 `false`。

## 数据与兼容

- **Room schema 1**，表内以 JSON 字符串存业务对象，没有迁移脚本。
  给数据类加字段必须带默认值，否则旧数据反序列化会失败。
- **备份格式** `duolian-native` 当前为 **v4**，导入时接受 v1–v4；新字段同样必须可空或有默认值。
- 历史数据保持原样：营养记录保存当时的营养快照，旧记录不按新食物库重算；户外轨迹不做后处理改写。
- HIIT 历史存在元数据键 `hiit-runs`（上限 200 条）；每日自动备份写入 `filesDir/auto-backup.json`。

## 应用内更新

- 清单是 `AppRelease` JSON（`version`、`versionCode`、`size`、`sha256`、`downloadUrl` 等）。
- `UpdatePolicy` 的限制：只接受 **HTTPS 或私有网段**；`downloadUrl` 必须与清单同源、以 `.apk` 结尾、
  **不能带查询参数**；包名必须是 `app.duolian.local`。
- 安装前校验下载文件的 SHA-256，并比对 APK 签名证书与已安装应用一致，再校验 `versionCode` 与版本名。
- 清单地址是 `BuildConfig.UPDATE_MANIFEST_URL`（编译期常量），当前指向局域网服务。

## 开发约定

- 业务规则放 `domain/`，不依赖 Android 框架，方便单元测试；界面只做展示与调用。
- 修改规则或数据结构时**同时补测试**：`app/src/test/` 下的测试是提交前的门槛。
- 训练中的写操作有并发保护：内联快速记录的延迟保存不得覆盖已完成的组（见 `TrainingAssist`）。
- 新增文档：每个版本在 `../docs/` 补一份 `V1_<版本>_VERIFICATION.md`，写清构建、测试、未验证边界。

## 常用命令

```powershell
# 单元测试
gradle.bat --no-daemon --console=plain :app:testDebugUnitTest
# Lint
gradle.bat --no-daemon --console=plain :app:lintDebug
# 发布包
gradle.bat --no-daemon --console=plain :app:assembleRelease
# APK 校验
apksigner verify --print-certs app\build\outputs\apk\release\app-release.apk
zipalign -c -v 4 app\build\outputs\apk\release\app-release.apk
```
