package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AnalyticsTest {
    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 10, 22)

    private fun at(date: LocalDate, hour: Int = 19): Long =
        date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    private fun session(date: LocalDate, sets: List<TrainingSet>, exerciseId: String = "bench",
        name: String = "卧推", minutes: Long = 30): Session {
        val start = at(date)
        return Session(title = name, startedAt = start, endedAt = start + minutes * 60_000,
            movements = listOf(Movement(exerciseId = exerciseId, name = name, sets = sets)))
    }

    private fun work(grams: Long, reps: Int, done: Boolean = true) =
        TrainingSet(grams = grams, reps = reps, done = done)

    @Test fun dailyBucketsIncludeEmptyDaysInOrder() {
        val sessions = listOf(session(today, listOf(work(40000, 10))),
            session(today.minusDays(2), listOf(work(20000, 10))))
        val daily = Analytics.daily(sessions, 4, today)
        assertEquals(4, daily.size)
        assertEquals(today.minusDays(3), daily.first().date)
        assertEquals(today, daily.last().date)
        assertEquals(200.0, daily[1].volumeKg, 0.001)
        assertEquals(0.0, daily[2].volumeKg, 0.001)
        assertEquals(400.0, daily[3].volumeKg, 0.001)
    }

    @Test fun dailyIgnoresUnfinishedSessions() {
        val active = session(today, listOf(work(40000, 10))).copy(endedAt = null)
        val daily = Analytics.daily(listOf(active), 1, today)
        assertEquals(0, daily.single().sessions)
        assertEquals(0.0, daily.single().volumeKg, 0.001)
    }

    @Test fun weeklyBucketsStartOnMonday() {
        val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val sessions = listOf(session(monday, listOf(work(10000, 10))))
        val weeks = Analytics.weekly(sessions, 2, today)
        assertEquals(2, weeks.size)
        assertEquals(monday.minusWeeks(1), weeks.first().date)
        assertEquals(monday, weeks.last().date)
        assertEquals(100.0, weeks.last().volumeKg, 0.001)
    }

    @Test fun volumeByMuscleSplitsCompletedWorkOnly() {
        val exercises = listOf(Exercise("bench", "卧推", "胸部", "杠铃"),
            Exercise("squat", "深蹲", "腿部", "杠铃"))
        val sessions = listOf(
            session(today, listOf(work(40000, 10), work(50000, 10, done = false), work(20000, 10).withKind(SetKind.WARMUP)), "bench"),
            session(today, listOf(work(60000, 10)), "squat", "深蹲"))
        val split = Analytics.volumeByMuscle(sessions, exercises)
        assertEquals(listOf("腿部", "胸部"), split.map { it.first })
        assertEquals(600.0, split[0].second, 0.001)
        assertEquals(400.0, split[1].second, 0.001)
    }

    @Test fun recordsKeepBestEstimatedSinglePerExercise() {
        val sessions = listOf(
            session(today.minusDays(3), listOf(work(40000, 10))),
            session(today, listOf(work(50000, 5))))
        val records = Analytics.records(sessions)
        assertEquals(1, records.size)
        assertEquals(50000L, records.single().grams)
        assertEquals(Progression.oneRm(50000, 5), records.single().oneRm)
        assertEquals(today, records.single().date)
    }

    @Test fun streakCountsConsecutiveDaysEndingToday() {
        val sessions = listOf(today, today.minusDays(1), today.minusDays(2))
            .map { session(it, listOf(work(10000, 10))) }
        assertEquals(3, Analytics.streak(sessions, today))
    }

    @Test fun streakToleratesRestDayToday() {
        val sessions = listOf(today.minusDays(1), today.minusDays(2))
            .map { session(it, listOf(work(10000, 10))) }
        assertEquals(2, Analytics.streak(sessions, today))
    }

    @Test fun streakBreaksOnGap() {
        val sessions = listOf(today, today.minusDays(2)).map { session(it, listOf(work(10000, 10))) }
        assertEquals(1, Analytics.streak(sessions, today))
        assertEquals(0, Analytics.streak(emptyList(), today))
    }

    @Test fun macrosSumOnlyTheRequestedRange() {
        val food = listOf(
            Food(date = today.toString(), meal = "早餐", name = "燕麦", protein = 10.0, carbs = 40.0, fat = 5.0),
            Food(date = today.minusDays(1).toString(), meal = "午餐", name = "鸡胸", protein = 30.0, carbs = 0.0, fat = 3.0),
            Food(date = today.minusDays(30).toString(), meal = "晚餐", name = "旧记录", protein = 99.0, carbs = 99.0, fat = 99.0))
        val macros = Analytics.macros(food, today.minusDays(6), today)
        assertEquals(40.0, macros.protein, 0.001)
        assertEquals(40.0, macros.carbs, 0.001)
        assertEquals(8.0, macros.fat, 0.001)
        assertEquals(40.0 * 4 + 40.0 * 4 + 8.0 * 9, macros.calories, 0.001)
    }

    @Test fun averageCaloriesAveragesRecordedDaysOnly() {
        val food = listOf(
            Food(date = today.toString(), meal = "早餐", name = "A", protein = 0.0, carbs = 0.0, fat = 10.0),
            Food(date = today.minusDays(2).toString(), meal = "早餐", name = "B", protein = 0.0, carbs = 0.0, fat = 20.0))
        assertEquals((90.0 + 180.0) / 2, Analytics.averageCalories(food, 7, today), 0.001)
        assertEquals(0.0, Analytics.averageCalories(emptyList(), 7, today), 0.001)
    }

    @Test fun emptyInputsProduceEmptyStats() {
        assertTrue(Analytics.daily(emptyList(), 7, today).all { it.volumeKg == 0.0 })
        assertTrue(Analytics.records(emptyList()).isEmpty())
        assertTrue(Analytics.volumeByMuscle(emptyList(), emptyList()).isEmpty())
        assertEquals(0L, Analytics.totalMinutes(emptyList()))
    }
}
