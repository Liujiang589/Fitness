# 独练 V1.2 命名更新验证

日期：2026-10-22。沿用0.12.0界面和功能，改用简短版本命名。

- 应用关于页：V1.2，不展示内部构建编号。
- Android版本：versionName 1.2 / versionCode16；包名app.duolian.local。
- 发布文件：Duolian-V1.2.apk，12,041,166字节。
- SHA-256：dbe37b8049ac410c82d522e07a172c5beebdc32da807fc67695139a3ae52000e。
- 签名证书SHA-256：c84e97599383f771c5fca780eb2c6cc158e1d32a88238dd6a6fe1a7e3f31d2fc，与0.12.0一致；内部编号由15升至16，支持覆盖升级。

## 检查结果

- assembleDebug和lintDebug通过，构建1分23秒；Lint 0错误、19警告、6信息，分布与上一版相同。
- apksigner verify、zipalign通过；aapt2实际读取到1.2 / 16。
- 下载服务10项HTTP测试通过，新增覆盖旧三段版本切换为V1.2短命名，以及页面、清单、文件名和实际字节一致。
- 实际WLAN地址下载HTTP200，MIME为application/vnd.android.package-archive，下载名Duolian-V1.2.apk；内容与发布文件逐字节、SHA-256一致。
- 下载页实际显示“独练 V1.2”，最新版清单显示1.2 / V1.2 / 16；启动脚本能识别短文件名并复用现有服务。

本轮只修改版本显示及下载解析，没有重跑上一版已通过的122项领域测试；未进行手机安装和原生界面截图验收。功能改版原验证见[UI012_VERIFICATION.md](UI012_VERIFICATION.md)，后续命名规则见[VERSIONING.md](VERSIONING.md)。
