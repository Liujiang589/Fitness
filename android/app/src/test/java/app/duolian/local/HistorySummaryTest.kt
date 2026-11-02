package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HistorySummaryTest {
    private fun movement(exercise: String, done: Boolean = true, warmup: Boolean = false) =
        Movement(exerciseId = exercise, name = exercise,
            sets = listOf(TrainingSet(grams = 40000, reps = 10, done = done, warmup = warmup)))
    private fun record(id: String, start: Long, end: Long? = start + 1,
        movements: List<Movement> = listOf(movement("bench"))) =
        Session(id, id, start, endedAt = end, movements = movements)
    private fun lookup(history: List<Session>) = TrainingAssist.previousByExercise("current", 100,
        setOf("bench", "squat", "missing"), history)

    @Test fun newestEligibleMovementWinsRegardlessOfHistoryOrder() {
        val latest = record("latest", 80)
        val squat = record("squat", 70, movements = listOf(movement("squat")))
        val result = lookup(listOf(latest, record("old", 10), squat))
        assertEquals(latest, result["bench"]!!.session)
        assertEquals(squat, result["squat"]!!.session)
        assertFalse(result.containsKey("missing"))
    }
    @Test fun currentUnfinishedAndOverlappingSessionsNeverBecomeReference() {
        val valid = record("valid", 10)
        val result = lookup(listOf(record("current", 90), record("active", 95, null),
            record("overlap", 80, 101), record("future", 110), valid))
        assertEquals(valid, result["bench"]!!.session)
    }
    @Test fun unfinishedAndWarmupOnlyMovementsDoNotHideOlderWork() {
        val valid = record("valid", 10)
        val result = lookup(listOf(record("warmup", 90, movements = listOf(movement("bench", warmup = true))),
            record("undone", 80, movements = listOf(movement("bench", done = false))), valid))
        assertEquals(valid, result["bench"]!!.session)
    }
    @Test fun tiedSessionDatesAndRepeatedMovementsKeepFirstEligibleEntry() {
        val first = movement("bench"); val second = movement("bench")
        val session = record("first", 80, movements = listOf(movement("bench", done = false), first, second))
        assertEquals(first, lookup(listOf(session, record("tie", 80)))["bench"]!!.movement)
    }
    @Test fun editsAndDeletedHistoryAreReflectedInANewSnapshot() {
        val old = record("old", 20); val recent = record("recent", 80)
        assertEquals(recent, lookup(listOf(old, recent))["bench"]!!.session)
        assertEquals(old, lookup(listOf(old))["bench"]!!.session)
        assertTrue(lookup(emptyList()).isEmpty())
    }
    @Test fun batchMatchesThePreviousSortedLookupOnLargeMixedHistory() {
        val exercises = (0..23).map { "e$it" }.toSet()
        val history = (0..1999).map { n -> record("s$n", n.toLong(), if (n % 11 == 0) null else n + 1L,
            (0..5).map { j -> movement("e${(n + j) % 24}", done = n % 5 != 0, warmup = n % 7 == 0) }) }.reversed()
        val result = TrainingAssist.previousByExercise("current", 1900, exercises, history)
        exercises.forEach { exercise ->
            val expected = history.filter { it.endedAt != null && it.endedAt <= 1900 }
                .sortedByDescending { it.startedAt }.firstNotNullOfOrNull { s ->
                    s.movements.firstOrNull { it.exerciseId == exercise && it.sets.any { it.countsVolume } }
                        ?.let { PreviousMovement(s, it) }
                }
            assertEquals(expected, result[exercise])
        }
    }
    private val sunday = LocalDate.of(2026, 11, 1)
    private fun at(date: LocalDate) = date.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    @Test fun homeWeekCountsCompletedDaysWithoutCountingActiveWorkouts() {
        val history = listOf(record("mon", at(sunday.minusDays(6))), record("sun", at(sunday)),
            record("sun2", at(sunday)), record("last-week", at(sunday.minusDays(7))),
            record("active", at(sunday.minusDays(1)), null))
        val week = TrainingHome.overview(emptyList(), history, emptyList(), sunday).rhythm
        assertEquals(3, week.sessions); assertEquals(2, week.trained.size)
        assertEquals(1200.0, week.volumeKg, 0.0)
        assertEquals(1, week.streak)
    }
    @Test fun midnightWeekRolloverRefreshesAllHomeDatesTogether() {
        val history = listOf(record("sunday", at(sunday)))
        val next = TrainingHome.overview(emptyList(), history, emptyList(), sunday.plusDays(1))
        assertEquals(sunday.plusDays(1), next.rhythm.monday)
        assertEquals(0, next.rhythm.sessions); assertEquals(1, next.rhythm.streak)
        assertTrue(next.rhythm.trained.isEmpty())
    }
    @Test fun emptyHistoryDoesNotInventTrainingOrRecommendedPlans() {
        val result = TrainingHome.overview(emptyList(), emptyList(), emptyList(), sunday)
        assertEquals(HomeMode.FIRST_PLAN, result.focus.mode)
        assertNull(result.focus.plan); assertEquals(0, result.rhythm.sessions)
        assertEquals(0.0, result.rhythm.volumeKg, 0.0); assertTrue(result.alerts.isEmpty())
    }
}
