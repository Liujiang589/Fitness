# 独练街道地图：高德授权接入

2026-10-23更新：用户已创建Android平台Key，本机配置和V1.12 APK均已写入；配置检查通过，仍未完成实际高德鉴权和手机地图验收。以下保留配置与换签名时的说明。

## 申请时可以直接填写的信息

| 高德控制台字段 | 填写内容 |
| --- | --- |
| 应用名称 | 独练 |
| Key 名称 | 独练 Android 地图 |
| 服务平台 | **Android 平台 SDK** |
| Package / 包名 | `app.duolian.local` |
| 当前交付 APK 的签名 SHA1 | `C6:68:54:50:71:D3:D8:E3:30:BD:97:7A:04:BC:D4:28:4A:24:17:92` |

SHA1 从交付APK经apksigner读取，V1.12、V1.11、V1.10、V1.9、V1.8与V1.7一致，当前工程沿用同一签名。当前分发包仍使用调试签名；如控制台分别填写发布版/调试版安全码，请以实际分发 APK 的证书为准。将来改用正式发布签名时，应添加对应的新指纹。不要把 SHA-256 填进 SHA1 栏，也不要申请 Web 服务 Key 或 JavaScript Key 代替 Android SDK Key。

操作入口：[高德开放平台控制台](https://console.amap.com/dev/key/app)。创建应用后添加 Android 平台 SDK Key，绑定上述包名和签名。[官方申请说明](https://lbs.amap.com/api/android-sdk/guide/create-project/get-key)。账号登录、验证码和账号身份资料应由账号本人完成。

## 本地配置方式

将 `android/amap.local.properties.example` 复制为 `android/amap.local.properties`，在其中填写：

```properties
AMAP_ANDROID_KEY=你的Android平台SDKKey
```

该本地文件已被 Git 忽略，不要提交。也可在构建进程中使用 `DUOLIAN_AMAP_ANDROID_KEY` 环境变量；不要通过会进入日志的命令行参数传递。Key 最终必须进入 APK 的高德 manifest 元数据，由包名和证书绑定限制使用；它不是服务端私钥。

没有配置时 `BuildConfig.AMAP_CONFIGURED=false`，地图区域明确显示尚未开启地图服务，不初始化 SDK，不使用演示 Key，也不把无街道示意线伪装为已接入的地图。已有 V1.7 安装包保留，不发布或覆盖为未授权的地图版。

## 当前实现

- 固定 SDK 依赖 `com.amap.api:3dmap-location-search:11.3.100_loc11.3.101_sea9.8.1`，版本来自高德发布的 Maven 元数据；其地图 SDK 版本为 11.3.100。只调用地图展示和坐标转换；不启用高德定位、搜索、导航或轨迹云服务。定位继续使用 Android GPS。
- 高德当前地图原生库为 ARMv7 / ARM64；对于不支持这些架构的设备，不创建地图引擎，显示不支持提示，保留计步与 GPS 记录。没有实际架构设备的运行验收。
- 真实街道地图、绿色运动轨迹、起点/当前点/终点、手势缩放、跟随位置、全程适配和全屏查看；保留高德来源标识与审图信息。没有采集位置时显示地图默认视野，不捏造当前位置。
- 绘制前通过高德 `CoordinateConverter` 的 GPS 类型转换显示坐标，避免原始 WGS84 点直接叠加高德地图产生偏移。数据库和 GPX 保持原始 WGS84，不改写旧轨迹。
- 当前速度优先取 GPS 地面速度；精度不足时使用短间隔位移估算，拒绝跳点、无效值和跨段估算；停止/暂停或超过十秒无有效速度时显示“—”。平均速度使用已记录距离和非暂停时长。
- 按两秒请求 GPS 更新；系统实际返回频率受设备和环境影响。最多14,400个接受点；最密集时约八小时，达到上限时提示新开运动。
- 进入运动中页面时，地图居于主要位置，主要呈现距离、当前速度和时长，配速放入展开详情。历史记录可以回看街道上的路线和平均速度。

## 隐私与网络行为变化

在用户明确开启街道地图前不初始化高德 SDK。开启前展示第三方名称、联网加载、可能处理的位置/IP/设备和应用信息，以及[高德隐私政策](https://lbs.amap.com/pages/privacy/)。可以单独关闭地图，保留本机运动记录。地图视图随前后台暂停恢复，离开或关闭时销毁。

接入在线地图后不能再笼统声称“位置绝不交给任何第三方”：本地运动记录不会上传为轨迹云数据，但地图 SDK 会依其政策处理必要信息与联网请求。完整隐私说明需要随正式地图版同步更新。新增 ACCESS_NETWORK_STATE；不增加通讯录、电话、Wi-Fi 扫描或始终允许定位权限。不通过放开全局 HTTP 来加载地图。

## 发布前验证

需要有效 Key 后执行最终构建，递增版本，验证实际安装包的包名/签名/授权一致性，并在手机上检查街道是否加载、轨迹是否贴合位置、实时速度、拒绝同意、关闭地图、暂停继续、锁屏、历史回看和无网络状态。仅有编译或单元测试通过，不能证明地图授权或室外路线已经可用。

发布前先运行 Gradle 任务 `:app:verifyStreetMapConfiguration`；未配置 Key 会明确失败。该检查只能确认存在格式正确的配置，不能代替高德授权和设备上实际加载地图的检查。当前源码和安装包版本为1.12/26；已配置Key，实际鉴权及设备渲染待验证。V1.7历史包保留。

代码依据：[官方 SDK 配置](https://lbs.amap.com/api/android-sdk/guide/create-project/android-studio-create-project)、[官方隐私初始化接口](https://lbs.amap.com/api/maps-sdk-for-android/guide/create-project/dev-attention)、[坐标转换 API](https://a.amap.com/lbs/static/unzip/Android_Map_Doc/3D/com/amap/api/maps/CoordinateConverter.html)。
