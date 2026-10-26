# 独练人体基础模型

作者：Quaternius。作品：Universal Base Characters Kit（Standard/FREE），Superhero Male。
许可：CC0 1.0 Universal。完整原始许可见 QUATERNIUS-LICENSE.txt；同一许可也随应用和交付包分发。

- 作者页面：https://quaternius.com/packs/universalbasecharacters.html
- 作者下载页：https://quaternius.itch.io/universal-base-characters
- GLB 分发来源：https://raw.githubusercontent.com/programasweights/avatar/main/public/assets/character.glb
- 分发说明：https://github.com/programasweights/avatar/blob/main/ASSETS.md
- 分发许可：https://raw.githubusercontent.com/programasweights/avatar/main/public/assets/QUATERNIUS-LICENSE.txt
- 获取日期：2026-10-26。
- 原始GLB SHA-256：`d6f3b64cab629f6ddca9ba063811aeffd3663a8a4e49ade47cd9505dc2bbd70f`。

保留原始人体、骨骼与权重。独练自行实现姿态重定向、手指弯曲、器械、训练服色彩、分区和实时绘制，未使用分发项目的代码或动画，也未使用其他健身应用的素材。

运行 `python tools/prepare_athlete.py --source assets/models/athlete-source.glb --output android/app/src/main/resources/models/athlete.bin.gz` 可确定性生成运行时资源。转换脚本依赖 NumPy（仅开发时）；应用不依赖 Python、GLB加载器或模型下载。

运行时包含65个骨骼、8,725个顶点、14,790个三角形，251,993字节压缩资源。转换时在衣裤和鞋的边缘切分三角形，并插值权重，避免锯齿。所有姿态由应用已有40个动作的关节数据驱动。
