package app.duolian.local.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One exercise that has stopped improving over the recent training block. */
data class PlateauNotice(
    val exerciseId: String,
    val name: String,
    val sessions: Int,
    val bestOneRm: Long,
    val changePercent: Double,
    val lastDate: LocalDate
)

enum class MuscleStatus { LOW, OK, HIGH }

data class MuscleLoad(val muscle: String, val sets: Int, val status: MuscleStatus) {
    val target: IntRange get() = 10..20
}

enum class RiskLevel { UNDER, OK, ELEVATED, HIGH }

/** Acute:chronic workload ratio plus rest-day context. */
data class LoadRisk(
    val acuteKg: Double,
    val chronicKg: Double,
    val ratio: Double,
    val level: RiskLevel,
    val consecutiveDays: Int,
    val headline: String,
    val advice: String
)

/**
 * Rule-based checks over local history. Every threshold is explicit and unit-tested,
 * so the app can explain *why* it raises a flag instead of showing a mystery score.
 */
object Diagnostics {
    /** Weekly set targets per major muscle (working sets, warm-ups excluded). */
    val MAJOR_MUSCLES = listOf("胸部", "背部", "腿部", "肩部", "手臂", "腹部", "小腿")
    const val SETS_LOW = 10
    const val SETS_HIGH = 22

    private fun zone(): ZoneId = ZoneId.systemDefault()

    fun dateOf(session: Session): LocalDate =
        Instant.ofEpochMilli(session.startedAt).atZone(zone()).toLocalDate()

    /**
     * Flags exercises whose best estimated 1RM has not improved by at least 1%
     * across the last [windowDays] days, provided there are at least [minSessions] exposures.
     */
    fun plateaus(sessions: List<Session>, exercises: List<Exercise>, today: LocalDate = LocalDate.now(),
        windowDays: Int = 28, minSessions: Int = 3): List<PlateauNotice> {
        require(windowDays in 7..365) { "窗口天数需为7–365" }
        require(minSessions in 2..20) { "最少次数需为2–20" }
        val from = today.minusDays(windowDays.toLong())
        val byExercise = sessions.filter { it.endedAt != null && dateOf(it) >= from }
            .flatMap { s -> s.movements.map { m -> dateOf(s) to m } }
            .filter { (_, m) -> m.sets.any { it.countsVolume && it.grams > 0 } }
            .groupBy { it.second.exerciseId }

        return byExercise.mapNotNull { (exerciseId, entries) ->
            val ordered = entries.sortedBy { it.first }
            if (ordered.size < minSessions) return@mapNotNull null
            val perSession = ordered.map { (date, movement) ->
                date to (movement.sets.filter { it.countsVolume && it.grams > 0 }
                    .maxOfOrNull { Progression.oneRm(it.grams, it.reps) } ?: 0L)
            }.filter { it.second > 0 }
            if (perSession.size < minSessions) return@mapNotNull null
            val first = perSession.first().second
            val last = perSession.last().second
            val change = (last - first).toDouble() / first
            if (change > 0.01) return@mapNotNull null
            val name = ordered.last().second.name
            PlateauNotice(exerciseId, name, perSession.size, last, change * 100,
                perSession.last().first)
        }.sortedBy { it.changePercent }
    }

    /** Working sets per muscle over the last [days] days, with a low/high verdict. */
    fun muscleBalance(sessions: List<Session>, exercises: List<Exercise>,
        today: LocalDate = LocalDate.now(), days: Int = 7): List<MuscleLoad> {
        require(days in 1..90) { "统计天数需为1–90" }
        val from = today.minusDays(days - 1L)
        val muscleOf = exercises.associate { it.id to it.muscle }
        val counts = mutableMapOf<String, Int>()
        sessions.filter { it.endedAt != null && dateOf(it) in from..today }.forEach { s ->
            s.movements.forEach { m ->
                val muscle = muscleOf[m.exerciseId] ?: return@forEach
                counts[muscle] = (counts[muscle] ?: 0) + m.sets.count { it.countsVolume }
            }
        }
        return counts.entries
            .sortedByDescending { it.value }
            .map { (muscle, sets) ->
                MuscleLoad(muscle, sets, when {
                    sets < SETS_LOW -> MuscleStatus.LOW
                    sets > SETS_HIGH -> MuscleStatus.HIGH
                    else -> MuscleStatus.OK
                })
            }
    }

    /** Major muscles with no working sets in the window. */
    fun missingMuscles(sessions: List<Session>, exercises: List<Exercise>,
        today: LocalDate = LocalDate.now(), days: Int = 7): List<String> {
        val trained = muscleBalance(sessions, exercises, today, days).map { it.muscle }.toSet()
        return MAJOR_MUSCLES.filter { it !in trained }
    }

    /**
     * Acute (last 7 days) vs chronic (last 28 days, weekly average) volume ratio.
     * 1.3–1.5 is a warning band, above 1.5 is a spike; below 0.6 suggests a layoff.
     */
    fun loadRisk(sessions: List<Session>, today: LocalDate = LocalDate.now()): LoadRisk {
        val finished = sessions.filter { it.endedAt != null }
        fun volumeWithin(days: Long): Double {
            val from = today.minusDays(days - 1)
            return finished.filter { dateOf(it) in from..today }.sumOf { Rules.volumeKg(it) }
        }
        val acute = volumeWithin(7)
        val chronicWeekly = volumeWithin(28) / 4.0
        val ratio = if (chronicWeekly <= 0.0) 1.0 else acute / chronicWeekly
        val consecutive = Analytics.streak(finished, today)
        val level = when {
            chronicWeekly <= 0.0 -> RiskLevel.OK
            ratio >= 1.5 -> RiskLevel.HIGH
            ratio >= 1.3 -> RiskLevel.ELEVATED
            ratio < 0.6 -> RiskLevel.UNDER
            else -> RiskLevel.OK
        }
        val headline = when (level) {
            RiskLevel.HIGH -> "本周负荷偏高"
            RiskLevel.ELEVATED -> "负荷上升较快"
            RiskLevel.UNDER -> "近期训练量偏低"
            RiskLevel.OK -> "负荷平稳"
        }
        val advice = when {
            consecutive >= 7 -> "已连续训练 $consecutive 天，建议安排一天完全休息。"
            level == RiskLevel.HIGH -> "比过去 4 周的周均高 ${((ratio - 1) * 100).toInt()}%，建议减量 20–30% 或增加休息日。"
            level == RiskLevel.ELEVATED -> "比周均高 ${((ratio - 1) * 100).toInt()}%，注意睡眠与恢复，别再加量。"
            level == RiskLevel.UNDER -> "仅为周均的 ${(ratio * 100).toInt()}%，若在减载周属正常，否则可逐步补回。"
            else -> "急性与慢性负荷比 ${"%.2f".format(ratio)}，处于合理区间。"
        }
        return LoadRisk(acute, chronicWeekly, ratio, level, consecutive, headline, advice)
    }

    /** Short list of things worth telling the user on the home page. */
    fun alerts(sessions: List<Session>, exercises: List<Exercise>,
        today: LocalDate = LocalDate.now()): List<String> {
        val result = mutableListOf<String>()
        val risk = loadRisk(sessions, today)
        if (risk.level == RiskLevel.HIGH || risk.level == RiskLevel.ELEVATED || risk.consecutiveDays >= 7)
            result += risk.headline
        val plateaus = plateaus(sessions, exercises, today)
        if (plateaus.isNotEmpty()) result += "${plateaus.size} 个动作停滞"
        val missing = missingMuscles(sessions, exercises, today)
        if (missing.isNotEmpty() && sessions.any { it.endedAt != null })
            result += "本周缺 ${missing.joinToString("、")}"
        return result
    }
}
