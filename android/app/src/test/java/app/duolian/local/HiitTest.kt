package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class HiitTest {
    private val template = HiitTemplate("t", "测试", prepareSeconds = 5, workSeconds = 20,
        restSeconds = 10, rounds = 3)

    @Test fun totalDurationCoversPrepareAndAllCycles() {
        assertEquals((5 + 3 * 30) * 1000L, HiitEngine.totalMs(template))
    }

    @Test fun preparePhaseRunsFirst() {
        val phase = HiitEngine.phaseAt(template, 0)
        assertEquals(HiitPhaseKind.PREPARE, phase.kind)
        assertEquals(5_000L, phase.remainingMs)
        assertEquals(5, phase.remainingSeconds)
    }

    @Test fun workAndRestAlternateInsideEachRound() {
        val work = HiitEngine.phaseAt(template, 5_000)
        assertEquals(HiitPhaseKind.WORK, work.kind); assertEquals(1, work.round)
        assertEquals(20_000L, work.remainingMs)

        val rest = HiitEngine.phaseAt(template, 25_000)
        assertEquals(HiitPhaseKind.REST, rest.kind); assertEquals(1, rest.round)
        assertEquals(10_000L, rest.remainingMs)

        val secondWork = HiitEngine.phaseAt(template, 35_000)
        assertEquals(HiitPhaseKind.WORK, secondWork.kind); assertEquals(2, secondWork.round)
    }

    @Test fun runFinishesAfterLastRest() {
        assertEquals(HiitPhaseKind.DONE, HiitEngine.phaseAt(template, 95_000).kind)
        assertEquals(HiitPhaseKind.DONE, HiitEngine.phaseAt(template, 200_000).kind)
    }

    @Test fun phaseBoundaryBelongsToTheNextPhase() {
        assertEquals(HiitPhaseKind.WORK, HiitEngine.phaseAt(template, 5_000).kind)
        assertEquals(HiitPhaseKind.REST, HiitEngine.phaseAt(template, 25_000).kind)
        assertEquals(HiitPhaseKind.DONE, HiitEngine.phaseAt(template, HiitEngine.totalMs(template)).kind)
    }

    @Test fun zeroRestTemplatesStayInWorkPhase() {
        val noRest = template.copy(restSeconds = 0, rounds = 2)
        assertEquals(HiitPhaseKind.WORK, HiitEngine.phaseAt(noRest, 5_000).kind)
        assertEquals(1, HiitEngine.phaseAt(noRest, 5_000).round)
        assertEquals(2, HiitEngine.phaseAt(noRest, 25_000).round)
        assertEquals(HiitPhaseKind.WORK, HiitEngine.phaseAt(noRest, 44_000).kind)
        assertEquals(HiitPhaseKind.DONE, HiitEngine.phaseAt(noRest, 45_000).kind)
    }

    @Test fun completedRoundsTracksOnlyFinishedWork() {
        assertEquals(0, HiitEngine.completedRounds(template, 4_000))
        assertEquals(0, HiitEngine.completedRounds(template, 5_000))
        assertEquals(1, HiitEngine.completedRounds(template, 25_000))
        assertEquals(1, HiitEngine.completedRounds(template, 30_000))
        assertEquals(2, HiitEngine.completedRounds(template, 55_000))
        assertEquals(3, HiitEngine.completedRounds(template, 95_000))
    }

    @Test fun templateValidationRejectsUnusableParameters() {
        try { Rules.hiitTemplate(template.copy(workSeconds = 2)); fail("训练时间过短应被拒绝") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("训练时间")) }
        try { Rules.hiitTemplate(template.copy(rounds = 0)); fail("轮数为0应被拒绝") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("轮数")) }
    }

    @Test fun runRecordValidationRejectsBadTimestamps() {
        val run = HiitRun(templateId = "t", name = "测试", startedAt = 5_000, endedAt = 6_000,
            completedRounds = 2, workSeconds = 20, restSeconds = 10, rounds = 3)
        Rules.hiitRun(run)
        try { Rules.hiitRun(run.copy(endedAt = 4_000)); fail("结束时间早于开始应被拒绝") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("时间戳")) }
    }

    @Test fun presetsAreValidTemplates() {
        listOf(HiitEngine.preset("经典", 20, 10, 8), HiitEngine.preset("Tabata", 20, 10, 8, 10),
            HiitEngine.preset("耐力", 40, 20, 6)).forEach { Rules.hiitTemplate(it) }
    }
}
