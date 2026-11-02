package app.duolian.local.domain

object TrainingFocus {
    /** Keep a superset round visible together; focus never changes execution order or data. */
    fun visibleMovements(session: Session, focusOnly: Boolean): List<Movement> {
        if (!focusOnly) return session.movements
        val next = TrainingAssist.next(session)?.movement ?: session.movements.lastOrNull() ?: return emptyList()
        return next.supersetGroup?.let { group -> session.movements.filter { it.supersetGroup == group } }
            ?: listOf(next)
    }
}
