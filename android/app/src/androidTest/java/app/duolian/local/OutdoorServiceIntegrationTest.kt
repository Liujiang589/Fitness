package app.duolian.local

import android.Manifest
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import android.os.PowerManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.duolian.local.domain.*
import app.duolian.local.outdoor.OutdoorService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Explicit opt-in: only run on a dedicated disposable emulator, never a user's phone. */
class OutdoorServiceIntegrationTest {
    private val app = ApplicationProvider.getApplicationContext<DuolianApp>()
    private val manager get() = app.getSystemService(LocationManager::class.java)
    private fun waitFor(predicate: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 12000
        while (!predicate() && SystemClock.elapsedRealtime() < until) Thread.sleep(50)
        assertTrue("service state did not settle", predicate())
    }
    private fun command(action: String) {
        val id = SystemClock.elapsedRealtimeNanos()
        OutdoorService.send(app, action, id)
        waitFor { OutdoorService.live.value.commandId == id && !OutdoorService.live.value.busy }
        assertNull(OutdoorService.live.value.error)
    }
    private fun fix(latitude: Double) {
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            this.latitude = latitude; longitude = 120.0; accuracy = 5f
            time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            speed = 2f; speedAccuracyMetersPerSecond = .2f
        }
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
    }
    @Test fun foregroundSessionPausesSplitsSavesAndRecoversCheckpoint() = runBlocking {
        assumeTrue("dedicated emulator opt-in required", InstrumentationRegistry.getArguments()
            .getString("outdoorDedicatedDevice") == "true")
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        automation.grantRuntimePermission(app.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        val existing = app.outdoor.runs().map { it.id }.toSet()
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            app.stopService(Intent(app, OutdoorService::class.java))
            waitFor { !OutdoorService.live.value.running }
            app.getSharedPreferences("outdoor-options", 0).edit().putBoolean("daily", false).commit()
            @Suppress("DEPRECATION")
            manager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true, 1, 1)
            manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            command(OutdoorService.RUN)
            val id = OutdoorService.live.value.run!!.id
            fix(30.0)
            waitFor { OutdoorService.live.value.run!!.points.size == 1 }
            Thread.sleep(2200); fix(30.00005)
            waitFor { OutdoorService.live.value.run!!.points.size == 2 }
            val before = OutdoorService.live.value.run!!
            command(OutdoorService.DAILY_OFF)
            assertEquals(before.segment, OutdoorService.live.value.run!!.segment)
            assertEquals(before.points, OutdoorService.live.value.run!!.points)
            command(OutdoorService.PAUSE)
            val paused = OutdoorService.live.value.run!!
            Thread.sleep(1200)
            assertEquals(paused.elapsedMs, OutdoorService.live.value.run!!.elapsedMs)
            command(OutdoorService.RESUME)
            Thread.sleep(100); fix(31.0)
            waitFor { OutdoorService.live.value.run!!.points.size == 3 }
            assertEquals(before.distanceMeters, OutdoorService.live.value.run!!.distanceMeters, .01)
            command(OutdoorService.PAUSE)
            val checkpoint = OutdoorService.live.value.run!!
            app.stopService(Intent(app, OutdoorService::class.java))
            waitFor { !OutdoorService.live.value.running }
            assertEquals(checkpoint.elapsedMs, app.outdoor.runs().first { it.id == id }.elapsedMs)
            command(OutdoorService.RESUME)
            assertTrue(OutdoorService.live.value.run!!.segment > checkpoint.segment)
            assertTrue(OutdoorService.live.value.run!!.elapsedMs - checkpoint.elapsedMs < 1500)
            command(OutdoorService.FINISH)
            waitFor { !OutdoorService.live.value.running }
            val saved = app.outdoor.runs().first { it.id == id }
            assertEquals(OutdoorStatus.FINISHED, saved.status)
            assertEquals(3, saved.points.size)
            assertEquals(before.distanceMeters, saved.distanceMeters, .01)
            OutdoorMath.validate(saved)
        } finally {
            app.stopService(Intent(app, OutdoorService::class.java))
            runCatching { manager.removeTestProvider(LocationManager.GPS_PROVIDER) }
            app.outdoor.runs().filter { it.id !in existing }.forEach { app.outdoor.delete(it.id) }
            scenario.close()
        }
    }
}
