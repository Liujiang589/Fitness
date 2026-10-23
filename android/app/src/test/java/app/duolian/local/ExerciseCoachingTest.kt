package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class ExerciseCoachingTest {
    @Test fun everyDemonstrationHasAShortSpecificTeachingCue() {
        ExerciseGuides.all.forEach { guide ->
            assertTrue(guide.name, ExerciseCoaching.cue(guide).length in 10..42)
        }
        assertEquals(40, ExerciseGuides.all.map(ExerciseCoaching::cue).distinct().size)
    }
    @Test fun alternativesAreDistinctRealDemonstrationsWithReasonsAndDifferences() {
        ExerciseGuides.all.forEach { source ->
            val options = ExerciseCoaching.alternatives(source)
            assertEquals(options.size, options.map { it.kind }.distinct().size)
            options.forEach { option ->
                assertNotEquals(source.kind, option.kind)
                assertTrue("${source.name} -> ${option.kind}", option.reason.length >= 6 && option.difference.length >= 12)
                assertNotNull(ExerciseCoaching.guide(option.kind))
            }
        }
    }
    @Test fun unsupportedAlternativesRemainEmptyRatherThanGuessingSameMuscleMatches() {
        listOf(MotionKind.LEG_CURL, MotionKind.LATERAL_RAISE, MotionKind.TWIST).forEach {
            assertTrue(ExerciseCoaching.alternatives(ExerciseCoaching.guide(it)).isEmpty())
        }
    }
    @Test fun verticalPullGuidanceDoesNotPresentSelfWeightAsAnAutomaticRegression() {
        val fromPullDown = ExerciseCoaching.alternatives(ExerciseCoaching.guide(MotionKind.PULLDOWN)).single()
        assertEquals(MotionKind.PULLUP, fromPullDown.kind)
        assertTrue(fromPullDown.difference.contains("更高"))
        val fromPullUp = ExerciseCoaching.alternatives(ExerciseCoaching.guide(MotionKind.PULLUP)).single()
        assertEquals(MotionKind.PULLDOWN, fromPullUp.kind)
    }
}
