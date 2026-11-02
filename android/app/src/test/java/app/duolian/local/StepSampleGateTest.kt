package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class StepSampleGateTest {
    @Test fun firstReadingDoesNotImportThePhonesSinceBootTotal() {
        val gate = StepSampleGate()
        assertEquals(StepSampleDecision.BASELINE, gate.accept(90000, 1000, 1000))
        assertEquals(StepSampleDecision.COUNT, gate.accept(90003, 2000, 2000))
    }
    @Test fun duplicateAndOlderEventsCannotRewindTheDailyBaseline() {
        val gate = StepSampleGate()
        var baseline: StepBaseline? = null
        var total = 0L
        fun sample(counter: Long, tick: Long) {
            val decision = gate.accept(counter, tick, 6000)
            if (decision == StepSampleDecision.REJECT) return
            if (decision == StepSampleDecision.BASELINE) baseline = null
            val next = StepBaseline("2026-11-01", counter, 1)
            total += OutdoorMath.stepDelta(baseline, next); baseline = next
        }
        sample(100, 1000); sample(105, 3000)
        sample(102, 2000); sample(106, 3000); sample(108, 4000)
        assertEquals(8L, total)
    }
    @Test fun staleFutureAndNegativeSamplesDoNotConsumeTheInitialBaseline() {
        val gate = StepSampleGate()
        assertEquals(StepSampleDecision.REJECT, gate.accept(10, 1000, 16001))
        assertEquals(StepSampleDecision.REJECT, gate.accept(10, 2001, 2000))
        assertEquals(StepSampleDecision.REJECT, gate.accept(-1, 2000, 2000))
        assertEquals(StepSampleDecision.REJECT, gate.accept(1, -1, 0))
        assertEquals(StepSampleDecision.BASELINE, gate.accept(10, 2000, 2000))
    }
    @Test fun newSubscriptionRejectsQueuedOldCallbacksAndRebaselines() {
        val gate = StepSampleGate()
        gate.accept(100, 1000, 1000)
        gate.reset(10000)
        assertEquals(StepSampleDecision.REJECT, gate.accept(500, 9000, 11000))
        assertEquals(StepSampleDecision.BASELINE, gate.accept(550, 11000, 11000))
        assertEquals(StepSampleDecision.COUNT, gate.accept(552, 12000, 12000))
    }
    @Test fun counterResetAndDayChangeCannotInventSteps() {
        val first = StepBaseline("2026-11-01", 100, 1)
        assertEquals(0L, OutdoorMath.stepDelta(first, first.copy(counter = 3)))
        assertEquals(0L, OutdoorMath.stepDelta(first, first.copy(date = "2026-11-02", counter = 150)))
        assertEquals(0L, OutdoorMath.stepDelta(first, first.copy(boot = 2, counter = 150)))
    }
}
