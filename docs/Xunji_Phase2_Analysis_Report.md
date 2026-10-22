# 训记 APK 第二阶段分析报告

项目：训记安卓 APP 逆向研究与独立复刻  
样本：`com.trainnote.rn`，7.0.302／versionCode 1002  
日期：2026 年 10 月 20 日（北京时间）  
范围：第二阶段有界静态分析与可取得界面的核对；未进入第三阶段需求定稿或第四阶段开发。

## 1. 当前结论

已经建立入口组件、导航声明、核心训练数据结构、主要本地存储路径和计算规则的证据索引。材料足以支持下一阶段对核心离线训练功能建模，但不能宣称已经完成原版全部页面和交互的运行验证。

已确认：

- React Native 业务包可静态读取；6,495 个 Metro 模块完成结构索引，8 个有界批次检查了 **62 个不同模块**。重复批次用于补查同一模块，不重复计数。结构索引不等于 6,495 个模块全部完成语义分析。
- 导航中找到 236 处带组件的路由声明，合并名称后为 234 个标识。这不是当前版本可访问页面总数，也不是 234 个已验证功能；存在不同导航上下文和复用组件。
- 核心训练有 SQLite 表、读写方法和界面到存储的调用链；另有旧版 AsyncStorage 离线逻辑。
- 训练恢复有独立本地缓存，包含动作、时间及计时状态等数据，并设有 24 小时有效期。实际杀进程恢复没有执行。
- 训练容量、1RM、营养素热量计算已找到具体实现表达式；未执行原包代码，也未将其复制到独立项目。
- 身体数据页面与饮食读取流程存在服务器依赖；饮食的 15 条日期缓存不能据此视作完整离线饮食数据库。
- 实际截图只有 **S01 首页**。手机拒绝 ADB 自动点击，错误为 `INJECT_EVENTS` 权限不足；已停止点击，没有改变安全设置。

不将本报告当作功能验收报告。原版业务源代码、SQL 或素材均未用于新 APP，新项目目录仍为空。

## 2. 方法、范围与证据定位

使用第一阶段保存的证据和 Node.js 已安装运行时内置的 Acorn 8.16.0 做词法索引及选定模块 AST 解析。分析器只解析文本，不执行 APK 的 JavaScript、DEX 或 native libraries。没有下载或安装解析器、REA、JADX、Apktool。

| 证据 | 内容 | 边界 |
| --- | --- | --- |
| P2-M | `analysis/phase2/manifest-tree.json`、`manifest-structure.json` | Manifest 二进制 XML 树、权限、组件和启动过滤器；资源引用仍保留资源 ID |
| P2-R | `analysis/phase2/resource-structure.json` | assets/native 目录与图像类型计数；不代表素材使用场景已逐一确认 |
| P2-D | `analysis/phase2/dex-structure.json` | 6 个 DEX 的头部、类描述符及内置校验；未反编译方法体 |
| P2-B | `analysis/phase2/batch1.json` 至 `batch8.json` | 62 个选定模块的导出、依赖、方法、SQL、存储调用、部分表达式和原文定位 |
| P2-N | `analysis/phase2/route-map.json` | 带组件的导航声明；不是运行时可达性证明 |
| P2-SQL | `analysis/phase2/sql-schema-evidence.json` | CREATE TABLE 和迁移语句的静态声明；不是手机数据库的实测 schema |
| P2-C | `analysis/phase2/confirmed-claims.json` | 本报告关键结论、函数/模块和偏移的对应关系 |
| P2-UI | `analysis/phase2/screenshots/S01-current.png`、`S01.json` | 唯一实拍截图；采集前后均确认训记处于前台 |
| P2-X | `analysis/phase2/execution-log.json`、`phase2-status.json` | 中断、输入阻断、范围和完成状态 |

**偏移说明：** JS 模块及函数的 `start/end` 为业务包 UTF-8 解码后字符串中的 **0 起始 UTF-16 代码单元偏移**，不是 APK 文件字节偏移；`end` 为不包含端点。Manifest 的 `byteOffset` 才是二进制 XML 字节偏移。模块编号仅对这个业务包哈希有效，升级后必须重建索引。

可信度“高”：样本字节、明确语法调用或现场截图直接支持；“中”：静态调用或组件定位支持，但仍有条件分支、依赖或运行时缺口；“待核验”：当前证据不足。不以任意百分比代替证据。

## 3. 样本基线与保护

APK SHA-256：

```text
f5a4817adc35fcbef241cca59db7134062b1e218979979794ae3d23a43f35413
```

业务包 SHA-256：

```text
1804d26c8cf067c020f3a7bda84ca36154fac47620881df41e53e169c591f9fc
```

原始 ZIP SHA-256：

```text
2fb005982e3a75a7222a80b9ab456739331aabc8f8653b95fb9a399514fa347f
```

本轮业务包进入分析前与第一阶段基线一致；原始 APK／ZIP 在本轮结束时也与第一阶段基线一致。没有改写原文件、清空应用、导出用户数据库、使用 root、注入、修改会员校验或调用原版后台接口。

系统创建 `%USERPROFILE%\XunjiLab` 的限制仍未解除，继续使用：

```text
C:\Users\阿三大苏打实打实\Documents\Codex\2026-10-20\new-chat\XunjiLab
```

REA／JADX Headless MCP 仍未在会话工具目录暴露，无法完成其连接往返。因此本报告是其他合法静态方法的结果，不冒充 REA 或 JADX 输出。正式签名密码学验证仍为第一阶段遗留待办，本轮未补做。

## 4. Manifest、DEX、资源与 native libraries

### 4.1 启动和组件

| 项目 | 结果 | 证据／可信度 | 局限 |
| --- | --- | --- | --- |
| 主启动 Activity | `com.trainnote.rn.MainActivity`，MAIN + LAUNCHER 过滤器，显式 exported=true | P2-M／高 | 未通过方法体确认具体生命周期实现 |
| Application | `com.trainnote.rn.MainApplication` | P2-M／高 | 当前有界 DEX 类描述符检查没有还原其实现 |
| 组件数量 | 37 Activity、0 alias、10 Service、8 Receiver、7 Provider | P2-M／高 | 包含第三方 SDK，不能等同于业务页面数量 |
| 桌面挑战组件 | `ChallengeWidget`、`ChallengeMediumWidget` Receiver | P2-M／高 | 只确认声明，不代表桌面小组件实测通过 |
| 微信相关组件 | `WXEntryActivity`、`WXPayEntryActivity` | P2-M／高 | 不调用、不登录、不支付、不研究绕过 |
| 深链 | MainActivity 另声明支付回跳类 VIEW/BROWSABLE 过滤器 | P2-M／高 | 不实际触发，不作为独立 APP 需要原样继承的设计 |
| 网络属性 | Manifest 声明 INTERNET、usesCleartextTraffic=true | P2-M／高 | 不表示所有请求都明文，也不证明所有功能必须联网 |

Manifest 还声明了通知、震动、前台服务、精确闹钟、悬浮窗、相机、日历、定位、手表厂商等相关权限。这里是**声明**，不是现场已授权清单；本轮没有申请或授予这些权限。独立离线 APP 不应机械复制原版全部权限。

### 4.2 DEX 与 native 边界

6 个 DEX 的文件大小、头部边界、Adler-32 和 SHA-1 头签名均匹配。类数量依次为 8,856、4,596、8,877、3,038、3,091、7,946；数量仅描述打包结构，不代表业务类数量。

在 `classes5.dex` 定位到 MainActivity、CountdownOverlayModule、ChallengeWidget、SharedStorage、多个手表桥接模块等应用包下的类描述符，支持“包含原生桥接组件”的判断；没有反编译这些类的方法，没有分析 VIPAuth 的校验实现。类名只作为结构证据。

Native 文件仍为 76 个，两套 ABI 各 38 个。React Native/JSC/Hermes 相关库共存，**实际启用哪一个 JS 引擎仍未确认**。没有进行 `.so` 全量反汇编。

### 4.3 资源清点

| 结构 | 数量 | 解释与局限 |
| --- | --- | --- |
| assets | 265 条目 | 包括业务 JS、字体和 SDK 配置等 |
| res | 3,007 条目 | 目录级清点，未完整解析资源 ID 到实际页面的所有关联 |
| PNG | 547 个 | APK 内后缀统计 |
| JPG | 103 个 | 同上 |
| WebP | 1 个 | 同上 |
| GIF | 1,111 个 | 不能据此声称有 1,111 个不同健身动作 |
| MP4 | 0 个 | 仅指包内这一后缀，不能证明应用没有视频；视频可能远程加载 |

没有把原图标、GIF、字体或讲解素材加入独立项目。后续动作演示需要自有或获许可素材。

## 5. 页面与功能对应关系（A–I）

以下是**已定位对象及证据边界**，不是第三阶段完整需求规格。业务模块不因含有页面名就自动获得“功能实现已确认”的结论。

| 模块 | 页面／路由候选 | 静态代码与数据路径 | 当前确认程度 | 截图 |
| --- | --- | --- | --- | --- |
| A 首页与导航 | Home；Tabs；Challenge；NewMovement；History；Me | M2388 路由组件映射；M2542 首页组件；实际首页五个标签 | 导航声明和首页可见入口：高；其他页面可达性：待核验 | S01 |
| B 自定义计划 | UserTrainingPlanPage→M6452；Plan→M5624；NewPlanEditor→M6208；PlanPage→M6231 | M6452 loadPlan/savePlan 调用 M4036 的 getUserTrainingPlan/insertUserTrainingPlan，planKey 必需 | 本地计划读写调用：高；所有计划类型行为和界面：待核验 | 待采集 |
| C 动作库 | NewMovement→M4467；EditMovement→M5671；ExeDatabase→M5665 | M5671 先判断 IsOffline，离线分支调用 M5667 的本地动作函数；M4036 另有 movements 表与增改删方法 | 存在本地与在线不同路径：高；当前模式、新增后的界面响应：待核验 | 待采集 |
| D 组、次数、重量 | Train→M4132，内部编辑器 M4133，组编辑组件 M4134 | sets／moves／doneMap；onDone 改完成标记并触发同步；M5096 updateTrain effect → M4036.updateTrain → localtrains | 记录和本地保存调用链：高；完整输入边界和运行验收：待核验 | 待采集 |
| E 计时与休息 | M4227 倒计时组件；AndroidCountdownOverlayPage 声明存在 | 每动作 restTime／warn_restTime；通知调度；原生 CountdownOverlayModule 类名佐证 | 静态计时规则：高；后台/息屏/提醒实际表现：待核验 | 待采集 |
| F 历史与统计 | History 在不同上下文映射 M5950／M5334；TrainDataPage→M6213；DataDetail→M5631 | localtrains 查询、histindex/prtables 声明；M4021 计算；历史组件读取与展示 | 静态读写及计算：高；各统计口径、所有图表展示：中／待核验 | 待采集 |
| G 身体数据 | Body→M5649；另有 WeightAndBodyfat 等声明 | M5649 fetchBody／setBodyData 包含服务器请求；本地保存的范围设置不能当作完整身体记录 | 存在远程依赖：高；完整本地模型与离线写入：未确认 | S01 仅确认“记体重”入口 |
| H 饮食、营养、饮水 | FoodEditor→M4549；QuickNutritionInput→M6114；FoodChart→M6333；饮水组件 M4905 | M4552 热量；M4907 日期缓存；M4549 服务读取/同步；M4905 杯量预设与输入回调 | 计算、缓存和远程路径：高；饮水全链离线保留：待核验 | S01 饮食摘要和入口 |
| I HIIT、挑战、工具 | NewHiitTrainer→M5941；Challenge/ChallengePageBase→M5782；OneRmCaculatorPage→M6331/6332 | HIIT 的动作/循环/工作与休息状态；挑战增删处理函数；M4021.oneRM | 功能结构与公式：中至高；挑战完整持久化、运行交互：待核验 | S01 仅确认挑战入口 |

表中的 M 数字为模块编号。完整路由清单另见 P2-N。导航里存在同名 History 在不同上下文指向不同组件，以及包装组件再引用其他组件的情况；不能仅凭 route name 得出唯一 UI 实现。

## 6. 数据结构与持久化证据

### 6.1 SQLite 声明

M4036 包含 **21 条 CREATE TABLE 声明**，以及本地训练迁移、索引与读写函数。主要对象：

| 对象 | 静态字段／用途 | 证据与局限 |
| --- | --- | --- |
| localtrains | id、openid、datestr、start、end、movement、title、templateid、note；迁移增加 watch、sync_type、version、delflag | M4036.createTrain；movement 为文本序列化数据；未读取手机真实数据库 |
| movements | id、openid、unit、type、name、uri/url、exetype、section、comment、superSetType、MuscleData、version | M4036.createMovementTable；不代表每列均在全部模式下使用 |
| templates | id、name、movement、rules、belongto、color、local_order、version | M4036.createTemplateTable |
| folders | id、name、uniqueid、local_order、version | M4036.createFolderTable |
| UserTrainingPlan | id、planKey、data | M4036.createUserTrainingPlanTable，M6452 的加载和保存使用该路径 |
| histindex | id、datestr、key、move | 用作历史索引的声明 |
| prtables | id、type、datestr、key、value、issuperset、setidx | PR/统计相关声明；具体所有计算触发条件未全量追踪 |
| 其他表 | targets、notetable、platformPlans、UniversalPlans、platformplanrules、equipment 等 | 名称/字段见 SQL 证据，不将其全部归入个人离线核心 |

M4036.deleteTrain 使用 `delflag=1`、`sync_type='not_sync'` 及版本递增的软删除路径，而非总是立即物理删行；其他删除入口可能不同，本轮不统一推断。

**声明不等于运行状态。** 其中一个旧 `fivefive` 建表字符串包含末尾多余逗号；这里只记录这一静态现象，不能声称它已成功执行或现有用户数据库一定有问题。没有运行或修复原 SQL。

### 6.2 训练对象

| 层级 | 已看到的字段／语义 | 证据／可信度 |
| --- | --- | --- |
| 训练记录 | id、datestr、title、note、start、end、templateid、movement | M4132._update/syncHook，M5096，M4036／高 |
| 常规动作 | key、type、exetype、sets、restTime、warn_restTime、currentWeight、oneSide | M4133、M4227、M4021／高（字段引用存在） |
| 每组 | reps、weight、unit、done、setType、dropset、left_weight | M4133.onDone、M4021／高 |
| 超级组/组合 | moves、doneMap、SuperSetWarmUpMap | M4133.onDone、M4021／高 |

这不是一个已经完整还原的 JSON Schema：必填性、所有枚举、默认值、兼容旧数据规则仍需继续核对；不得据此伪造原版完整导入格式。

### 6.3 新旧本地机制与恢复

M5667 存在 OFFLINE_MODE、MOVEMENT_KEY、SUPER_SET、TRAIN_DATA_、PLAN_KEY_ 等 AsyncStorage 路径和 get/create/update/delete 函数。它是已找到的旧离线机制，不表示当前所有新版入口都走这套机制。

M4132 将训练记录通过 updateTrain action 交给状态层；M5096 的 updateTrain effect 调用 M4036 的数据库方法并再次查询，静态链路可定位。它与旧离线 KV 路径应分别记录，不能混成一个统一数据库实现。

恢复缓存另位于 M5313：

- 活跃指针键：`@xunji/train/recovery-cache/active/v1`。
- 记录键前缀：`@xunji/train/recovery-cache/v1/`。
- 保存数据包括 id、movement、start/end、watchId、timerState、title、templateid、planid/plandayid、note、date；另加 version、updatedAt、expiresAt。
- 保存逻辑 `expiresAt = 当前时间 + 86,400,000 毫秒`，即 24 小时；过期判断与清理函数存在。
- 首页 M2542.tryRestoreTrainFromCrash 读取、归一化缓存并重新打开训练流程。

可信度：缓存结构与调用存在为高。限制：没有杀进程、重启、断网或等待过期测试，因此恢复是否在所有场景正确运行仍未验收。24 小时过期限制也不是新 APP 必须照搬的要求。

## 7. 已确认计算与规则

### 7.1 训练容量

M4021 中可定位 Calulator、CaculateOneMoveVolume、CaculateVolume：

1. 常规加重训练按次数 × 有效重量 × 单位系数计算并汇总。
2. 常规实际完成容量分支检查 done，排除 `setType` 为“热”的热身组；另有参数可切换包含未完成组的统计口径。
3. `CaculateOneMoveVolume` 会处理递减组、单侧/双侧标志、辅助自重等情况。部分单侧动作使用左侧重量，部分 oneSide 路径乘 2，二者不是同一个标记。
4. 核心 helper 的磅换算系数为 0.4532；另一些 UI 分支出现 0.45 等近似值，因此不能声称全应用单位换算实现完全一致。
5. 基础 helper 对非有限数及负数有归零处理，部分结果舍入到 1 位小数；输入界面是否拒绝非法值属于另一条证据链。
6. 有氧、纯次数、自重和辅助/负重自重包含分支，不能把所有动作一律套“体重 × 次数”。

可信度：高（静态表达式）；局限：没有用实际训练记录执行数值对拍，未检查所有统计页是否使用相同 helper。独立 APP 的最终容量口径需要在第三阶段明确，不能照搬不同分支中的不一致近似值。

### 7.2 1RM

M4021.oneRM 的已确认公式为：

```text
预测 1RM = 重量 × (1 + 0.0333 × 次数)
```

结果取 1 位小数；这只证明该 helper 的实现，不代表应用所有 1RM 工具只提供这个公式，更不是运动建议或医学验证。

### 7.3 休息计时

M4227.getRestTimeV2 分别读取动作的 restTime 与 warn_restTime，并在该 helper 中采用 `parseInt(值 || 60)`。因此未配置或该位置为 0 时会回退到 60 秒；特定计划分支另有 90 秒初始化，不应说成全应用固定 60 秒。

perform 存在启动/停止分支、清理 interval、更新状态和结束回调；caculateTime 会调整剩余时间并按当前时间加剩余秒数调度本地通知；存在返回前台、后台任务及悬浮倒计时相关逻辑。Manifest 和 DEX 类名提供结构佐证。

可信度：静态规则高；限制：未申请权限、启动计时、息屏或切后台，因此不能宣称息屏提醒、进程死亡后准确性已经验证。

### 7.4 营养与临时录入

M4552.CalorieCaculator 使用：

```text
热量 = parseInt(4 × 碳水克数 + 9 × 脂肪克数 + 4 × 蛋白质克数)
```

M4549 等选定路径按 `addedValue × 每100g营养值 / 100` 汇总营养素。并非所有食物计量单位都在本轮完整核对。

M6114 的快速营养输入要求三个营养素字段非空；名称可以留空并回退为“临时录入”。提交对象包含 isQuickInput、ntr、默认 100 的 addedValue 和一份 100g 的计量表示，通过回调交给上层后返回。只确认该界面函数的规则，未实际提交记录，也不把非空检查误报为完整数值合法性校验。

## 8. 饮食、饮水和身体数据的离线边界

| 对象 | 已确认事实 | 不应推导的结论 |
| --- | --- | --- |
| 饮食读取 | M4549.fetchData 调用原服务读取饮食/计划等数据 | 不能说原版饮食页可完全离线初始化 |
| 饮食保存 | M4549._syncData 先调用 saveFoodCache，再发起远程同步 | 先缓存不是所有失败条件下都能完整恢复的证明 |
| 饮食缓存 | M4907 用 `@food_cache_storage`；相同日期替换；按日期降序保留最近 15 条日期记录 | 不是完整历史数据库；也不必然是连续 15 个自然日 |
| 缓存有效条件 | isValidFoodData 检查三餐 normal/added、训练前/后食物等是否有内容 | 没有在此函数看到仅凭饮水即判有效；不能保证“只有饮水的日期”进入这条缓存路径 |
| 饮水组件 | M4905 输入 value、timestamp，经 onDone 回调给父级；杯量预设和上次输入量有本地 KV | 不能据此宣称完整按日饮水日志本地持久化链已确认；父级链路仍待补核 |
| 个人身体数据 | M5649 当前身体值、历史身体记录及目标体重读取包含服务器请求 | 本地日期范围设置不是体重/体脂完整本地数据库 |

以上均为静态代码证据，没有向这些原接口发送请求。普通原应用在用户打开期间自身可能正常联网，本报告的“未调用接口”指分析器未主动构造、发送或冒用任何原版服务请求。

对独立 APP 的实际含义：核心训练具备清晰的本地模型参考；体重、饮食、饮水需要独立设计长期离线存储与备份，不能以原版缓存代替。此处仅说明差异，具体 Room 表和验收标准留到第三阶段。

## 9. 实际截图与交互索引

| 截图 | 采集时间（北京时间） | 已观察事实 | 静态关联与局限 |
| --- | --- | --- | --- |
| S01 | 2026-10-20 11:26:48 | 首页有饮食摘要；蛋白/碳水/脂肪/饮水；早餐/午餐/晚餐/加餐/饮水/记体重入口；今日安排与即将到来卡片；底部训练、挑战、动作、历史、我的及训练/饮食切换入口 | 与导航声明和饮食组件结构相互印证；没有点击，不能确认点击后的路由参数、全部布局或保存行为 |

截图尺寸 1440 × 3200。采集前后前台均为 `com.trainnote.rn/.MainActivity`。此前手机处于其他应用时，没有截取其界面。

一次点击“动作”入口的 ADB input 操作被手机以 `INJECT_EVENTS` SecurityException 拒绝，未成功切换页面；因此 **不存在 S02 的成功截图**，不能将其列为实测证据。没有重试点击或修改手机安全权限。

后续可由用户手动打开并提供以下页面，每个页面优先一张完整截图；有弹窗再补一张。请勿为采集而改动真实训练记录：

| 待采集编号 | 页面 | 需确认的交互事实 |
| --- | --- | --- |
| UI-P02 | 动作库、动作详情 | 分类、搜索、动作类型和字段名称 |
| UI-P03 | 自定义动作编辑表单（不保存） | 必填项、单位、类型选择及错误提示 |
| UI-P04 | 自定义计划列表与编辑表单（避免自动创建记录的入口） | 计划/模板/训练日的组织层级 |
| UI-P05 | 已有训练的组编辑界面或用户愿意提供的演示记录 | 每组数据、热身/递减/超级组的实际表现 |
| UI-P06 | 已有休息计时界面 | 启停、加减秒数、结束提示；不要为采集授权额外权限 |
| UI-P07 | 历史详情和统计 | 日期筛选、容量口径、图表维度 |
| UI-P08 | 身体、饮食、饮水的未提交输入界面 | 输入单位、目标值、每日归属、保存反馈 |
| UI-P09 | HIIT 与挑战 | 配置字段、挑战目标和完成展示 |

这些编号是采集待办，不是假称已存在的截图。当前无法完成所有页面的实际交互核验，因此第二阶段的动态/界面部分明确保留缺口。

## 10. 异常与恢复记录

| 事件 | 原因／定位 | 处理 | 结果 |
| --- | --- | --- | --- |
| 解析器首次启动失败 | 内置 Acorn 的 UMD 导出缺少 CommonJS module 绑定，tokenizer 未导出 | 补齐可信解析器的 module.exports 绑定，重试一次 | 成功；不执行原业务代码，无安装 |
| 自动点击失败 | 手机缺少 INJECT_EVENTS 输入注入权限 | 停止自动点击，不提权、不改设置 | 仅保留 S01，其他 UI 列待办 |
| 两次只读补查命令连接中断 | exec-server transport disconnected；未出现内存不足证据 | 复用已经落盘的批次；第一次缩短读取恢复，第二次停止可选饮水父级补查 | 不重新解包，不无限追依赖；饮水全链明确待验证 |

每批上限 20 模块、单模块 250,000 字符、每批合计 2,000,000 字符。业务包结构索引有时间边界，DEX 仅做头部和类表读取。没有对全 APK、全部 DEX 或 native 做无边界反编译。

## 11. 后续与完成边界

本轮交付的是第二阶段**静态分析成果与界面核验现状**。尚未完成：

1. 除首页外各页面的实际截图与操作流程核验。
2. 断网、杀进程、重启、通知/悬浮倒计时的运行测试。
3. 全部 JSON 枚举、旧版兼容规则、原版导入/导出格式的完整恢复。
4. 实际 JS 引擎启用路径、所有 native 方法的反编译和正式签名验签。
5. 原版全部功能的分析；本轮没有给出虚假的“复刻完成率”或“功能覆盖百分比”。

下一阶段可使用已有高可信结论建立 A–I 模块的需求、离线数据边界和验收标准；截图缺口应列为独立待核验项。原版会员、账户、云同步、支付、私有接口和无权使用的素材不作为新应用依赖。

**第三阶段尚未执行，app 目录没有应用代码，也没有可安装的复刻 APK。** 本轮未作任何原版功能验收通过的承诺。

## 12. 交付内容

交付包包含本报告、页面/数据映射 CSV、精选静态证据批次、Manifest/DEX/资源结构、路由/SQL 声明、结论索引、执行日志、S01 原图与截图元数据、只读分析脚本及续接说明。没有包含原 APK、完整 JS 业务包、DEX 文件、native 文件或原版动作媒体。

索引中可能包含原页面字段、必要的短表达式和 SQL 声明，目的是定位与审核，不是用于直接复制到独立 APP。完整模块词法索引仍在本地分析目录，后续可按需读取，无需再次上传原 APK。
