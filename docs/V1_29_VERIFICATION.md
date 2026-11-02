# 独练 V1.29：验证记录

2026-11-02。versionName 1.29 / versionCode 43。

## 检查结果

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleRelease、assembleDebugAndroidTest 构建成功。
- JVM 测试 330 项通过，失败 / 错误 / 跳过为 0。新增 7 项覆盖专注动作、超级组、暂停/完成/撤销后的展示，以及全部动作肌群的图示覆盖。
- 新增 4 项界面用例：组菜单内沿用/专业设置、已完成组撤销、忙碌时禁用操作、文字肌群图例；只编译，未运行。
- Lint：{"Warning": 17, "Information": 6}，无 Error / Fatal。
- Release 非 debuggable；版本、包名和名称核验通过；apksigner 签名与 zipalign 16 KiB 对齐检查通过。
- V1.28 原交付 APK 哈希不变；保持相同签名，未修改数据库和备份格式。
- APK：72,387,027 字节；SHA-256：`64013c91af012bbc6c04698f2c0659f65b245c884b946609ee5c82272054f1ad`。

## 视觉与验证边界

已使用与应用相同的原创矢量路径渲染正背面肌群图，检查卧推、深蹲、划船的主要/辅助区域及图例。该图不是真实界面截图，也不替代 Android 端排版验收。

本机模拟器启动后未进入可连接状态，已停止本次启动的模拟器进程；未安装或操作用户手机。界面测试未执行，真实大字号布局、手势、帧率及设备交互仍需验收。

没有改动账号或同步代码，没有导入 openGym 动作素材或源码；没有恢复苹果开发、更新下载网站或在线更新清单。
