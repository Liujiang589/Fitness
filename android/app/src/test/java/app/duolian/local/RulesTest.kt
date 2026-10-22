package app.duolian.local

import app.duolian.local.domain.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class RulesTest {
    private val e = Exercise("e", "测试动作", "胸部", "杠铃", 40000)
    private val p = Plan("p", "测试计划", listOf("e"))
    private fun session() = Rules.start(p, listOf(e), 1000)
    private fun completed(): Session {
        val s = session(); return Rules.complete(s, s.movements[0].id, s.movements[0].sets[0].id, 60, 2000)
    }
    private fun backup(s: List<Session> = emptyList()) = Backup(exercises = listOf(e), plans = listOf(p),
        sessions = s, body = emptyList(), food = emptyList(), water = emptyList())

    @Test fun volumeOnlyCountsCompletedWorkSets() {
        val base = completed(); val m = base.movements[0]
        val sets = listOf(TrainingSet(grams = 40000, reps = 10, done = true),
            TrainingSet(grams = 60000, reps = 10, done = false),
            TrainingSet(grams = 20000, reps = 20, done = true, warmup = true))
        assertEquals(400.0, Rules.volumeKg(base.copy(movements = listOf(m.copy(sets = sets)))), 0.001)
    }
    @Test fun pauseAndResumeFreezeBothTimers() {
        val paused = Rules.pause(completed(), 12000)
        assertEquals(50000L, Rules.restMs(paused, 90000))
        assertEquals(11000L, Rules.elapsedMs(paused, 90000))
        val resumed = Rules.resume(paused, 32000)
        assertEquals(50000L, Rules.restMs(resumed, 32000))
        assertEquals(12000L, Rules.elapsedMs(resumed, 33000))
    }
    @Test fun countdownUsesAbsoluteDeadlineAfterReopen() {
        val restored = Codec.decodeFromString<Session>(Codec.encodeToString(completed()))
        assertEquals(10000L, Rules.restMs(restored, 52000))
        assertEquals(0L, Rules.restMs(restored, 100000))
    }
    @Test fun finishingWhilePausedExcludesLastPause() {
        val result = Rules.finish(Rules.pause(completed(), 12000), 32000)
        assertEquals(32000L, result.endedAt!!)
        assertEquals(11000L, Rules.elapsedMs(result, 999999))
        assertNull(result.pausedAt); assertNull(result.restDeadline)
    }
    @Test fun planEditsDoNotAlterSessionSnapshots() {
        val old = session(); val renamed = e.copy(name = "新动作名", defaultGrams = 50000)
        assertEquals("测试动作", old.movements[0].name)
        assertEquals(40000L, old.movements[0].sets[0].grams)
        assertEquals("新动作名", Rules.start(p, listOf(renamed), 2000).movements[0].name)
    }
    @Test fun repeatedPauseDoesNotResetPauseStart() {
        val once = Rules.pause(completed(), 12000)
        assertEquals(once, Rules.pause(once, 22000))
    }
    @Test fun kilogramConversionAvoidsFloatingPointDrift() {
        assertEquals(453L, Rules.parseKg("0.453")); assertEquals(1250L, Rules.parseKg("1.25"))
        assertEquals(1L, Rules.parseKg("0.0005"))
    }
    @Test(expected = IllegalArgumentException::class) fun negativeWeightRejected() { Rules.parseKg("-1") }
    @Test(expected = IllegalArgumentException::class) fun invalidPlanReferenceRejected() { Rules.plan(p, emptyList()) }
    @Test(expected = IllegalArgumentException::class) fun finishWithoutCompletedSetRejected() { Rules.finish(session(), 5000) }
    @Test(expected = IllegalArgumentException::class) fun completeWhilePausedRejected() {
        val s = Rules.pause(session(), 1200)
        Rules.complete(s, s.movements[0].id, s.movements[0].sets[0].id, 60, 1400)
    }
    @Test(expected = IllegalArgumentException::class) fun duplicateActiveSessionsRejected() {
        Rules.backup(backup(listOf(session(), session().copy(id = "other"))))
    }
    @Test(expected = IllegalArgumentException::class) fun invalidDateRejected() {
        Rules.body(Body(date = "2026-02-30", grams = 70000))
    }
    @Test(expected = IllegalArgumentException::class) fun nonFiniteNutrientsRejected() {
        Rules.food(Food(meal = "早餐", name = "测试", protein = Double.NaN, carbs = 0.0, fat = 0.0))
    }
    @Test fun nativeBackupRoundTripKeepsAllData() {
        val b = backup(listOf(completed())).copy(body = listOf(Body(grams = 70000)),
            food = listOf(Food(meal = "早餐", name = "自定义早餐", protein = 20.0, carbs = 30.0, fat = 10.0)),
            water = listOf(Water(ml = 250)), restSeconds = 90)
        val decoded = Codec.decodeFromString<Backup>(Codec.encodeToString(b)); Rules.backup(decoded)
        assertEquals(b, decoded); assertEquals(290.0, decoded.food.single().calories, 0.001)
    }
}
