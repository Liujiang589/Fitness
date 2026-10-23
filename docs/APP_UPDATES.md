# 应用内更新（V1.5起）

## 用户流程

1. V1.4及更早版本需要先手动覆盖安装V1.5一次。旧安装包没有检查更新代码，不能自行获得这项功能。
2. V1.5进入前台时检查更新，同一进程内60秒限流，防止从权限页面返回时重复检查。
3. 有新版时显示版本、包大小和更新说明。默认不会自动下载，可选择稍后；有进行中的训练或其他编辑弹窗时，自动提示暂缓显示。
4. “我的 → 关于 → 检查更新”可随时手动检查，也可找回下载进度与已下载的安装包。
5. 用户点击下载，完成后点击安装。首次从独练安装更新可能需要在系统页面允许此来源安装；返回后继续校验并打开系统安装界面。用户取消授权或安装后仍可稍后重试。
6. 安装由手机系统确认，不静默安装、不卸载旧版；正常覆盖安装保留原训练数据。

自动检查网络失败时不弹错误。手动检查失败会说明同Wi-Fi及电脑服务条件；检查和下载不占用训练记录的操作锁。未接入账号、训练数据上传或分析SDK，更新请求只读取发布信息和APK。

## 当前局域网部署

用户已选择先使用现有局域网入口：`http://192.168.224.217:8765/latest.json`。

手机和电脑必须在可互通的同一Wi-Fi内，电脑保持开机且下载服务运行；访客网络隔离等可能阻止连接。此版本没有公网更新能力，外地用户无法通过该地址更新。建议在路由器为电脑保留这个IP。

`tools/Start-Download.ps1`启动服务。`tools/update-notes.json`按版本保存更新说明，服务将其与最新APK的版本、内部编号、大小、SHA-256、下载URL、包名和minSdk合并到`/latest.json`。说明变化无需重启；本次服务代码升级需要重启一次。

发布新版时，递增`versionName`和`versionCode`，沿用同一签名，填写更新说明并构建。验证成功后，将APK发布到根目录对应`独练-V版本.apk`，客户端才会发现新版本。没有发布APK时服务返回503，不回退提供旧包。

以后迁移公网更新，需要准备固定HTTPS入口，并在`android/app/build.gradle.kts`的`UPDATE_MANIFEST_URL`中改为该地址，重新构建并覆盖安装。现有已安装的局域网版不会自动知道一个从未配置过的公网地址；迁移版需通过仍可用的旧局域网入口或手动发送APK交付。

## 实现与验证边界

- `domain/AppUpdates.kt`：发布信息校验，以内部versionCode判断新版，支持未来字段，不改变训练数据使用的严格Codec。
- `data/UpdateTransport.kt`：IO线程请求，连接8秒/读取15秒超时；发布信息上限64KiB、APK上限200MiB；禁止自动跟随重定向和跨源APK下载。
- 下载仅进入私有`cache/updates/`，校验长度与SHA-256后再改名；取消、损坏、截断或版本变化会清除临时文件。无断点续传；系统终止进程后需重新下载，缓存也可能被系统清理。
- `UpdateInstaller.kt`：再次检查文件、包名、版本和已安装应用的签名证书；使用专用FileProvider仅临时授予更新缓存的读取权限，最后由系统安装器完成实际签名验证与安装。
- `AppUpdateViewModel.kt`、`AppUpdateUi.kt`：独立更新状态，不阻塞本地训练；启动检查、手动检查、下载进度、取消及安装授权/重试。
- 增加INTERNET与REQUEST_INSTALL_PACKAGES。明文网络仅为当前局域网IP单独放行；其他网络默认HTTPS。
- 数据库、备份格式、首次自主创建流程、40动作动态示范及作者信息保持原有行为。

实现参考[Android安装来源授权](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls())、[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)、[网络安全配置](https://developer.android.com/privacy-and-security/security-config)。

单元和HTTP测试能够检查协议、完整性及错误处理；设备UI和安装器测试目前仅编译，**尚未在真机完成“旧版发现新版→下载→授权→覆盖安装”的完整验收**。实际构建、文件与下载结果见[V1_5_VERIFICATION.md](V1_5_VERIFICATION.md)。
