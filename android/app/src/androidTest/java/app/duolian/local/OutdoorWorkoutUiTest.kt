package app.duolian.local

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.duolian.local.domain.*
import app.duolian.local.outdoor.OutdoorLive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic route only; no GPS permission, SDK consent or application database changes. */
class OutdoorWorkoutUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun runningFocusKeepsTechnicalNumbersBehindDisclosure() {
        val r = OutdoorRun(kind = OutdoorKind.RUN, startedAt = 1000, updatedAt = 601000,
            elapsedMs = 600000, distanceMeters = 2000.0, steps = 1700, stepCounting = true)
        ui.setContent { DuolianTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutdoorWorkout(r, OutdoorLive(running = true, run = r, signal = OutdoorSignal.GOOD, speedMps = 3.0, accuracy = 6f))
        } } }
        ui.onNodeWithText("2.00").assertIsDisplayed()
        ui.onNodeWithText("定位正常").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("本次采集 1700 步").assertDoesNotExist()
        ui.onNodeWithText("配速、步数与定位详情", substring = true).performScrollTo().performClick()
        ui.onNodeWithText("本次采集 1700 步").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("最近定位精度约 6 米").performScrollTo().assertIsDisplayed()
    }
    @Test fun interruptedCheckpointHasRecoveryInstructions() {
        val r = OutdoorRun(kind = OutdoorKind.WALK, startedAt = 1000)
        ui.setContent { DuolianTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutdoorWorkout(r, OutdoorLive())
        } } }
        ui.onNodeWithText(OutdoorSignal.INTERRUPTED.title).performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(OutdoorSignal.INTERRUPTED.detail).performScrollTo().assertIsDisplayed()
    }
    @Test fun fixedControlsBlockDuplicateActionsAndRequireSeparateFinishIntent() {
        var paused = 0; var finished = 0
        ui.setContent { DuolianTheme { WorkoutControls(true, false, { paused++ }, { finished++ }) } }
        ui.onNodeWithText("暂停运动").performClick()
        assertEquals(1, paused); assertEquals(0, finished)
        ui.onNodeWithText("结束并保存").performClick()
        assertEquals(1, finished)
    }
    @Test fun busyControlsCannotSendAnotherCommand() {
        ui.setContent { DuolianTheme { WorkoutControls(false, true, { error("duplicate") }, { error("duplicate") }) } }
        ui.onNodeWithText("正在处理…").assertIsNotEnabled()
        ui.onNodeWithText("结束并保存").assertIsNotEnabled()
    }
}
