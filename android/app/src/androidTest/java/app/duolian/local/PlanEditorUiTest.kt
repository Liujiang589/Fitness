package app.duolian.local

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.duolian.local.domain.*
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Local drafts only: tests do not modify the user's repository. */
class PlanEditorUiTest {
    @get:Rule val ui = createComposeRule()
    private val exercises = (1..40).map { Exercise("move-$it", "动作$it", if (it <= 20) "胸部" else "背部", "哑铃") }
    private fun field(tag: String) = ui.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))
    private fun scene(plan: Plan? = null, dismiss: () -> Unit = {}, save: suspend (Plan) -> Unit = {}) {
        ui.setContent { DuolianTheme { PlanForm(plan, exercises, dismiss, save) } }
    }
    @Test fun homeCreationOpensTheSharedEditorEvenWhileBusy() {
        var opens = 0
        ui.setContent { DuolianTheme {
            var open by remember { mutableStateOf(false) }
            TrainingSpotlight(TrainingHome.focus(emptyList(), emptyList(), java.time.LocalDate.of(2026,10,26)), true) {
                opens++; open = true
            }
            if (open) PlanForm(null, exercises, { open = false }) {}
        } }
        ui.onNodeWithText("创建我的计划").performClick()
        ui.onNodeWithTag("plan-name").assertIsDisplayed()
        ui.onNodeWithTag("plan-save").assertIsDisplayed()
        assertEquals(1, opens)
    }
    @Test fun pickerShowsBeyondTheOldTwelveItemLimitAndKeepsSelection() {
        scene()
        ui.onNodeWithText("＋ 添加动作").performClick()
        ui.onNodeWithTag("plan-exercise-list").performScrollToNode(hasText("动作40"))
        ui.onNodeWithText("动作40").performClick()
        ui.onNodeWithTag("plan-save").performClick()
        ui.onNodeWithText("动作40").assertIsDisplayed()
        ui.onNodeWithText("＋ 添加动作").performClick()
        field("plan-exercise-search").performTextInput("动作40")
        ui.onNodeWithText("动作40").assertIsDisplayed()
        ui.onNodeWithTag("plan-save").assertTextContains("完成选择 · 1 个动作")
    }
    @Test fun validationExplainsMissingContentWithoutClosingDraft() {
        var dismissed = false
        scene(dismiss = { dismissed = true })
        ui.onNodeWithTag("plan-save").performClick()
        ui.onNodeWithText("给计划起个名字，再保存。").assertIsDisplayed()
        field("plan-name").performTextInput("我的上肢日")
        ui.onNodeWithTag("plan-save").performClick()
        ui.onNodeWithText("先选择至少一个训练动作。").assertIsDisplayed()
        assertFalse(dismissed)
    }
    @Test fun saveWaitsForPersistencePreventsDuplicatesAndRetainsDraftOnFailure() {
        val pending = CompletableDeferred<Unit>()
        var attempts = 0
        var dismissed = false
        val plan = Plan(name = "上肢日", exerciseIds = listOf("move-1"))
        scene(plan, { dismissed = true }) {
            attempts++
            if (attempts == 1) { pending.await(); error("测试保存失败") }
        }
        ui.onNodeWithTag("plan-save").performClick().assertIsNotEnabled()
        assertEquals(1, attempts)
        assertFalse(dismissed)
        ui.runOnIdle { pending.complete(Unit) }
        ui.onNodeWithText("测试保存失败").assertIsDisplayed()
        field("plan-name").assertTextEquals("上肢日")
        ui.onNodeWithTag("plan-save").assertIsEnabled().performClick()
        ui.runOnIdle { assertEquals(2, attempts); assertTrue(dismissed) }
    }
    @Test fun editingPreservesAdvancedFieldsIdentityAndReorderedExercises() {
        val plan = Plan(name = "旧计划", exerciseIds = listOf("move-1", "move-2"), sets = 4, reps = 8,
            restSeconds = 90, scheme = SetScheme.entries.last(), superset = SupersetMode.PAIRS, weekdays = listOf(1,5))
        var saved: Plan? = null
        scene(plan, save = { saved = it })
        ui.onNodeWithContentDescription("下移动作1").performScrollTo().performClick()
        ui.onNodeWithTag("plan-save").performClick()
        ui.runOnIdle { assertEquals(plan.copy(exerciseIds = listOf("move-2", "move-1")), saved) }
    }
    @Test fun cancellationConfirmsUnsavedChanges() {
        var dismissed = false
        scene(dismiss = { dismissed = true })
        field("plan-name").performTextInput("草稿")
        ui.onNodeWithText("取消").performClick()
        ui.onNodeWithText("继续编辑").performClick()
        field("plan-name").assertTextEquals("草稿")
        assertFalse(dismissed)
        ui.onNodeWithText("取消").performClick()
        ui.onNodeWithText("放弃修改").performClick()
        ui.runOnIdle { assertTrue(dismissed) }
    }
}
