# 独练 V1.27：验证记录

2026-11-01。versionName 1.27 / versionCode 41。安卓稳定性优化。

## 检查结果

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleRelease、assembleDebugAndroidTest 构建成功。
- JVM 测试 314 项通过，失败 / 错误 / 跳过均为 0。新增 10 项覆盖训练草稿事务失败与取消、较新输入保护、计步乱序及重订阅等情况。
- 新增 2 项界面回归用例：保存失败可重试、处理中阻止重复操作。只完成编译，未连接设备运行。
- Lint：{"Warning": 17, "Information": 6}，无 Error / Fatal。
- Release 非 debuggable；包名、版本与应用名称核对通过。apksigner 校验通过，沿用 V1.26 证书；zipalign 16 KiB 对齐检查通过。
- V1.26 原交付 APK 哈希不变。未变更数据库版本、备份格式、依赖或高德配置。
- APK：72,370,643 字节；SHA-256：`4bf5eb7ea4d0511f4eb015909fd3a4e205ffe5736d2a5aef9a0edaa0cf28d064`。

## 验证边界

没有连接、安装或操作用户手机，没有执行真机 UI 测试，没有测量帧率、步数误差、定位精度或首次定位耗时。不能将本轮修改视为已确认修复全部启动闪退或 GPS 漂移。实机覆盖升级与户外路线仍需验收。

输入草稿失败恢复仅限当前进程，尚未实现失败草稿的跨进程持久化。步数仍受设备传感器、权限和后台运行情况影响；本次仅防止异常采样顺序破坏基线，不提供系统级全天历史步数。

账号认证仍未接通，苹果网页版保持暂停。没有发布网站、更新二维码或在线更新清单。
