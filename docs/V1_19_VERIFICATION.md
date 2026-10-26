# 独练 V1.19 验证记录

2026-10-26。versionName1.19 / versionCode33，app.duolian.local，Android8.0以上，原签名Release优化变体。

## 构建与回归

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest、assembleRelease通过，见本地V1.19-final-check.log。
- 239项JVM测试通过，失败、错误与跳过均0。新增检查覆盖准备定位的新鲜度和精度、90秒超时、路线分块边界与断点、14000点增量追加、计时状态隔离、过期速度隐藏与6秒短暂保持。
- Lint有27项Warning、6项Information，没有Error/Fatal；这些结果不代表全部静态建议已处理。
- APK1.19/33、非debuggable，86个名称资源均为独练；原签名证书、zipalign、R8与高德配置检查通过。模型及许可与源码资源逐字节一致。
- 开发预览入口只在Debug源集；正式APK的Manifest不包含OutdoorPreviewActivity。

## 手机验证

- 已在连接的安卓手机上覆盖安装最终Release，versionName1.19 / versionCode33；MainActivity启动Status: ok，原始firstInstallTime保留。没有清除应用数据或卸载用户应用。
- 5项设备交互测试通过：运动主界面信息收纳、中断恢复提示、重复操作阻止、忙碌时禁用操作、取消放弃不保存也不恢复运动。
- 其余7项设备测试没有完成：测试期间系统清理终止进程，随后ActivityScenario启动出现超时。结果不计为通过；没有要求更改手机安全设置。
- 使用Debug临时预览加载实际生产界面组件，检查GPS准备页、运动仪表和原生高德街道地图。定位点、蓝色路线及跟随状态在手机上正常显示；预览使用模拟数据，不请求GPS、不读写运动仓库。
- 两张1440×3200预览图来自上述手机界面，均标明“开发预览 · 模拟数据”。不是实际户外路线或真实运动成绩。
- 一次Debug模拟12000点路线的绘制采样为595帧、48帧janky、95分位10毫秒。不是受控性能对照，可能受到系统通知干扰，不能据此宣称正式版帧率提升或无掉帧。

## 交付与边界

APK 71,087,237字节，SHA-256 `caa4d6ea2bcf652d42f1746a8fac80282a6808272d105efe620d385d2c4b3ee6`。

ZIP包含APK、两张预览图、使用说明、验证记录、安装说明、模型来源、模型许可与SHA256SUMS，共9个文件，执行CRC及逐字节核对。APK和ZIP均放在原项目目录。

真实户外距离/速度准确性、GPS失联与恢复、锁屏长时间记录、耗电和不同手机上的表现仍需实测。地图/数字动画仅改善显示，不生成测量点；请求间隔1秒不保证每秒有有效GPS。没有声称达到Keep、Strava或NRC的全部功能。

本轮没有重新发布公网网站或更换应用内更新URL。数据库schema1、备份v4及历史轨迹格式沿用；原签名覆盖升级。使用见[V1_19_OUTDOOR.md](V1_19_OUTDOOR.md)。
