# 独练 0.10.0 验证记录

日期：2026-10-22。包名 app.duolian.local；versionName 0.10.0；versionCode 12。

## 实际执行结果

| 检查 | 结果 |
|---|---|
| testDebugUnitTest | 95 项通过，0 失败 |
| lintDebug | 0 错误，18 项警告，6 项信息 |
| assembleDebug | 成功 |
| assembleDebugAndroidTest | 成功，仅编译未执行 |
| apksigner verify --verbose | 通过，APK Signature Scheme v2 |
| 签名比对 | 与随仓库 0.7.0 证书 SHA-256 一致 |
| zipalign -c -p 4 | 通过，退出码 0 |
| 离线图片 | APK 内图像字节与项目 WebP 一致 |

签名证书 SHA-256：c84e97599383f771c5fca780eb2c6cc158e1d32a88238dd6a6fe1a7e3f31d2fc。

最终全套构建用时 1 分 58 秒。未更新依赖版本；Lint 的依赖升级提示与其他警告保留在报告中。

## 安装包

- 文件：Duolian-0.10.0-debug.apk
- 大小：11,657,984 字节
- SHA-256：0b3c829423d114e43eb6214f5a10803a0f0fbb60cef2ed4fcc9c25d320c86e07
- 兼容 Android 8.0 / API 26 及以上。
- 包名、数据库与签名延续现有工程，可作为覆盖升级包；本次未执行手机升级。

## 警告分布

- DataExtractionRules：1
- GradleDependency：11
- ImplicitSamInstance：1
- ModifierParameter：1
- MonochromeLauncherIcon：1
- ObsoleteSdkInt：2
- UseKtx：1

## 尚未完成的验证

本机 Android 模拟器缺少硬件加速驱动。已尝试独立的无窗口软件模拟器与纯英文路径，软件模式仍未完成启动，故未获得新版原生页面截图。

未安装到用户手机；未执行 connectedDebugAndroidTest。训练端到端测试已更新：从首页主卡开始、完成一组、保存历史、检查容量，以及动作、工具、我的导航与截图代码均编译通过。不能把测试 APK 编译通过视为运行通过。

仍需设备检查：小屏 / 大字体下的主卡和页头、键盘与系统手势区、五页滚动、照片读取、HIIT 后台和通知行为。

## 复现构建

```powershell
.\android\Build-Local.ps1 -JdkPath '<JDK17>' -SdkPath '<SDK35>' -GradlePath '<Gradle8.11.1>' -Tasks ':app:testDebugUnitTest',':app:lintDebug',':app:assembleDebug',':app:assembleDebugAndroidTest'
```

保留项目原有签名文件，不提交签名私钥。设备测试会临时替换测试数据，遵守 android/README.md 中的专用设备与同意要求。
