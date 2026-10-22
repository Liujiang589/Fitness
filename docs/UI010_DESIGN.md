# 独练 0.10.0 · 视觉与交互升级

日期：2026-10-22。基于源码 0.9.0 / versionCode 11，升级至 0.10.0 / 12。

## 设计方向

炭黑 #20261F、暖白 #F5F5F0、荧光黄绿 #D5F578；正文与交互用深绿 #466329，确保浅色背景上的可读性。24–28 dp 圆角、细边框和有节制的留白；训练主卡、训练中的计时卡和个人摘要使用深色，以区分主任务与辅助内容。

## 参考与取舍

- [Nike Training Club](https://www.nike.com/ntc-app)：训练内容与课程入口的展示。借鉴主训练卡与明确的开始操作。
- [Hevy](https://www.hevyapp.com/)：训练计划、逐组记录、训练历史与个人纪录。保留独练已有的真实记录流程。
- [Fitbod](https://fitbod.me/)：训练计划、进步与恢复信息。保留已有的负荷、肌群和平台期诊断，展示真实记录计算结果。

参考用于信息组织和交互取舍，没有引入第三方界面素材或品牌标志。

## 页面变化

1. 训练：首页优先展示今日排期；若无排期，展示现有第一份计划；无计划则进入创建表单。接着是本周七日打卡和训练次数、容量、连续天数。自建计划、模板、后续排期和营养依次下移。
2. 训练中：高对比计时、完成组数、训练容量、休息和完成进度；保留暂停、逐组编辑、热身、递减组、RPE、超级组、结束保存等操作。
3. 动作：全宽列表取代左侧分类加双列小卡，支持动作名、肌群、器械搜索；肌群、器械和收藏可组合筛选；保留用户照片、详情和编辑。
4. 工具：统一四宫格入口的图标、间距和箭头；原有组间休息、HIIT、1RM、杠铃片与表现排行保留。
5. 历史：新增近 30 天训练和容量摘要，保留日历、诊断、负荷图、身体与营养统计、个人纪录及记录明细。
6. 我的：深色个人摘要、统一设置分组与空状态。备份导入导出保留。
7. 导航与表单：深色五项导航、黄绿选中态，使用 Tab 语义；适配系统状态栏与底部手势区。返回训练页会回到训练首页；饮食页支持系统返回。计划星期选择支持横向滚动。

所有统计来自已有本地数据；无记录时显示 0 或明确空状态，没有演示成绩。

## 原创图像

- 使用内置 image_gen 生成；下方为最终生成提示词。
- 项目资产：[training_editorial.webp](../android/app/src/main/res/drawable-nodpi/training_editorial.webp)，1536 × 1024，91,764 字节。
- 图像在 APK 内离线加载，无外部媒体请求。
- 原始图保留在生成工具默认目录；项目使用压缩 WebP，不依赖默认目录。

```text
Create a premium editorial fitness photography asset for an Android strength-training app. Landscape 3:2 crop. A focused athletic East Asian woman in charcoal sportswear preparing a single heavy dumbbell in an upscale brutalist gym, three-quarter portrait from waist up, athlete occupies the RIGHT HALF, LEFT HALF is deep near-black empty negative space for UI text. Authentic athletic build, natural anatomy and hands, subtle sweat, strong directional window light, cinematic black-and-white photography with very muted olive highlights, textured concrete background, photographic grain, refined luxury sports campaign art direction. No text, no logo, no graphics, no watermark, no UI, no borders. The image should remain readable at mobile card scale.
```

## 验证与边界

最终验证结果见 [UI010_VERIFICATION.md](UI010_VERIFICATION.md)。Android 模拟器缺少硬件加速驱动，软件模式未完成启动，因此未取得新版原生截图，也未安装到用户手机。训练端到端测试已更新为新版入口和文本，并编译测试 APK；未在设备执行。

数据库结构、包名与备份格式不变。升级兼容性以签名证书比对为准；不执行卸载、清除数据或自动升级手机。

