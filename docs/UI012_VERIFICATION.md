# 独练 0.12.0 验证记录

日期：2026-10-22；包名 app.duolian.local；版本0.12.0 / versionCode15；开发调试版。

## 交付

- 文件：Duolian-0.12.0-debug.apk
- 大小：12,041,170字节（约11.48MiB）
- SHA-256：86aff5d1b87f646a61dd4a5e81691648806fbbace19f45ec423a365ad0f232d5
- 签名证书SHA-256：c84e97599383f771c5fca780eb2c6cc158e1d32a88238dd6a6fe1a7e3f31d2fc
- minSdk26 / targetSdk35；与0.11.1使用相同包名和证书，版本号递增。

设计与能力入口见 [UI012_DESIGN.md](UI012_DESIGN.md)，完整生成提示词见 [UI012_IMAGE_PROMPTS.json](UI012_IMAGE_PROMPTS.json)。

## 最终构建检查

最终完整构建用时1分41秒，执行testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest。没有数据库、备份或算法变更。

| 检查 | 结果 |
|---|---|
| JVM单元测试 | 122项通过，0失败、0错误、0跳过 |
| Android Lint | 0错误，19警告，6信息 |
| 应用APK、设备测试APK | 均编译成功 |
| apksigner verify | APK v2签名有效，证书与0.11.1一致 |
| zipalign -c -p 4 | 通过 |
| aapt2包信息 | app.duolian.local、0.12.0、15、minSdk26、targetSdk35符合预期 |
| 联系方式资源 | 实际APK中保留LiuJiang及17549207425 |
| 两张新配图 | APK内实际图片与工程文件逐字节相同，均可解码为1536×1024 |
| 局域网下载 | HTTP200，APK MIME正确，下载名Duolian-0.12.0.apk |
| 实际下载内容 | 12,041,170字节，与根目录发布APK逐字节、SHA-256一致 |
| 最新版清单和网页 | 均已自动切到0.12.0 / 15 |

相比0.11.1，Lint新增1项UnusedResources：旧版training_editorial.webp因新图片接替而不再引用，暂保留原文件用于设计回溯。其他警告为既有依赖版本、计时服务、参数、系统适配及图标提示。没有通过禁用Lint规则来掩盖问题。

## UI覆盖与图片检查

- 更新TrainingUiTest：完成训练后主动展开统计再核对容量；历史页默认不显示容量，切到专业分析后核对原值；动作库允许滚动到动作列表。
- 新增CalmPresentationUiTest：独立营养摘要默认隐藏详细数字，展开后显示原值；保存状态重建后仍展开，收起不改变营养数据。该测试不操作应用数据库。
- NutritionUiTest原有食物与重量换算、无选择保存报错、旧记录兼容测试继续编译通过。
- 已人工查看两张生成原图，核对主题、留白、清晰度与无文字条件；按原尺寸压缩成WebP quality92，训练图176,366字节，餐食图418,582字节。没有用插值放大伪造更高分辨率。
- 源码审查保留训练输入控件和计时器的组合生命周期；只折叠次要统计，避免影响进行中的输入和计时。

## 验证边界

本机没有可用的Android测试设备，本轮设备UI测试仅编译，未执行；未安装到用户手机。未声称已完成原生界面截图验收、各种字号和屏幕尺寸的实机核验。

下载服务在电脑本机的实际WLAN地址验证通过，但尚未由手机确认连通性。手机与电脑需在同一Wi-Fi，且网络允许互访。没有发布到外部服务器，也没有关闭防火墙。
