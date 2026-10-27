package app.duolian.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import app.duolian.local.domain.*
import app.duolian.local.outdoor.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Local overrides only: never change the user's font/display settings or database. */
@OptIn(ExperimentalTestApi::class)
@RunWith(Parameterized::class)
class ResponsiveHomeUiTest(private val width: Int, private val scale: Float) {
    @get:Rule val ui = createComposeRule()
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}dp_font{1}")
        fun sizes() = listOf(arrayOf(320, .92f), arrayOf(320, 1f), arrayOf(320, 1.3f),
            arrayOf(320, 2f), arrayOf(360, 1.6f), arrayOf(411, 1f), arrayOf(411, 2f))
    }
    private fun scene(content: @Composable () -> Unit) {
        ui.setContent {
            DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(width.dp, 900.dp))) {
                DeviceConfigurationOverride(DeviceConfigurationOverride.FontScale(scale)) {
                    DuolianTheme { Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) { content() } }
                }
            }
        }
    }
    private fun DpRect.overlaps(other: DpRect) = left < other.right && right > other.left && top < other.bottom && bottom > other.top
    @Test fun planActionsStayReadableAndClickable() {
        var creates = 0; var selected = 0
        scene { HomePlanToolbar(selected, { selected = it }, { creates++ }) }
        ui.onNodeWithText("今天").assertIsDisplayed()
        ui.onNodeWithText("计划库").assertIsDisplayed().performClick()
        ui.onNodeWithText("＋ 创建计划").assertIsDisplayed().performClick()
        assertEquals(1, selected); assertEquals(1, creates)
        assertFalse(ui.onNodeWithText("计划库").getUnclippedBoundsInRoot()
            .overlaps(ui.onNodeWithText("＋ 创建计划").getUnclippedBoundsInRoot()))
    }
    @Test fun weeklyCalendarKeepsAllSevenDaysWithoutHorizontalScrolling() {
        scene { WeeklyMomentum(emptyList()) }
        for (day in listOf("一", "二", "三", "四", "五", "六", "日"))
            ui.onNodeWithText(day).performScrollTo().assertIsDisplayed()
        if (scale <= 1f) {
            val first = ui.onNodeWithText("一").getUnclippedBoundsInRoot().top
            assertEquals(first, ui.onNodeWithText("日").getUnclippedBoundsInRoot().top)
        }
    }
    @Test fun lifeCardsDoNotOverlapAndStillOpenTheirOwnActions() {
        var meals = 0; var body = 0
        scene { HomeLifeLinks({ meals++ }, { body++ }) }
        ui.onNodeWithText("好好吃饭").performScrollTo().performClick()
        ui.onNodeWithText("关心身体").performScrollTo().performClick()
        assertEquals(1, meals); assertEquals(1, body)
        val a = ui.onNodeWithText("好好吃饭").getUnclippedBoundsInRoot()
        val b = ui.onNodeWithText("关心身体").getUnclippedBoundsInRoot()
        assertFalse(a.overlaps(b))
    }
    @Test fun titleAndActionReflowWithoutClippingOrOverlap() {
        scene { ResponsiveHeader(leading = { Text("今天的训练安排", Modifier.testTag("heading"), style = MaterialTheme.typography.headlineLarge) },
            trailing = { TextButton(onClick = {}) { Text("查看全部计划") } }) }
        ui.onNodeWithText("今天的训练安排").assertIsDisplayed()
        ui.onNodeWithText("查看全部计划").assertIsDisplayed()
        assertFalse(ui.onNodeWithTag("heading").getUnclippedBoundsInRoot()
            .overlaps(ui.onNodeWithText("查看全部计划").getUnclippedBoundsInRoot()))
    }
    @Test fun workoutMetricsAndMapRemainReachableWithLargeText() {
        val run = OutdoorRun(kind=OutdoorKind.RUN, startedAt=1000, distanceMeters=2340.0, elapsedMs=120000)
        val source = MutableStateFlow(OutdoorLive(running=true, run=run, signal=OutdoorSignal.GOOD,
            fixAgeMs=0, speedMps=2.5))
        scene { OutdoorLiveWorkout(run, Modifier.height(650.dp), source, mapRenderer={ _, area ->
            Box(area) { Text("模拟地图") }
        }) }
        ui.onNodeWithText("00:02:00").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("模拟地图").performScrollTo().assertIsDisplayed()
    }
}
