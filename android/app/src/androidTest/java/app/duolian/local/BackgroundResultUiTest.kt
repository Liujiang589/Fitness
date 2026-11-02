package app.duolian.local

import android.os.Looper
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class BackgroundResultUiTest {
    @get:Rule val ui = createComposeRule()
    @Test fun calculationRunsOutsideTheUiThread() {
        val main = AtomicBoolean(true)
        ui.setContent {
            val result = rememberBackgroundResult("sample") {
                main.set(Looper.myLooper() == Looper.getMainLooper()); "ready"
            }
            Text(result ?: "loading")
        }
        ui.waitUntil(5000) { ui.onAllNodesWithText("ready").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(main.get())
    }
    @Test fun delayedOldCalculationCannotReplaceNewInputs() {
        val key = mutableIntStateOf(1)
        val entered = CountDownLatch(1); val release = CountDownLatch(1)
        try {
            ui.setContent {
                val input = key.intValue
                val result = rememberBackgroundResult(input) {
                    if (input == 1) { entered.countDown(); release.await(5, TimeUnit.SECONDS) }
                    "result-$input"
                }
                Text(result ?: "loading")
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            ui.runOnIdle { key.intValue = 2 }
            ui.waitUntil(5000) { ui.onAllNodesWithText("result-2").fetchSemanticsNodes().isNotEmpty() }
            release.countDown(); ui.waitForIdle()
            ui.onNodeWithText("result-2").assertExists()
            ui.onNodeWithText("result-1").assertDoesNotExist()
        } finally { release.countDown() }
    }
}
