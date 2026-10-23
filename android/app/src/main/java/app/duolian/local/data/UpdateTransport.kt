package app.duolian.local.data

import app.duolian.local.domain.AppRelease
import app.duolian.local.domain.UpdatePolicy
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** No training data, identifier, analytics or account information is sent to the release server. */
class UpdateTransport(private val manifestUrl: String) {
    private fun connection(url: String): HttpURLConnection {
        UpdatePolicy.trustedUrl(url)
        return (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 15000
            instanceFollowRedirects = false
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
    }

    suspend fun check(): AppRelease = withContext(Dispatchers.IO) {
        val connection = connection(manifestUrl)
        try {
            require(connection.responseCode == 200) { "更新服务暂时不可用" }
            require(connection.contentLengthLong <= UpdatePolicy.MAX_MANIFEST_BYTES) { "更新信息过大" }
            val text = connection.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(out.size() + n <= UpdatePolicy.MAX_MANIFEST_BYTES) { "更新信息过大" }
                    out.write(buffer, 0, n)
                }
                out.toByteArray().toString(Charsets.UTF_8)
            }
            UpdatePolicy.parse(text, manifestUrl)
        } finally { connection.disconnect() }
    }

    suspend fun download(release: AppRelease, directory: File, progress: (Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            UpdatePolicy.validate(release, manifestUrl)
            require(directory.isDirectory || directory.mkdirs()) { "无法创建更新目录" }
            val target = File(directory, "Duolian-${release.versionCode}.apk")
            val partial = File(directory, "download.part")
            // Only this private update cache is cleaned; never touch records or backup files.
            directory.listFiles()?.filter { it != target }?.forEach { if (it.isFile) it.delete() }
            val connection = connection(release.downloadUrl)
            var published = false
            try {
                require(connection.responseCode == 200) { "无法下载更新，请重新检查版本" }
                val advertised = connection.contentLengthLong
                require(advertised == -1L || advertised == release.size) { "版本已变化，请重新检查更新" }
                val hash = MessageDigest.getInstance("SHA-256")
                var total = 0L
                connection.inputStream.use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(32768)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        total += n
                        require(total <= release.size) { "更新文件大小不符，请重新检查更新" }
                        output.write(buffer, 0, n); hash.update(buffer, 0, n); progress(total)
                    }
                } }
                require(total == release.size && hex(hash.digest()).equals(release.sha256, true)) {
                    "下载不完整或版本已变化，请重新检查更新"
                }
                currentCoroutineContext().ensureActive()
                require((!target.exists() || target.delete()) && partial.renameTo(target)) { "无法保存更新文件" }
                published = true
                target
            } finally {
                connection.disconnect()
                if (!published) partial.delete()
            }
        }

    companion object {
        fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        fun sha256(file: File): String {
            val hash = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(32768)
                while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
            }
            return hex(hash.digest())
        }
    }
}
