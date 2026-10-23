package app.duolian.local

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.duolian.local.data.UpdateTransport
import app.duolian.local.domain.AppRelease
import app.duolian.local.domain.UpdatePolicy
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class AppUpdateState(
    val checking: Boolean = false,
    val release: AppRelease? = null,
    val dialog: Boolean = false,
    val manual: Boolean = false,
    val downloading: Boolean = false,
    val downloadedBytes: Long = 0,
    val file: File? = null,
    val preparingInstall: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val installer: Intent? = null
)

internal class AppUpdateViewModel(app: Application) : AndroidViewModel(app) {
    private val transport = UpdateTransport(BuildConfig.UPDATE_MANIFEST_URL)
    private val mutable = MutableStateFlow(AppUpdateState())
    val state = mutable.asStateFlow()
    private var lastAutomaticAttempt: Long? = null
    private var downloadJob: Job? = null

    fun check(manual: Boolean = false) {
        val before = mutable.value
        if (!manual && before.dialog) return
        if (before.downloading || before.preparingInstall || before.checking) {
            if (manual) mutable.update { it.copy(dialog = true, manual = true) }
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (!manual && lastAutomaticAttempt?.let { now - it < 60_000 } == true) return
        lastAutomaticAttempt = now
        mutable.update { it.copy(checking = true, manual = manual, dialog = manual || it.dialog, error = null, notice = null) }
        viewModelScope.launch {
            try {
                val release = transport.check()
                if (UpdatePolicy.isNewer(release, BuildConfig.VERSION_CODE.toLong(), Build.VERSION.SDK_INT)) {
                    mutable.update {
                        val sameFile = it.release?.versionCode == release.versionCode && it.release.sha256 == release.sha256
                        it.copy(checking = false, release = release, dialog = true,
                            file = if (sameFile) it.file else null, downloadedBytes = if (sameFile) it.downloadedBytes else 0)
                    }
                } else mutable.update {
                    it.copy(checking = false, release = null, file = null, dialog = it.manual,
                        notice = when {
                            release.minSdk > Build.VERSION.SDK_INT -> "暂无适用于当前手机系统的新版"
                            release.versionCode < BuildConfig.VERSION_CODE -> "当前版本 V${BuildConfig.VERSION_NAME}，暂无可安装的新版"
                            else -> "当前已是最新版本 V${BuildConfig.VERSION_NAME}"
                        })
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                mutable.update { it.copy(checking = false,
                    error = if (it.manual) "暂时无法检查更新。请确认手机和电脑连接同一 Wi-Fi，且电脑的下载服务正在运行。" else null) }
            }
        }
    }

    fun dismiss() { mutable.update { it.copy(dialog = false, manual = false) } }
    fun open() { mutable.update { it.copy(dialog = true, manual = true) } }
    fun download() {
        val release = mutable.value.release ?: return
        if (mutable.value.downloading || mutable.value.preparingInstall) return
        mutable.update { it.copy(downloading = true, downloadedBytes = 0, file = null, error = null) }
        downloadJob = viewModelScope.launch {
            try {
                var lastProgress = 0L
                val file = transport.download(release, File(getApplication<Application>().cacheDir, "updates")) { bytes ->
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastProgress >= 150 || bytes == release.size) {
                        lastProgress = now; mutable.update { it.copy(downloadedBytes = bytes) }
                    }
                }
                mutable.update { it.copy(file = file, downloading = false, downloadedBytes = release.size) }
            } catch (e: CancellationException) { mutable.update { it.copy(downloading = false) }; throw e }
            catch (_: Exception) {
                mutable.update { it.copy(downloading = false, error = "下载未完成或文件校验未通过，请检查网络后重新检查更新。") }
            }
        }
    }

    fun cancelDownload() { downloadJob?.cancel() }
    fun prepareInstall() {
        val before = mutable.value
        val file = before.file ?: return
        val release = before.release ?: return
        if (before.preparingInstall || before.downloading) return
        mutable.update { it.copy(preparingInstall = true, error = null) }
        viewModelScope.launch {
            try {
                val intent = withContext(Dispatchers.IO) { UpdateInstaller.verifiedIntent(getApplication(), file, release) }
                mutable.update { it.copy(preparingInstall = false, installer = intent) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                mutable.update { it.copy(preparingInstall = false, file = null, error = e.message ?: "无法准备安装，请重新下载") }
            }
        }
    }
    fun consumeInstaller() { mutable.update { it.copy(installer = null) } }
    fun installFailed() { mutable.update { it.copy(error = "未能打开系统安装页面，可稍后重试。") } }
    fun permissionDeclined() { mutable.update { it.copy(error = "安装尚未开始。可点击“安装更新”，按系统提示允许后继续。") } }
}
