package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class OutdoorRecorderTest {
    private val epoch = 1_700_000_000_000L
    private fun recorder() = OutdoorRecorder().apply { start(OutdoorKind.RUN, epoch, 1000) }
    private fun OutdoorRecorder.fixAt(tick: Long, lat: Double = 30.0, precision: Float = 5f,
        speed: Double? = 2.0, delivery: Long = tick, wall: Long = epoch + delivery - 1000) =
        fix(lat, 120.0, precision, tick, delivery, wall, speed, .2)

    @Test fun pauseResumeExcludesWaitingAndRepeatedResumeDoesNotResetTimer() {
        val r = recorder()
        r.advance(epoch + 10000, 11000)
        r.pause(epoch + 11000, 12000)
        r.advance(epoch + 61000, 62000)
        assertEquals(11000L, r.run!!.elapsedMs)
        r.resume(epoch + 61000, 62000)
        r.resume(epoch + 62000, 63000)
        assertEquals(13000L, r.finished(epoch + 63000, 64000)!!.elapsedMs)
    }
    @Test fun stoppedScreenTicksAreAccountedFromMonotonicClock() {
        val r = recorder()
        r.advance(epoch + 120000, 121000)
        assertEquals(120000L, r.run!!.elapsedMs)
        assertEquals(OutdoorSignal.WEAK, r.signal)
    }
    @Test fun wallClockCorrectionsDoNotChangeDurationOrFreezeCoordinates() {
        val r = recorder()
        r.fixAt(1000)
        r.fixAt(6000, 30.0001, wall = epoch + 3600000)
        r.fixAt(11000, 30.0002, wall = epoch - 3600000)
        assertEquals(10000L, r.run!!.elapsedMs)
        assertEquals(3, r.run!!.points.size)
        assertEquals(epoch + 10000, r.run!!.points.last().time)
        OutdoorMath.validate(r.run!!)
    }
    @Test fun recoveryKeepsCheckpointAndRequiresExplicitResume() {
        val old = recorder().apply { fixAt(1000); fixAt(6000, 30.0001) }.run!!
        val next = OutdoorRecorder()
        next.recover(old, 200)
        next.advance(epoch + 3600000, 2200)
        assertEquals(OutdoorStatus.PAUSED, next.run!!.status)
        assertEquals(old.elapsedMs, next.run!!.elapsedMs)
        assertEquals(OutdoorSignal.INTERRUPTED, next.signal)
        next.resume(epoch + 3600000, 2200)
        next.fixAt(3200, 31.0, wall = epoch + 3601000)
        assertEquals(old.distanceMeters, next.run!!.distanceMeters, .001)
        assertTrue(next.run!!.points.last().segment > old.points.last().segment)
        assertEquals(old.elapsedMs + 1000, next.run!!.elapsedMs)
        OutdoorMath.validate(next.run!!)
    }
    @Test fun recoveredPauseCanBeFinishedWithoutAddingDeadTime() {
        val old = recorder().apply { advance(epoch + 5000, 6000) }.run!!
        val next = OutdoorRecorder().apply { recover(old, 100) }
        val finished = next.finished(epoch + 100000, 10000)!!
        assertEquals(5000L, finished.elapsedMs)
        assertEquals(OutdoorStatus.FINISHED, finished.status)
        OutdoorMath.validate(finished)
    }
    @Test fun delayedAndOutOfOrderFixesCannotChangeTrackOrSpeed() {
        val r = recorder().apply { fixAt(6000, 30.0001) }
        val before = r.run
        r.fixAt(5000, 31.0, speed = 11.0)
        r.fixAt(6000, 31.0, speed = 11.0)
        r.fixAt(8000, 31.0, delivery = 24000)
        r.fixAt(30000, 31.0, delivery = 29000)
        assertEquals(before, r.run)
        assertEquals(2.0, r.speedMps!!, .0)
    }
    @Test fun staleFixFromBeforeResumeIsIgnored() {
        val r = recorder().apply { fixAt(1000); pause(epoch + 1000, 2000); resume(epoch + 5000, 6000) }
        val before = r.run
        r.fixAt(5000, 31.0, delivery = 7000)
        assertEquals(before, r.run)
        assertNull(r.speedMps)
    }
    @Test fun poorAccuracySplitsRouteAndCannotBridgeMissingDistance() {
        val r = recorder().apply { fixAt(1000) }
        r.fixAt(6000, 30.0001, precision = 90f)
        assertEquals(OutdoorSignal.INACCURATE, r.signal)
        assertNull(r.accuracy)
        r.fixAt(11000, 31.0)
        assertEquals(0.0, r.run!!.distanceMeters, .0)
        assertEquals(2, r.run!!.points.size)
        assertNotEquals(r.run!!.points.first().segment, r.run!!.points.last().segment)
    }
    @Test fun speedExpiresAndGpsLossMakesOnlyOneNewSegment() {
        val r = recorder().apply { fixAt(1000) }
        r.advance(epoch + 11000, 12000)
        assertNull(r.speedMps)
        r.advance(epoch + 31000, 32000)
        val segment = r.run!!.segment
        r.advance(epoch + 40000, 41000)
        assertEquals(segment, r.run!!.segment)
        r.fixAt(42000, 31.0)
        assertEquals(0.0, r.run!!.distanceMeters, .0)
        assertEquals(OutdoorSignal.GOOD, r.signal)
    }
    @Test fun rejectedJumpCannotSupplyCurrentSpeedOrPoisonNextGoodFix() {
        val r = recorder().apply { fixAt(1000) }
        r.fixAt(6000, 31.0, speed = 4.0)
        assertNull(r.speedMps)
        assertEquals(1, r.run!!.points.size)
        r.fixAt(11000, 30.0002)
        assertEquals(2, r.run!!.points.size)
        assertEquals(22.239, r.run!!.distanceMeters, .01)
    }
    @Test fun pauseGpsOffAndRepeatedPauseDoNotAddSegmentsOrAcceptPositions() {
        val r = recorder().apply { fixAt(1000) }
        r.pause(epoch + 1000, 2000, OutdoorSignal.LOCATION_OFF)
        val segment = r.run!!.segment
        r.pause(epoch + 2000, 3000, OutdoorSignal.LOCATION_OFF)
        r.fixAt(6000, 31.0)
        assertEquals(segment, r.run!!.segment)
        assertEquals(1, r.run!!.points.size)
        assertEquals(1000L, r.run!!.elapsedMs)
    }
    @Test fun pauseStepsAndCounterResetDoNotLeakIntoWorkout() {
        val r = recorder()
        r.stepCounter(1000, epoch, 1000)
        r.stepCounter(1020, epoch + 1000, 2000)
        r.pause(epoch + 1000, 2000)
        r.stepCounter(1200, epoch + 2000, 3000)
        r.resume(epoch + 3000, 4000)
        r.stepCounter(1300, epoch + 4000, 5000)
        r.stepCounter(1310, epoch + 5000, 6000)
        r.stepCounter(2, epoch + 6000, 7000)
        r.stepCounter(7, epoch + 7000, 8000)
        assertEquals(35L, r.run!!.steps)
    }
    @Test fun delayedStepBatchFromBeforeResumeCannotInflateWorkout() {
        val r = recorder()
        r.stepCounter(100, epoch, 1000)
        r.pause(epoch + 1000, 2000)
        r.resume(epoch + 5000, 6000)
        r.stepCounter(150, epoch + 6000, 7000, sampleTick = 5000)
        r.stepCounter(170, epoch + 7000, 8000)
        r.stepCounter(180, epoch + 8000, 9000)
        assertEquals(10L, r.run!!.steps)
    }
    @Test fun finishingRetainsOriginalUntilCommitAndClear() {
        val r = recorder().apply { fixAt(1000) }
        val finished = r.finished(epoch + 1000, 2000)!!
        assertNotNull(r.run)
        assertEquals(OutdoorStatus.ACTIVE, r.run!!.status)
        OutdoorMath.validate(finished)
        r.clear()
        assertNull(r.run); assertNull(r.speedMps); assertEquals(OutdoorSignal.IDLE, r.signal)
    }
    @Test fun startingOverUnfinishedRunFailsWithoutLosingIt() {
        val r = recorder()
        val id = r.run!!.id
        try { r.start(OutdoorKind.WALK, epoch + 1000, 2000); fail("must preserve old record") }
        catch (_: IllegalStateException) { }
        assertEquals(id, r.run!!.id)
    }
    @Test fun routeLimitCannotKeepAdvertisingARecordedRoute() {
        val points = (0 until OutdoorMath.MAX_POINTS).map { RoutePoint(30.0, 120.0, epoch + it, 5f) }
        val next = OutdoorRecorder().apply {
            recover(OutdoorRun(kind = OutdoorKind.RUN, startedAt = epoch, updatedAt = epoch + 20000, points = points), 1000)
            resume(epoch + 20000, 1000)
            fixAt(2000, 30.0001, wall = epoch + 21000)
        }
        assertEquals(OutdoorSignal.LIMIT, next.signal)
        assertNull(next.speedMps)
        assertEquals(OutdoorMath.MAX_POINTS, next.run!!.points.size)
    }
}
