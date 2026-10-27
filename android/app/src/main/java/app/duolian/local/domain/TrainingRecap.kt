package app.duolian.local.domain

/** Facts from a saved session; unfinished sets never count as completed work. */
data class TrainingRecap(val movements: Int, val completed: Int, val planned: Int,
    val volumeKg: Double, val elapsedMs: Long) {
    val unfinished: Int get() = planned - completed
    companion object {
        fun from(session: Session): TrainingRecap {
            require(session.endedAt != null) { "训练尚未保存" }
            return TrainingRecap(session.movements.count { m -> m.sets.any { it.done } },
                session.movements.sumOf { m -> m.sets.count { it.done } },
                session.movements.sumOf { it.sets.size }, Rules.volumeKg(session),
                Rules.elapsedMs(session, session.endedAt))
        }
    }
}
