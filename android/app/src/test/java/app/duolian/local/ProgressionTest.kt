package app.duolian.local

import app.duolian.local.domain.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class ProgressionTest {
    private val exercise = Exercise("e", "测试动作", "胸部", "杠铃", 40000)

    @Test fun straightSchemeRepeatsBaseWeightAndReps() {
        val sets = Progression.schemeSets(SetScheme.STRAIGHT, 40000, 10, 3)
        assertEquals(3, sets.size)
        assertTrue(sets.all { it.grams == 40000L && it.reps == 10 && it.effectiveKind == SetKind.WORK })
    }

    @Test fun pyramidAddsFivePercentPerSetAndDropsOneRep() {
        val sets = Progression.schemeSets(SetScheme.PYRAMID, 40000, 10, 3)
        assertEquals(listOf(40000L, 42000L, 44000L), sets.map { it.grams })
        assertEquals(listOf(10, 9, 8), sets.map { it.reps })
    }

    @Test fun reversePyramidSubtractsWeightAndAddsReps() {
        val sets = Progression.schemeSets(SetScheme.REVERSE_PYRAMID, 40000, 10, 3)
        assertEquals(listOf(40000L, 38000L, 36000L), sets.map { it.grams })
        assertEquals(listOf(10, 11, 12), sets.map { it.reps })
    }

    @Test fun pyramidNeverGoesBelowOneRep() {
        val sets = Progression.schemeSets(SetScheme.PYRAMID, 10000, 2, 5)
        assertEquals(listOf(2, 1, 1, 1, 1), sets.map { it.reps })
    }

    @Test fun dropSchemeKeepsFirstWorkingSetThenAddsDropSets() {
        val sets = Progression.schemeSets(SetScheme.DROP, 50000, 8, 4)
        assertEquals(SetKind.WORK, sets[0].effectiveKind)
        assertEquals(50000L, sets[0].grams)
        assertEquals(listOf(SetKind.DROP, SetKind.DROP, SetKind.DROP), sets.drop(1).map { it.effectiveKind })
        assertEquals(listOf(40000L, 30000L, 20000L), sets.drop(1).map { it.grams })
        assertTrue(sets.drop(1).all { it.reps == 8 })
    }

    @Test fun dropSchemeFloorsWeightAtFortyPercent() {
        val sets = Progression.schemeSets(SetScheme.DROP, 50000, 8, 6)
        assertEquals(20000L, sets.last().grams)
    }

    @Test fun supersetModesGroupConsecutiveExercises() {
        assertEquals(listOf(null, null, null), Progression.supersetGroups(SupersetMode.NONE, 3))
        assertEquals(listOf(1, 1, 2, 2), Progression.supersetGroups(SupersetMode.PAIRS, 4))
        assertEquals(listOf(1, 1, null), Progression.supersetGroups(SupersetMode.PAIRS, 3))
        assertEquals(listOf(1, 1, 1), Progression.supersetGroups(SupersetMode.ALL, 3))
        assertEquals(listOf(null), Progression.supersetGroups(SupersetMode.ALL, 1))
    }

    @Test fun planStartAppliesSchemeAndSupersets() {
        val plan = Plan("p", "全身", listOf("e", "e2"), sets = 2, reps = 10,
            scheme = SetScheme.PYRAMID, superset = SupersetMode.PAIRS)
        val other = Exercise("e2", "第二动作", "背部", "器械", 20000)
        val s = Rules.start(plan, listOf(exercise, other), 1000)
        assertEquals(plan.id, s.planId)
        assertEquals(listOf(1, 1), s.movements.map { it.supersetGroup })
        assertEquals(listOf(40000L, 42000L), s.movements[0].sets.map { it.grams })
        assertEquals(listOf(20000L, 21000L), s.movements[1].sets.map { it.grams })
    }

    @Test fun oneRmUsesEpleyFormula() {
        assertEquals(116700L, Progression.oneRm(100000, 5))
        assertEquals(100000L, Progression.oneRm(100000, 1))
        assertEquals(0L, Progression.oneRm(0, 5))
        assertEquals(0L, Progression.oneRm(100000, 0))
    }

    @Test fun sessionBestOneRmIgnoresWarmupAndUnfinishedSets() {
        val e2 = Exercise("e", "测试动作", "胸部", "杠铃", 100000)
        val plan = Plan("p", "测试", listOf("e"), sets = 3, reps = 5)
        val base = Rules.start(plan, listOf(e2), 1000)
        val m = base.movements[0]
        val sets = listOf(
            TrainingSet(grams = 120000, reps = 1, done = true, warmup = true).withKind(SetKind.WARMUP),
            TrainingSet(grams = 100000, reps = 5, done = true),
            TrainingSet(grams = 130000, reps = 5, done = false))
        assertEquals(116700L, Progression.bestOneRm(base.copy(movements = listOf(m.copy(sets = sets)))))
    }

    @Test fun suggestionAsksForMoreWeightAfterAllTargetsHit() {
        val previous = listOf(TrainingSet(grams = 40000, reps = 10, done = true, rpe = 8))
        val s = Progression.suggest(previous, 10, 40000)
        assertEquals(SuggestionLevel.PROGRESS, s.level)
        assertEquals(2500L, s.deltaGrams)
    }

    @Test fun suggestionDeloadsWhenTargetRepsMissed() {
        val previous = listOf(TrainingSet(grams = 40000, reps = 7, done = true))
        val s = Progression.suggest(previous, 10, 40000)
        assertEquals(SuggestionLevel.DELOAD, s.level)
        assertTrue(s.deltaGrams < 0)
        assertEquals(-2500L, s.deltaGrams)
    }

    @Test fun suggestionHoldsAfterFailureEffort() {
        val previous = listOf(TrainingSet(grams = 40000, reps = 10, done = true, rpe = 10))
        assertEquals(SuggestionLevel.HOLD, Progression.suggest(previous, 10, 40000).level)
    }

    @Test fun suggestionWithoutHistoryHasNoData() {
        assertEquals(SuggestionLevel.NO_DATA, Progression.suggest(emptyList(), 10, 40000).level)
    }

    @Test fun suggestionDeloadNeverExceedsBaseWeight() {
        assertEquals(-1000L, Progression.suggest(listOf(TrainingSet(grams = 1000, reps = 3, done = true)), 10, 1000).deltaGrams)
    }

    @Test fun suggestForUsesMostRecentFinishedSession() {
        val older = Session(title = "旧训练", startedAt = 1000, endedAt = 2000,
            movements = listOf(Movement(exerciseId = "e", name = "测试动作",
                sets = listOf(TrainingSet(grams = 40000, reps = 10, done = true)))))
        val newer = older.copy(id = "newer", startedAt = 5000, endedAt = 6000,
            movements = listOf(Movement(exerciseId = "e", name = "测试动作",
                sets = listOf(TrainingSet(grams = 40000, reps = 6, done = true)))))
        val s = Progression.suggestFor("e", listOf(newer, older), 10, 40000)
        assertEquals(SuggestionLevel.DELOAD, s.level)
    }

    @Test fun supersetTransitionUsesShortRestAndLastMemberFullRest() {
        val plan = Plan("p", "超级组", listOf("e", "e2"), sets = 1, reps = 10, superset = SupersetMode.PAIRS)
        val other = Exercise("e2", "第二动作", "背部", "器械", 20000)
        val s = Rules.start(plan, listOf(exercise, other), 1000)
        val first = s.movements[0]
        val second = s.movements[1]
        assertEquals(Rules.SupersetRestSeconds, Rules.restAfter(s, first, first.sets[0], 90))
        assertEquals(90, Rules.restAfter(s, second, second.sets[0], 90))
    }

    @Test fun perSetRestOverrideWinsOverSupersetTransition() {
        val plan = Plan("p", "超级组", listOf("e", "e2"), sets = 1)
        val s = Rules.start(plan, listOf(exercise, Exercise("e2", "第二动作", "背部", "器械", 20000)), 1000)
        val m = s.movements[0].copy(supersetGroup = 1)
        val custom = m.sets[0].copy(restSeconds = 45)
        assertEquals(45, Rules.restAfter(s.copy(movements = listOf(m, s.movements[1].copy(supersetGroup = 1))), m, custom, 90))
    }

    @Test fun completingInsideSupersetSchedulesTransitionRest() {
        val plan = Plan("p", "超级组", listOf("e", "e2"), sets = 1, reps = 10, superset = SupersetMode.PAIRS)
        val s = Rules.start(plan, listOf(exercise, Exercise("e2", "第二动作", "背部", "器械", 20000)), 1000)
        val after = Rules.complete(s, s.movements[0].id, s.movements[0].sets[0].id, 90, 2000)
        assertEquals(2000L + Rules.SupersetRestSeconds * 1000L, after.restDeadline)
    }

    @Test fun warmupAndDropSetsCountTowardVolumeButWarmupDoesNot() {
        val plan = Plan("p", "测试", listOf("e"), sets = 1, reps = 10)
        val base = Rules.start(plan, listOf(exercise), 1000)
        val m = base.movements[0]
        val sets = listOf(
            TrainingSet(grams = 40000, reps = 10, done = true).withKind(SetKind.WARMUP),
            TrainingSet(grams = 40000, reps = 10, done = true).withKind(SetKind.DROP))
        assertEquals(400.0, Rules.volumeKg(base.copy(movements = listOf(m.copy(sets = sets)))), 0.001)
    }

    @Test fun rpeOutsideFiveToTenIsRejected() {
        try { Rules.trainingSet(TrainingSet(grams = 1000, reps = 5, rpe = 11)); fail("RPE 11 应被拒绝") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("RPE")) }
    }

    @Test fun legacyPayloadWithoutNewFieldsStillDecodes() {
        val json = """{"id":"s1","title":"旧训练","startedAt":1000,"endedAt":2000,"movements":[{"id":"m1","exerciseId":"e1","name":"旧动作","sets":[{"id":"t1","grams":40000,"reps":10,"done":true,"warmup":true}]}]}"""
        val s = Codec.decodeFromString<Session>(json)
        assertNull(s.planId); assertEquals("", s.note); assertNull(s.movements[0].supersetGroup)
        val set = s.movements[0].sets[0]
        assertEquals(SetKind.WORK, set.kind); assertEquals(SetKind.WARMUP, set.effectiveKind)
        assertFalse(set.countsVolume); assertNull(set.rpe); assertNull(set.restSeconds)
        assertEquals(0.0, Rules.volumeKg(s), 0.001)
    }

    @Test fun versionOneBackupStillImports() {
        val json = """{"format":"duolian-native","version":1,"exercises":[],"plans":[],"sessions":[],"body":[],"food":[],"water":[],"restSeconds":60}"""
        val b = Codec.decodeFromString<Backup>(json)
        Rules.backup(b)
        assertEquals(1, b.version)
    }

    @Test fun versionTwoBackupRoundTripsNewFields() {
        val plan = Plan("p", "测试", listOf("e"), sets = 2, reps = 8, scheme = SetScheme.DROP,
            restSeconds = 90, superset = SupersetMode.ALL)
        val s = Rules.start(plan, listOf(exercise), 1000).copy(note = "状态不错")
        val backup = Backup(exercises = listOf(exercise), plans = listOf(plan), sessions = listOf(s),
            body = emptyList(), food = emptyList(), water = emptyList(), restSeconds = 90)
        val decoded = Codec.decodeFromString<Backup>(Codec.encodeToString(backup))
        Rules.backup(decoded)
        assertEquals(2, decoded.version)
        assertEquals(SetScheme.DROP, decoded.plans.single().scheme)
        assertEquals(SetKind.DROP, decoded.sessions.single().movements.single().sets[1].effectiveKind)
        assertEquals("状态不错", decoded.sessions.single().note)
    }

    @Test fun pairSupersetRequiresTwoExercises() {
        try { Rules.plan(Plan("p", "单人", listOf("e"), superset = SupersetMode.PAIRS), listOf(exercise)); fail("单动作不能组成两两超级组") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("超级组")) }
    }
}
