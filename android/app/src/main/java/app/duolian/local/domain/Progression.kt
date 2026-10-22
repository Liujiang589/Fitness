package app.duolian.local.domain

/** How an individual set contributes to the session. */
enum class SetKind(val label: String, val short: String) {
    WORK("正式组", "正式"), WARMUP("热身组", "热身"), DROP("递减组", "递减"),
    AMRAP("AMRAP 力竭组", "力竭"), BACKOFF("退让组", "退让")
}

/** How a plan expands into sets when a session starts. */
enum class SetScheme(val label: String) {
    STRAIGHT("常规组"), PYRAMID("递增金字塔"), REVERSE_PYRAMID("递减金字塔"), DROP("递减组")
}

/** How consecutive plan exercises are grouped into supersets. */
enum class SupersetMode(val label: String) {
    NONE("单组执行"), PAIRS("两两超级组"), ALL("整轮循环")
}

enum class SuggestionLevel { NO_DATA, PROGRESS, HOLD, DELOAD }

data class Suggestion(val level: SuggestionLevel, val deltaGrams: Long, val headline: String, val detail: String)

object Progression {
    /** Weight granularity used when a scheme generates sets (250 g keeps 2.5 kg and 0.5 kg steps reachable). */
    const val PLATE_STEP_GRAMS = 250L
    /** Default next-session increment, chosen to match a pair of 1.25 kg plates. */
    const val INCREMENT_GRAMS = 2500L

    fun roundToStep(grams: Long, step: Long = PLATE_STEP_GRAMS): Long {
        require(step > 0) { "步进必须为正" }
        val half = step / 2
        return ((grams.coerceAtLeast(0) + half) / step) * step
    }

    /** Builds the initial sets for one movement. Deterministic and unit-tested. */
    fun schemeSets(scheme: SetScheme, baseGrams: Long, baseReps: Int, count: Int): List<TrainingSet> {
        require(count in 1..100) { "组数需为1–100" }
        require(baseGrams in 0..1_000_000) { "重量需为0–1000kg" }
        require(baseReps in 0..1000) { "次数需为0–1000" }
        return List(count) { i ->
            when (scheme) {
                SetScheme.STRAIGHT -> TrainingSet(grams = baseGrams, reps = baseReps)
                SetScheme.PYRAMID -> TrainingSet(
                    grams = roundToStep(baseGrams + baseGrams * 5 * i / 100),
                    reps = (baseReps - i).coerceAtLeast(1))
                SetScheme.REVERSE_PYRAMID -> TrainingSet(
                    grams = roundToStep(baseGrams - baseGrams * 5 * i / 100),
                    reps = baseReps + i)
                SetScheme.DROP -> if (i == 0) TrainingSet(grams = baseGrams, reps = baseReps)
                    else TrainingSet(
                        grams = roundToStep(baseGrams * (100 - 20 * i).coerceAtLeast(40) / 100),
                        reps = baseReps, kind = SetKind.DROP, warmup = false)
            }
        }
    }

    /** Superset group per plan exercise; null means "performed on its own". */
    fun supersetGroups(mode: SupersetMode, count: Int): List<Int?> {
        require(count in 1..100) { "动作数需为1–100" }
        return when (mode) {
            SupersetMode.NONE -> List(count) { null }
            SupersetMode.PAIRS -> List(count) { i ->
                val group = i / 2 + 1
                if (i / 2 * 2 + 1 < count) group else null
            }
            SupersetMode.ALL -> if (count >= 2) List(count) { 1 } else listOf(null)
        }
    }

    /** Epley estimate; a true single is the weight itself, reps above 30 are capped. */
    fun oneRm(grams: Long, reps: Int): Long {
        if (grams <= 0 || reps <= 0) return 0
        if (reps == 1) return grams
        val capped = reps.coerceAtMost(30)
        return roundToStep((grams * (1.0 + capped / 30.0)).toLong(), 100L)
    }

    /** Best estimated 1RM inside one session, ignoring warm-up and non-completed sets. */
    fun bestOneRm(s: Session): Long = s.movements.flatMap { it.sets }
        .filter { it.countsVolume && it.reps in 1..30 }
        .maxOfOrNull { oneRm(it.grams, it.reps) } ?: 0

    /**
     * Decides the next-session weight change for one exercise.
     * [previous] is the most recent finished history for that exercise.
     */
    fun suggest(previous: List<TrainingSet>, targetReps: Int, baseGrams: Long): Suggestion {
        val work = previous.filter { it.countsVolume }
        if (work.isEmpty()) return Suggestion(SuggestionLevel.NO_DATA, 0,
            "暂无历史", "完成一次训练后可按实际表现给出建议")
        val missed = work.count { it.reps < targetReps }
        val maxRpe = work.mapNotNull { it.rpe }.maxOrNull()
        return when {
            missed > 0 -> Suggestion(SuggestionLevel.DELOAD, -minOf(INCREMENT_GRAMS, baseGrams),
                "先减重再冲", "上次有 $missed 组未达 $targetReps 次，建议降 2.5 kg 打稳基础")
            maxRpe != null && maxRpe >= 10 -> Suggestion(SuggestionLevel.HOLD, 0,
                "维持重量", "上次最高 RPE $maxRpe，已是力竭，先巩固动作质量")
            else -> Suggestion(SuggestionLevel.PROGRESS, INCREMENT_GRAMS, "建议 +2.5 kg",
                "上次全部达 $targetReps 次" + (maxRpe?.let { "，最高 RPE $it" } ?: "") + "，可以加重")
        }
    }

    /** Suggestion for a movement inside a session, using the newest matching history entry. */
    fun suggestFor(exerciseId: String, history: List<Session>, targetReps: Int, baseGrams: Long): Suggestion {
        val last = history.firstOrNull { s -> s.endedAt != null && s.movements.any { it.exerciseId == exerciseId } }
        val sets = last?.movements?.firstOrNull { it.exerciseId == exerciseId }?.sets.orEmpty()
        return suggest(sets, targetReps, baseGrams)
    }
}
