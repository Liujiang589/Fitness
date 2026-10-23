# 独练 V1.9 名称统一验证

2026-10-23。开发调试版，versionName1.9 / versionCode23。

对外名称统一为独练：桌面和安装界面使用app_name资源，首页/作者标题、安装包、备份/GPX导出以及下载模板使用独练；GPX创建程序名也统一。Android包名app.duolian.local、签名、数据库及备份格式保留，保证升级/高德绑定兼容。

## 检查

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest成功，日志.local-download/V1.9-release-check.log。
- JVM165项通过，无失败/错误/跳过；既有更新传输检查同步使用中文缓存文件名。未为文案另加镜像测试。
- 下载服务13项测试通过，新增实际HTTP验证中文filename*、ASCII兼容名、JSON文件名、SHA与Range返回；历史英文名行为也通过。
- Lint统计{'Warning': 24, 'Information': 6}，无错误。
- aapt2确认1.9/23、app.duolian.local，所有86个application-label均为独练。
- apksigner与zipalign通过，证书与V1.8一致；实际APK中的高德Key与本机配置相符，值不输出。旧V1.8文件摘要未变。
- ZIP CRC及4个内含文件逐字节核对通过。


- 本机局域网服务重启后实测：latest.json为1.9/23，文件名/大小/摘要与交付一致，HEAD的中文filename*、Range分段及V1.9页面均通过；仅验证电脑端局域网入口。

## 交付

- 独练-V1.9.apk：81,763,701字节；SHA-256 `7d59363d6435c0cdbae0a0c368b7c2ba9b402181970a0696a18e030f35ce3eac`。
- 独练-V1.9-安装包.zip：46,287,121字节；SHA-256 `369ee42c1a62d4ae9d37b0e35e92b3fa3461f6c11fd5a14cf624e804dc3d02ce`。
- 目录：独练-V1.9-安装包/。未另生成Duolian-V1.9副本，避免当前版本名称混淆。

尚未执行手机安装、桌面缓存刷新、中文SAF导出、地图鉴权/渲染、真实GPS、锁屏与覆盖升级的设备验收；设备测试仅编译。没有重新部署WorkBuddy公网下载站。名称范围及历史兼容见[BRANDING.md](BRANDING.md)。
