# 高德地图接入：授权前的历史开发验证记录

本页保留2026-10-23授权前阶段的检查。后续已配置用户Key并交付V1.8，当前状态见[V1_8_VERIFICATION.md](V1_8_VERIFICATION.md)。

以下为当时记录：已完成对应源码接入与构建验证，**未完成高德授权或真机地图显示验收，未交付新的地图版 APK**。

## 代码变更

StreetRouteMap 集成高德街道地图、起终点与路线、跟随、全程适配、手势和全屏；地图 SDK 仅在配置有效 Key 且用户明确同意地图处理说明后初始化。停止同意和离开时销毁地图视图，页面前后台对应暂停与恢复。保留地图来源标识。非 ARMv7/ARM64 设备不创建当前 SDK 的原生地图引擎。

OutdoorSpeed 与 OutdoorService 增加实时 GPS 地面速度、短间隔位移估算、无效值/跳点/暂停/失联处理和当前配速；地图显示坐标使用官方 GPS 转换，数据库与 GPX 保留 WGS84。请求定位间隔改为两秒，实际频率由系统决定。

原先的无地图 Canvas 路线不再充当街道地图；未配置授权时明确提示地图尚未开启。地图为主的运动页面保留主要数据，配速按需展开。保留现有应用内更新地址，没有重部署公网下载服务。

## 实际检查

- 最终 testDebugUnitTest、lintDebug、assembleDebug 和 assembleDebugAndroidTest 构建成功；日志 .local-download/map-integration-final-check.log。
- JVM 165 项通过，失败/错误/跳过为0。新增7项速度测试覆盖直接地面速度、精度拒绝、非法速度、短间隔回退、漂移、断段/长间隔/重复时间、无效定位、配速零值和平均速度。
- Lint：{'Warning': 24, 'Information': 6}，无错误。设备测试只编译，没有在用户手机上安装或运行测试。
- SDK地图原生库ARMv7和ARM64已包含；内部APK 82,511,792 字节，签名与V1.7一致，apksigner和zipalign检查通过。
- 当前构建 `AMAP_CONFIGURED=false`。缺少Key时 `verifyStreetMapConfiguration` 按预期拒绝，日志 .local-download/map-key-gate.log。该任务不代表高德服务实际授权验证。
- 已交付 Duolian-V1.7.apk 和原ZIP摘要与此前交付完全相同；没有用未授权构建覆盖它们。

## 尚缺条件与验证边界

需要应用所有者的高德 **Android平台SDK Key**，绑定 `app.duolian.local` 和实际APK签名SHA1。没有使用演示Key、他人Key或申请付费服务。申请资料见 [AMAP_SETUP.md](AMAP_SETUP.md)。

尚未验证真实Key鉴权、街道加载、实际坐标贴合、用户隐私选择、拒绝权限、全屏手势、真实运动速度、室外GPS、锁屏/厂商省电、断网和历史路线的真机表现。上述编译、单元测试与原生库打包检查不能替代这些设备验证。

源码版本号暂保持1.7；待授权配置和设备检查完成后递增版本，再生成正式可体验的地图版。当前公开V1.7仍是此前交付的轨迹示意版。
