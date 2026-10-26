# 独练 V1.18 验证记录

2026-10-26。versionName1.18 / versionCode32，app.duolian.local，Android8.0以上，原签名Release优化变体。

## 修复与复现

MotionEngine中徒手深蹲的肘部导向由外侧改为接近矢状面、轻微屈肘的前伸姿态；AthleteBody在这个动作中让上臂使用独立于躯干前倾的参考方向，减少前臂与掌向之间的扭转。

- 修复前运行新增姿态测试，230项中只有新测试失败，报告“elbow should only bend softly”（V1.18-regression-before.log）。
- 修复后231项JVM通过，失败/错误/跳过均0。新增姿态检查覆盖61阶段、左右上肢；新增网格检查覆盖4种下蹲深度的前臂坐标与法线稳定性。
- 原有40动作、坐标/骨长、单位法线、肌群、多个视角、完整资源、衣裤颜色与动态投影检查继续通过。
- verifyStreetMapConfiguration、testDebugUnitTest、lintDebug、assembleRelease通过（V1.18-delivery-check.log）。Lint {'Warning': 24, 'Information': 6}，没有Error/Fatal。
- 对同一完整人体修复前/后的斜侧画面制作960×620对比图；逐阶段查看侧面、斜侧、正面。GIF为60源帧合并成47编码帧，1440×620、5.4秒循环，使用与应用相同的模型和投影代码。
- APK1.18/32、非debuggable，86个名称资源均为独练；apksigner、原签名证书、zipalign、R8输出与高德Key配置检查通过。模型/许可与源码资源逐字节相同，确认包含在Release APK。
- V1.17 APK保留原SHA-256。GPS、训练记录、营养与数据库/备份格式本轮未改。

## 交付与边界

APK 71,041,681字节，SHA-256 `1eda6edf735e4ce3e55c1c039ffd3c82a46512031a280140a726d146da8ee421`。

ZIP包含APK、三视角GIF、前后对比PNG、修复说明、验证记录、安装说明、模型来源、模型许可和SHA256SUMS，共9个文件；逐字节与CRC校验。原签名覆盖升级，建议先备份。

预览为离线渲染，尚未完成V1.18真机显示/控制/帧率验收。本轮未运行设备UI测试，未重新发布公网站或切换应用内更新URL。APK和ZIP放在原项目目录。

使用见[V1_18_ARMS.md](V1_18_ARMS.md)。
