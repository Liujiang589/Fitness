package app.duolian.local

import app.duolian.local.domain.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

class TrainingHomeTest {
    private val today = LocalDate.of(2026, 10, 23)
    private fun plan(id: String, days: List<Int> = emptyList()) = Plan(id = id, name = id, exerciseIds = listOf("bench"), weekdays = days)
    private fun done(plan: Plan, day: LocalDate = today) = Session(title = plan.name, planId = plan.id,
        startedAt = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        endedAt = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60000, movements = emptyList())
    @Test fun firstUsePromptsCreationWithoutInventingAPlan() {
        val focus = TrainingHome.focus(emptyList(), emptyList(), today)
        assertEquals(HomeMode.FIRST_PLAN, focus.mode)
        assertNull(focus.plan); assertEquals("创建我的计划", focus.action)
    }
    @Test fun scheduledPlanTakesPriorityOverUnscheduledLibraryOrder() {
        val free = plan("free"); val scheduled = plan("scheduled", listOf(5))
        val focus = TrainingHome.focus(listOf(free, scheduled), emptyList(), today)
        assertEquals(HomeMode.SCHEDULED, focus.mode); assertEquals(scheduled, focus.plan)
    }
    @Test fun completingOneOfSeveralScheduledPlansShowsTheRemainingOne() {
        val a = plan("a", listOf(5)); val b = plan("b", listOf(5))
        assertEquals(b, TrainingHome.focus(listOf(a, b), listOf(done(a)), today).plan)
    }
    @Test fun completedScheduleDoesNotImmediatelyPushTheSameWorkoutAgain() {
        val a = plan("a", listOf(5))
        val focus = TrainingHome.focus(listOf(a), listOf(done(a)), today)
        assertEquals(HomeMode.COMPLETED, focus.mode); assertNull(focus.plan)
    }
    @Test fun completionUsesPlanIdNotAnAmbiguousMatchingTitle() {
        val a = plan("a", listOf(5)); val b = plan("b", listOf(5)).copy(name = "a")
        assertEquals(a, TrainingHome.focus(listOf(a), listOf(done(b)), today).plan)
    }
    @Test fun yesterdayAndUnfinishedSessionsDoNotMarkTodayComplete() {
        val a = plan("a", listOf(5))
        assertEquals(a, TrainingHome.focus(listOf(a), listOf(done(a, today.minusDays(1)), done(a).copy(endedAt = null)), today).plan)
    }
    @Test fun scheduledRestDayDoesNotStartAnArbitraryLibraryPlan() {
        val focus = TrainingHome.focus(listOf(plan("monday", listOf(1))), emptyList(), today)
        assertEquals(HomeMode.REST_DAY, focus.mode); assertNull(focus.plan)
    }
    @Test fun flexiblePlansAreExplicitlyAChoiceAndInputsStayUnchanged() {
        val a = plan("free")
        val plans = listOf(a); val sessions = emptyList<Session>()
        val focus = TrainingHome.focus(plans, sessions, today)
        assertEquals(HomeMode.FREE_CHOICE, focus.mode); assertEquals(a, focus.plan)
        assertEquals(listOf(a), plans); assertTrue(sessions.isEmpty())
    }
    @Test fun dateRolloverReturnsCompletedScheduleToNextValidScheduledDay() {
        val a = plan("daily", listOf(5, 6))
        assertEquals(a, TrainingHome.focus(listOf(a), listOf(done(a)), today.plusDays(1)).plan)
    }
}
