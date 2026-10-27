package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class OutdoorSessionGuardTest {
    @Test fun activeAndPausedCurrentSessionsAcceptTheirOwnCommands() {
        for (status in listOf(OutdoorStatus.ACTIVE, OutdoorStatus.PAUSED)) {
            val run = OutdoorRun(kind = OutdoorKind.RUN, startedAt = 1000, status = status)
            OutdoorSessionGuard.requireCurrent(run, run.id)
        }
    }
    @Test fun delayedNotificationCannotPauseOrFinishANewerWorkout() {
        val recorder = OutdoorRecorder().apply { start(OutdoorKind.RUN, 1000, 1000) }
        val before = recorder.run
        try {
            OutdoorSessionGuard.requireCurrent(recorder.run, "previous-workout")
            recorder.pause(5000, 5000)
            fail("stale command was accepted")
        } catch (_: IllegalStateException) { }
        assertEquals(before, recorder.run)
        assertEquals(OutdoorStatus.ACTIVE, recorder.run!!.status)
    }
    @Test fun clearedAndFinishedSessionsCannotBeResumedOrSavedAgain() {
        for (run in listOf(null, OutdoorRun(kind = OutdoorKind.WALK, startedAt = 1000,
            status = OutdoorStatus.FINISHED, endedAt = 2000))) {
            try { OutdoorSessionGuard.requireCurrent(run, run?.id); fail("closed session was accepted") }
            catch (_: IllegalStateException) { }
        }
    }
    @Test fun legacyInternalCommandsStillRequireAnOpenSession() {
        OutdoorSessionGuard.requireCurrent(OutdoorRun(kind = OutdoorKind.WALK, startedAt = 1000), null)
    }
}
