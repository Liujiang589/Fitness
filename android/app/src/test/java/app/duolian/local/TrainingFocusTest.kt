package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class TrainingFocusTest {
    private fun movement(id: String, done: Boolean = false, group: Int? = null) =
        Movement(id, id, id, listOf(TrainingSet(grams = 40000, done = done)), supersetGroup = group)
    private fun session(vararg m: Movement) = Session("session", "训练", 1000, movements = m.toList())
    @Test fun focusAdvancesPastCompletedExercisesWithoutChangingData() {
        val s = session(movement("a", true), movement("b"), movement("c"))
        assertEquals(listOf("b"), TrainingFocus.visibleMovements(s, true).map { it.id })
        assertEquals(3, s.movements.size); assertFalse(s.movements[1].sets[0].done)
    }
    @Test fun supersetRoundStaysTogetherIncludingCompletedPartner() {
        val s = session(movement("a", true, 1), movement("b", group = 1), movement("c"))
        assertEquals(listOf("a", "b"), TrainingFocus.visibleMovements(s, true).map { it.id })
    }
    @Test fun allModePreservesOrderAndPausedTrainingKeepsItsFocus() {
        val s = session(movement("a"), movement("b")).copy(pausedAt = 2000)
        assertEquals(s.movements, TrainingFocus.visibleMovements(s, false))
        assertEquals(listOf("a"), TrainingFocus.visibleMovements(s, true).map { it.id })
    }
    @Test fun completedTrainingAndEmptySessionsRemainReviewable() {
        val s = session(movement("a", true), movement("b", true))
        assertEquals(listOf(s.movements.last()), TrainingFocus.visibleMovements(s, true))
        assertEquals(s.movements, TrainingFocus.visibleMovements(s, false))
        assertTrue(TrainingFocus.visibleMovements(session(), true).isEmpty())
    }
    @Test fun undoingEarlierWorkBringsItBackIntoFocus() {
        val s = session(movement("a", true), movement("b"))
        val undone = s.copy(movements = listOf(movement("a"), s.movements[1]))
        assertEquals("a", TrainingFocus.visibleMovements(undone, true).single().id)
    }
    @Test fun everyTaughtMuscleHasAVisibleRegionAndReadableName() {
        val represented = MuscleDiagram.regions.map { it.part }.toSet()
        MotionKind.entries.forEach { kind ->
            val profile = AnatomyMesh.profile(kind)
            assertTrue("missing map region for $kind", represented.containsAll(profile.primary + profile.secondary))
            assertTrue(profile.primary.intersect(profile.secondary).isEmpty())
            (profile.primary + profile.secondary).forEach { assertTrue(MuscleDiagram.name(it).isNotBlank()) }
        }
    }
    @Test fun frontAndBackKeepChestAndPosteriorMusclesDistinct() {
        assertTrue(MuscleDiagram.regions.filter { it.part == MusclePart.PECTORAL }.all { it.side == BodySide.FRONT })
        assertTrue(MuscleDiagram.regions.filter { it.part in setOf(MusclePart.GLUTE, MusclePart.HAMSTRING, MusclePart.LAT) }
            .all { it.side == BodySide.BACK })
    }
}
