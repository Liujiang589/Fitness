# 独练 V1.21 验证记录

2026-10-26。versionName1.21 / versionCode35，app.duolian.local。

## 改动与证据

- 焦点管理器移到Dialog内容中读取，顶部返回/取消、添加动作与底部提交使用当前编辑窗口的焦点树。
- 已选方案摘要放到折叠区域外；动作编排说明根据NONE、PAIRS、ALL分别显示。
- PlanEditorInput在保存前统一解析与校验文本，原有Rules.plan作为最终规则继续调用。生成Plan复制动作和星期列表，保留原ID、动作顺序与全部进阶字段。
- 可保存的动作/星期状态使用List与listSaver，移除两项MutableCollectionMutableState警告。

## 已完成检查

- testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest、assembleRelease通过，V1.21-build.log显示BUILD SUCCESSFUL。
- 249项JVM通过，失败/错误/跳过均0。新增10项PlanEditorInput测试：粘贴空格与顺序；所有训练方案/编排组合与原ID；名称边界；空/重复/删除/过量动作；组数边界/小数/溢出；零次数及上下限；休息沿用/边界；超级组至少两个动作；ISO排期合法性；提交后不持有可变草稿列表。
- 计划编辑器8项设备测试和首页导航回归测试编译通过。本版新补充当前弹窗焦点清理及草稿状态恢复测试，尚未在设备运行。
- Lint有27项Warning、6项Information，无Error/Fatal；较V1.20减少两项集合状态警告。
- 正式APK为1.21/35、非debuggable，86个名称资源均为独练。签名证书与旧版相同，apksigner、zipalign、R8、高德配置和模型/许可逐字节核对通过。正式Manifest不包含开发预览Activity。
- V1.20历史APK SHA-256保持`6901002775caf8fc0a8d5163ecb46e8d47f2612d944913e697d80b40ea1be108`。

## 未完成的界面验收

ADB未发现连接手机；没有安装V1.21到用户手机。尝试启动原有本地测试AVD，普通及软件渲染启动均退出，未获得在线设备。因此本轮没有运行设备UI测试，没有新界面截图，未确认小屏/大字体、键盘遮挡和旋转的实际表现。单元测试只证明纯输入与业务规则部分，不替代界面验收。

## 交付

APK 71,103,621字节，SHA-256 `b207c2fe470abe08e973361f9a886461564812ae705ba499b5d4f40790edb2de`。

独练-V1.21-安装包.zip含APK、使用说明、验证记录、安装说明、模型来源、模型许可和SHA256SUMS，共7项，CRC与逐字节校验通过。独立APK与ZIP放在原项目目录；不需要解压ZIP才能使用独立APK。

本轮没有改变更新URL、数据格式或发布公网网站。本地下载页版本和校验值随新包更新。使用见[V1_21_PLANS.md](V1_21_PLANS.md)。
