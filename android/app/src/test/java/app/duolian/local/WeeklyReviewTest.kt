package app.duolian.local

import app.duolian.local.domain.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class WeeklyReviewTest {
    private val today = LocalDate.of(2026, 10, 23)
    private fun at(daysAgo: Long) = today.minusDays(daysAgo).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun session(daysAgo: Long, reps: Int = 8, grams: Long = 20000, name: String = "卧推",
        kind: SetKind = SetKind.WORK, done: Boolean = true) = Session(title = "训练", startedAt = at(daysAgo),
        endedAt = at(daysAgo) + 120000, movements = listOf(Movement(exerciseId = "bench", name = name,
            sets = listOf(TrainingSet(grams = grams, reps = reps, done = done, kind = kind)))))
    private fun review(s: List<Session> = emptyList(), runs: List<OutdoorRun> = emptyList(), food: List<Food> = emptyList()) =
        WeeklyReviews.build(s, runs, food, today)

    @Test fun consecutiveSevenDayWindowsHaveNoOverlapAndIgnoreFutureOrUnfinished() {
        val r = review(listOf(0L, 6L, 7L, 13L, 14L, -1L).map { session(it) } + session(0).copy(endedAt = null))
        assertEquals(today.minusDays(6), r.from); assertEquals(today, r.to)
        assertEquals(2, r.strength.sessions); assertEquals(2, r.previous.sessions)
        assertEquals(2, r.strength.days)
    }
    @Test fun elapsedTimeExcludesPausesAndVolumeExcludesWarmups() {
        val r = review(listOf(session(0).copy(pausedMs = 60000), session(0, kind = SetKind.WARMUP)))
        assertEquals(1, r.strength.days); assertEquals(3L, r.strength.minutes)
        assertEquals(160.0, r.strength.volumeKg, .0001)
    }
    @Test fun onlyFinishedOutdoorRecordsInWindowAreCounted() {
        val run = OutdoorRun(kind = OutdoorKind.WALK, startedAt = at(0), updatedAt = at(0) + 60000,
            endedAt = at(0) + 60000, status = OutdoorStatus.FINISHED, distanceMeters = 500.0)
        val r = review(listOf(session(0)), listOf(run, run.copy(startedAt = at(7)),
            run.copy(status = OutdoorStatus.PAUSED, endedAt = null), run.copy(startedAt = at(-1))))
        assertEquals(1, r.outdoorCount); assertEquals(500.0, r.outdoorMeters, .0001)
        assertEquals(2L, r.strength.minutes) // No outdoor duration is added to strength time.
    }
    @Test fun foodAverageUsesLoggedDatesAndNeverFillsMissingDaysWithZero() {
        val f = Food(date = today.toString(), meal = "午餐", name = "记录", protein = 10.0, carbs = 20.0, fat = 0.0)
        assertNull(review().averageRecordedCalories)
        val r = review(food = listOf(f, f.copy(), f.copy(date = today.minusDays(6).toString()),
            f.copy(date = today.minusDays(7).toString()), f.copy(date = "invalid")))
        assertEquals(2, r.foodDays); assertEquals(180.0, r.averageRecordedCalories!!, .0001)
    }
    @Test fun progressUsesBestHistoricalAndCurrentWorkingSetsAtTheSameWeight() {
        val r = review(listOf(session(20, 6), session(9, 8), session(6, 9), session(0, 11), session(0, 10)))
        assertEquals(1, r.progress.size)
        assertEquals(8, r.progress.single().previousReps); assertEquals(11, r.progress.single().reps)
    }
    @Test fun renamedChangedWeightWarmupUnfinishedAndNoBaselineAreNotProgress() {
        val baseline = session(10)
        assertTrue(review(listOf(baseline, session(0, 12, name = "另一动作"), session(0, 12, grams = 25000),
            session(0, 12, kind = SetKind.WARMUP), session(0, 12, done = false),
            session(0, 12).copy(endedAt = null))).progress.isEmpty())
        assertTrue(review(listOf(session(0, 12))).progress.isEmpty())
    }
    @Test fun legacyWarmupFlagIsExcludedEvenIfKindDefaultsToWork() {
        val warmup = session(0, 20).let { s -> s.copy(movements = s.movements.map { m ->
            m.copy(sets = m.sets.map { it.copy(warmup = true) }) }) }
        assertTrue(review(listOf(session(10), warmup)).progress.isEmpty())
    }
    @Test fun fewerRecordsDoNotProduceAClaimOfOvertraining() {
        val r = review(listOf(session(0), session(8), session(10)))
        assertTrue(r.nextStep.contains("漏记或主动休息"))
        assertEquals(0, r.foodDays)
    }
}
