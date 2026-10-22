package app.duolian.local

import app.duolian.local.domain.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private val exercise = Exercise("e", "卧推", "胸部", "杠铃", 40000)
    private val plan = Plan("p", "测试", listOf("e"))

    private fun run() = HiitRun(templateId = "t", name = "经典 20/10", startedAt = 1_000, endedAt = 61_000,
        completedRounds = 8, workSeconds = 20, restSeconds = 10, rounds = 8)

    @Test fun versionTwoBackupCarriesHiitHistory() {
        val backup = Backup(exercises = listOf(exercise), plans = listOf(plan), sessions = emptyList(),
            body = emptyList(), food = emptyList(), water = emptyList(), hiitRuns = listOf(run()))
        val decoded = Codec.decodeFromString<Backup>(Codec.encodeToString(backup))
        Rules.backup(decoded)
        assertEquals(1, decoded.hiitRuns.size)
        assertEquals(8, decoded.hiitRuns.single().completedRounds)
        assertEquals(backup, decoded)
    }

    @Test fun versionOneBackupDefaultsToNoHiitHistory() {
        val json = """{"format":"duolian-native","version":1,"exercises":[],"plans":[],"sessions":[],"body":[],"food":[],"water":[],"restSeconds":60}"""
        val decoded = Codec.decodeFromString<Backup>(json)
        Rules.backup(decoded)
        assertTrue(decoded.hiitRuns.isEmpty())
    }

    @Test fun duplicateHiitRunIdsAreRejected() {
        val single = run()
        val duplicated = listOf(single, single)
        try {
            Rules.backup(Backup(exercises = emptyList(), plans = emptyList(), sessions = emptyList(),
                body = emptyList(), food = emptyList(), water = emptyList(), hiitRuns = duplicated))
            fail("重复的 HIIT 记录应被拒绝")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("记录过多或ID重复"))
        }
    }

    @Test fun exerciseKeepsMediaAndFavouriteWhenRenamed() {
        val withPhoto = exercise.copy(favorite = true, mediaUri = "content://media/external/images/1")
        val renamed = withPhoto.copy(name = "上斜卧推")
        assertEquals("content://media/external/images/1", renamed.mediaUri)
        assertTrue(renamed.favorite)
        Rules.exercise(renamed)
    }

    @Test fun overlongMediaUriIsRejected() {
        val tooLong = exercise.copy(mediaUri = "content://" + "x".repeat(2000))
        try { Rules.exercise(tooLong); fail("超长图片地址应被拒绝") }
        catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("图片地址")) }
    }

    @Test fun legacyExercisePayloadWithoutNewFieldsDecodes() {
        val json = """{"id":"e1","name":"深蹲","muscle":"腿部","equipment":"杠铃","defaultGrams":60000,"notes":""}"""
        val decoded = Codec.decodeFromString<Exercise>(json)
        assertFalse(decoded.favorite)
        assertNull(decoded.mediaUri)
        Rules.exercise(decoded)
    }
}
