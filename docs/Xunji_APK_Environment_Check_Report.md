# 训记 APK 分析环境检查报告

项目：训记安卓 APP 逆向研究与独立复刻  
阶段：第一阶段／环境检查、APK 清点及技术框架识别  
检查日期：2026 年 10 月 20 日（北京时间）  
报告状态：第一阶段检查完成；分析工具链和构建环境尚未就绪；未进入第二阶段和开发阶段。

## 1. 结论与执行边界

原始 APK 可以读取，归档及内容校验通过，适合继续进行有限范围的静态研究。已确认包名 `com.trainnote.rn`、版本 `7.0.302`、versionCode `1002`，主要框架为 React Native。ADB 正常，手机已授权。

本轮未检出可直接使用的 JDK、Android SDK、Android Studio、JADX 或 Apktool；当前 Codex 会话未暴露 REA／JADX Headless MCP 工具，不能宣称已经连接成功。这里的“未检出”限于本报告列明的检查范围，不代表已经证明全电脑都没有安装。

本轮没有安装软件、修改全局环境变量或 MCP 配置、删除或改写原始 APK、修改原应用数据、访问原服务接口、破解会员、开发复刻应用。只运行了读取安装元数据和计算文件哈希的 ADB 命令。没有全量反编译 DEX 或批量分析 JavaScript 业务逻辑。

本报告的“校验通过”指归档条目的长度、CRC32 及各来源 SHA-256 一致性；**不等同于 APK 签名已通过密码学验证，也不证明发布者身份或应用安全性。**

## 2. 证据索引与可信度规则

| 编号 | 证据文件或现场读取 | 用途与局限 |
| --- | --- | --- |
| E01 | 用户指定的 `D:\platform-tools\Xunji-APK-20261020-104513.zip` | 原始导出归档；只读 |
| E02 | `D:\platform-tools\Xunji-APK-20261020-104513\base.apk` | 已导出的 APK；只读 |
| E03 | `analysis/phase1-evidence.json` | 原始文件哈希、版本、组件计数、框架标识、签名块结构及校验结果 |
| E04 | `analysis/apk-entry-inventory.json` | 全部 3,637 个条目的名称、大小、压缩方法、CRC32、实际解压长度及 SHA-256 |
| E05 | `analysis/environment-evidence.json` | PATH 检查、固定候选位置、环境变量、ADB、配置摘要、目录写入限制 |
| E06 | `analysis/device-apk-hashes.json` | 手机安装文件路径和现场 SHA-256；只读读取 |
| E07 | APK 的 `AndroidManifest.xml` 二进制 XML | 用有界解析器读取版本、SDK、Application 和组件数量；未解析资源 ID 对应的显示文本 |
| E08 | APK 的 `assets/index.android.bundle` 及 `lib/` 文件目录 | 识别框架及业务包格式；未执行其中代码 |
| E09 | `analysis/phase1-inspect.mjs` | 本轮检查脚本；无网络、安装或反编译功能，可复核检查方法 |

可信度“高”表示文件字节或现场命令直接支持；“中”表示结构标识支持但仍有其他解释；“待验证”表示证据不足。本报告不以百分比伪装置信度。

## 3. 环境与工具检查

| 项目 | 结果 | 证据／可信度 | 局限与处理 |
| --- | --- | --- | --- |
| Windows | ADB 运行输出为 Windows `10.0.19045` | E05／高 | 不是完整系统兼容性评测 |
| ADB | 可用，Android Debug Bridge `1.0.41`，Platform-Tools `37.0.1-15733141` | E05／高 | 通过完整路径运行；`adb` 尚未加入当前 PATH，无需为此修改全局配置 |
| ADB 路径 | `D:\platform-tools\adb.exe` | E05／高 | 原位置保留 |
| 手机授权 | `adb devices -l` 显示 `device`；现场读取包信息、路径及哈希成功 | 现场命令、E05、E06／高 | 仅反映检查时连接状态；不对外提供设备序列号 |
| JDK | 当前 PATH 无 `java`、`javac`；`JAVA_HOME` 未设置；所查常见位置及匹配卸载项未检出 | E05、现场有限检查／高（对范围内结论） | 自定义目录或便携版本仍可能存在；不能认定全电脑没有 JDK |
| Android SDK | `ANDROID_HOME`、`ANDROID_SDK_ROOT` 均未设置；默认位置及所查候选位置不存在 | E05／高（对范围内结论） | 自定义位置待提供；未运行 SDK Manager |
| Android Studio | 默认位置、所查 D 盘候选位置、应用路径注册项及匹配卸载项未检出 | E05、现场有限检查／高（对范围内结论） | 未启动或安装 IDE |
| JADX | PATH 和所查候选目录未检出 | E05、现场有限检查／高（对范围内结论） | 不代表所有磁盘上均不存在 |
| JADX Headless MCP | 当前可调用工具列表无对应工具；Codex 配置中无匹配 JADX／REA 名称的 MCP 节 | 工具目录检查、E05／高 | 未能进行 initialize／tools/list 往返验证；任意命名的其他配置不能仅靠名称判断用途 |
| REA | 当前会话无可调用 REA 接口，也未见对应名称配置 | 工具目录检查、E05／高 | REA 的确切仓库、版本及安装目录尚未确认；状态为“未能验证连接／当前不可调用”，不是“连接成功” |
| Apktool | PATH 与有限候选位置未检出 | E05、现场有限检查／高（对范围内结论） | 未安装，未执行解包命令 |
| Gradle | 当前 PATH 未检出 | E05／高 | 未创建项目，因此未检查 Wrapper；不能据此推断以后必须全局安装 Gradle |
| Node.js | 已有 `v24.18.0`，内置 CRC32 可用 | 现场命令、E09／高 | 仅用作本轮只读检查，不是复刻应用技术选型 |

检查范围固定为：当前 PATH、相关环境变量、已知常见安装位置、相关卸载注册项、选定目录的一层名称筛选、当前会话工具目录和 Codex MCP 配置节名。没有对 C／D／E 盘作无限递归搜索，没有读取或导出无关配置内容与凭据。

MCP 配置检查前后 SHA-256 均为：

```text
08d789e54b661c64dd08cadfa55dc16ea7e8b4d96d3eb739c1a4809a22dc4fe8
```

配置中观察到 2 个 MCP 服务器节；未发现匹配 REA／JADX／Apktool／Reverse 名称的节，且没有可调用的 REA/JADX 工具。本轮没有改写该配置。

## 4. 目录状态与原始资料保护

用户要求的根目录为：`C:\Users\阿三大苏打实打实\XunjiLab`。检查时不存在；本轮尝试创建其 `analysis` 子目录，系统返回 **Access denied**。没有提升权限、改动 ACL 或绕过访问限制。

已在当前可写工作目录建立同名项目目录：

```text
C:\Users\阿三大苏打实打实\Documents\Codex\2026-10-20\new-chat\XunjiLab
```

| 子目录 | 本轮用途 | 状态 |
| --- | --- | --- |
| `original` | 保存原始资料来源说明，不复制或改写原包 | 原始文件继续位于 D 盘；本目录不冒充原始 APK 存储位置 |
| `analysis` | 校验脚本、条目清单、环境和 APK 证据 | 已生成 |
| `app` | 后续独立应用源项目 | 为空；本轮没有初始化应用工程 |
| `docs` | 本阶段报告、续接说明、交付归档 | 已生成 |

之前会话已存在一个分析副本：`C:\Users\阿三大苏打实打实\Documents\Codex\2026-10-20\new-chat\xunji-analysis\base.apk`。本轮复用并校验该副本，没有重新下载 APK。原始 ZIP 和导出 APK 在检查开始、结束时分别计算哈希，结果一致。

## 5. APK 清点与完整性

### 5.1 清点范围与文件

限定检查用户明确提供的归档、`D:\platform-tools` 的 APK/ZIP 文件、其直接 `Xunji-APK-*` 子目录中的 APK，以及既有分析副本。没有搜索全电脑。

| 对象 | 数量／字节数 | 结论 | 证据／可信度 |
| --- | --- | --- | --- |
| 原始 ZIP | 272,686,421 字节，约 260.05 MiB | 内含 1 个 `base.apk` | E01、E03／高 |
| 导出 APK | 342,772,763 字节，约 326.89 MiB | 发现 1 个不同内容的 APK 样本 | E02、E03／高 |
| 既有分析副本 | 342,772,763 字节 | 与导出 APK 相同，不重复计算为新样本 | E03／高 |
| 手机安装路径 | `pm path com.trainnote.rn` 返回 1 个 `base.apk` | 本次安装未返回 split APK | E05、E06／高 |
| APK 归档内部 | 3,637 个条目，合计解压长度 401,955,028 字节 | 逐条读取完成 | E03、E04／高 |

“全部 APK”指已知导出位置及当前设备安装实例。不能据此保证云端动态资源、未安装的功能模块或其他设备上的变体也已取得。

### 5.2 哈希基线

原始 ZIP 的 SHA-256：

```text
2fb005982e3a75a7222a80b9ab456739331aabc8f8653b95fb9a399514fa347f
```

导出 APK、ZIP 内解压得到的 APK、分析副本、手机已安装 APK 的 SHA-256 **四方一致**：

```text
f5a4817adc35fcbef241cca59db7134062b1e218979979794ae3d23a43f35413
```

| 校验 | 结果 | 说明 |
| --- | --- | --- |
| ZIP 中 `base.apk` 解压长度及 CRC32 | 通过 | 与归档目录声明相符 |
| ZIP 内 APK 与已导出 APK 的 SHA-256 | 一致 | 验证归档来源一致性 |
| 已导出 APK 与既有分析副本的 SHA-256 | 一致 | 验证副本一致性 |
| 手机安装 APK 与导出 APK 的 SHA-256 | 一致 | 现场通过只读 `sha256sum` 核对 |
| APK 内条目长度及 CRC32 | 3,637 / 3,637 通过；失败 0 | 不是只检查文件能否打开 |
| 原始 ZIP／APK 检查前后 SHA-256 | 一致 | 本轮未改变原始文件字节 |
| APK 签名块结构 | 检出 | 发现 `APK Sig Block 42`，包括 v2 标识 `0x7109871a` |
| 签名密码学验证 | **未完成** | 当前没有可用 `apksigner`；没有对证书、签名与受保护内容摘要作完整验签 |
| 发布者身份验证 | **未完成** | 没有官方证书指纹基准，不作官方身份背书 |

签名块还包含 `0x504b4453`、`0x42726577` 两个 ID；本阶段不解释其业务含义。CRC32／SHA-256 检查不能替代正式验签。[R1][R2]

## 6. 版本与技术框架识别

### 6.1 版本、SDK 和应用入口

| 字段 | 结果 | 证据／可信度 | 局限 |
| --- | --- | --- | --- |
| package | `com.trainnote.rn` | E07 + 手机安装元数据／高 | 本报告不从包名推导功能 |
| versionName | `7.0.302` | E07 + 手机安装元数据／高 | 当前样本版本，不代表商店最新版本 |
| versionCode | `1002` | E07 + 手机安装元数据／高 | 同上 |
| minSdkVersion | `26` | E07 + 手机安装元数据／高 | 原应用要求，不强制新应用使用相同最低版本 |
| targetSdkVersion | `34` | E07 + 手机安装元数据／高 | 原样本构建属性 |
| compileSdkVersion | `34` | E07／高 | Manifest 记录值 |
| Application 类 | `com.trainnote.rn.MainApplication` | E07／高 | 未反编译其具体实现 |

二进制 Manifest 由本轮有界读取器解析，并与设备端 `dumpsys package` 返回的包名、版本及 SDK 信息交叉核对。图标和 label 仍为资源 ID，本轮未假装已经解出其显示内容。

### 6.2 框架判断

| 判断 | 结论 | 证据／可信度 | 局限 |
| --- | --- | --- | --- |
| React Native | **已确认使用** | E08：`assets/index.android.bundle`、两套 ABI 中的 `libreactnativejni.so`、JS 包标准运行时开头；高 | 尚未确认 React Native 的确切版本 |
| JS 包格式 | **纯文本 JavaScript，可静态读取** | E08：前 16 字节为 `766172205f5f42554e444c455f535441`，对应文本 `var __BUNDLE_STA` 开头；高 | 这是打包产物，不等于原始 JSX/TS 项目；不保证所有业务都在本地包内 |
| JS 包大小 | 30,073,959 字节，约 28.68 MiB | E03、E04／高 | 未执行该脚本或访问它引用的服务 |
| Hermes 相关库 | **存在** | E08：`libhermes-executor-debug.so`、`libhermes-executor-release.so`、`libhermes-inspector.so`；高 | 库存在不证明实际启用了 Hermes |
| JSC 相关库 | **存在** | E08：`libjsc.so`、`libjscexecutor.so`；高 | 库存在不证明实际启用了 JSC |
| 实际 JS 引擎 | **待验证** | JSC 与 Hermes 相关库共存；证据不足 | 本轮不启动调试、不注入、不依据默认配置猜测实际引擎；纯文本入口本身也不能排除 Hermes 解释执行 |
| Hermes HBC 入口 | 当前 `index.android.bundle` **不是 HBC 字节码入口** | 可读文本头及正文结构；高 | 不排除其他资源或运行时加载途径 |
| Flutter | 未发现常见 `libflutter.so`／`flutter_assets` 标识 | E04、E08／中 | 不把“未发现典型标识”扩大成对所有潜在动态模块的绝对否定 |

业务包 SHA-256：

```text
1804d26c8cf067c020f3a7bda84ca36154fac47620881df41e53e169c591f9fc
```

Hermes 与 React Native 的技术关系参考官方文档，但样本实际启用的引擎仍须由样本证据确认。[R3]

## 7. 应用结构概览

| 结构项 | 数量／大小 | 证据／可信度 |
| --- | --- | --- |
| DEX | 6 个，共 44,291,336 字节 | E04／高 |
| `assets` | 265 个归档条目 | E04／高 |
| `res` | 3,007 个归档条目 | E04／高 |
| Native libraries | 76 个；`arm64-v8a` 38 个、`armeabi-v7a` 38 个 | E04／高 |
| `resources.arsc` | 存在 | E04／高 |
| `META-INF` | 92 个条目 | E04／高 |
| Manifest Activity | 37 个 | E07／高 |
| Manifest Activity Alias | 0 个 | E07／高 |
| Manifest Service | 10 个 | E07／高 |
| Manifest Receiver | 8 个 | E07／高 |
| Manifest Provider | 7 个 | E07／高 |

DEX 名称：`classes.dex`、`classes2.dex`、`classes3.dex`、`classes4.dex`、`classes5.dex`、`classes6.dex`。

组件数量包含第三方 SDK 组件，不等于功能页数。React Native 的多个页面可以由同一 Activity 承载；本轮没有从 37 个 Activity 推断 37 个业务页面。第三方库存在也不作为对应业务已完成、可离线或当前启用的证据。

## 8. 异常、未完成项与续接点

| 事项 | 已知原因／状态 | 已采取的处理 | 影响 |
| --- | --- | --- | --- |
| 指定根目录创建失败 | 文件系统返回 Access denied | 保存到当前允许写入的工作目录；不提权、不改权限 | 尚未按用户要求落到 `%USERPROFILE%\XunjiLab` |
| REA／JADX MCP 无法验证 | 当前会话没有对应可调用工具；未见匹配名称配置 | 如实标记为不可调用，不伪造成功调用或输出 | 不能声称 REA 已接管分析 |
| 无可用构建工具链 | 本轮有限检查未检出 JDK、SDK、IDE 等 | 不擅自下载安装或改全局配置 | 本轮不能构建独立 APP |
| 正式验签未完成 | 没有可用的 Android Build Tools／apksigner | 保留签名块结构结果与原始哈希，待工具可用后只执行 verify | 不能报告“签名验证通过” |
| 官方文档旧跳转一次超时 | Android 文档跳转链接抓取超时 | 改为直接查阅 AOSP 官方 v2 文档成功；没有循环重试 | 不影响本地校验结果 |

本轮 APK 校验脚本完成，`completed=true`、`errors=[]`，没有发生 APK 解压超时、内存不足或条目校验失败。流式读取解压内容，逐条校验；只对 Manifest 和 JS 包作有限缓存。脚本上限：10,000 条目、单条目 512 MiB、总解压 2 GiB、签名块 16 MiB。限制是本轮检查器的安全边界，不是 APK 格式本身的限制。

阶段检查点保存了源文件哈希、外层归档校验、最终证据 JSON；若以后继续，应先读取现有 JSON 并确认原始 SHA-256，避免无依据重复全量操作。

## 9. 下一步具体操作（尚未执行）

以下顺序以证据闭环为先。安装、连接配置和任何可能产生破坏性影响的操作均须另行确认；本报告不构成执行这些操作。

| 顺序 | 操作 | 依赖／是否需要确认 | 完成标准 |
| --- | --- | --- | --- |
| 1 | 确定正式项目根目录 | 由用户手动建立 `%USERPROFILE%\XunjiLab` 并使其成为可写工作目录，或明确继续使用当前目录；不修改 ACL | original、analysis、app、docs 路径明确；原包仅复制保留，不覆盖不移动 |
| 2 | 确认 REA 的确切仓库、版本和入口，以及已有工具路径 | 如工具已存在，先只读检查；如需安装或改 MCP 配置，先提交具体安装清单、来源和影响范围并获确认 | 能说明究竟使用哪个 REA；不能只凭“逆向一切”名称选择任意项目 |
| 3 | 补齐最小工具链 | 已有环境优先；必要时申请 Android Studio、匹配 JDK、Android SDK Build Tools，以及 JADX／Headless MCP；Apktool 按需 | `java -version`、SDK 工具版本检查成功；JADX MCP 完成 initialize、tools/list 和一次只读往返 |
| 4 | 对原 APK 补做正式验签 | Build Tools 可用后；只运行 verify | 保存 `apksigner verify --verbose --print-certs` 的完整退出码与输出；记录证书 SHA-256；不执行 sign |
| 5 | 第二阶段第一批：Manifest 与入口映射 | 另行推进第二阶段；目前仅有结构计数 | 保存组件、启动入口及相关资源引用；结论有证据编号和局限 |
| 6 | 第二阶段第二批：有限 JS 模块索引 | 只读、按主题分批；先覆盖训练计划、动作、训练记录和计时 | 先列模块候选和明确依赖，不执行原包脚本；每批最多 20 个候选模块，达到边界即保存报告，不无限追依赖 |
| 7 | 补充实际页面截图与交互说明 | 用户正常操作提供截图／录屏，或后续明确授权限定操作；不改已有真实训练数据 | 为每个截图分配编号，区分“界面可见”“静态代码支持”“推测” |
| 8 | 才进入功能需求建模和独立开发 | 第二阶段结论达到要求后 | A–I 模块逐个建立输入输出、离线数据、验收用例；独立 Kotlin／Compose／Room／DataStore 工程 |

工具就绪后计划执行的只读核验示例（本轮未运行 apksigner）：

```powershell
& '<Android SDK>\build-tools\<版本>\apksigner.bat' verify --verbose --print-certs 'D:\platform-tools\Xunji-APK-20261020-104513\base.apk'
```

`<Android SDK>` 和 `<版本>` 是待确认路径，不能原样执行。Android 官方提供 `verify` 和证书输出参数。[R1]

本轮不将前次会话中检索到的页面名直接升级成完整功能说明，不把包内存在 SQLite／AsyncStorage 等依赖当成数据结构已经确认，也不推断会员、云同步或饮食功能一定能脱离服务器运行。下一阶段应逐项取证。

## 10. 交付与复核

交付包含本报告、环境证据、APK 清单、设备端哈希和可复核检查脚本。**交付包不包含原 APK、业务 JavaScript 全文、账号、凭据或原应用用户数据库。** 原始资料继续保留在 D 盘。

后续接手时先检查：

1. `analysis/phase1-evidence.json` 中 `completed` 为 true，`originalBytesUnchanged` 为 true。
2. 原始 APK 的 SHA-256 与本报告基线相同。
3. `analysis/device-apk-hashes.json` 的现场哈希与基线相同；若应用更新，另建新样本，不能覆盖旧样本。
4. `analysis/environment-evidence.json` 中的工具缺项是否已经解决；不要把本次“未检出”当作永久事实。
5. 未经确认不进行安装、改配置、迁移或第二阶段操作。

本轮第一阶段的实际结果为：**材料可用、版本和框架已识别；归档完整性及来源一致性通过；正式验签、REA/JADX 连接和安卓构建工具链仍待补齐。**

## 官方方法参考

- [R1：Android Developers — apksigner](https://developer.android.com/tools/apksigner)：用于后续正式验签方法；本轮未冒充已经运行。
- [R2：AOSP — APK Signature Scheme v2](https://source.android.com/docs/security/features/apksigning/v2)：签名块结构与 `0x7109871a` 标识；结构存在与验签成功是不同结论。
- [R3：React Native — Using Hermes](https://reactnative.dev/docs/hermes)：解释运行时技术关系；样本实际引擎仍以样本证据为准。
- [R4：Android Developers — ADB](https://developer.android.com/tools/adb)：安装路径、包信息及文件读取相关命令的方法说明。

以上在线资料均于 2026 年 10 月 20 日查阅；本报告的样本结论来自本机实际文件和设备读取，而非网上同名版本描述。
