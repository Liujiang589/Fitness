# 独练 V1.10 第一批开发验证

2026-10-23。versionName1.10 / versionCode24，开发调试签名。范围为户外记录和运动过程页面。

## 实现

- OutdoorRecorder独立负责会话状态、单调计时、暂停/恢复、样本过滤、速度过期和步数基线；不改变OutdoorRun序列化结构或数据库schema。
- GPS关闭或定位权限失效后暂停；无修复的路线不跨段连线。进程恢复只加载已保存检查点为暂停，由用户继续。最终记录先写入，再清理内存。
- 日常计步开关只同步必要资源，不重启现有GPS或重置运动计步；陈旧计步事件不带入恢复后的运动。
- 正常五秒检查点与关键操作即时保存；路线编码解码移到后台线程，减少界面阻塞。存储异常暂停并保留此前检查点。
- 活动GPS会话使用10分钟有时限、可续期的部分CPU唤醒锁；暂停、结束、销毁释放；单独日常计步不持锁。不能据此宣称已验证耗电或绕过厂商清理。
- 运动页主卡片/定位状态/地图/折叠详情/固定操作栏；区分步行速度和跑步配速。结束确认及本次总结，本机路线示意，断网提示、按需亮屏和系统电池设置入口。

## 已通过

- Gradle verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest；日志.local-download/V1.10-delivery-check.log。
- JVM 181项通过，无失败/错误/跳过（相比V1.9新增16项会话边界检查）。覆盖进程恢复、暂停、重复命令、时钟校正、延迟/乱序/过期位置、精度不足、跳点、速度过期、路线容量和延迟计步等。
- 下载HTTP13项通过，保留UTF-8中文文件名、Range、摘要与历史文件兼容。
- Lint统计：{'Warning': 23, 'Information': 6}，无Error/Fatal。APK包名app.duolian.local，所有86个应用名称均为独练，1.10/24。
- apksigner与zipalign通过，证书与V1.9一致；APK中的高德Key与本机配置相符，仅检查匹配，不在报告输出Key。V1.9/V1.8旧APK摘要未变。
- ZIP CRC和5个文件逐字节一致。

- 现有局域网入口实测返回1.10/24；JSON、HEAD中文文件名、Range及完整APK下载摘要与本地交付一致。此项只验证电脑本机访问现有LAN服务，不证明其他网络/微信或公网可达。

## 未完成的设备验收

新增4项OutdoorWorkoutUiTest及1项OutdoorServiceIntegrationTest，连同既有存储/UI测试均已编译，未执行。本机硬件加速不可用，独立软件模拟器未启动完成，已停止本次创建的实例；没有改动用户手机或旧模拟器。

因此没有宣称实际手机地图鉴权、街道渲染、界面截图/大字布局、GPS精度、锁屏连续性、电话打断、耗电及覆盖安装已通过。设备测试只编译不能替代运行；验收步骤见[V1_10_ACCEPTANCE.md](V1_10_ACCEPTANCE.md)。集成测试需专用可丢弃模拟器，传outdoorDedicatedDevice=true并允许mock_location，不能直接在用户主机手机运行。

未重新部署WorkBuddy公网站、改动其二维码或测试公网自动更新。

## 交付

- 独练-V1.10.apk：81,961,484字节；SHA-256 `fc7627f88b5c87877ba420f6910bb6679586d9c214cde507da07945d12ec5f5f`。
- 独练-V1.10-安装包.zip：46,324,818字节；SHA-256 `cdca5e35a72c7cde708cb87f3e84c77f8d0b3b6fa7edb373357f73d41503ef52`。
- 同名目录包含APK、安装说明、户外使用说明、手机体验清单和摘要。

设计实现参考[Android前台定位权限](https://developer.android.com/develop/sensors-and-location/location/permissions)、[前台服务启动限制](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start)和[PowerManager](https://developer.android.com/reference/android/os/PowerManager)。
