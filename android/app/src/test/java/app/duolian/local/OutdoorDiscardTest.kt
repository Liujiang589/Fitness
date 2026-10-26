package app.duolian.local

import app.duolian.local.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OutdoorDiscardTest {
    private fun recording() = OutdoorRecorder().apply {
        start(OutdoorKind.RUN, 100000, 1000)
        fix(30.0, 120.0, 5f, 1000, 1000, 100000, 2.0, .2)
        advance(105000, 6000)
    }

    @Test fun activeDiscardDeletesCheckpointBeforeClearingAndAllowsNewWorkout() = runBlocking {
        val recorder = recording()
        val original = recorder.run!!
        var checkpoint: OutdoorRun? = original
        recorder.discard(original.id) { id ->
            assertEquals(original.id, id)
            assertEquals(original, recorder.run)
            checkpoint = null
        }
        assertNull(checkpoint)
        assertNull(recorder.run)
        assertNull(recorder.mapPosition)
        assertNull(recorder.speedMps)
        assertEquals(OutdoorSignal.IDLE, recorder.signal)
        recorder.advance(110000, 11000)
        recorder.stepCounter(100, 110000, 11000)
        assertNull(recorder.run)
        recorder.start(OutdoorKind.WALK, 120000, 21000)
        assertNotEquals(original.id, recorder.run!!.id)
        assertEquals(0L, recorder.run!!.elapsedMs)
    }

    @Test fun failedDeleteRetainsCheckpointAndSessionForRetry() = runBlocking {
        val recorder = recording()
        val original = recorder.run!!
        try {
            recorder.discard(original.id) { throw java.io.IOException("disk unavailable") }
            fail("failure must not discard recovery state")
        } catch (_: java.io.IOException) { }
        assertEquals(original, recorder.run)
        recorder.discard(original.id) { }
        assertNull(recorder.run)
    }

    @Test fun staleConfirmationCannotDiscardAnotherSession() = runBlocking {
        val recorder = recording()
        val original = recorder.run
        try {
            recorder.discard("old-session") { fail("must not touch persistence") }
            fail("stale id must fail")
        } catch (_: IllegalStateException) { }
        assertEquals(original, recorder.run)
    }

    @Test fun recoveredPausedRecordCanBeDiscardedWithoutCreatingFinishedHistory() = runBlocking {
        val old = recording().run!!
        val recorder = OutdoorRecorder().apply { recover(old, 10) }
        val history = mutableMapOf(old.id to old)
        recorder.discard(old.id) { history.remove(it) }
        assertTrue(history.isEmpty())
        assertNull(recorder.finished(200000, 60000))
    }
}
