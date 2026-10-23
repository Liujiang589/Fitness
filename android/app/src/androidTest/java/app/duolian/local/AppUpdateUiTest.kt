package app.duolian.local

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.duolian.local.domain.AppRelease
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AppUpdateUiTest {
    @get:Rule val ui = createComposeRule()
    private val release = AppRelease("2.0", 100, 1000, "a".repeat(64), "http://192.168.224.217:8765/latest.apk",
        releaseNotes = listOf("新版更新说明"))
    @Test fun promptShowsReleaseNotesAndRequiresADownloadTap() {
        var downloads = 0
        ui.setContent { DuolianTheme {
            AppUpdateDialog(AppUpdateState(release = release), {}, {}, { downloads++ }, {}, {})
        } }
        ui.onNodeWithText("发现新版本 V2.0").assertIsDisplayed()
        ui.onNodeWithText("• 新版更新说明").assertIsDisplayed()
        assertEquals(0, downloads)
        ui.onNodeWithText("下载更新").performClick(); assertEquals(1, downloads)
    }
    @Test fun downloadedReleaseOffersExplicitInstallation() {
        var installs = 0
        ui.setContent { DuolianTheme {
            AppUpdateDialog(AppUpdateState(release = release, file = File("unused-preview.apk")), {}, {}, {}, {}, { installs++ })
        } }
        assertEquals(0, installs)
        ui.onNodeWithText("安装更新").performClick(); assertEquals(1, installs)
    }
}
