# 独练 V1.7 验证记录

日期：2026-10-23。开发调试版，versionName 1.7 / versionCode 21。

## 实现

新增步行/跑步 GPS 前台服务、每日传感器计步、暂停继续、轨迹分段、漂移过滤、近七天统计、历史位置详情与 GPX 导出。首页及快捷菜单提供入口，沿用暖白、炭黑、荧光绿视觉，详细数字按需展开。新增代码位于 OutdoorPage、outdoor/OutdoorService、domain/Outdoor、data/OutdoorStore。

数据库 schema 仍为 1，在现有 metadata 表按日期/运动分别存储，不做清库迁移。备份为 v4，兼容 v1–v3；运动或计步进行中禁止导入替换。未重新部署下载网站，保留既有更新地址配置。

## 已完成检查

- testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest 构建成功，日志见 .local-download/V1.7-release-check.log。
- JVM 158 项通过，失败/错误/跳过均为 0；新增 14 项覆盖计步基线、重复读数、跨日/重启/回退、球面距离、定位精度/漂移/速度过滤、失联和暂停分段、过期点、配速、GPX XML、v4 备份及旧格式兼容。
- 原有营养快照测试随当前默认备份格式更新为 v4，仍检查完整往返相等。
- Lint 统计：{"Warning": 22, "Information": 6}；无错误。
- 新增 2 项设备存储测试编译通过，覆盖重开、步数差分、轨迹恢复为暂停、旧格式替换及非法备份不变更数据。未执行设备测试。
- aapt2 确认包名 app.duolian.local、版本 1.7/21、minSdk 26，以及精确/粗略定位、身体活动和 location/health 前台服务权限；不声明后台始终定位权限。
- apksigner 和 zipalign 校验通过；证书 SHA-256 为 c84e97599383f771c5fca780eb2c6cc158e1d32a88238dd6a6fe1a7e3f31d2fc，与 V1.5 相同，具备覆盖升级条件。
- ZIP CRC 与四个内含文件逐字节检查通过。

## 产物

- Duolian-V1.7.apk：12,255,639 字节；SHA-256 `b3417ab2adb160c30eb1928dcd9e9aba0d63e54a831dd052391a7bcaeab96520`。
- 独练-V1.7-安装包.zip：11,771,595 字节；SHA-256 `012993cb37e32885620f69b85272807d956fd380bf006227b90bd22e56123d1f`。
- 未压缩交付目录：独练-V1.7-安装包/。

## 验证边界

没有在用户手机上安装 APK、运行设备测试或采集真实轨迹；没有完成首次授权、拒绝权限、实际锁屏、厂商省电、室外 GPS 精度、实际覆盖升级和 UI 截图的设备验收。通过编译及单元测试不表示上述实机行为均已验证。

当前路线为 Canvas 示意，无街道地图或导航。计步从开启后采集，不代表手机全天历史。系统清理会中断采集，最多可能丢失最后几秒未保存的数据。详见 [功能与隐私说明](OUTDOOR_TRACKING.md)。
