package app.duolian.local

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import android.graphics.Bitmap
import androidx.room.Room
import app.duolian.local.data.LocalDatabase
import app.duolian.local.data.Repository
import java.io.File
import kotlinx.coroutines.flow.first
import app.duolian.local.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test

class TrainingUiTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private var original: Backup? = null
    @After fun restoreAcceptanceData() {
        val before = original ?: return
        val app = ui.activity.application as DuolianApp
        // Old 0.2 acceptance left this exact synthetic fixture behind. Restore the
        // starter catalog only for that fixture; preserve all other user datasets.
        val onlyOldFixture = before.exercises.map { it.id } == listOf("ui-e") &&
            before.plans.map { it.id } == listOf("ui-p") &&
            before.body.isEmpty() && before.food.isEmpty() && before.water.isEmpty() &&
            before.sessions.all { it.title == "界面验收计划" && it.movements.all { m -> m.exerciseId == "ui-e" } }
        runBlocking {
            val restore = if (onlyOldFixture) {
                val name = "acceptance-starter-${newId()}.db"
                val db = Room.databaseBuilder(app, LocalDatabase::class.java, name).build()
                try { val repo = Repository(db); repo.initialize(); repo.snapshot(before.restSeconds) }
                finally { db.close(); app.deleteDatabase(name) }
            } else before
            app.repository.restore(restore)
        }
        ui.onNodeWithText("训练", useUnmergedTree = true).performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("我的计划").fetchSemanticsNodes().isNotEmpty() }
        screenshot("ui03-home.png")
        ui.onNodeWithText("动作", useUnmergedTree = true).performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("全部动作").fetchSemanticsNodes().isNotEmpty() }
        ui.onNodeWithText("胸部").performClick()
        screenshot("ui03-exercises.png")
        ui.onNodeWithText("训练", useUnmergedTree = true).performClick()
    }
    private fun screenshot(name: String) {
        val bitmap = ui.onRoot().captureToImage().asAndroidBitmap()
        File(ui.activity.filesDir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
    @Test fun startCompleteAndSaveIntoHistory() {
        ui.waitUntil(10000) { ui.onAllNodesWithText("我的计划").fetchSemanticsNodes().isNotEmpty() }
        val app = ui.activity.application as DuolianApp
        runBlocking { original = app.repository.snapshot(app.restSeconds.first()) }
        // Instrumentation only: isolate deterministic acceptance data in the independent app database.
        runBlocking { app.repository.restore(Backup(exercises = listOf(Exercise("ui-e", "界面测试动作", "胸部", "杠铃", 40000)),
            plans = listOf(Plan("ui-p", "界面验收计划", listOf("ui-e"), sets = 1)),
            sessions = emptyList(), body = emptyList(), food = emptyList(), water = emptyList())) }
        ui.waitUntil(10000) { ui.onAllNodesWithText("界面验收计划").fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("开始训练"))
        ui.onNodeWithText("开始训练").performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("正在训练").fetchSemanticsNodes().isNotEmpty() }
        ui.onNode(isToggleable()).performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("容量 400.0 kg", substring = true).fetchSemanticsNodes().isNotEmpty() }
        screenshot("ui03-training.png")
        ui.onNodeWithText("结束并保存训练").performScrollTo().performClick()
        ui.onNodeWithText("确认").performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("正在训练").fetchSemanticsNodes().isEmpty() }
        ui.onNodeWithText("历史", useUnmergedTree = true).performClick()
        ui.onNodeWithText("累计 1 次训练").assertIsDisplayed()
        ui.onNodeWithText("容量 400.0 kg").assertExists()
        screenshot("ui03-history.png")
    }
}
