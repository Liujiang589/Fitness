package app.duolian.local.domain

data class TrainingDraftKey(val sessionId: String, val movementId: String, val setId: String)
data class TrainingDraft(val key: TrainingDraftKey, val grams: Long, val reps: Int) {
    fun apply(session: Session): Session {
        require(session.id == key.sessionId) { "这场训练已经结束，请刷新当前记录" }
        return TrainingAssist.updateValues(session, key.movementId, key.setId, grams, reps)
    }
}

/** Owned on the UI dispatcher, independent of whether the edited lazy row is still visible. */
class TrainingDrafts {
    private val pending = linkedMapOf<TrainingDraftKey, TrainingDraft>()
    fun offer(draft: TrainingDraft) { pending[draft.key] = draft }
    fun take(key: TrainingDraftKey): TrainingDraft? = pending.remove(key)
    fun takeSession(sessionId: String): List<TrainingDraft> = pending.keys.filter { it.sessionId == sessionId }
        .mapNotNull { pending.remove(it) }
}
