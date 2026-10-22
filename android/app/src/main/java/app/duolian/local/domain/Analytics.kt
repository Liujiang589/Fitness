package app.duolian.local.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayPoint(val date: LocalDate, val volumeKg: Double, val sessions: Int, val minutes: Long) {
    val trained: Boolean get() = sessions > 0
}

data class ExerciseRecord(
    val exerciseId: String, val name: String, val grams: Long,
    val oneRm: Long, val date: LocalDate
)

data class Macros(val protein: Double, val carbs: Double, val fat: Double) {
    val calories: Double get() = protein * 4 + carbs * 4 + fat * 9
    val empty: Boolean get() = protein == 0.0 && carbs == 0.0 && fat == 0.0
}

/** Pure aggregation over local history; everything here is unit-tested without Android. */
object Analytics {
    private fun zone(): ZoneId = ZoneId.systemDefault()

    fun dateOf(session: Session): LocalDate =
        Instant.ofEpochMilli(session.startedAt).atZone(zone()).toLocalDate()

    /** One bucket per day, oldest first, including days without training. */
    fun daily(sessions: List<Session>, days: Int, today: LocalDate = LocalDate.now()): List<DayPoint> {
        require(days in 1..366) { "统计天数需为1–366" }
        val finished = sessions.filter { it.endedAt != null }
        val byDate = finished.groupBy { dateOf(it) }
        return (days - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val daySessions = byDate[date].orEmpty()
            DayPoint(date, daySessions.sumOf { Rules.volumeKg(it) }, daySessions.size,
                daySessions.sumOf { Rules.elapsedMs(it, it.endedAt!!) } / 60_000)
        }
    }

    /** Weekly buckets ending on [today]'s week (Monday-based), oldest first. */
    fun weekly(sessions: List<Session>, weeks: Int, today: LocalDate = LocalDate.now()): List<DayPoint> {
        require(weeks in 1..104) { "周数需为1–104" }
        val monday = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        return (weeks - 1 downTo 0).map { offset ->
            val start = monday.minusWeeks(offset.toLong())
            val end = start.plusDays(6)
            val inWeek = sessions.filter { it.endedAt != null && dateOf(it) in start..end }
            DayPoint(start, inWeek.sumOf { Rules.volumeKg(it) }, inWeek.size,
                inWeek.sumOf { Rules.elapsedMs(it, it.endedAt!!) } / 60_000)
        }
    }

    fun totalVolume(sessions: List<Session>): Double =
        sessions.filter { it.endedAt != null }.sumOf { Rules.volumeKg(it) }

    fun totalMinutes(sessions: List<Session>): Long =
        sessions.filter { it.endedAt != null && it.pausedAt == null }
            .sumOf { Rules.elapsedMs(it, it.endedAt!!) } / 60_000

    /** Volume split by muscle group; unknown exercises fall back to their stored name. */
    fun volumeByMuscle(sessions: List<Session>, exercises: List<Exercise>): List<Pair<String, Double>> {
        val muscleOf = exercises.associate { it.id to it.muscle }
        val totals = mutableMapOf<String, Double>()
        sessions.filter { it.endedAt != null }.forEach { s ->
            s.movements.forEach { m ->
                val volume = m.sets.filter { it.countsVolume }.sumOf { it.grams * it.reps } / 1000.0
                if (volume > 0) {
                    val key = muscleOf[m.exerciseId] ?: "其他"
                    totals[key] = (totals[key] ?: 0.0) + volume
                }
            }
        }
        return totals.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    /** Heaviest estimated single per exercise across finished sessions. */
    fun records(sessions: List<Session>): List<ExerciseRecord> {
        val best = mutableMapOf<String, ExerciseRecord>()
        sessions.filter { it.endedAt != null }.forEach { s ->
            val date = dateOf(s)
            s.movements.forEach { m ->
                m.sets.forEach { set ->
                    if (!set.countsVolume) return@forEach
                    val oneRm = Progression.oneRm(set.grams, set.reps)
                    val previous = best[m.exerciseId]
                    if (previous == null || oneRm > previous.oneRm)
                        best[m.exerciseId] = ExerciseRecord(m.exerciseId, m.name, set.grams, oneRm, date)
                }
            }
        }
        return best.values.sortedByDescending { it.oneRm }
    }

    /** Consecutive training days ending today or yesterday (a rest day today should not reset it). */
    fun streak(sessions: List<Session>, today: LocalDate = LocalDate.now()): Int {
        val days = sessions.filter { it.endedAt != null }.map { dateOf(it) }.toSet()
        if (days.isEmpty()) return 0
        var cursor = if (today in days) today else today.minusDays(1)
        if (cursor !in days) return 0
        var count = 0
        while (cursor in days) { count++; cursor = cursor.minusDays(1) }
        return count
    }

    fun macros(food: List<Food>, from: LocalDate, to: LocalDate): Macros {
        val inRange = food.filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> d in from..to } == true }
        return Macros(inRange.sumOf { it.protein }, inRange.sumOf { it.carbs }, inRange.sumOf { it.fat })
    }

    fun calories(food: List<Food>, date: LocalDate): Double =
        food.filter { it.date == date.toString() }.sumOf { it.calories }

    fun water(food: List<Water>, date: LocalDate): Int =
        food.filter { it.date == date.toString() }.sumOf { it.ml }

    /** Average daily calories over the last [days] days, ignoring days without records. */
    fun averageCalories(food: List<Food>, days: Int, today: LocalDate = LocalDate.now()): Double {
        require(days in 1..366)
        val from = today.minusDays(days - 1L)
        val byDate = food.groupBy { it.date }
        val recorded = byDate.filterKeys { key ->
            runCatching { LocalDate.parse(key) }.getOrNull()?.let { it in from..today } == true
        }
        if (recorded.isEmpty()) return 0.0
        return recorded.values.sumOf { list -> list.sumOf { it.calories } } / recorded.size
    }
}
