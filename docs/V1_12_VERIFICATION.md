# 独练 V1.12 第三批验证

2026-10-23。versionName1.12 / versionCode26，Android8.0以上。交付变体Release，非debuggable，启用R8与资源压缩；为覆盖升级继续使用原开发证书，这不等于已经完成应用商店签名与上架准备。

## 已通过

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleDebugAndroidTest、assembleRelease通过，见本地V1.12-release-check.log。首轮同时assembleDebug通过，但Release因SDK缺少类而失败，修正依赖和精确规则后重新构建。
- JVM206项，失败/错误/跳过均0；新增12项涵盖两段七天窗口、暂停、未结束/未来记录、可比正式组、食物缺记、每份换算及近期份量快照。
- 下载HTTP13项通过。Lint：{'Warning': 23, 'Information': 6}，无Error/Fatal。
- APK包名app.duolian.local，版本1.12/26，86个名称资源均为独练，未设置debuggable；apksigner、zipalign通过，证书与V1.11一致。高德配置在APK中匹配本地配置，不输出Key。
- 旧V1.11 APK摘要未变。数据库schema1与备份duolian-native v4不变，未迁移或清空用户数据。
- 原有界面及存储/服务测试APK编译成功，未执行本轮设备仪器测试，不能把编译当作UI或GPS验收。

## 功能及实现边界

周回顾采用连续两个七天窗口；同动作ID、名称、重量的正式组才比较次数；饮食平均仅覆盖有记录日期，不推断完整摄入。走跑与力量分别汇总，不把可能重叠的时段直接相加。

包装标签支持每100g/每份，后者先按明确克数归一。常用份量按记录日期排序去重；同一天没有录入时间字段，不能保证同日多条记录中最后录入者优先。营养快照沿用原记录，旧版无重量条目不猜测克数。

高德组合SDK的POM遗漏FastMath依赖，补入net.jafama:jafama:2.3.2；GnssSoftLocator是SDK引用但未分发的软RTK扩展类。本应用不初始化高德定位Client，定位继续使用Android系统；仅对该缺失扩展设置dontwarn，并保留地图/JNI规则，没有全局屏蔽依赖错误。仍需真机核对地图联网和鉴权，不能用构建通过替代。

## 真机与性能

已在连接的22011211C手机上以`adb install -r`覆盖安装成功，未卸载或清除应用数据。系统确认版本1.12/code26、非debuggable；应用启动成功，首页高清照片、入口与静态矢量示范正常显示，旧的“步行待继续”状态仍可见。启动检查时应用进程存活，限定该进程的AndroidRuntime错误日志无FATAL EXCEPTION。没有据此声称全部历史记录已逐项核对。

清零新版绘制统计后，用户再次在首页上下滑动并回复“新版已滑动”，读取时前台仍是独练。新版采样4787帧，23帧超过系统截止时间，比例0.48%；P50/P90/P95/P99为7/9/10/12ms，Slow UI thread为4，Slow draw commands为20，GPU P95/P99为6/7ms。清零到读取约65秒，包含约15秒滑动及等待期间的应用帧。原始记录为本地忽略目录`.local-download/V1.12-home-after-manual-gfx.txt`。

对照旧版同机手动采样：3.04% → 0.48%，P95 20 → 10ms，P99 36 → 12ms。仅能判断本次现场采样明显改善；旧版V1.9调试构建、新版V1.12发布构建及布局均不同，不能把差异归因到某一项代码，也不是固定手势、固定刷新率的受控基准。动作库、长训练列表、地图及大数据历史未取得相同方式的前后采样。


详细采样、原因范围与限制见[流畅度检查](V1_12_PERFORMANCE.md)。未执行真实GPS走跑、锁屏耗电、小屏/大字体等完整验收。没有修改手机安全设置。性能优化阶段未涉及WorkBuddy网站；后续网站同步情况另见发布回执。

## 交付

- APK 70,765,659字节，SHA-256 `e738162d2c104cb2b5d57b54ebfd37b10a58758dd4ac12523df842dac384c4b0`。
- 项目根目录独练-V1.12.apk、独练-V1.12-安装包.zip与同名安装包目录。
- ZIP含APK、安装说明、第三轮指南、手机体验清单、流畅度报告及SHA256SUMS，共6个文件；打包脚本检查CRC、逐字节一致性，ZIP摘要记录在根目录SHA256SUMS。

使用见[指南](V1_12_GUIDE.md)，手机步骤见[体验清单](V1_12_ACCEPTANCE.md)。
