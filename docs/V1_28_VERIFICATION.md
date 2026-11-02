# 独练 V1.28：验证记录

2026-11-02。versionName 1.28 / versionCode 42。

## 检查结果

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleRelease、assembleDebugAndroidTest 构建成功。
- JVM 测试 323 项通过，失败 / 错误 / 跳过为 0。新增 9 项覆盖批量历史查询的时间、热身/未完成组、重复动作、历史删除，以及跨周汇总；混合的 2000 条历史与旧排序规则逐项对比一致。
- 新增 2 项界面用例，检查计算不占用 UI 线程、旧任务不能覆盖新输入。界面测试只编译，未在手机运行。
- Lint：{"Warning": 17, "Information": 6}，无 Error / Fatal。
- Release 非 debuggable；应用名、版本和包名核验通过。原签名延续，apksigner 和 zipalign 16 KiB 对齐检查通过。
- 原 V1.27 APK 哈希不变；数据库格式、备份格式及高德配置未变。
- APK：72,370,643 字节；SHA-256：`15cbcfb7ee73d89d55481c2d74ce7c6868fc5e4dbb8ccdfccba489ec438f5ee6`。

## 实际边界

未操作用户手机，未执行真机界面测试或测量 FPS、掉帧率、内存峰值、定位精度。仅确认代码减少了主线程计算和重复查询，不能承诺所有设备已无卡顿。

后台统计完成前显示加载状态；完成后的统计保存在页面中，离开页面后重进会重新计算。没有新增账号认证、恢复苹果网页开发或发布网站，也没有导入 openGym 代码/媒体。
