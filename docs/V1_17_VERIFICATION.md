# 独练 V1.17 验证记录

2026-10-26。versionName1.17 / versionCode31，Android8.0以上，原签名Release优化变体。

## 最终实现

完整CC0骨骼人体替代程序人偶；65骨骼、8,725顶点、14,790人体三角形，运行时资源251,993字节。身体与五官、手指为同一导入模型；深色短裤/鞋，体表肌群渐变，圆柱握杆和有厚度杠铃片、矩形训练凳。衣裤下摆切分三角形与权重插值，握持时弯指，地面动作调整手掌朝向。

人体基础作者Quaternius，Universal Base Characters / Superhero Male，CC0 1.0。原始许可保存在assets/models与APK的models/QUATERNIUS-LICENSE.txt，模型来源在动作说明中可见；交付包保留许可与来源文档。没有使用其他健身应用素材。

## 检查结果

- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest、assembleRelease通过（V1.17-delivery-check.log）。
- 229项JVM全部通过，失败/错误/跳过0；下载HTTP13项通过。
- 模型检查覆盖40动作、三种姿态的有限坐标/单位法线/目标肌群覆盖，五个视角的范围与深度，前后腿肌顺序及动态移动。新增资源细节、衣裤/鞋、顶点肌群与短裤颜色独立于目标肌群的检查。
- 资源加载检查格式、数量、骨骼拓扑、索引、权重范围/归一化和流结束。离线生成后复核40动作的三阶段画面；修整短裤边缘与俯卧撑手掌。
- 预览为应用相同Kotlin模型/投影代码离线渲染，60源帧合并为47编码帧，1440×620、5.4秒循环，展示卧推/深蹲/弯举。不是手机录屏。
- Lint {'Warning': 24, 'Information': 6}，没有Error/Fatal；设备检查仅编译，本轮没有执行真机UI、覆盖安装或帧率验收。桌面离线运行时间不能作为手机帧率证据。
- APK1.17/31、app.duolian.local、非debuggable；86个名称资源均为独练。apksigner、原签名证书、zipalign、高德配置与R8输出检查通过；完整模型及许可与源码资源逐字节相同，确认已打入Release APK。
- V1.16原APK摘要未改变。训练、营养、GPS持久化与备份格式未改，数据库schema1、备份v4沿用。

## 交付

APK 71,041,681字节，SHA-256 `e1cc8eaa372037e7161cff81c8b1b86915bf16364ae60f16cd94bab0386a7b51`。ZIP共8个文件：APK、GIF、模型说明、验证记录、安装说明、模型来源、模型许可与SHA256SUMS。ZIP逐字节核对、CRC和摘要检查。

用户此前选择先取安装包；本轮直接交付文件，不等待手机连接。建议先备份，再原签名覆盖安装，不先卸载。公网下载站未重新发布，应用内更新URL未切换。

实现与使用见[V1_17_MODEL.md](V1_17_MODEL.md)。
