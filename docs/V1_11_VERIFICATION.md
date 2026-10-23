# 独练 V1.11 第二批验证

2026-10-23。versionName1.11 / versionCode25，开发调试签名。范围为首页减负和动作指导。

## 实现

- TrainingHome纯展示模型区分首次使用、当天未完成排期、自由安排、无排期及今日已训练；按planId匹配完成情况，多个排期选尚未完成者。前台日期更新、恢复应用时刷新。
- 首页“今天”突出主行动、走跑、动作学习与周足迹，完整计划/模板/排期放入计划库；饮食详细数值和专业分析仍可访问。
- 40条原创短动作提示；播放器阶段回调联动完整分解高亮。要领、常见错误、替代动作分主题显示。
- 替代关系逐项整理，显示理由和差异；无适当条目时明确留空。导航只读取内置指导，不给临时动作写入个人设置，也不自动改计划或重量。
- 计划和当前训练动作有指导入口，训练中的指导只读；已有动态播放器及高清素材复用。原训练照片、备注、收藏和数据结构保留。

## 已通过

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest成功（.local-download/V1.11-delivery-check.log）。界面测试使用手动时钟管理循环动画；单独编译记录另见V1.11-ui-test-build.log。
- JVM 194项通过，无失败/错误/跳过，包含新增9项首页排期及4项动作指导关系检查；原有户外、备份、动作引擎及更新回归保留。
- HTTP13项通过；中文文件名、Range、SHA及历史命名兼容。
- Lint：{'Warning': 23, 'Information': 6}，无Error/Fatal。应用包名app.duolian.local，名称独练，所有86个名称资源一致；1.11/25。
- apksigner与zipalign通过，证书与V1.10一致。APK内高德Key与本地配置匹配，值不输出；V1.10旧APK摘要未变。
- ZIP CRC及5个内含文件逐字节一致。既有训练/饮食图片均1536×1024；动态示范为原生矢量，不新增模糊媒体。

- 电脑本机访问现有LAN入口：latest.json为1.11/25，HEAD中文文件名、Range、整包下载摘要及页面版本均匹配。此项不证明手机/微信或公网可达。

## 设备验收边界

新增5项HomeCoachingUiTest已编译，覆盖首次创建、操作防重、自定义动作个人备注、替代浏览回退和训练只读指导；既有ExerciseMotionUiTest改用手动测试时钟，避免自定义循环动画阻止空闲同步。设备测试未运行，不能据此声称UI交互和显示已通过。

沿用前轮本机模拟器/硬件加速不可用的限制，本轮未改动用户手机，也未重复启动该故障模拟器。小屏/大字/横屏、真实动画观感、后台停播、跨日恢复、覆盖安装与旧数据保留仍需手机验收。没有额外声称高德鉴权、真实GPS、锁屏或耗电已通过。

没有调整数据库schema、备份v4结构或重新部署WorkBuddy公网站。原创指导和替代关系不等于持证教练逐项审校，不是伤病或个性化负荷处方。

## 交付

- 独练-V1.11.apk：81,845,645字节；SHA-256 `0ea08aa9e7b2fc582a1dea4e05d21dac4bd9c44ccb77405b2390d349731e184a`。
- 独练-V1.11-安装包.zip：46,358,905字节；SHA-256 `ccd291f0732fcfdca8779c198cd34e769ad2d415b00994a7aab1bc40f2c18b0b`。
- 同名目录包括APK、安装说明、首页与动作指南、手机体验清单及摘要。

使用与内容来源见[V1_11_HOME_AND_COACHING.md](V1_11_HOME_AND_COACHING.md)，手机步骤见[V1_11_ACCEPTANCE.md](V1_11_ACCEPTANCE.md)。动画测试时钟参考[Android Compose测试同步](https://developer.android.com/develop/ui/compose/testing/synchronization)。
