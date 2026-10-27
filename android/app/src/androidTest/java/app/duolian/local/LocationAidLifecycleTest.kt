package app.duolian.local

import android.content.*
import android.os.*
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import app.duolian.local.outdoor.NetworkLocationAid
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Fake bindings only. Does not start SDK/location, grant permissions or touch workout records. */
class LocationAidLifecycleTest {
    private val app = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private fun onMain(block: () -> Unit) = instrumentation.runOnMainSync(block)
    private class BindingContext(base: Context, val preferences: SharedPreferences) : ContextWrapper(base) {
        var connection: ServiceConnection? = null
        var unbinds = 0
        override fun getSharedPreferences(name: String, mode: Int) = preferences
        override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean {
            this.connection = connection; return true
        }
        override fun unbindService(connection: ServiceConnection) { unbinds++ }
    }
    private fun fixture(test: (BindingContext) -> Unit) {
        val name = "location-aid-test-${System.nanoTime()}"
        val preferences = app.getSharedPreferences(name, 0)
        preferences.edit().putBoolean(NetworkLocationAid.CONSENT_KEY, true).commit()
        try { test(BindingContext(app, preferences)) } finally { app.deleteSharedPreferences(name) }
    }
    @Test fun unresponsiveBindingTimesOutOnceAndReleasesTheConnection() = fixture { context ->
        val failures = AtomicInteger()
        lateinit var aid: NetworkLocationAid
        onMain { aid = NetworkLocationAid(context, {}, { failures.incrementAndGet() }, 50); aid.start() }
        val until = SystemClock.elapsedRealtime() + 2000
        while (failures.get() == 0 && SystemClock.elapsedRealtime() < until) Thread.sleep(10)
        onMain { assertEquals(1, failures.get()); assertEquals(1, context.unbinds); aid.stop() }
    }
    @Test fun lateConnectedCallbackAfterStopCannotStartTheSdk() = fixture { context ->
        val starts = AtomicInteger()
        val failures = AtomicInteger()
        onMain {
            val worker = Messenger(Handler(Looper.getMainLooper()) { starts.incrementAndGet(); true })
            val aid = NetworkLocationAid(context, {}, { failures.incrementAndGet() }, 500)
            aid.start(); val old = context.connection!!; aid.stop()
            old.onServiceConnected(ComponentName(app, "FakeWorker"), worker.binder)
        }
        instrumentation.waitForIdleSync()
        assertEquals(0, starts.get()); assertEquals(0, failures.get()); assertEquals(1, context.unbinds)
    }
    @Test fun deadWorkerReportsFallbackWithoutRetryingOrLeakingTheBinding() = fixture { context ->
        val failures = AtomicInteger()
        onMain {
            val aid = NetworkLocationAid(context, {}, { failures.incrementAndGet() }, 500)
            aid.start(); val old = context.connection!!
            old.onServiceDisconnected(ComponentName(app, "FakeWorker"))
            old.onServiceDisconnected(ComponentName(app, "FakeWorker"))
            assertEquals(1, failures.get()); assertEquals(1, context.unbinds)
            aid.stop()
        }
    }
}
