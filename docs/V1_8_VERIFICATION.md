# 独练 V1.8 地图版：交付验证

2026-10-23。versionName 1.8 / versionCode 22，开发调试版。用户已创建Android平台Key并授权接入本应用；Key仅写入被Git忽略的本机配置及构建产物，不写入仓库或文档。

## 已检查

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest全部成功；日志 .local-download/V1.8-release-check.log。
- JVM165项通过，失败/错误/跳过为0；覆盖旧功能、户外计步、轨迹过滤/分段、速度与配速、GPX和备份兼容。
- Lint：{'Warning': 24, 'Information': 6}；无错误。设备测试仅编译，未运行。
- BuildConfig.AMAP_CONFIGURED=true；实际APK manifest中的高德meta-data与用户提供的本地配置逐值相符，不输出Key。
- aapt2确认app.duolian.local、1.8/22、minSdk26；没有ACCESS_BACKGROUND_LOCATION权限。高德11.3.100地图原生库ARMv7/ARM64已包含。
- apksigner及zipalign检查通过。证书SHA-256 `c84e97599383f771c5fca780eb2c6cc158e1d32a88238dd6a6fe1a7e3f31d2fc`，SHA1 `C6:68:54:50:71:D3:D8:E3:30:BD:97:7A:04:BC:D4:28:4A:24:17:92`，与V1.7相同。原V1.7文件未改，具备覆盖升级条件。
- ZIP CRC和4个文件逐字节验证通过。

## 交付文件

- Duolian-V1.8.apk：81,763,321字节；SHA-256 `b7b790bbeca7e8ad9265d1a60e9adb970dd5ac2c9a4ddae4118b54d7b52ab45e`。
- 独练-V1.8-安装包.zip：46,287,228字节；SHA-256 `6584718076541040ed0e28b494cd6c9413d1b5f133b3a459aaf1257ff8ccd78d`。
- 未压缩目录：独练-V1.8-安装包/。

## 待手机验证

未在用户手机上安装或执行设备测试；未验证实际高德服务鉴权、街道地图渲染、真实位置贴合、全屏手势、隐私同意/关闭、运行中权限拒绝、实际速度/GPS精度、锁屏保活、厂商省电和覆盖升级。配置与静态检查通过不等于上述设备验收完成。

地图开启前不初始化SDK；允许关闭地图后继续本机运动记录。GPS/GPX原始坐标保持WGS84，显示坐标通过高德转换。没有重部署WorkBuddy公网入口，也没有确认线上下载包同步。详细使用和隐私见[户外记录说明](OUTDOOR_TRACKING.md)。
