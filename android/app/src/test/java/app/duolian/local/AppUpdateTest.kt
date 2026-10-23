package app.duolian.local

import app.duolian.local.data.UpdateTransport
import app.duolian.local.domain.*
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.concurrent.thread
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AppUpdateTest {
    private lateinit var server: ServerSocket
    private lateinit var worker: Thread
    private lateinit var directory: File
    private lateinit var base: String
    @Volatile private var apk = ByteArray(100000) { (it % 251).toByte() }
    @Volatile private var manifest = ""
    @Volatile private var redirect = false
    @Volatile private var omitLength = false

    @Before fun setup() {
        directory = Files.createTempDirectory("duolian-update-test").toFile()
        server = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        base = "http://127.0.0.1:${server.localPort}"
        manifest = Codec.encodeToString(release())
        worker = thread(isDaemon = true, name = "update-test-http") {
            while (!server.isClosed) {
                try { server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)
                    val path = reader.readLine()?.split(' ')?.getOrNull(1)
                    while (!reader.readLine().isNullOrEmpty()) { }
                    val isManifest = path == "/latest.json"
                    val bytes = if (isManifest) manifest.toByteArray(Charsets.UTF_8) else apk
                    val status = if (isManifest && redirect) "302 Found" else "200 OK"
                    val headers = buildString {
                        append("HTTP/1.1 $status\r\nConnection: close\r\n")
                        if (isManifest && redirect) append("Location: $base/other.json\r\n")
                        if (isManifest || !omitLength) append("Content-Length: ${bytes.size}\r\n")
                        append("\r\n")
                    }
                    socket.getOutputStream().apply {
                        write(headers.toByteArray(Charsets.US_ASCII))
                        if (!(isManifest && redirect)) write(bytes)
                        flush()
                    }
                } } catch (_: java.io.IOException) { /* Cancellation and early response rejection close sockets. */ }
            }
        }
    }
    @After fun cleanup() { server.close(); worker.join(1000); directory.deleteRecursively() }
    private fun release() = AppRelease("1.10", 20, apk.size.toLong(),
        UpdateTransport.hex(MessageDigest.getInstance("SHA-256").digest(apk)), "$base/latest.apk",
        releaseNotes = listOf("改进更新体验"))
    private fun reject(block: () -> Unit) {
        try { block(); fail("invalid update accepted") } catch (_: IllegalArgumentException) { }
    }

    @Test fun manifestsAcceptForwardCompatibleFieldsWithoutMixingTrainingCodec() {
        val text = Codec.encodeToString(release()).dropLast(1) + ",\"futureField\":true}"
        assertEquals(release(), UpdatePolicy.parse(text, "$base/latest.json"))
    }
    @Test fun upgradesCompareBuildCodesAndRespectDeviceCompatibility() {
        assertTrue(UpdatePolicy.isNewer(release(), 19, 35))
        assertFalse(UpdatePolicy.isNewer(release(), 20, 35))
        assertFalse(UpdatePolicy.isNewer(release(), 21, 35))
        assertFalse(UpdatePolicy.isNewer(release().copy(minSdk = 36), 19, 35))
        assertTrue(UpdatePolicy.isNewer(release().copy(version = "1.2", versionCode = 21), 20, 35))
    }
    @Test fun malformedIdentitySizeHashAndNotesCannotDescribeAnInstallableRelease() {
        listOf(release().copy(packageName = "other.app"), release().copy(size = 0),
            release().copy(size = UpdatePolicy.MAX_APK_BYTES + 1), release().copy(sha256 = "bad"),
            release().copy(versionCode = 0), release().copy(version = "latest"),
            release().copy(releaseNotes = listOf("x".repeat(601)))).forEach {
            reject { UpdatePolicy.validate(it, "$base/latest.json") }
        }
    }
    @Test fun manifestsCannotRedirectDownloadsToAnotherOriginOrProtocol() {
        for (url in listOf("https://example.com/latest.apk", "http://127.0.0.1:1/latest.apk", "$base/not-an-apk", "$base/latest.apk#fragment")) {
            reject { UpdatePolicy.validate(release().copy(downloadUrl = url), "$base/latest.json") }
        }
    }
    @Test fun cleartextIsRestrictedToLiteralPrivateOrLoopbackAddresses() {
        listOf("http://example.com/latest.json", "http://192.168.extra.224.217/latest.json",
            "file:///tmp/update.json", "http://user:pass@127.0.0.1/latest.json").forEach {
            reject { UpdatePolicy.trustedUrl(it) }
        }
        UpdatePolicy.trustedUrl("https://example.com/latest.json")
        UpdatePolicy.trustedUrl("http://192.168.224.217:8765/latest.json")
    }
    @Test fun checksFetchTheActualManifest() = runBlocking {
        assertEquals(release(), UpdateTransport("$base/latest.json").check())
    }
    @Test fun oversizedManifestsAreRejectedBeforeParsing() = runBlocking {
        manifest = "x".repeat(UpdatePolicy.MAX_MANIFEST_BYTES + 1)
        assertNotNull(runCatching { UpdateTransport("$base/latest.json").check() }.exceptionOrNull())
    }
    @Test fun redirectsAreNotFollowed() = runBlocking {
        redirect = true
        assertNotNull(runCatching { UpdateTransport("$base/latest.json").check() }.exceptionOrNull())
    }
    @Test fun validDownloadsPublishExactBytesAndProgressWithoutTouchingBackups() = runBlocking {
        val backup = File(directory, "backup.json").apply { writeText("keep") }
        val cache = File(directory, "updates")
        var last = 0L
        val file = UpdateTransport("$base/latest.json").download(release(), cache) { assertTrue(it >= last); last = it }
        assertArrayEquals(apk, file.readBytes()); assertEquals(apk.size.toLong(), last)
        assertEquals("keep", backup.readText()); assertFalse(File(cache, "download.part").exists())
    }
    @Test fun alteredDownloadsNeverBecomeAnInstallableFile() = runBlocking {
        val expected = release(); apk = apk.copyOf().also { it[7] = 99 }
        assertNotNull(runCatching { UpdateTransport("$base/latest.json").download(expected, directory) {} }.exceptionOrNull())
        assertFalse(File(directory, "Duolian-20.apk").exists()); assertFalse(File(directory, "download.part").exists())
    }
    @Test fun truncatedChunkedDownloadsAreRemoved() = runBlocking {
        val expected = release(); apk = apk.copyOf(apk.size - 1); omitLength = true
        assertNotNull(runCatching { UpdateTransport("$base/latest.json").download(expected, directory) {} }.exceptionOrNull())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun changedReleaseSizeForcesRecheckingInsteadOfCombiningVersions() = runBlocking {
        val expected = release(); apk += byteArrayOf(1)
        assertNotNull(runCatching { UpdateTransport("$base/latest.json").download(expected, directory) {} }.exceptionOrNull())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun cancellingADownloadRemovesTheIncompletePackage() = runBlocking {
        val job = launch {
            val owner = currentCoroutineContext()
            UpdateTransport("$base/latest.json").download(release(), directory) { owner.cancel() }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
}
