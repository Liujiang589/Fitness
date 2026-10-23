package app.duolian.local

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.duolian.local.domain.ExerciseGuides
import org.junit.Rule
import org.junit.Test

class ExerciseMotionUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun playerCanPauseSeekSlowAndChangeView() {
        ui.setContent { DuolianTheme { ExerciseDemoPlayer(ExerciseGuides.all.first()) } }
        if(ui.onAllNodesWithText("暂停示范").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithText("暂停示范").performClick()
        ui.onNodeWithText("播放示范").assertIsDisplayed()
        ui.onNodeWithText("发力过程").performClick()
        ui.onNodeWithText("播放示范").assertIsDisplayed()
        ui.onNodeWithText("1× 速度").performClick()
        ui.onNodeWithText("0.5× 慢速").assertIsDisplayed()
        ui.onNodeWithText("侧面").performClick()
        ui.onNodeWithText("斜侧").assertIsDisplayed()
        ui.onNodeWithContentDescription("动作示范进度").assertIsDisplayed()
    }
}
