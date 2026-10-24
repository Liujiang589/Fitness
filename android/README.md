# 独练 Android 工程（V1.13）

包名 `app.duolian.local`，Kotlin + Jetpack Compose + Room，支持 Android 8.0（API 26）及以上。

## V1.13 肌群与走跑质量

40个动作的体表/肌群网格、OpenGL深度缓冲与轨迹，GPS质量过滤/速度/显示位置分离，步数口径；224项JVM与13项HTTP通过，实机待验收。[验证](../docs/V1_13_VERIFICATION.md)、[指南](../docs/V1_13_GUIDE.md)。

## V1.12 周回顾、饮食与性能

新增周回顾、份量复用与标签换算。采用Release优化变体、旧证书签名；206项JVM与13项HTTP通过。[验证](../docs/V1_12_VERIFICATION.md)、[性能](../docs/V1_12_PERFORMANCE.md)。

## V1.11 首页与动作指导

今天/计划库分层、排期状态、40条短要领、分主题指导与替代示范，计划及训练内可查看。194项JVM、13项HTTP、Lint和构建通过；设备UI仅编译。[验证](../docs/V1_11_VERIFICATION.md)、[使用](../docs/V1_11_HOME_AND_COACHING.md)。

## V1.10 户外记录与运动页

独立会话记录器、单调计时、检查点恢复、定位状态与固定操作栏。181项JVM、13项HTTP、Lint和构建通过；设备测试编译但未运行。[验证](../docs/V1_10_VERIFICATION.md)、[手机验收](../docs/V1_10_ACCEPTANCE.md)。

## V1.9 名称统一

app_name资源、应用标题、安装包和导出名统一“独练”。工程显示名也统一；applicationId、签名、存储与高德配置不变。165项JVM、13项HTTP测试、Lint及构建通过；设备测试仅编译。[名称](../docs/BRANDING.md)、[验证](../docs/V1_9_VERIFICATION.md)。

## V1.8 高德街道地图

Key以被Git忽略的amap.local.properties注入manifest，AMAP_CONFIGURED=true。接入StreetRouteMap/OutdoorSpeed、高德11.3.100地图、显示坐标转换、地图跟随/全屏与实时速度；用户同意后才初始化SDK。165项JVM、Lint及两个APK构建通过，实际高德鉴权/地图渲染、GPS、锁屏及覆盖安装未做手机验收。[配置](../docs/AMAP_SETUP.md)、[使用](../docs/OUTDOOR_TRACKING.md)、[验证](../docs/V1_8_VERIFICATION.md)。

## V1.7 户外记录

新增 OutdoorPage、OutdoorService、OutdoorStore 和 domain/Outdoor，使用系统 GPS 和计步传感器，按需申请定位/身体活动权限。location/health 前台服务在用户主动开启后采集，轨迹与日步数逐条保存到现有 metadata 表，不修改 schema。备份 v4 兼容旧格式；新增14项JVM及2项设备存储测试，后者仅编译。158项JVM、Lint与构建通过；未做实机授权、UI、GPS、锁屏和覆盖安装验收。[说明](../docs/OUTDOOR_TRACKING.md)、[验证](../docs/V1_7_VERIFICATION.md)。

## V1.5 应用内更新

新增AppUpdates/UpdateTransport/UpdateInstaller/AppUpdateViewModel/AppUpdateUi，前台检查、手动检查、下载校验和系统安装流程独立于训练读写。网络配置仅为当前局域网IP放行HTTP，FileProvider只分享更新缓存。新增13项JVM测试和4项设备测试，后者仅编译。[使用部署](../docs/APP_UPDATES.md)、[验证记录](../docs/V1_5_VERIFICATION.md)。

## V1.4 首次启动

Repository初始化仅种入动作库，不再自动创建基础计划。训练页进入自主创建流程；既有初始化标记和用户数据保持原样。StorageTest新增首次启动、重复初始化、自选训练及旧计划保留覆盖，设备测试仅编译。[验证记录](../docs/V1_4_VERIFICATION.md)。

## V1.3 动作动态示范

新增`domain/ExerciseGuides.kt`（40个动作的要领与精确匹配）、`domain/MotionEngine.kt`（关节、逆运动学、器械与动作轨迹）和`ExerciseMotion.kt`（原生Canvas播放器及教程详情）。动作列表只给主要可见卡片启动预览，帧循环跟随RESUMED生命周期；支持暂停、慢放、拖动、分阶段、视角和放大。

新增9项JVM测试，合计131项通过；导出真实引擎姿态供`tools/render_motion_previews.py`检查。新增ExerciseMotionUiTest仅编译，未在设备执行；无数据库迁移。详见[动作改版说明](../docs/V1_3_ACTIONS.md)、[验证记录](../docs/V1_3_VERIFICATION.md)。

## 当前版本命名

versionName为1.13，关于页展示V1.13；内部versionCode27，继续使用现有签名。对外文件为独练-V1.13.apk。规则见[VERSIONING.md](../docs/VERSIONING.md)。

## 0.12.0 信息分层与高清配图

新增CalmDesign.kt：可保存状态的明细展开控件、适应文字高度的图片横幅、由真实宏量营养供能计算的色条。首页、训练、历史、饮食、工具与我的页调整展示层级，领域逻辑和数据库保持兼容。

新图片位于app/src/main/res/drawable-nodpi/training_daylight.webp和nutrition_daylight.webp，均1536×1024。生成方式与提示词见[设计记录](../docs/UI012_DESIGN.md)。

新增CalmPresentationUiTest，更新TrainingUiTest的明细展开与专业分析入口；本轮设备测试仅编译。122项JVM测试通过，最终检查与限制见[UI012_VERIFICATION.md](../docs/UI012_VERIFICATION.md)。

## 0.11.1 作者联系方式

新增 `AuthorContact.kt`，联系方式资源在 `app/src/main/res/values/author.xml`。复制号码由用户点击触发，不需要电话权限；关于版本号使用BuildConfig自动更新。

局域网分发见 [LAN_DOWNLOAD.md](../docs/LAN_DOWNLOAD.md)；本版验证见 [UI0111_VERIFICATION.md](../docs/UI0111_VERIFICATION.md)。

## 0.11.0 快速记录与营养换算

新增 `InlineTraining.kt`、`FoodEntryDialog.kt` 及纯逻辑 `TrainingAssist.kt`、`Nutrition.kt`、生成式 `FoodCatalog.kt`。食物库来源与复现脚本见 [nutrition/README.md](../docs/nutrition/README.md)。

饮食可空新字段兼容旧 Room JSON 载荷；备份默认 v3，支持导入 v1–v3。训练行内保存只更新重量和次数，完成与撤销在 Room 事务内执行。

本版变更：[UI011_CHANGES.md](../docs/UI011_CHANGES.md)；验证：[UI011_VERIFICATION.md](../docs/UI011_VERIFICATION.md)。

## 0.10.0 视觉升级

新增 `PremiumDesign.kt`：统一主题、页头、底部导航、训练主卡、周打卡与进步摘要。原有页面与数据层继续使用，离线主视觉位于 `app/src/main/res/drawable-nodpi/training_editorial.webp`。

设计说明：[UI010_DESIGN.md](../docs/UI010_DESIGN.md)。0.10.0 验证：[UI010_VERIFICATION.md](../docs/UI010_VERIFICATION.md)。下方 0.4 / 0.4.1 为历史功能与验证记录。

## 0.4 新增

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

## 0.4.1 验证结果

| 项目 | 结果 |
|---|---|
| JVM 单元测试 | 67 项通过（Rules / Progression / Hiit / Analytics / Backup） |
| 编译 | `:app:assembleDebug` 通过 |
| 设备测试包 | `:app:assembleDebugAndroidTest` 通过编译 |
| 真机执行 | 小米 22011211C / Android 14 安装 0.4.1 并逐页截图，界面渲染通过；功能路径未验收 |
| 签名 | 调试签名，与 0.3.0 使用同一密钥，可覆盖升级 |

## 已知限制

- 只做了界面渲染验收：通知权限、锁屏计时、相册选择、进程被杀后的恢复仍未在设备上验证。
- HIIT 记录存在 metadata 表中（最多 200 条），不参与训练容量统计。
- 不含动作演示视频；照片由用户自行添加，应用不内置任何第三方媒体。
- 备份上限 8 MiB；超过时导出会提示先清理历史。
- 无云同步、无账号、无网络权限。
