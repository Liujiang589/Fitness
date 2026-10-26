package app.duolian.local

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.duolian.local.domain.*
import app.duolian.local.outdoor.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Private synthetic StateFlows only: no services, GPS, permission grants or repository writes. */
class OutdoorExperienceUiTest {
    @get:Rule val ui=createComposeRule()
    @Test fun tickingClockDoesNotRecomposeTheMap() {
        val run=OutdoorRun(kind=OutdoorKind.RUN,startedAt=1000)
        val source=MutableStateFlow(OutdoorLive(running=true,run=run,signal=OutdoorSignal.GOOD))
        var maps=0
        ui.setContent { DuolianTheme {
            OutdoorLiveWorkout(run,Modifier.fillMaxSize(),source,mapRenderer={_,area->
                SideEffect{maps++};Box(area)
            })
        } }
        ui.waitForIdle();val initial=maps
        for(i in 1..20)ui.runOnIdle{source.value=source.value.copy(run=run.copy(elapsedMs=i*1000L,updatedAt=1000+i*1000L))}
        ui.waitForIdle()
        ui.onNodeWithText(clock(20000)).assertIsDisplayed()
        assertEquals(initial,maps)
    }
    @Test fun gpsPreparationCanBeCancelledWithoutStartingAWorkout() {
        var starts=0;var cancels=0
        ui.setContent{DuolianTheme{OutdoorPreparationView(OutdoorKind.WALK,1,false,null,false,
            Modifier.fillMaxSize(),{starts++},{cancels++})}}
        ui.onNodeWithText("开始步行").assertIsNotEnabled()
        ui.onNodeWithText("取消准备").performClick()
        assertEquals(0,starts);assertEquals(1,cancels)
    }
    @Test fun readyCountdownStartsOnceAndBlocksDuplicateTaps() {
        var starts=0
        ui.setContent{DuolianTheme{OutdoorPreparationView(OutdoorKind.RUN,1,true,6f,false,
            Modifier.fillMaxSize(),{starts++},{})}}
        ui.onNodeWithText("开始跑步").performClick()
        ui.onNodeWithText("倒数出发…").assertIsNotEnabled()
        ui.waitUntil(6000){starts==1}
        assertEquals(1,starts)
    }
    @Test fun cancellingTheCountdownReturnsToReadyWithoutStarting() {
        var starts=0;var cancelled=0
        ui.setContent{DuolianTheme{OutdoorPreparationView(OutdoorKind.WALK,1,true,6f,false,
            Modifier.fillMaxSize(),{starts++},{cancelled++})}}
        ui.onNodeWithText("开始步行").performClick()
        ui.onNodeWithText("取消倒数").performClick()
        ui.onNodeWithText("开始步行").assertIsEnabled()
        assertEquals(0,starts);assertEquals(0,cancelled)
    }
    @Test fun lostReadinessAbortsCountdown() {
        var ready by mutableStateOf(true);var starts=0
        ui.setContent{DuolianTheme{OutdoorPreparationView(OutdoorKind.RUN,1,ready,6f,false,
            Modifier.fillMaxSize(),{starts++},{})}}
        ui.onNodeWithText("开始跑步").performClick()
        ui.runOnIdle{ready=false}
        ui.waitUntil(4000){ui.onAllNodesWithText("正在寻找 GPS").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("开始跑步").assertIsNotEnabled();assertEquals(0,starts)
    }
    @Test fun captureNewOutdoorScreens() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("outdoorVisualReview")=="true")
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val route=List(24){i->RoutePoint(30.0+i*.00003,120.0+kotlin.math.sin(i*.2)*.0002,
            1700000000000+i*1000L,5f)}
        val run=OutdoorRun(kind=OutdoorKind.RUN,startedAt=1700000000000,updatedAt=1700000660000,
            elapsedMs=660000,distanceMeters=2180.0,steps=2110,stepCounting=true,points=route)
        val source=MutableStateFlow(OutdoorLive(running=true,run=run,signal=OutdoorSignal.GOOD,
            speedMps=3.3,accuracy=5f,mapPosition=route.last(),fixAgeMs=1000))
        var prepare by mutableStateOf(false)
        ui.setContent{DuolianTheme{Surface(color=Page){Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Text("步行与跑步",Modifier.padding(20.dp),style=MaterialTheme.typography.titleLarge)
            if(prepare)OutdoorPreparationView(OutdoorKind.RUN,1,true,5f,false,Modifier.weight(1f),{},{})
            else {
                OutdoorLiveWorkout(run,Modifier.weight(1f),source)
                WorkoutControls(true,false,{},{},{})
            }
        }}}}
        val folder=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"outdoor-review-v119").apply{mkdirs()}
        ui.waitForIdle();Thread.sleep(1800)
        fun capture(name: String) {
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            java.io.File(folder,name).outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            bitmap.recycle()
        }
        capture("WORKOUT.png")
        ui.runOnIdle{prepare=true};ui.waitForIdle();capture("PREPARATION.png")
    }
}
