# 训记独立复刻：第四阶段进度报告

日期：2026-10-20（北京时间）

## 结论

本地 PowerShell 和文件读写已恢复，阶段一至三报告和 HTML 预览仍在。已创建独立原生 Android 工程并写入第一批功能实现。**尚未构建 APK，尚无安卓功能测试通过结果。**

## 环境实际检查

| 检查 | 本轮结果 | 局限 |
|---|---|---|
| PowerShell / 本地文件 | 可执行、可读写工程 | 两次出现 exec-server transport disconnected/closed，重试恢复；连接存在偶发中断 |
| Node | v24.18.0 | 只用于辅助文件操作，不代替Android编译 |
| ADB | D:\platform-tools\adb.exe 可运行 | 本轮 adb devices 为空，没有手机连接 |
| JDK / javac / Gradle | PATH 中未检出 | 常见Java/Adoptium路径和用户Gradle缓存也未检出；未全盘扫描，不能宣称绝对未安装 |
| Android SDK / Android Studio | 常见默认路径不存在；未检出相关环境变量 | 如果另装在其他目录，可复用已有工具 |
| REA / JADX MCP | 当前会话未提供对应可调用接口 | 本轮使用已有有界分析结论，不声称连接已修复 |
| 工具检查脚本 | java、gradle、SDK35、Build Tools35均为false | 构建前提缺失，未尝试假构建 |

## 原生实现与验证状态

工程：`XunjiLab/app/android-native`；包名 `app.duolian.local`。

| 模块 | 已写源码 | 运行验证 |
|---|---|---|
| 动作 | CRUD、搜索、肌群与器械筛选、共用详情、8个独立示例动作 | 待编译与实测 |
| 计划 | CRUD、动作选择与排序、默认组数与次数、引用校验 | 待编译与实测 |
| 训练 | 每组重量/次数、普通/热身组、完成切换、增删组、结束和放弃 | 待编译与实测 |
| 计时 | 暂停/恢复、截止时间休息计时、跳过休息、前台提示音 | 待编译与实测；后台通知未实现 |
| 离线数据 | Room保存动作/计划/训练/身体/饮食/饮水；DataStore保存休息偏好 | 重开数据库测试已写，未运行 |
| 历史 | 训练快照、逐组详情、总容量、最近7次容量条图 | 待编译与实测 |
| 备份 | 系统文件选择器、校验、确认替换、导入前恢复快照 | 待编译与实测；4MiB上限，不兼容预览/原版备份 |
| 身体/饮食/饮水 | 新增、查看、删除、饮食日期筛选和本地保存 | 待编译与实测；身体/饮食编辑未实现 |

未申请 INTERNET 权限；未安装手机应用、修改原版数据、读取原版账户或请求原服务器；未复制原版专有源码、图片或视频。原始APK和此前HTML预览未改动。

## 数据与计时口径

- 容量只计算已完成正式组，重量以整数克保存。40kg×10次×3组=1200kg。单侧、减重组、辅助重量尚未实现。
- 训练开始保存动作名称和组配置快照；来源后续编辑/删除不改历史。
- 唯一 nullable activeSlot 索引限制一个未结束训练；修改在事务内读取最新训练快照后写入。
- 计时使用墙钟与暂停余量。手动修改系统时间、后台保活、手机重启提醒仍需专项验证。
- Room使用实体级JSON载荷与训练聚合快照，并非第三阶段规划的完整逐组关系表；后续复杂查询须规范化字段并加入迁移。
- 导入先校验，数据库替换为单事务。导入前快照由AtomicFile保存且可导出；休息偏好单独更新，失败提示。

## 验证证据与局限

`analysis/phase4/source-check.json` 已记录本轮实际检查：9个Kotlin源码文件，PowerShell脚本语法错误0，两个XML解析成功，Manifest权限0。

已写15项业务JUnit测试、4项Room设备测试、1项Compose界面流程测试，共20项。**执行数0，不提供通过率。** 未执行Kotlin编译、Room代码生成、Android Lint、模拟器或手机运行；仍可能存在依赖或编译问题。旧HTML模拟测试不算原生验收。

## 下一步安装方案（待确认）

使用项目专用 `C:\workspace\Duolian`。源码先由Stage-AsciiWorkspace.ps1复制到其中的纯英文路径，脚本拒绝覆盖已有目录。准备以下官方工具：

| 内容 | 版本与用途 | 计划目录 |
|---|---|---|
| Temurin JDK | 17，Windows x64 ZIP便携版 | C:\workspace\Duolian\toolchain\jdk-17 |
| Gradle | 8.11.1，官方ZIP | C:\workspace\Duolian\toolchain\gradle-8.11.1 |
| Android Command-line Tools | 官方可用版本，含sdkmanager | C:\workspace\Duolian\toolchain\android-sdk\cmdline-tools |
| Android SDK | platforms;android-35、build-tools;35.0.0、platform-tools | C:\workspace\Duolian\toolchain\android-sdk |
| Maven/Gradle依赖 | 工程固定版本，首次构建下载 | 工程专用.gradle-user-home |

只供本项目使用；不修改系统PATH、注册表或其他项目配置。暂不安装完整Android Studio或模拟器。SDK需接受相应许可证。获批后先跑JUnit、Lint和Debug构建，按真实报错修复；手机安装与设备测试另行安排。设备UI测试会替换独练自身测试数据，只应在空数据测试安装上运行，不影响原版训记。

用户原任务明确要求“所有安装和可能产生破坏性影响的操作，先征得我的确认”，因此本轮停在工具安装前。工程实现、测试编写与交付准备已经完成，无需重做前三阶段。

## 官方依据

- AGP8.9.x：Gradle8.11.1、JDK17、Build Tools35.0.0： https://developer.android.com/build/releases/agp-8-9-0-release-notes
- Kotlin2.0+ Compose插件： https://developer.android.com/jetpack/androidx/releases/compose-kotlin
- Room及KAPT： https://developer.android.com/jetpack/androidx/releases/room
- SDK命令行工具及许可证： https://developer.android.google.cn/tools/sdkmanager?hl=en
- Temurin便携压缩包： https://adoptium.net/installation/archives
