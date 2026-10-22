package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DiagnosticsTest {
    private val today = LocalDate.of(2026, 10, 22)
    private val bench = Exercise("bench", "杠铃卧推", "胸部", "杠铃", 40000)
    private val row = Exercise("row", "坐姿划船", "背部", "器械", 30000)
    private val squat = Exercise("squat", "杠铃深蹲", "腿部", "杠铃", 60000)
    private val exercises = listOf(bench, row, squat)

    private fun session(date: LocalDate, exerciseId: String = "bench", name: String = "杠铃卧推",
        grams: Long = 40000, reps: Int = 10, sets: Int = 3): Session {
        val start = date.atTime(19, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return Session(title = name, startedAt = start, endedAt = start + 30 * 60_000,
            movements = listOf(Movement(exerciseId = exerciseId, name = name,
                sets = List(sets) { TrainingSet(grams = grams, reps = reps, done = true) })))
    }

    @Test fun plateauDetectedWhenEstimatedOneRepMaxIsFlat() {
        val sessions = listOf(today.minusDays(20), today.minusDays(12), today.minusDays(4))
            .map { session(it) }
        val plateaus = Diagnostics.plateaus(sessions, exercises, today)
        assertEquals(1, plateaus.size)
        assertEquals("bench", plateaus.single().exerciseId)
        assertEquals(3, plateaus.single().sessions)
        assertTrue(plateaus.single().changePercent <= 1.0)
    }

    @Test fun plateauNotReportedWhenWeightProgresses() {
        val sessions = listOf(
            session(today.minusDays(20), grams = 40000),
            session(today.minusDays(12), grams = 45000),
            session(today.minusDays(4), grams = 50000))
        assertTrue(Diagnostics.plateaus(sessions, exercises, today).isEmpty())
    }

    @Test fun plateauNeedsMinimumExposures() {
        val sessions = listOf(session(today.minusDays(10)), session(today.minusDays(3)))
        assertTrue(Diagnostics.plateaus(sessions, exercises, today).isEmpty())
    }

    @Test fun plateauIgnoresBodyweightOnlyExercises() {
        val sessions = listOf(today.minusDays(20), today.minusDays(12), today.minusDays(4))
            .map { session(it, grams = 0) }
        assertTrue(Diagnostics.plateaus(sessions, exercises, today).isEmpty())
    }

    @Test fun plateauIgnoresSessionsOutsideWindow() {
        val sessions = listOf(today.minusDays(60), today.minusDays(50), today.minusDays(40))
            .map { session(it) }
        assertTrue(Diagnostics.plateaus(sessions, exercises, today).isEmpty())
    }

    @Test fun muscleBalanceCountsWorkingSetsOnly() {
        val sessions = listOf(
            session(today.minusDays(1), exerciseId = "bench", sets = 6),
            session(today.minusDays(2), exerciseId = "row", name = "坐姿划船", sets = 4))
        val load = Diagnostics.muscleBalance(sessions, exercises, today)
        val chest = load.first { it.muscle == "胸部" }
        assertEquals(6, chest.sets)
        assertEquals(MuscleStatus.LOW, chest.status)
        assertEquals(MuscleStatus.LOW, load.first { it.muscle == "背部" }.status)
    }

    @Test fun muscleBalanceFlagsOvertraining() {
        val sessions = listOf(session(today.minusDays(1), sets = 24))
        val chest = Diagnostics.muscleBalance(sessions, exercises, today).first { it.muscle == "胸部" }
        assertEquals(MuscleStatus.HIGH, chest.status)
    }

    @Test fun missingMusclesReportsUntrainedMajorGroups() {
        val sessions = listOf(session(today.minusDays(1), sets = 12))
        val missing = Diagnostics.missingMuscles(sessions, exercises, today)
        assertTrue(missing.contains("背部"))
        assertTrue(missing.contains("腿部"))
        assertFalse(missing.contains("胸部"))
    }

    @Test fun highRiskWhenAcuteVolumeSpikes() {
        val base = session(today.minusDays(20), grams = 50000, reps = 10, sets = 2) // 1000 kg
        val sessions = listOf(base, base.copy(startedAt = base.startedAt + 7L * 86_400_000),
            base.copy(startedAt = base.startedAt + 14L * 86_400_000),
            session(today.minusDays(1), grams = 50000, reps = 10, sets = 10), // 5000 kg acute
            session(today.minusDays(2), grams = 50000, reps = 10, sets = 10))
        val risk = Diagnostics.loadRisk(sessions, today)
        assertEquals(RiskLevel.HIGH, risk.level)
        assertTrue(risk.ratio >= 1.5)
        assertTrue(risk.advice.contains("减量") || risk.advice.contains("休息"))
    }

    @Test fun underRiskAfterLayoff() {
        val old = session(today.minusDays(24), grams = 50000, reps = 10, sets = 4)
        val risk = Diagnostics.loadRisk(listOf(old), today)
        assertEquals(RiskLevel.UNDER, risk.level)
        assertTrue(risk.ratio < 0.6)
    }

    @Test fun steadyWeekIsOk() {
        val sessions = (0 until 4).map { i ->
            session(today.minusDays((21 - i * 7).toLong()), grams = 50000, reps = 10, sets = 3)
        }
        val risk = Diagnostics.loadRisk(sessions, today)
        assertEquals(RiskLevel.OK, risk.level)
    }

    @Test fun consecutiveTrainingDaysTriggerRestAdvice() {
        val sessions = (0..7).map { session(today.minusDays(it.toLong()), grams = 20000, sets = 2) }
        val risk = Diagnostics.loadRisk(sessions, today)
        assertTrue(risk.consecutiveDays >= 7)
        assertTrue(risk.advice.contains("休息"))
    }

    @Test fun alertsSummariseActionableIssues() {
        val sessions = (0..7).map {
            session(today.minusDays(it.toLong()), grams = 50000, reps = 10, sets = 6)
        }
        val alerts = Diagnostics.alerts(sessions, exercises, today)
        assertTrue(alerts.isNotEmpty())
        assertTrue(alerts.any { it.contains("停滞") || it.contains("缺") || it.contains("负荷") })
    }

    @Test fun emptyHistoryProducesNoAlerts() {
        assertTrue(Diagnostics.alerts(emptyList(), exercises, today).isEmpty())
        assertTrue(Diagnostics.muscleBalance(emptyList(), exercises, today).isEmpty())
        assertEquals(RiskLevel.OK, Diagnostics.loadRisk(emptyList(), today).level)
    }
}
