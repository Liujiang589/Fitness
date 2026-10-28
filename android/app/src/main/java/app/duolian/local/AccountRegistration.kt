package app.duolian.local

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Preparation UI only. No identity is created until the real backend and per-user storage exist.
 * Do not enable these actions with a BuildConfig flag or a client-side verification code.
 * Phone numbers and codes are deliberately not collected, saved, logged or backed up in this build.
 */
@Composable internal fun AccountRegistrationDialog(dismiss: () -> Unit) {
    var registering by rememberSaveable { mutableStateOf(false) }
    var explanation by rememberSaveable { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(
        usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
        dismissOnClickOutside = false
    )) {
        Surface(Modifier.fillMaxSize(), color = Page) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = dismiss, modifier = Modifier.testTag("account-close")) { Text("返回") }
                    Text("独练账号", Modifier.weight(1f).padding(horizontal = 8.dp),
                        style = MaterialTheme.typography.titleLarge)
                    Text("准备中", Modifier.padding(end = 12.dp), color = Muted,
                        style = MaterialTheme.typography.labelMedium)
                }
                LazyColumn(Modifier.weight(1f).testTag("account-page"),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(58.dp).background(PaleBlue, RoundedCornerShape(19.dp)),
                                contentAlignment = Alignment.Center) {
                                Text("独", color = Blue, style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold)
                            }
                            Text(if (registering) "从这里，建立你的账号。" else "欢迎回到独练。",
                                style = MaterialTheme.typography.headlineSmall, color = Ink)
                            Text("按自己的节奏，记录每一次进步。", color = Muted,
                                style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().background(PaleBlue, RoundedCornerShape(16.dp)).padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            AccountModeButton("登录", !registering, Modifier.weight(1f)) { registering = false }
                            AccountModeButton("注册", registering, Modifier.weight(1f)) { registering = true }
                        }
                    }
                    item {
                        Panel {
                            Text("手机号${if (registering) "注册" else "登录"}", color = Ink,
                                fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                            Text("账号服务暂未开放。当前页面仅供预览，不会收集手机号或发送验证码。",
                                color = Muted, style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(value = "", onValueChange = {}, enabled = false,
                                label = { Text("手机号") }, prefix = { Text("+86  ") },
                                placeholder = { Text("开通后填写手机号") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                modifier = Modifier.fillMaxWidth().testTag("account-phone"),
                                shape = RoundedCornerShape(14.dp))
                            OutlinedTextField(value = "", onValueChange = {}, enabled = false,
                                label = { Text("短信验证码") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                                modifier = Modifier.fillMaxWidth().testTag("account-code"),
                                shape = RoundedCornerShape(14.dp))
                            OutlinedButton(onClick = {}, enabled = false,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("account-send"),
                                shape = RoundedCornerShape(14.dp)) { Text("验证码服务待开通") }
                            Button(onClick = {}, enabled = false,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("account-submit"),
                                shape = RoundedCornerShape(14.dp)) {
                                Text(if (registering) "注册暂未开放" else "登录暂未开放")
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("其他登录方式", color = Muted, style = MaterialTheme.typography.labelLarge)
                            GroupCard {
                                IconSettingRow(8, Blue, "微信登录", "暂未开放 · 查看说明") { explanation = "微信" }
                                HorizontalDivider(color = Page)
                                IconSettingRow(8, Blue, "QQ 登录", "暂未开放 · 查看说明") { explanation = "QQ" }
                            }
                        }
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("现在也能安心记录", style = MaterialTheme.typography.titleMedium, color = Ink)
                            Text("无需注册即可继续训练。现有记录仍保存在这台手机，尚未归属任何在线账号，也没有上传云端。正式启用账号后，旧记录如何归入账号会由你选择。",
                                color = Muted, style = MaterialTheme.typography.bodyMedium)
                            Button(onClick = dismiss,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("account-local"),
                                shape = RoundedCornerShape(16.dp)) { Text("继续本机使用") }
                        }
                    }
                }
            }
        }
    }
    explanation?.let { provider ->
        AlertDialog(onDismissRequest = { explanation = null }, title = { Text("$provider 登录暂未开放") },
            text = { Text("独练还未接通$provider 的官方授权服务。本版本不会要求你输入$provider 密码，也不会创建在线账号。你可以返回继续使用本机训练记录。") },
            confirmButton = { TextButton(onClick = { explanation = null }) { Text("知道了") } })
    }
}

@Composable private fun AccountModeButton(label: String, selected: Boolean, modifier: Modifier, click: () -> Unit) {
    // Standard button semantics and wrapping labels remain readable at large system font sizes.
    TextButton(onClick = click, modifier = modifier.heightIn(min = 48.dp).testTag("account-mode-$label"),
        shape = RoundedCornerShape(13.dp), colors = ButtonDefaults.textButtonColors(
            containerColor = if (selected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) Blue else Muted)) {
        Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}
