package app.duolian.local

import app.duolian.local.domain.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class TrainingDraftPersistenceTest {
    private fun draft(set: String = "a", grams: Long = 45000) =
        TrainingDraft(TrainingDraftKey("s", "m", set), grams, 10)

    @Test fun failedTransactionKeepsAllInputsForRetry() = runBlocking {
        val pending = TrainingDrafts()
        pending.offer(draft()); pending.offer(draft("b"))
        try { pending.persist(pending.snapshot("s")) { error("disk full") }; fail("must fail") }
        catch (_: IllegalStateException) { }
        assertEquals(2, pending.snapshot("s").size)
        var saved = emptyList<TrainingDraft>()
        pending.persist(pending.snapshot("s")) { saved = it }
        assertEquals(2, saved.size); assertTrue(pending.isEmpty)
    }
    @Test fun anOlderSuccessfulWriteCannotRemoveNewerEdits() = runBlocking {
        val pending = TrainingDrafts(); val original = draft(); pending.offer(original)
        val latest = draft(grams = 60000)
        pending.persist(listOf(original)) { pending.offer(latest) }
        assertSame(latest, pending.peek(latest.key))
        pending.persist(pending.snapshot("s")) { }
        assertTrue(pending.isEmpty)
    }
    @Test fun cancelledWriteLeavesAnExactRetrySnapshot() = runBlocking {
        val pending = TrainingDrafts(); val value = draft(); pending.offer(value)
        val entered = CompletableDeferred<Unit>()
        val writer = launch {
            pending.persist(pending.snapshot("s")) { entered.complete(Unit); awaitCancellation() }
        }
        entered.await(); writer.cancelAndJoin()
        assertSame(value, pending.peek(value.key))
    }
    @Test fun equalButNewlyEnteredValueStillRequiresItsOwnAcknowledgement() = runBlocking {
        val pending = TrainingDrafts(); val first = draft(); pending.offer(first)
        val second = first.copy()
        pending.persist(listOf(first)) { pending.offer(second) }
        assertSame(second, pending.peek(first.key))
    }
    @Test fun flushingOneSessionPreservesOtherSessionInputs() = runBlocking {
        val pending = TrainingDrafts(); val first = draft()
        val other = first.copy(key = first.key.copy(sessionId = "other"))
        pending.offer(first); pending.offer(other)
        pending.persist(pending.snapshot("s")) { }
        assertSame(other, pending.peek(other.key)); assertNull(pending.peek(first.key))
    }
}
