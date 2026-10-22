package app.duolian.local.domain

import kotlinx.serialization.Serializable

/** A reusable interval template; the engine is pure so it can be unit-tested without Android. */
@Serializable data class HiitTemplate(
    val id: String = newId(), val name: String,
    val prepareSeconds: Int = 5, val workSeconds: Int = 20,
    val restSeconds: Int = 10, val rounds: Int = 8
)
@Serializable data class HiitRun(
    val id: String = newId(), val templateId: String, val name: String,
    val startedAt: Long, val endedAt: Long, val completedRounds: Int,
    val workSeconds: Int, val restSeconds: Int, val rounds: Int
)

enum class HiitPhaseKind(val label: String) { PREPARE("准备"), WORK("训练"), REST("休息"), DONE("完成") }

data class HiitPhase(val kind: HiitPhaseKind, val round: Int, val remainingMs: Long) {
    val remainingSeconds: Int get() = ((remainingMs + 999) / 1000).toInt()
}

object HiitEngine {
    fun totalMs(t: HiitTemplate): Long =
        (t.prepareSeconds.toLong() + t.rounds.toLong() * (t.workSeconds + t.restSeconds)) * 1000L

    /** Pure phase calculation for a template and elapsed time since the run started. */
    fun phaseAt(t: HiitTemplate, elapsedMs: Long): HiitPhase {
        val elapsed = elapsedMs.coerceAtLeast(0)
        val prepare = t.prepareSeconds * 1000L
        if (elapsed < prepare) return HiitPhase(HiitPhaseKind.PREPARE, 0, prepare - elapsed)
        val cycle = (t.workSeconds + t.restSeconds) * 1000L
        val after = elapsed - prepare
        val round = (after / cycle).toInt()
        if (round >= t.rounds) return HiitPhase(HiitPhaseKind.DONE, t.rounds, 0)
        val inCycle = after % cycle
        val work = t.workSeconds * 1000L
        return if (inCycle < work) HiitPhase(HiitPhaseKind.WORK, round + 1, work - inCycle)
        else HiitPhase(HiitPhaseKind.REST, round + 1, cycle - inCycle)
    }

    /** Rounds fully completed at the given elapsed time, used when a run is aborted early. */
    fun completedRounds(t: HiitTemplate, elapsedMs: Long): Int {
        val phase = phaseAt(t, elapsedMs)
        return when (phase.kind) {
            HiitPhaseKind.DONE -> t.rounds
            HiitPhaseKind.PREPARE -> 0
            HiitPhaseKind.WORK -> phase.round - 1
            HiitPhaseKind.REST -> phase.round
        }.coerceIn(0, t.rounds)
    }

    fun preset(name: String, work: Int, rest: Int, rounds: Int, prepare: Int = 5) =
        HiitTemplate(name = name, prepareSeconds = prepare, workSeconds = work, restSeconds = rest, rounds = rounds)
}

