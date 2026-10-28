# 独练 · 本地训练手记

一款不依赖账号的 Android 训练记录应用：训练、计划、动作示范、户外走跑、营养与回顾都保存在本机。
包名 `app.duolian.local`，支持 Android 8.0（API 26）及以上。

## 当前版本

**V1.26 账号页面准备版**：只提供注册 / 登录界面预览，手机号、验证码与第三方入口全部禁用，**真实认证尚未接通**（见 [说明](docs/V1_26_GUIDE.md)）。苹果网页版已按要求暂停。

| 项目 | 值 |
|---|---|
| 版本 | **V1.26**（versionCode 40） |
| 构建 | Release（R8 + 资源压缩，非 debuggable） |
| 安装包 | [`独练-V1.26.apk`](独练-V1.26.apk)，72,370,643 字节 |
| SHA-256 | `66b609ebf935f5bffe5f5f6ccb1fae48a8568ebbe3762b170ac00f5bd81c2cd3` |
| 交付包 | [`独练-V1.26-账号页面准备版.zip`](独练-V1.26-账号页面准备版.zip)（含安装指引与验证说明） |
| 签名 | 沿用最初开发证书，可直接覆盖升级 |

## 下载

- **在线页面**：<https://liujiang589.github.io/Fitness/>（GitHub Pages，随仓库更新）
- **仓库直链**：上面的 `独练-V1.26.apk`
- 国内镜像（WorkBuddy 站点）落后于仓库，扫码前请以本页版本号为准。

安装：下载 APK → 在通知栏或文件管理器打开 → 按提示允许「安装未知来源应用」→ 覆盖安装即可，训练数据保留。
升级前建议先在「我的 → 导出全部数据」留一份备份。

## 功能

**训练**
- 计划编辑器：全屏编辑（名称、动作、组数次数、训练日），休息/方案/超级组收进进阶设置，保存失败会明确提示
- 计划：组数/次数、训练方案（常规、递增/递减金字塔、递减组）、超级组（两两或整轮）
- 组记录：正式/热身/递减/AMRAP/退让，RPE 5–10，每组可单独设置休息秒数
- 训练中：大号计时、组间休息倒计时、逐组输入重量与次数，按需显示长计划
- 休息与 HIIT：前台服务 + 通知栏倒计时，锁屏可见

**动作**
- 40 个内置动作，带原创 3D 人体示范：可暂停、慢放、拖进度、切换斜侧/侧/正/背视角
- 目标肌群珊瑚色、辅助肌群浅金色；支持自定义动作、收藏与筛选、动作照片

**户外**
- 步行/跑步：准备 → 出发 → 运动 → 暂停 → 回顾
- 出发前只用临时定位，仅接受新鲜且精度合格的定位；运动中显示距离、时间、配速、街道地图
- 路线分块保存与断点续记；过期的速度读数会立即隐藏
- 网络辅助定位在独立进程运行，中断时自动回退系统定位并提示；异常可在应用内一键复制诊断信息

**数据与回顾**
- 近七天回顾：练了什么、哪里有进步、下一步怎么安排
- 历史：月历筛选、容量/次数/连续天数、体重曲线、营养环、肌群分布、个人纪录
- 营养：食物库按每 100g / 每份换算，可复用最近份量
- 诊断：负荷急慢比、肌群组数、平台期提示

**数据管理**
- 账号：仅有注册/登录入口界面（准备版），服务端尚未接入，输入框与发码按钮为禁用状态
- 备份格式 `duolian-native` **v4**，兼容导入 v1–v4；每日自动备份一份
- 支持导出 JSON 与 GPX；数据库 Room schema 1，未做破坏性迁移

## 权限说明

| 权限 | 用途 |
|---|---|
| `INTERNET` / `ACCESS_NETWORK_STATE` | 检查更新、加载地图 |
| `REQUEST_INSTALL_PACKAGES` | 应用内更新时拉起系统安装器 |
| `POST_NOTIFICATIONS` | 组间休息、HIIT 的倒计时通知 |
| `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` | 户外步行跑步的轨迹与地图 |
| `ACCESS_WIFI_STATE` / `CHANGE_WIFI_STATE` | 可选网络辅助定位使用Wi-Fi信息与扫描 |
| `ACTIVITY_RECOGNITION` | 户外步数 |
| `FOREGROUND_SERVICE`（含 LOCATION / HEALTH / SPECIAL_USE） | 锁屏时继续计时与记录 |
| `WAKE_LOCK` | 运动中保持计时准确 |

训练与营养记录保存在本机。联网用于更新、街道地图和用户选择的高德辅助定位；辅助定位会向高德提供定位所需信息，没有自建训练记录上传服务。

## 从源码构建

需要 JDK 17、Gradle 8.11.1、Android SDK 35（build-tools 35.0.0）。仓库不含签名私钥。

```powershell
cd android
Set-ExecutionPolicy -Scope Process Bypass
# 调试版 + 单元测试 + Lint + 设备测试包
.\Build-Local.ps1 -JdkPath '<JDK>' -SdkPath '<SDK>' -GradlePath '<Gradle>'
# 发布版（R8 + 资源压缩）
.\Build-Local.ps1 -JdkPath '<JDK>' -SdkPath '<SDK>' -GradlePath '<Gradle>' -Tasks ':app:assembleRelease'
```

高德地图 Key 通过本机 `android/amap.local.properties` 或环境变量 `DUOLIAN_AMAP_ANDROID_KEY` 注入，**不写入仓库**；缺失时应用其余功能照常可用，地图页不可用。

## 仓库结构

```
android/             Android 源码（Kotlin + Compose + Room），详见 android/README.md
docs/                版本验证记录、专题文档与早期阶段资料，索引见 docs/README.md
history/             历史版本安装包，供回溯
tools/               食物库构建、局域网下载、模型预处理等脚本
index.html           在线下载页（GitHub Pages 根目录）
独练-V1.26.apk        当前版本安装包
独练-V1.26-账号页面准备版.zip  当前版本交付包
SHA256SUMS.txt       本目录文件的校验清单
CHANGELOG.md         版本记录
```

本版完善页面状态恢复、训练逐组懒加载与延迟输入保存，统一记录窗口保存反馈，增加训练完成回顾并整理历史数据呈现。见[使用说明](docs/V1_25_GUIDE.md)、[验证记录](docs/V1_26_VERIFICATION.md)。

## 验证状态

- JVM 单元测试 **276 项通过**（0 失败 / 0 错误 / 0 跳过）
- Android Lint：28 项 Warning、6 项 Information，无 Error
- APK：`apksigner` 与 `zipalign` 通过，非 debuggable
- 本轮未操作手机；设备UI测试仅编译。详细边界见 [验证记录](docs/V1_26_VERIFICATION.md)。

这里只陈述构建与测试能证明的部分；界面观感、GPS 实际精度、长期使用体验仍以真机为准。

## 已知限制

- **体积**：本版APK约69.0MB，包含两种ARM架构、离线人体模型与统一中文字体。
- **应用内更新入口**：仍沿用构建配置中的旧 HTTPS 清单地址；本轮没有重新发布或核验该远端更新源。
- **国内镜像滞后**：WorkBuddy 站点仍是 V1.12，需要重新发布才会与仓库一致。
- **签名**：使用调试/开发证书，不是应用商店签名，不适合直接上架。
- **账号未上线**：1.26 只做了账号页面的界面，没有认证后端、没有会话与数据隔离，旧记录仍归本机。
- **iPhone 网页版不在本仓库**：独立的 PWA 工程位于 `iphone-web/`，本轮暂停，未纳入同步。
- **文档历史混杂**：`docs/` 保留了 0.x 阶段的原始记录，部分描述只对应当时的实现。

## 文档

- [CHANGELOG.md](CHANGELOG.md)：按版本记录用户可感知的变化
- [docs/README.md](docs/README.md)：全部文档的索引与阅读顺序
- [android/README.md](android/README.md)：源码结构、构建与开发约定
