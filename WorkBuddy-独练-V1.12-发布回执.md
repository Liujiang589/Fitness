# 独练 V1.12 发布回执（WorkBuddy）

- 发布时间：2026-10-23 18:12（GMT+8）
- 发布方式：WorkBuddy 应用发布（sites），沿用原「独练下载页」应用，域名前缀 `duolian15`，**访问地址与 sharecode 未变**
- 站点源目录（用户已重命名为）：`C:\Users\阿三大苏打实打实\Desktop\独练-V1.12-发布包`（发布时目录名为 `独练-V1.5-发布包`，发布完成后被重命名，后续再发布请用新目录名）

## 一、访问地址

| 项目 | 地址 |
| --- | --- |
| 官网/分享地址（含 sharecode） | https://duolian15.app.workbuddy.host/?sharecode=N6M3DJP2HU |
| 版本清单 latest.json | https://duolian15.app.workbuddy.host/latest.json?sharecode=N6M3DJP2HU |
| 二维码图片 | https://duolian15.app.workbuddy.host/qr-download.png?sharecode=N6M3DJP2HU |

## 二、下载地址（公网直链，均带 sharecode）

| 文件 | 公网下载地址 |
| --- | --- |
| 独练-V1.12.apk | https://duolian15.app.workbuddy.host/%E7%8B%AC%E7%BB%83-V1.12.apk?sharecode=N6M3DJP2HU |
| 独练-V1.12-安装包.zip | https://duolian15.app.workbuddy.host/%E7%8B%AC%E7%BB%83-V1.12-%E5%AE%89%E8%A3%85%E5%8C%85.zip?sharecode=N6M3DJP2HU |

页面上的两个下载按钮使用相对路径（`独练-V1.12.apk`、`独练-V1.12-安装包.zip`），并由页面脚本自动把当前 URL 的 `?sharecode=...` 拼到两个链接上，因此从分享链接进入后点按钮等同于上面的带 sharecode 直链。

## 三、二维码

- 二维码对应 URL：`https://duolian15.app.workbuddy.host/?sharecode=N6M3DJP2HU`（公网可访问，非 127.0.0.1 / 192.168.x.x）
- 线上二维码图片与本地生成文件逐字节一致，SHA-256：`45fb553fa2b3fc050c4906b7f8e9cab7042dcbc46fc984e38bb5066e948f86ba`

## 四、公网校验结果（从公网重新拉取，非本地预览）

| 校验项 | HTTP | Content-Type | 字节数 | SHA-256 | 结论 |
| --- | ---: | --- | ---: | --- | --- |
| 首页 index.html | 200 | text/html | 10,475 | — | 版本显示 V1.12 · versionCode 26，两个按钮 href 分别为 APK / ZIP 相对路径，二维码指向本页 |
| 独练-V1.12.apk | 200 | application/vnd.android.package-archive | 70,765,659 | `e738162d2c104cb2b5d57b54ebfd37b10a58758dd4ac12523df842dac384c4b0` | 与交接文档一致，与本地交付文件 cmp 逐字节一致 |
| 独练-V1.12-安装包.zip | 200 | application/zip | 35,592,533 | `b7d3055255392c1ef05ef1c20a80701b473a2b3a1e6c13bf545b3e2e81d74360` | 与交接文档一致，与本地交付文件 cmp 逐字节一致 |
| latest.json | 200 | application/json | 811 | — | version 1.12 / versionCode 26 / size 70765659 / packageName app.duolian.local / minSdk 26 / sha256 与真实 APK 一致，downloadUrl 为带 sharecode 的可访问公网直链 |
| qr-download.png | 200 | image/png | 2,009 | `45fb553f…86ba` | 与本地生成文件一致 |

## 五、站点同步内容

1. 部署了真实 V1.12 APK 与 ZIP；页面版本、包大小、文件名、更新说明、SHA-256 已同步；沿用原布局与分享入口，未另起局域网或短期隧道。
2. 主按钮直接下载 APK，压缩包为独立「资料下载」按钮；APK MIME 为 `application/vnd.android.package-archive`。
3. 分享码机制保留：APK、ZIP、latest.json、二维码全部保持所需 sharecode。
4. 更新说明按交接第 4 条写入：近七天回顾；生熟重量筛选、最近份量复用、每份标签换算；保留高清图并改善界面刷新、解码与滚动负担；保留训练、动作示范、高德路线和旧数据。
5. 已生成 `latest.json`（见上表）；页面明确写明「本轮只更新分发网站，应用内置更新地址尚未指向本页」，未声称应用内自动更新已打通。
6. 页面含微信限制提示：如被微信限制，请用系统浏览器下载并在文件管理器打开 APK；提示先备份、覆盖安装、不卸载旧版。
7. 站点已移除旧版 `Duolian-V1.5.apk`，避免旧包与新页并存造成误下载。

## 六、备注

- 本回执不含任何账号 Token、密钥或登录凭证。
- 真机流畅度数据（旧版 3.04% 超时帧、新版 0.48%，P95 20ms→10ms）为同机现场采样结果，页面与回执均未作普适性宣传。
