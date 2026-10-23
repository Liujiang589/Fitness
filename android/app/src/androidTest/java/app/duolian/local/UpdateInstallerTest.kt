package app.duolian.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.duolian.local.data.UpdateTransport
import app.duolian.local.domain.AppRelease
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test

class UpdateInstallerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun release(file: File) = AppRelease(BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toLong(),
        file.length(), UpdateTransport.sha256(file), "http://192.168.224.217:8765/latest.apk")

    @Test fun identicalInstalledVersionCannotBeOfferedAsAnUpgrade() {
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        val target = File(folder, "test-${UUID.randomUUID()}.apk")
        try {
            File(context.applicationInfo.sourceDir).copyTo(target)
            try { UpdateInstaller.verifiedIntent(context, target, release(target)); fail("same version accepted") }
            catch (e: IllegalArgumentException) { assertTrue(e.message.orEmpty().contains("版本")) }
        } finally { target.delete() }
    }
    @Test fun installerCannotShareFilesOutsideTheDedicatedUpdateCache() {
        val target = File(context.cacheDir, "test-${UUID.randomUUID()}.apk").apply { writeText("not an apk") }
        try {
            try { UpdateInstaller.verifiedIntent(context, target, release(target)); fail("outside cache accepted") }
            catch (e: IllegalArgumentException) { assertTrue(e.message.orEmpty().contains("文件")) }
        } finally { target.delete() }
    }
}
