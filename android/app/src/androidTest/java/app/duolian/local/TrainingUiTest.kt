package app.duolian.local

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import app.duolian.local.domain.*
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Run only on a dedicated test device: the fixture is restored even after assertion failure. */
class TrainingUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private var original: Backup? = null

    @After fun restoreAcceptanceData() {
        val before = original ?: return
        val app = ui.activity.application as DuolianApp
        runBlocking { app.repository.restore(before) }
    }

    private fun screenshot(name: String) {
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        File(ui.activity.filesDir, name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test fun startCompleteAndSaveIntoHistory() {
        ui.waitUntil(10000) {
            ui.onAllNodesWithText("开始训练").fetchSemanticsNodes().isNotEmpty() ||
                ui.onAllNodesWithText("创建我的计划").fetchSemanticsNodes().isNotEmpty()
        }
        val app = ui.activity.application as DuolianApp
        runBlocking {
            original = app.repository.snapshot(app.restSeconds.first())
            app.repository.restore(Backup(
                exercises = listOf(Exercise("ui-e", "界面测试动作", "胸部", "杠铃", 40000)),
                plans = listOf(Plan("ui-p", "界面验收计划", listOf("ui-e"), sets = 1)),
                sessions = emptyList(), body = emptyList(), food = emptyList(), water = emptyList()))
        }
        ui.waitUntil(10000) { ui.onAllNodesWithText("界面验收计划").fetchSemanticsNodes().isNotEmpty() }
        screenshot("premium-home.png")
        // The spotlight and the plan card both offer a start action; use the visible spotlight.
        ui.onAllNodesWithText("开始训练").onFirst().performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("正在训练").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithContentDescription("第1组重量").performScrollTo().performTextReplacement("45")
        ui.onNodeWithContentDescription("第1组次数").performTextReplacement("12")
        ui.onNode(isToggleable()).performClick()
        ui.onNodeWithText("查看本次统计").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("540.0").fetchSemanticsNodes().isNotEmpty() }
        screenshot("premium-training.png")
        ui.onNodeWithText("结束并保存训练").performScrollTo().performClick()
        ui.onNodeWithText("确认").performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("正在训练").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("历史").performClick()
        ui.onNodeWithText("每一次努力，都有迹可循。").assertIsDisplayed()
        ui.onNodeWithText("540.0").assertDoesNotExist()
        ui.onNodeWithText("专业分析").performClick()
        ui.onNodeWithText("训练负荷").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("540.0").assertIsDisplayed()
        screenshot("premium-history.png")
        ui.onNodeWithText("动作").performClick()
        ui.onNodeWithText("探索动作").assertIsDisplayed()
        ui.onNodeWithText("界面测试动作").performScrollTo().assertIsDisplayed()
        screenshot("premium-exercises.png")
        ui.onNodeWithText("工具").performClick()
        ui.onNodeWithText("训练工具").assertIsDisplayed()
        screenshot("premium-tools.png")
        ui.onNodeWithText("我的").performClick()
        ui.onNodeWithText("独练者").assertIsDisplayed()
        screenshot("premium-profile.png")
    }
}
