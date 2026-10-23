package app.duolian.local

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import app.duolian.local.data.UpdateTransport
import app.duolian.local.domain.AppRelease
import java.io.File
import java.security.MessageDigest

class UpdateFileProvider : FileProvider()

internal object UpdateInstaller {
    @Suppress("DEPRECATION")
    fun verifiedIntent(context: Context, file: File, release: AppRelease): Intent {
        val directory = File(context.cacheDir, "updates").canonicalFile
        require(file.canonicalFile.parentFile == directory && file.isFile && file.length() == release.size &&
            UpdateTransport.sha256(file).equals(release.sha256, true)) { "更新文件已失效，请重新下载" }
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(file.absolutePath, flags) ?: error("无法识别更新安装包")
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun code(info: PackageInfo) = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        fun signers(info: PackageInfo): Set<String> {
            val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
            return signatures.orEmpty().map {
                UpdateTransport.hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray()))
            }.toSet()
        }
        require(archive.packageName == context.packageName && code(archive) == release.versionCode &&
            code(archive) > code(installed) && archive.versionName == release.version) { "更新安装包版本不符，请重新检查" }
        require((archive.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= Build.VERSION.SDK_INT) { "更新包不支持当前手机系统" }
        require(signers(installed).isNotEmpty() && signers(archive) == signers(installed)) {
            "更新包签名不符，已停止安装"
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
