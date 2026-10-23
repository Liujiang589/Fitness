package app.duolian.local

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.duolian.local.domain.*
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Presentation checks only: no repository, GPS, personal media or training database writes. */
class HomeCoachingUiTest {
    @get:Rule val ui = createComposeRule()
    private fun settle() { ui.mainClock.advanceTimeBy(600); ui.waitForIdle() }
    private fun scene(content: @Composable () -> Unit) {
        ui.mainClock.autoAdvance = false // Custom frame loop must not prevent test idleness.
        ui.setContent(content)
        settle()
    }
    @Test fun firstUseCardOffersCreationInsteadOfPresetTraining() {
        var clicks = 0
        val focus = TrainingHome.focus(emptyList(), emptyList(), LocalDate.of(2026, 10, 23))
        scene { DuolianTheme { TrainingSpotlight(focus, false) { clicks++ } } }
        ui.onNodeWithText("创建我的计划").performClick()
        assertEquals(1, clicks)
        ui.onNodeWithText("开始这场训练").assertDoesNotExist()
    }
    @Test fun busySpotlightCannotStartAnotherSession() {
        val plan = Plan(name = "我的计划", exerciseIds = listOf("test"), weekdays = listOf(5))
        val focus = TrainingHome.focus(listOf(plan), emptyList(), LocalDate.of(2026, 10, 23))
        scene { DuolianTheme { TrainingSpotlight(focus, true) { error("duplicate") } } }
        ui.onNodeWithText("开始这场训练").assertIsNotEnabled()
    }
    @Test fun unknownCustomActionKeepsPersonalNotesWithoutIncorrectDemonstration() {
        val exercise = Exercise(name = "个人自定义动作", muscle = "全身", equipment = "徒手", notes = "个人节奏")
        scene { DuolianTheme { ExerciseGuideDialog(exercise, false, {}, {}, {}, {}) } }
        ui.onNodeWithText("这个自定义动作暂未内置示范").assertIsDisplayed()
        ui.onNodeWithText("查看个人训练设置").performScrollTo().performClick()
        settle()
        ui.onNodeWithText("个人节奏").performScrollTo().assertIsDisplayed()
    }
    @Test fun alternativeExplorationReturnsToOriginalAndCannotEditSyntheticExercise() {
        val exercise = Exercise(name = "杠铃卧推", muscle = "胸部", equipment = "杠铃")
        scene { DuolianTheme { ExerciseGuideDialog(exercise, false, {}, {}, {}, {}) } }
        if (ui.onAllNodesWithText("暂停示范").fetchSemanticsNodes().isNotEmpty()) ui.onNodeWithText("暂停示范").performClick()
        ui.onNodeWithText("替代动作").performScrollTo().performClick()
        settle()
        ui.onNodeWithText("查看哑铃卧推示范 ›").performScrollTo().performClick()
        settle()
        ui.onNodeWithText("哑铃卧推", useUnmergedTree = true).assertIsDisplayed()
        ui.onNodeWithText("编辑动作").assertDoesNotExist()
        ui.onNodeWithText("返回原动作").performClick()
        settle()
        ui.onNodeWithText("杠铃卧推", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun trainingGuideHidesEditingAndPreservesTheReturnAction() {
        val exercise = Exercise(name = "徒手深蹲", muscle = "腿部", equipment = "徒手")
        scene { DuolianTheme { ExerciseGuideDialog(exercise, false, {}, {}, {}, {}, readOnly = true) } }
        ui.onNodeWithText("编辑动作").assertDoesNotExist()
        ui.onNodeWithText("完成查看").assertIsDisplayed()
    }
}
