package app.duolian.local.domain

import java.net.URI
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable data class AppRelease(
    val version: String,
    val versionCode: Long,
    val size: Long,
    val sha256: String,
    val downloadUrl: String,
    val packageName: String = "app.duolian.local",
    val releaseNotes: List<String> = emptyList(),
    val minSdk: Int = 26
)

object UpdatePolicy {
    const val MAX_APK_BYTES = 200L * 1024 * 1024
    const val MAX_MANIFEST_BYTES = 64 * 1024
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String, manifestUrl: String): AppRelease =
        json.decodeFromString<AppRelease>(text).also { validate(it, manifestUrl) }

    fun validate(release: AppRelease, manifestUrl: String) {
        val manifest = trustedUrl(manifestUrl)
        val download = trustedUrl(release.downloadUrl)
        require(download.scheme == manifest.scheme && download.host == manifest.host &&
            download.port == manifest.port) { "更新下载地址与更新服务不一致" }
        require(download.path.endsWith(".apk") && download.rawQuery == null) { "更新文件地址无效" }
        require(release.packageName == "app.duolian.local") { "更新包不属于独练" }
        require(release.version.matches(Regex("[0-9]{1,5}\\.[0-9]{1,5}(\\.[0-9]{1,5})?"))) { "更新版本格式无效" }
        require(release.versionCode in 1..Int.MAX_VALUE.toLong()) { "更新版本编号无效" }
        require(release.size in 1..MAX_APK_BYTES) { "更新包大小无效" }
        require(release.sha256.matches(Regex("[a-fA-F0-9]{64}"))) { "更新校验信息无效" }
        require(release.minSdk in 26..100 && release.releaseNotes.size <= 12 &&
            release.releaseNotes.all { it.isNotBlank() && it.length <= 600 }) { "更新说明无效" }
    }

    /** Compare Android build codes, never decimal version names such as 1.9 and 1.10. */
    fun isNewer(release: AppRelease, installedCode: Long, sdk: Int) =
        release.versionCode > installedCode && release.minSdk <= sdk

    fun trustedUrl(value: String): URI {
        val uri = URI(value)
        require(uri.host != null && uri.userInfo == null && uri.fragment == null &&
            uri.port in -1..65535 && uri.port != 0) { "更新服务地址无效" }
        val parts = uri.host.split('.')
        val local = parts.mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 0..255 } }
        val privateIp = parts.size == 4 && local.size == 4 && (local[0] == 10 || local[0] == 127 ||
            (local[0] == 192 && local[1] == 168) || (local[0] == 172 && local[1] in 16..31))
        require(uri.scheme == "https" || (uri.scheme == "http" && privateIp)) { "更新服务需要HTTPS或本地网络地址" }
        return uri
    }
}
