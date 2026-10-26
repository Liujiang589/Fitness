# 独练 V1.20 验证记录

2026-10-26。versionName1.20 / versionCode34，app.duolian.local。

## 问题与改动

代码检查发现：首页焦点按钮一律使用!busy，创建/查看计划也被无关后台操作禁用。旧PlanForm用AlertDialog容纳全部设置及动作列表，只展示筛选结果的前12项；调用异步vm.act后立即dismiss，不能等待真实保存结果。以上是本轮修正的可确认代码问题，没有将其宣称为用户手机每次点击无反应的唯一原因。

改用共享全屏PlanEditor；首页增加常驻创建入口；导航和开始训练分别处理忙碌状态。新保存接口等待Repository事务，并向编辑器传播失败；草稿、编辑原计划、取消确认与动作排序沿用原数据格式。

## 已完成检查

- testDebugUnitTest、lintDebug、assembleDebug、assembleDebugAndroidTest、assembleRelease全部通过，构建日志V1.20-build.log。
- 239项JVM通过，失败、错误、跳过均0，覆盖原有业务计算和规则。
- 新增6项计划编辑器设备测试，以及1项首页忙碌时仍可创建的回归测试；全部编译通过。覆盖首页进入编辑器、选择第40个动作、必填提示、异步保存失败/重试与重复提交、编辑保留专业字段及排序、取消确认。未在手机执行，不能计为通过。
- Lint有29项Warning、6项Information，无Error/Fatal。新增两项MutableCollectionMutableState提示对应可保存的ArrayList字段；交互以替换新列表更新，不原地更改已有状态集合。
- APK1.20/34、Android8.0以上、非debuggable；86个应用名称资源均为独练。apksigner原证书、zipalign、R8、现有高德配置及人体模型/许可逐字节检查通过。
- V1.19历史APK SHA-256保持不变。原签名可覆盖升级，不需要卸载旧版。正式APK不包含开发预览Activity。

## 真机与边界

准备安装Debug进行验证时，ADB返回device not found；手机连接断开。用户选择“先给我安装包”，因此本轮未在手机安装V1.20、未执行本轮设备交互测试或采集新界面图片。此前V1.19设备通过结果不计入V1.20。

本次不更改数据schema、备份版本或更新URL，不发布公网网站。创建页实际显示、大字体/键盘/旋转、点击保存与错误恢复仍待真机验收。

## 交付

APK 71,103,621字节，SHA-256 `6901002775caf8fc0a8d5163ecb46e8d47f2612d944913e697d80b40ea1be108`。

独练-V1.20-安装包.zip含APK、使用说明、验证记录、安装说明、模型来源、模型许可和SHA256SUMS，共7项，CRC和逐字节检查通过。另提供原目录独立APK，方便直接传输安装。

见[使用说明](V1_20_PLANS.md)。
