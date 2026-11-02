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
    val isEmpty: Boolean get() = pending.isEmpty()
    fun offer(draft: TrainingDraft) { pending[draft.key] = draft }
    fun peek(key: TrainingDraftKey): TrainingDraft? = pending[key]
    fun snapshot(sessionId: String): List<TrainingDraft> = pending.values.filter { it.key.sessionId == sessionId }
    /** A completed write may only acknowledge the exact draft it captured before suspension. */
    fun acknowledge(drafts: List<TrainingDraft>) {
        drafts.forEach { if (pending[it.key] === it) pending.remove(it.key) }
    }
    suspend fun persist(drafts: List<TrainingDraft>, write: suspend (List<TrainingDraft>) -> Unit) {
        if (drafts.isEmpty()) return
        write(drafts)
        acknowledge(drafts)
    }
    fun take(key: TrainingDraftKey): TrainingDraft? = pending.remove(key)
    fun takeSession(sessionId: String): List<TrainingDraft> = pending.keys.filter { it.sessionId == sessionId }
        .mapNotNull { pending.remove(it) }
}
