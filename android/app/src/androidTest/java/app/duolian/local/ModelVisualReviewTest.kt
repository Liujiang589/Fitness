package app.duolian.local

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import app.duolian.local.domain.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

/** Opt-in visual review only. Synthetic guides; no repositories, GPS, permissions or user record writes. */
class ModelVisualReviewTest {
    @get:Rule val ui = createComposeRule()
    @Test fun captureSmoothModelOnDevice() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("modelVisualReview")=="true")
        var kind by mutableStateOf(MotionKind.BODY_SQUAT)
        ui.mainClock.autoAdvance=false
        ui.setContent { DuolianTheme {
            Surface(color=MaterialTheme.colorScheme.background) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    key(kind) { ExerciseDemoPlayer(ExerciseCoaching.guide(kind)) }
                }
            }
        } }
        val folder=java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"model-review-v116")
        folder.mkdirs()
        for(sample in listOf(MotionKind.BODY_SQUAT,MotionKind.CURL,MotionKind.BENCH)) {
            ui.runOnUiThread { kind=sample }
            ui.mainClock.advanceTimeBy(400);ui.waitForIdle()
            ui.onNodeWithText("暂停示范").performClick()
            ui.mainClock.advanceTimeBy(100);ui.waitForIdle()
            val phase=ExerciseCoaching.guide(sample).phaseNames[1]
            ui.onNodeWithText(phase).performScrollTo().performClick()
            ui.mainClock.advanceTimeBy(400);ui.waitForIdle()
            // EGL draws on a separate handler; allow its queued frame to reach the actual display.
            Thread.sleep(700)
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                java.io.File(folder,"${sample.name}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                }
                bitmap.recycle()
            }
        }
    }
}
