package app.duolian.local.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class TrainingWeek(val sessions: Int, val days: Int, val minutes: Long, val volumeKg: Double)
data class RecordedProgress(val name: String, val grams: Long, val previousReps: Int, val reps: Int)
data class WeeklyReview(val from: LocalDate, val to: LocalDate, val strength: TrainingWeek,
    val previous: TrainingWeek, val outdoorCount: Int, val outdoorMeters: Double,
    val foodDays: Int, val averageRecordedCalories: Double?, val progress: List<RecordedProgress>, val nextStep: String)

/** Comparable rolling 7-day windows. Missing food logs never imply zero intake. */
object WeeklyReviews {
    fun build(sessions: List<Session>, runs: List<OutdoorRun>, food: List<Food>, today: LocalDate): WeeklyReview {
        val from = today.minusDays(6)
        val finished = sessions.filter { it.endedAt != null && Analytics.dateOf(it) <= today }
        val current = finished.filter { Analytics.dateOf(it) in from..today }
        val before = finished.filter { Analytics.dateOf(it) in from.minusDays(7)..from.minusDays(1) }
        fun stats(list: List<Session>) = TrainingWeek(list.size, list.map(Analytics::dateOf).distinct().size,
            list.sumOf { Rules.elapsedMs(it, it.endedAt!!) } / 60000, list.sumOf(Rules::volumeKg))
        val outdoor = runs.filter { it.status == OutdoorStatus.FINISHED &&
            Instant.ofEpochMilli(it.startedAt).atZone(ZoneId.systemDefault()).toLocalDate() in from..today }
        val logged = food.filter { runCatching { LocalDate.parse(it.date) in from..today }.getOrDefault(false) }
        val foodDays = logged.map { it.date }.distinct().size
        val pastBest = mutableMapOf<Triple<String, String, Long>, Int>()
        finished.filter { Analytics.dateOf(it) < from }.forEach { session -> session.movements.forEach { m ->
            m.sets.filter { it.done && it.effectiveKind == SetKind.WORK && it.grams > 0 }.forEach { set ->
                val key = Triple(m.exerciseId, m.name, set.grams)
                pastBest[key] = maxOf(pastBest[key] ?: 0, set.reps)
            }
        } }
        val improved = mutableMapOf<Triple<String, String, Long>, RecordedProgress>()
        current.forEach { session -> session.movements.forEach { m ->
            m.sets.filter { it.done && it.effectiveKind == SetKind.WORK && it.grams > 0 }.forEach { set ->
                val key = Triple(m.exerciseId, m.name, set.grams)
                val previous = pastBest[key]
                if (previous != null && set.reps > previous && set.reps > (improved[key]?.reps ?: 0))
                    improved[key] = RecordedProgress(m.name, set.grams, previous, set.reps)
            }
        } }
        val strength = stats(current)
        val next = when {
            strength.sessions == 0 && outdoor.isEmpty() -> "先选一次适合自己的训练或散步，留下第一份记录。"
            improved.isNotEmpty() -> "已有同记录重量下的次数进步，下一次先保持动作质量与可控节奏。"
            strength.sessions < before.size -> "记录比前一段少，也可能是漏记或主动休息；按自己的安排继续即可。"
            else -> "延续适合自己的节奏，先把训练和恢复安排稳定，再考虑调整负荷。"
        }
        return WeeklyReview(from, today, strength, stats(before), outdoor.size, outdoor.sumOf { it.distanceMeters },
            foodDays, if (foodDays == 0) null else logged.sumOf { it.calories } / foodDays,
            improved.values.sortedByDescending { it.reps - it.previousReps }.take(3), next)
    }
}
