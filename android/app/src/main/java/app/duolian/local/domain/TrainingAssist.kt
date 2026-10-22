package app.duolian.local.domain

data class SetTarget(val movement: Movement, val set: TrainingSet, val index: Int)
data class PreviousMovement(val session: Session, val movement: Movement)

object TrainingAssist {
    fun previous(current: Session, exerciseId: String, sessions: List<Session>): PreviousMovement? = sessions
        .filter { it.id != current.id && it.endedAt != null && it.endedAt <= current.startedAt }
        .sortedByDescending { it.startedAt }
        .firstNotNullOfOrNull { session -> session.movements.firstOrNull {
            it.exerciseId == exerciseId && it.sets.any { set -> set.countsVolume }
        }?.let { PreviousMovement(session, it) } }

    fun reference(movement: Movement, index: Int, previous: Movement?): TrainingSet? {
        val current = movement.sets[index]
        val candidates = previous?.sets?.filter { it.done && it.effectiveKind == current.effectiveKind }.orEmpty()
        val ordinal = movement.sets.take(index).count { it.effectiveKind == current.effectiveKind }
        return candidates.getOrNull(ordinal)
    }

    /** Round-robin supersets, sequential sets for standalone movements. */
    fun next(session: Session): SetTarget? {
        val visited = mutableSetOf<Int>()
        for (movement in session.movements) {
            val group = movement.supersetGroup
            val members = if (group == null) listOf(movement) else {
                if (!visited.add(group)) continue
                session.movements.filter { it.supersetGroup == group }
            }
            val rounds = members.maxOf { it.sets.size }
            for (index in 0 until rounds) for (member in members) {
                val set = member.sets.getOrNull(index) ?: continue
                if (!set.done) return SetTarget(member, set, index)
            }
        }
        return null
    }

    fun updateValues(s: Session, movementId: String, setId: String, grams: Long, reps: Int): Session {
        require(s.endedAt == null && s.pausedAt == null) { "请先恢复训练" }
        val movement = s.movements.first { it.id == movementId }
        val set = movement.sets.first { it.id == setId }
        Rules.trainingSet(set.copy(grams = grams, reps = reps))
        // A delayed inline save must never overwrite a set that was already completed.
        if (set.done) return s
        return s.copy(movements = s.movements.map { m -> if (m.id != movementId) m else m.copy(
            sets = m.sets.map { if (it.id == setId) it.copy(grams = grams, reps = reps) else it }) })
    }

    fun completeValues(s: Session, movementId: String, setId: String, grams: Long, reps: Int,
        restSeconds: Int, now: Long): Session {
        require(s.endedAt == null && s.pausedAt == null) { "请先恢复训练" }
        if (s.movements.first { it.id == movementId }.sets.first { it.id == setId }.done) return s
        return Rules.complete(updateValues(s, movementId, setId, grams, reps), movementId, setId, restSeconds, now)
    }

    fun undo(s: Session, movementId: String, setId: String, now: Long): Session {
        require(s.endedAt == null && s.pausedAt == null) { "请先恢复训练" }
        if (!s.movements.first { it.id == movementId }.sets.first { it.id == setId }.done) return s
        return Rules.complete(s, movementId, setId, 0, now)
    }
}
