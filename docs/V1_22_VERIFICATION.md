# 独练 V1.22 验证记录

2026-10-26。versionName1.22 / versionCode36，app.duolian.local。

## 改动

- 原生GPS只负责可靠轨迹；系统NETWORK_PROVIDER与可选高德网络辅助提供显示位置，不向OutdoorRecorder传网络点或速度。
- 新增NetworkLocationAid：沿用已配置的Android Key及现有组合SDK，使用HTTPS、网络定位模式、2秒请求、8秒联网超时；不等首次最新Wi-Fi扫描完成才返回，不启用SDK缓存回调。辅助失败不暂停原生记录。
- 新增独立授权选择、隐私政策入口和定位设置。没有同意时不初始化AMapLocationClient；日常计步不启用客户端。取消/暂停/结束销毁客户端并关闭停止后的基站更新、自启动与常驻扫描选项。
- 准备页允许弱信号先开始计时/计步，并按实际计步权限和硬件能力调整按钮文字。保留3秒倒数、取消、90秒准备超时及准备阶段不保存记录。
- 显示位置的估计误差上限为1000米、时间上限30秒，界面显示误差圈和估计值；路线仍保留原20米质量门槛。没有通过放宽计距门槛来伪造精准。
- 精确GPS优先；高德GCJ02位置不再次偏转，WGS84系统位置按原流程转换。网络位置过期且没有路线时清除旧地图标记和误差圈。

## 检查结果

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest、assembleRelease通过，V1.22-build.log显示BUILD SUCCESSFUL。
- 258项JVM通过，失败/错误/跳过均0。新增9项测试覆盖缓存与过期、未来/无效坐标、粗略位置替换策略、GCJ02/WGS84分流、GPS优先及失联回退、暂停/其他会话隔离、网络位置不生成距离/速度/GPX点。
- 新测试曾复现显示位置误用路线精度规则导致4项失败，修正为独立显示门槛后通过；历史路线质量门槛不变。
- 新增3项设备UI用例并编译通过：弱信号可显式开始；网络位置不标为路线就绪并可取消；缺少计步能力时不承诺计步。未在设备执行。
- Lint有28项Warning、6项Information，没有Error/Fatal。
- APK1.22/36、非debuggable、86个名称资源均为独练；原签名、zipalign、R8、高德Key、APSService/Wi-Fi权限声明、定位客户端DEX及模型/许可资源检查通过。
- V1.21历史APK SHA-256保持不变。没有修改数据库schema、备份版本、历史WGS84轨迹或更新URL。

## 实测边界

当前没有连接手机，本次未安装到用户手机、未运行设备UI用例，也没有室内定位耗时/误差、楼层/房间级精度、室内路线或耗电实测。没有将编译、单元测试或增加定位来源当成真实精度改善的实测证明。

## 交付

APK 71,431,385字节，SHA-256 `0664bb818b6e6ecc80d21484c04ff161d73cc00402c1292947c62da8b210d483`。

ZIP包含APK、使用说明、验证记录、安装说明、模型来源、模型许可和SHA256SUMS，共7项，CRC与逐字节校验通过。原项目目录同时保留独立APK，方便直接安装。

本轮同步本地下载页版本与校验值，没有发布公网网站或调用WorkBuddy。使用见[V1_22_LOCATION.md](V1_22_LOCATION.md)。
