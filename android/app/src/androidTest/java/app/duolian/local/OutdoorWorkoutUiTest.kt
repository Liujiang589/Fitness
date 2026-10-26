package app.duolian.local

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import app.duolian.local.domain.*
import app.duolian.local.outdoor.OutdoorLive
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** Synthetic route only; no GPS permission, SDK consent or application database changes. */
class OutdoorWorkoutUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun runningFocusKeepsTechnicalNumbersBehindDisclosure() {
        val r = OutdoorRun(kind = OutdoorKind.RUN, startedAt = 1000, updatedAt = 601000,
            elapsedMs = 600000, distanceMeters = 2000.0, steps = 1700, stepCounting = true)
        val source=MutableStateFlow(OutdoorLive(running=true,run=r,signal=OutdoorSignal.GOOD,speedMps=3.0,accuracy=6f,fixAgeMs=1000))
        ui.setContent { DuolianTheme {
            OutdoorLiveWorkout(r,Modifier.fillMaxSize(),source,mapRenderer={_,area->Box(area)})
        } }
        ui.onNodeWithText("2.00").assertIsDisplayed()
        ui.onNodeWithText("定位正常").assertIsDisplayed()
        ui.onNodeWithText("定位精度约 6 米").assertDoesNotExist()
        ui.onNodeWithText("运动设置").performClick()
        ui.onNodeWithText("定位精度约 6 米").assertIsDisplayed()
    }
    @Test fun interruptedCheckpointHasRecoveryInstructions() {
        val r=OutdoorRun(kind=OutdoorKind.WALK,startedAt=1000)
        ui.setContent { DuolianTheme {
            OutdoorLiveWorkout(r,Modifier.fillMaxSize(),MutableStateFlow(OutdoorLive()),mapRenderer={_,area->Box(area)})
        } }
        ui.onNodeWithText(OutdoorSignal.INTERRUPTED.title).assertIsDisplayed()
        ui.onNodeWithText("运动设置").performClick()
        ui.onNodeWithText(OutdoorSignal.INTERRUPTED.detail).assertIsDisplayed()
    }
    @Test fun fixedControlsBlockDuplicateActionsAndRequireSeparateFinishIntent() {
        var paused = 0; var finished = 0; var discarded = 0
        ui.setContent { DuolianTheme { WorkoutControls(true, false, { paused++ }, { finished++ }, { discarded++ }) } }
        ui.onNodeWithText("暂停运动").performClick()
        assertEquals(1, paused); assertEquals(0, finished)
        ui.onNodeWithText("结束并保存").performClick()
        assertEquals(1, finished)
        assertEquals(0, discarded)
        ui.onNodeWithText("放弃本次记录").performClick()
        assertEquals(1, discarded)
    }
    @Test fun busyControlsCannotSendAnotherCommand() {
        ui.setContent { DuolianTheme { WorkoutControls(false, true, { error("duplicate") }, { error("duplicate") }, { error("duplicate") }) } }
        ui.onNodeWithText("正在处理…").assertIsNotEnabled()
        ui.onNodeWithText("结束并保存").assertIsNotEnabled()
        ui.onNodeWithText("放弃本次记录").assertIsNotEnabled()
    }
    @Test fun dismissingDiscardDoesNotSaveOrResumePausedWorkout() {
        var confirmed = 0; var dismissed = 0
        val r = OutdoorRun(kind = OutdoorKind.RUN, startedAt = 1000, status = OutdoorStatus.PAUSED)
        ui.setContent { DuolianTheme { WorkoutExitDialog(r, WorkoutExit.DISCARD, false, false,
            dismiss = { dismissed++ }, confirm = { confirmed++ }) } }
        ui.onNodeWithText("当天已采集的步数保留。", substring = true).assertIsDisplayed()
        ui.onNodeWithText("当前已暂停；返回运动页不会自动继续记录。").assertIsDisplayed()
        ui.onNodeWithText("返回运动页").performClick()
        assertEquals(1, dismissed); assertEquals(0, confirmed)
    }
    @Test fun emptyRouteSaveExplainsWhatWillBeKept() {
        var confirmed = 0
        val r = OutdoorRun(kind = OutdoorKind.WALK, startedAt = 1000)
        ui.setContent { DuolianTheme { WorkoutExitDialog(r, WorkoutExit.SAVE, true, false,
            dismiss = {}, confirm = { confirmed++ }) } }
        ui.onNodeWithText("目前没有可靠路线，保存后仍可查看已记录的时长和步数。").assertIsDisplayed()
        ui.onNodeWithText("结束并保存").performClick()
        assertEquals(1, confirmed)
    }
}
