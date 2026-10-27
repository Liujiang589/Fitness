package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class TrainingExperienceTest {
    private fun session() = Session("s", "训练", 1000, movements = listOf(
        Movement("m", "bench", "卧推", listOf(TrainingSet("a", 40000), TrainingSet("b", 20000)))))
    private fun draft(session: String = "s", set: String = "a", grams: Long = 45000) =
        TrainingDraft(TrainingDraftKey(session, "m", set), grams, 12)

    @Test fun latestDraftWinsWhileOtherSetsRemainIndependent() {
        val buffer = TrainingDrafts()
        buffer.offer(draft()); buffer.offer(draft(set = "b")); buffer.offer(draft(grams = 50000))
        val rows = buffer.takeSession("s")
        assertEquals(2, rows.size)
        assertEquals(50000L, rows.first { it.key.setId == "a" }.grams)
        assertTrue(buffer.takeSession("s").isEmpty())
    }
    @Test fun finishingOneSessionDoesNotConsumeAnotherSessionsDrafts() {
        val buffer = TrainingDrafts()
        buffer.offer(draft()); buffer.offer(draft(session = "other"))
        assertEquals(1, buffer.takeSession("s").size)
        assertEquals("other", buffer.take(draft(session = "other").key)!!.key.sessionId)
    }
    @Test fun cancellationPreventsAQueuedSaveFromApplyingLater() {
        val buffer = TrainingDrafts(); val edit = draft()
        buffer.offer(edit); buffer.take(edit.key)
        assertNull(buffer.take(edit.key))
    }
    @Test fun pendingChangesCanBeFlushedBeforePause() {
        val buffer = TrainingDrafts(); buffer.offer(draft()); buffer.offer(draft(set = "b", grams = 30000))
        val edited = buffer.takeSession("s").fold(session()) { s, d -> d.apply(s) }
        val paused = Rules.pause(edited, 2000)
        assertEquals(listOf(45000L, 30000L), paused.movements.single().sets.map { it.grams })
        assertEquals(2000L, paused.pausedAt)
    }
    @Test(expected = IllegalArgumentException::class) fun oldDraftCannotEditANewSessionWithReusedSetIds() {
        draft().apply(session().copy(id = "another-session"))
    }
    @Test fun delayedDraftCannotOverwriteACompletedSet() {
        val completed = TrainingAssist.completeValues(session(), "m", "a", 60000, 8, 60, 2000)
        assertEquals(completed, draft().apply(completed))
    }
    @Test fun recapExcludesUnfinishedSetsAndWarmupVolume() {
        val finished = session().copy(endedAt = 121000, pausedMs = 20000, movements = listOf(
            Movement("m1", "e1", "卧推", listOf(TrainingSet(grams = 40000, reps = 10, done = true),
                TrainingSet(grams = 20000, reps = 10, done = true, warmup = true))),
            Movement("m2", "e2", "深蹲", listOf(TrainingSet(grams = 100000, reps = 20)))))
        val summary = TrainingRecap.from(finished)
        assertEquals(1, summary.movements); assertEquals(2, summary.completed)
        assertEquals(1, summary.unfinished); assertEquals(400.0, summary.volumeKg, 0.0)
        assertEquals(100000L, summary.elapsedMs)
    }
    @Test fun bodyweightWorkCountsWithoutInventingExternalWeight() {
        val finished = session().copy(endedAt = 2000, movements = listOf(Movement("m", "e", "俯卧撑",
            listOf(TrainingSet(grams = 0, reps = 15, done = true)))))
        val summary = TrainingRecap.from(finished)
        assertEquals(1, summary.movements); assertEquals(1, summary.completed)
        assertEquals(0.0, summary.volumeKg, 0.0)
    }
    @Test(expected = IllegalArgumentException::class) fun activeSessionCannotAppearAsSavedRecap() {
        TrainingRecap.from(session())
    }
}
