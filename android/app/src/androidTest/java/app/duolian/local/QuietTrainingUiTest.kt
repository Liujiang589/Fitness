package app.duolian.local

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class QuietTrainingUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun professionalToolsStayBehindTheGroupMenu() {
        var advanced: TrainingSet? = null
        ui.setContent { DuolianTheme {
            QuickSetRow(TrainingSet("s", 40000), 0, TrainingSet(grams = 50000, reps = 8), true, true, false,
                { _, _ -> }, { _, _ -> }, {}, { advanced = it }, {})
        } }
        ui.onNodeWithText("组类型 / RPE / 休息").assertDoesNotExist()
        ui.onNodeWithText("删除这一组").assertDoesNotExist()
        ui.onNodeWithContentDescription("第1组更多选项").performClick()
        ui.onNodeWithText("沿用上次重量和次数").performClick()
        ui.onNodeWithContentDescription("第1组重量").assertTextEquals("50")
        ui.onNodeWithContentDescription("第1组更多选项").performClick()
        ui.onNodeWithText("组类型 / RPE / 休息").performClick()
        assertEquals(50000L, advanced!!.grams); assertEquals(8, advanced!!.reps)
    }
    @Test fun completedRowsKeepUndoWithoutDisabledInputClutter() {
        var undone = false
        ui.setContent { DuolianTheme {
            QuickSetRow(TrainingSet("s", 40000, done = true), 0, null, false, true, false,
                { _, _ -> }, { _, _ -> }, { undone = true }, {}, null)
        } }
        ui.onNodeWithContentDescription("第1组重量").assertDoesNotExist()
        ui.onNodeWithContentDescription("第1组撤销完成").performClick()
        assertTrue(undone)
    }
    @Test fun busyRowsCannotOpenToolsOrSendCompletion() {
        ui.setContent { DuolianTheme {
            QuickSetRow(TrainingSet("s", 40000), 0, null, true, true, true,
                { _, _ -> error("busy") }, { _, _ -> error("busy") }, {}, {}, {})
        } }
        ui.onNodeWithContentDescription("第1组更多选项").assertIsNotEnabled()
        ui.onNodeWithContentDescription("第1组完成").assertIsNotEnabled()
    }
    @Test fun muscleLegendNamesPrimaryAndSecondaryWithoutRelyingOnColour() {
        val guide = ExerciseGuides.all.first { it.kind == MotionKind.BENCH }
        ui.setContent { DuolianTheme { Column(Modifier.width(320.dp)) { MuscleFocusCard(guide) } } }
        ui.onNodeWithText("主要 · 胸大肌").assertIsDisplayed()
        ui.onNodeWithText("辅助 · 肱三头肌、三角肌").assertIsDisplayed()
        ui.onNodeWithText("正面").assertIsDisplayed()
        ui.onNodeWithText("背面").assertIsDisplayed()
    }
}
