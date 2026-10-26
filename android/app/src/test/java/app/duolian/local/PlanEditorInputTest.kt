package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class PlanEditorInputTest {
    private val exercises = listOf(
        Exercise("bench", "杠铃卧推", "胸部", "杠铃"),
        Exercise("row", "坐姿划船", "背部", "器械"))
    private val draft = PlanEditorInput("my-plan", "上肢日", listOf("bench", "row"))
    private fun rejects(input: PlanEditorInput, message: String) {
        assertTrue(assertThrows(IllegalArgumentException::class.java) { input.toPlan(exercises) }
            .message.orEmpty().contains(message))
    }
    @Test fun newPlanTrimsPastedTextWithoutChangingIdentityOrExerciseOrder() {
        val actual = draft.copy(name = "  我的计划  ", sets = " 4 ", reps = " 8 ", rest = " 90 ",
            exerciseIds = listOf("row", "bench")).toPlan(exercises)
        assertEquals(Plan("my-plan", "我的计划", listOf("row", "bench"), 4, 8,
            SetScheme.STRAIGHT, 90, SupersetMode.NONE), actual)
    }
    @Test fun editingRetainsEveryProfessionalOptionAndNormalizesScheduleOrder() {
        for (scheme in SetScheme.entries) for (superset in SupersetMode.entries) {
            val actual = draft.copy(sets = "5", reps = "6", rest = "120", scheme = scheme,
                superset = superset, weekdays = listOf(5,1)).toPlan(exercises)
            assertEquals(Plan("my-plan", "上肢日", listOf("bench", "row"), 5, 6, scheme,
                120, superset, listOf(1,5)), actual)
        }
    }
    @Test fun nameValidationIsSpecificAndAllowsTheExistingHundredCharacterLimit() {
        rejects(draft.copy(name = " \n "), "起个名字")
        rejects(draft.copy(name = "练".repeat(101)), "100字")
        assertEquals(100, draft.copy(name = "练".repeat(100)).toPlan(exercises).name.length)
    }
    @Test fun selectionErrorsDistinguishMissingDuplicateDeletedAndTooManyExercises() {
        rejects(draft.copy(exerciseIds = emptyList()), "至少一个")
        rejects(draft.copy(exerciseIds = listOf("bench", "bench")), "重复动作")
        rejects(draft.copy(exerciseIds = listOf("deleted")), "已被删除")
        rejects(draft.copy(exerciseIds = (1..101).map { "x$it" }), "最多选择100")
    }
    @Test fun setCountRejectsOutOfRangeFractionalEmptyAndOverflowingValues() {
        for (invalid in listOf("0", "101", "-1", "1.5", "", "9999999999999999999"))
            rejects(draft.copy(sets = invalid), "组数请输入1–100")
        assertEquals(1, draft.copy(sets = "1").toPlan(exercises).sets)
        assertEquals(100, draft.copy(sets = "100").toPlan(exercises).sets)
    }
    @Test fun repetitionCountPreservesZeroAndTheExistingUpperBoundary() {
        for (invalid in listOf("-1", "1001", "2.5", "")) rejects(draft.copy(reps = invalid), "次数请输入0–1000")
        assertEquals(0, draft.copy(reps = "0").toPlan(exercises).reps)
        assertEquals(1000, draft.copy(reps = "1000").toPlan(exercises).reps)
    }
    @Test fun restZeroKeepsGlobalFallbackAndInvalidRestExplainsThatMeaning() {
        assertEquals(0, draft.toPlan(exercises).restSeconds)
        assertEquals(600, draft.copy(rest = "600").toPlan(exercises).restSeconds)
        for (invalid in listOf("-1", "601", "1.5", "")) rejects(draft.copy(rest = invalid), "0表示沿用默认休息")
    }
    @Test fun pairedSupersetNeedsTwoExercisesWhileAnOrdinarySingleActionPlanRemainsValid() {
        rejects(draft.copy(exerciseIds = listOf("bench"), superset = SupersetMode.PAIRS), "至少需要两个")
        assertEquals(listOf("bench"), draft.copy(exerciseIds = listOf("bench")).toPlan(exercises).exerciseIds)
        assertEquals(SupersetMode.PAIRS, draft.copy(superset = SupersetMode.PAIRS).toPlan(exercises).superset)
    }
    @Test fun scheduleStillUsesIsoDaysAndDoesNotSilentlyDropInvalidValues() {
        rejects(draft.copy(weekdays = listOf(0)), "排期星期")
        rejects(draft.copy(weekdays = listOf(8)), "排期星期")
        rejects(draft.copy(weekdays = listOf(1,1)), "排期星期")
        assertEquals(emptyList<Int>(), draft.toPlan(exercises).weekdays)
    }
    @Test fun submittedPlanDoesNotRetainMutableDraftListReferences() {
        val ids = mutableListOf("bench", "row"); val days = mutableListOf(5,1)
        val actual = draft.copy(exerciseIds = ids, weekdays = days).toPlan(exercises)
        ids.clear(); days.clear()
        assertEquals(listOf("bench", "row"), actual.exerciseIds)
        assertEquals(listOf(1,5), actual.weekdays)
    }
}
