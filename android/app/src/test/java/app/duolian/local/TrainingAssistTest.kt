package app.duolian.local

import app.duolian.local.domain.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class TrainingAssistTest {
    private fun movement(id: String = "m", exercise: String = "bench", group: Int? = null, count: Int = 2) =
        Movement(id, exercise, "卧推", List(count) { TrainingSet("${id}_$it", 40000, 10) }, group)
    private fun session(movements: List<Movement> = listOf(movement())) =
        Session("active", "计划", 10000, movements = movements)
    private fun finished(start: Long, grams: Long, kind: SetKind = SetKind.WORK) =
        Session("s$start", "旧训练", start, endedAt = start + 100,
            movements = listOf(movement().copy(sets = listOf(TrainingSet(grams = grams, reps = 8, done = true, kind = kind)))))

    @Test fun previousUsesLatestFinishedSessionBeforeCurrentStart() {
        val current = session()
        val history = listOf(finished(1000, 30000), finished(5000, 50000), finished(20000, 90000),
            current.copy(id = "other-active"))
        assertEquals(50000L, TrainingAssist.previous(current, "bench", history)!!.movement.sets.single().grams)
    }
    @Test fun previousIgnoresWarmupOnlyAndUnfinishedHistory() {
        assertNull(TrainingAssist.previous(session(), "bench", listOf(finished(5000, 20000, SetKind.WARMUP))))
        assertNull(TrainingAssist.previous(session(), "other", listOf(finished(5000, 40000))))
    }
    @Test fun referencesMatchKindAndOrdinalWithoutBorrowingDifferentSets() {
        val previous = movement().copy(sets = listOf(TrainingSet(grams = 20000, kind = SetKind.WARMUP, done = true),
            TrainingSet(grams = 50000, done = true)))
        assertEquals(50000L, TrainingAssist.reference(movement(), 0, previous)!!.grams)
        assertNull(TrainingAssist.reference(movement(), 1, previous))
    }
    @Test fun nextSequentialSetChangesAfterCompletion() {
        val s = session()
        assertEquals("m_0", TrainingAssist.next(s)!!.set.id)
        val after = TrainingAssist.completeValues(s, "m", "m_0", 45000, 12, 60, 11000)
        assertEquals("m_1", TrainingAssist.next(after)!!.set.id)
        assertEquals(540.0, Rules.volumeKg(after), .00001)
        assertEquals(71000L, after.restDeadline!!)
    }
    @Test fun supersetsAlternateExercisesWithinEachRound() {
        var s = session(listOf(movement("a", group = 1), movement("b", group = 1)))
        val order = mutableListOf<String>()
        repeat(4) {
            val next = TrainingAssist.next(s)!!
            order += next.set.id
            s = TrainingAssist.completeValues(s, next.movement.id, next.set.id, 40000, 10, 60, 11000)
        }
        assertEquals(listOf("a_0", "b_0", "a_1", "b_1"), order)
        assertNull(TrainingAssist.next(s))
    }
    @Test fun unequalSupersetLengthsStillExposeRemainingSets() {
        var s = session(listOf(movement("a", group = 1, count = 1), movement("b", group = 1, count = 2)))
        repeat(2) {
            val next = TrainingAssist.next(s)!!
            s = TrainingAssist.completeValues(s, next.movement.id, next.set.id, 40000, 10, 60, 11000)
        }
        assertEquals("b_1", TrainingAssist.next(s)!!.set.id)
    }
    @Test fun quickEditingKeepsMetadataAndSavesOnlyValues() {
        val original = movement().copy(sets = listOf(TrainingSet("x", 40000, rpe = 8, restSeconds = 90, kind = SetKind.DROP)))
        val after = TrainingAssist.updateValues(session(listOf(original)), "m", "x", 37500, 12).movements.single().sets.single()
        assertEquals(37500L, after.grams)
        assertEquals(12, after.reps)
        assertEquals(8, after.rpe)
        assertEquals(90, after.restSeconds)
        assertEquals(SetKind.DROP, after.kind)
    }
    @Test fun lateDebouncedSaveCannotOverwriteCompletedValues() {
        val done = TrainingAssist.completeValues(session(), "m", "m_0", 50000, 8, 60, 11000)
        assertEquals(done, TrainingAssist.updateValues(done, "m", "m_0", 20000, 1))
    }
    @Test fun undoStopsRestAndRemovesSetVolume() {
        val done = TrainingAssist.completeValues(session(), "m", "m_0", 40000, 10, 60, 11000)
        val undone = TrainingAssist.undo(done, "m", "m_0", 12000)
        assertEquals(0.0, Rules.volumeKg(undone), .00001)
        assertNull(undone.restDeadline)
        assertEquals("m_0", TrainingAssist.next(undone)!!.set.id)
    }
    @Test fun repeatedCompleteIsIdempotentAndCannotOverwriteValues() {
        val done = TrainingAssist.completeValues(session(), "m", "m_0", 45000, 12, 60, 11000)
        assertEquals(done, TrainingAssist.completeValues(done, "m", "m_0", 20000, 1, 60, 12000))
    }
    @Test fun repeatedUndoCannotCompleteTheSetAgain() {
        val done = TrainingAssist.completeValues(session(), "m", "m_0", 45000, 12, 60, 11000)
        val undone = TrainingAssist.undo(done, "m", "m_0", 12000)
        assertEquals(undone, TrainingAssist.undo(undone, "m", "m_0", 13000))
    }
    @Test fun quickCompletionUsesOverrideAndPreservesRestOnRoundTrip() {
        val m = movement().copy(sets = listOf(TrainingSet("x", 40000, restSeconds = 90)))
        val done = TrainingAssist.completeValues(session(listOf(m)), "m", "x", 45000, 8, 60, 11000)
        val restored = Codec.decodeFromString<Session>(Codec.encodeToString(done))
        assertEquals(101000L, restored.restDeadline!!)
        assertEquals(80000L, Rules.restMs(restored, 21000))
    }
    @Test fun invalidValuesDoNotChangeSession() {
        val s = session()
        assertTrue(runCatching { TrainingAssist.completeValues(s, "m", "m_0", -1, 10, 60, 11000) }.isFailure)
        assertTrue(runCatching { TrainingAssist.updateValues(s, "m", "m_0", 40000, 1001) }.isFailure)
        assertFalse(s.movements.single().sets.first().done)
    }
    @Test fun pausedAndFinishedSessionsRejectInlineEdits() {
        val s = session()
        assertTrue(runCatching { TrainingAssist.updateValues(Rules.pause(s, 11000), "m", "m_0", 45000, 8) }.isFailure)
        val done = TrainingAssist.completeValues(s, "m", "m_0", 40000, 10, 60, 11000)
        assertTrue(runCatching { TrainingAssist.updateValues(Rules.finish(done, 12000), "m", "m_1", 45000, 8) }.isFailure)
    }
}
