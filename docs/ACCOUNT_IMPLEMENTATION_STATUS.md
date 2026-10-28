# 独练账号：实现进度与阻塞点

更新：2026-10-29。对应 APK 尚未重新打包发布，原因见文末「为什么先不发版」。

## 已完成（不依赖任何平台资质）

| 模块 | 内容 | 验证 |
|---|---|---|
| `domain/Account.kt` | 手机号规范化；身份=（类型, 规范化值）；同一身份才同账号；显式绑定/解绑（最后一个身份不可解绑）；验证码 5 分钟有效、60 秒重发、5 次尝试、每小时 5 次/每天 10 次；加盐 SHA-256 存储、一次性消费；会话 30 分钟 + 30 天刷新 | `AccountTest` 15 项 |
| `domain/AccountScope.kt` | 每个账号独立数据库文件名（uid 哈希，不暴露服务端 ID）；本机使用沿用 `duolian.db`；迁移前判断：本机为空则无需迁移、账号已有记录则拒绝自动合并、仅空账号可迁入 | `AccountScopeTest` 13 项 |
| `domain/AccountSessionRecord.kt` | 只持久化 uid + 刷新令牌 + 过期时间；损坏或被篡改的存储视为未登录 | 上述测试内覆盖 |
| `data/ScopedDatabase.kt` | 按 scope 打开/关闭 Room 数据库，切换账号前先关闭上一个 | 编译 + 仪器测试 |
| `data/AccountSessionStore.kt` | DataStore 保存会话（不含资料、不含训练数据） | 编译 |
| `data/AccountMigrator.kt` | 先写备份文件，再把本机记录 restore 进账号库；拒绝合并到非空账号 | 编译 + 领域测试 |
| `DuolianApp` | 暴露 `scopedDatabase` / `sessionStore` / `migrator` / `repositoryFor(scope)` | 编译 |
| `androidTest/AccountIsolationTest.kt` | A 账号写入后 B 账号与游客库都读不到；覆盖 session/food/body | 编译通过，**需真机执行** |

全量 JVM 测试：**304 项通过**。`assembleDebugAndroidTest`、`assembleRelease`、`lintDebug` 均通过；Release APK 72,370,643 字节。

## 仍然阻塞（需要平台资格）

| 能力 | 阻塞原因 | 解锁条件 |
|---|---|---|
| 手机号验证码 | 腾讯云、阿里云均已停止个人自用短信资质 | 企业认证，或真实企业授权「他用」资质 |
| 微信登录 | 移动应用需开发者资质认证（企业主体） | 企业主体 + 付费认证 + 创建移动应用 |
| QQ 登录 | 移动应用审核要营业执照（加盖公章） | 营业执照 + 应用审核 |
| 真实短信通道 | 需签名与模板报备 | 上述主体 + 签名审核 |

没有这些条件时，任何「能发验证码」「能微信登录」的实现都只能是假通道；本仓库不做。

## 下一步（按你选择的路线）

- **选 A（办执照）**：我继续写服务端（Node/Kotlin 均可，自托管）、provider 适配层（短信/微信/QQ 可插拔）、接口契约测试与本地联调脚本；你拿到 AppID/签名后只回填配置。
- **选 B/C/D**：只保留已完成的账号核心代码，不再扩展。
- 无论哪条，UI 的登录按钮在你确认前保持禁用。

## 为什么先不发版

1. 登录尚不可用，此时发 APK 与 1.26 界面完全一致，只会制造一个「看起来更新了」的版本；
2. 版本号由 Codex 侧维护（1.26 / versionCode 40），我不抢占版本；
3. 已构建的 Release APK 仅作为验证产物存在 `android/app/build/outputs/apk/release/`，未改名、未放入根目录、未上传。

## 复现验证

```powershell
cd android
gradle.bat --no-daemon --console=plain :app:testDebugUnitTest
gradle.bat --no-daemon --console=plain :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug
```
