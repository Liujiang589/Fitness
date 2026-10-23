package app.duolian.local

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

@Composable internal fun AppUpdateHost(vm: AppUpdateViewModel, state: AppUpdateState, canPrompt: Boolean) {
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.check() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (context.packageManager.canRequestPackageInstalls()) vm.prepareInstall() else vm.permissionDeclined()
    }
    val install = {
        if (context.packageManager.canRequestPackageInstalls()) vm.prepareInstall()
        else runCatching {
            permission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()))
        }.onFailure { vm.installFailed() }
        Unit
    }
    LaunchedEffect(state.installer) {
        state.installer?.let { intent ->
            vm.consumeInstaller()
            runCatching { context.startActivity(intent) }.onFailure { vm.installFailed() }
        }
    }
    if (state.dialog && (state.manual || canPrompt)) AppUpdateDialog(state, vm::dismiss,
        check = { vm.check(true) }, download = vm::download, cancel = vm::cancelDownload, install = install)
}

@Composable internal fun AppUpdateDialog(state: AppUpdateState, dismiss: () -> Unit, check: () -> Unit,
    download: () -> Unit, cancel: () -> Unit, install: () -> Unit) {
    val release = state.release
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(if (release == null) "检查更新" else "发现新版本 V${release.version}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.checking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("正在检查新版本…")
                } else if (release != null) {
                    Text("当前 V${BuildConfig.VERSION_NAME}  →  V${release.version} · ${"%.1f".format(release.size / 1024.0 / 1024.0)} MB",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    Text("这次更新", fontWeight = FontWeight.SemiBold)
                    (release.releaseNotes.ifEmpty { listOf("优化使用体验，改进稳定性。") }).forEach {
                        Text("• $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    when {
                        state.downloading -> {
                            val progress = (state.downloadedBytes.toFloat() / release.size).coerceIn(0f, 1f)
                            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                            Text("正在下载 ${(progress * 100).toInt()}%", color = Blue)
                            TextButton(onClick = cancel) { Text("取消下载") }
                        }
                        state.preparingInstall -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("正在准备安装…") }
                        state.file != null -> Text("下载完成，点击安装并按手机系统提示确认。", color = Blue)
                    }
                    Text("覆盖更新会保留训练记录，无需卸载旧版。", color = Muted, style = MaterialTheme.typography.bodySmall)
                } else if (state.notice != null) Text(state.notice)
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when {
                state.checking || state.downloading || state.preparingInstall -> Unit
                state.error != null && state.file == null -> TextButton(onClick = check) { Text("重新检查") }
                state.file != null -> TextButton(onClick = install) { Text("安装更新") }
                release != null -> TextButton(onClick = download) { Text("下载更新") }
                else -> TextButton(onClick = dismiss) { Text("知道了") }
            }
        },
        dismissButton = { if (release != null || state.checking || state.error != null)
            TextButton(onClick = dismiss) { Text(if (state.downloading) "收起" else "稍后") } })
}
