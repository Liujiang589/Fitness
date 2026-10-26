# 独练 V1.23 验证记录

2026-10-26。versionName1.23 / versionCode37，包名app.duolian.local。

## 已处理的问题与依据

- V1.22使用的官方组合包11.3.100_loc11.3.101_sea9.8.1中，com/amap/location/fusion/original/a/a.class引用com/amap/ams/gnss/GnssSoftLocator，但包内没有该类。javap检查显示构造路径可到达此引用；此前Release存在该类dontwarn规则，打包成功不能证明运行时依赖完整。
- 改用官方组合包10.1.700_loc6.5.1_sea9.7.4，逐类扫描未再发现该缺失引用；保留jafama依赖和SDK反射/JNI保留规则，移除缺失类dontwarn。旧/新包对照记录位于本地V1.23-sdk-linkage.json。
- 新增非导出的LocationAidService，和APSService一同位于:location_aid进程；APSService标注location前台服务类型。原OutdoorService仍在主进程。通过Messenger传递基本类型位置数据，未向记录器传SDK对象或网络点。
- 仅在用户选择且有定位权限时显式绑定辅助服务；停止后发送STOP并解绑。旧会话回调通过generation丢弃；绑定失败、空绑定、进程退出、连接死亡均提示回退，不重启或结束运动记录。
- 应用两个进程均安装本地异常记录器，保留Android原异常处理器；“我的 → 异常诊断”可复制。Android 11以上退出摘要的API检查单独封装，Android 8–10仍可查看Java异常本地记录。

## 检查结果

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest、assembleRelease通过，最终V1.23-build.log显示BUILD SUCCESSFUL。
- 263项JVM通过，失败/错误/跳过均0。新增2项SDK依赖检查、3项诊断测试；保留网络位置不增加里程/速度/GPX、路线质量、准备/取消、计步和旧数据规则回归。
- 诊断测试覆盖版本/进程/调用链、常见凭据/位置赋值脱敏、超长内容和循环异常链有界处理。
- 首次Lint发现4处Android 11退出信息API兼容检查不完整，修正后重新构建。最终Lint 28项Warning、6项Information，无Error/Fatal。
- Release非debuggable、R8 mapping存在，签名及zipalign通过；APK内版本1.23/37、应用名称独练、高德Key、辅助服务进程声明、定位客户端DEX及缺失Gnss引用清除检查通过。
- 人体模型与许可资源保持逐字节一致；V1.22历史APK SHA-256保持不变；未更换数据库、备份版本或更新源。
- APK 61,026,225字节，SHA-256 `ef40f0554751f74ac96c339dca6978f6e65366bf1267920777b4188a3283f0b7`。

## 未完成的设备验证

用户当前无法连接手机。本轮没有读取其崩溃日志、没有在用户手机复现或验证开始/暂停/结束、后台/锁屏、辅助进程死亡恢复和实际地图兼容性；设备测试包仅编译，未执行。也没有室内精度、首次定位耗时或耗电实测。

本版属于依据代码和依赖证据完成的针对性修复。不能将依赖风险移除、隔离或单元测试通过视为已经证明用户的实际闪退彻底解决。若仍有异常，用新增诊断入口继续收集依据。

## 交付

独立APK与安装ZIP放在原项目根目录。ZIP含APK、使用说明、验证记录、安装说明、模型来源、模型许可、SHA256SUMS，共7项；CRC与逐字节检查通过。本轮更新本地下载页，没有发布公网网站或调用WorkBuddy。

详见[使用说明](V1_23_STABILITY.md)。
