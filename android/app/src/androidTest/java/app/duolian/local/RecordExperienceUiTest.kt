package app.duolian.local

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import app.duolian.local.domain.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** In-memory fixtures only. These tests never install a fixture into the user's database. */
@OptIn(ExperimentalTestApi::class)
class RecordExperienceUiTest {
    @get:Rule val ui = createComposeRule()

    @Test fun recordWaitsForWriteAndRetainsDraftAfterFailure() {
        var closed = false; var attempts = 0
        val write = CompletableDeferred<Unit>()
        ui.setContent { DuolianTheme {
            FieldsDialog("记录测试", listOf("重量" to "40"), { closed = true }) {
                attempts++; write.await()
            }
        } }
        ui.onNode(hasSetTextAction()).performTextReplacement("45")
        ui.onNodeWithText("保存").performClick()
        ui.onNodeWithText("保存中…").assertIsNotEnabled()
        ui.runOnIdle { assertFalse(closed); assertEquals(1, attempts); write.completeExceptionally(IllegalStateException("写入失败，请重试")) }
        ui.onNodeWithText("写入失败，请重试").assertExists()
        ui.onNode(hasSetTextAction()).assertTextEquals("45")
        ui.runOnIdle { assertFalse(closed) }
    }
    @Test fun changedRecordRequiresExplicitDiscard() {
        var closed = false
        ui.setContent { DuolianTheme { FieldsDialog("测试记录", listOf("重量" to "40"), { closed = true }) {} } }
        ui.onNode(hasSetTextAction()).performTextReplacement("50")
        ui.onNodeWithText("取消").performClick()
        ui.onNodeWithText("继续编辑").performClick()
        ui.onNode(hasSetTextAction()).assertTextEquals("50")
        ui.onNodeWithText("取消").performClick()
        ui.onNodeWithText("放弃修改").performClick()
        ui.runOnIdle { assertTrue(closed) }
    }
    @Test fun tabStateSurvivesDisposalWithoutKeepingInactivePageComposed() {
        val exercises = listOf(Exercise("a", "自定义上肢练习", "胸部", "徒手"),
            Exercise("b", "自定义下肢练习", "腿部", "徒手"))
        val actions = ScreenActions({}, {}, {}, {}, {}, {}, { _, _ -> }, {})
        ui.setContent { DuolianTheme {
            val pages = rememberSaveableStateHolder()
            var page by remember { mutableIntStateOf(0) }
            Column {
                Button(onClick = { page = 1 - page }) { Text("切换") }
                pages.SaveableStateProvider(page) {
                    if (page == 0) ExercisePage(exercises, actions) else Text("其他页面")
                }
            }
        } }
        ui.onNode(hasSetTextAction()).performTextReplacement("上肢")
        ui.onNodeWithText("切换").performClick()
        ui.onNodeWithText("其他页面").assertIsDisplayed()
        ui.onNode(hasSetTextAction()).assertDoesNotExist()
        ui.onNodeWithText("切换").performClick()
        ui.onNode(hasSetTextAction()).assertTextEquals("上肢")
        ui.onNodeWithText("1 个动作").assertExists()
    }
    @Test fun largeFontSetFieldsRemainEditableAndCompleteWithActualValues() {
        var complete: Pair<Long, Int>? = null
        ui.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(320.dp, 1000.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(2f)) { DuolianTheme {
                    QuickSetRow(TrainingSet("set", 40000, 10), 0, null, true, true, false,
                        { _, _ -> }, { g, r -> complete = g to r }, {}, {}, {})
                } }
            }
        }
        ui.onNodeWithContentDescription("第1组重量").performTextReplacement("45")
        ui.onNodeWithContentDescription("第1组次数").performTextReplacement("12")
        ui.onNodeWithContentDescription("第1组完成").performClick()
        ui.runOnIdle { assertEquals(45000L to 12, complete) }
    }
}
