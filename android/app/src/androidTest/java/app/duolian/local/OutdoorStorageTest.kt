package app.duolian.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.duolian.local.data.*
import app.duolian.local.domain.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutdoorStorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun open(name: String) = Room.databaseBuilder(context, LocalDatabase::class.java, name).build()

    @Test fun counterAndRouteSurviveReopenAndBackupRestoresPaused() = runBlocking {
        val name = "outdoor-${newId()}.db"
        var db = open(name)
        try {
            var store = OutdoorStore(db)
            assertEquals(0L, store.steps(StepBaseline("2026-10-23", 5000, 1)))
            assertEquals(23L, store.steps(StepBaseline("2026-10-23", 5023, 1)))
            val r = OutdoorRun(kind = OutdoorKind.WALK, startedAt = 1000, updatedAt = 6000,
                elapsedMs = 5000, points = listOf(RoutePoint(30.0, 120.0, 1000, 5f)))
            store.save(r)
            val repo = Repository(db)
            val backup = repo.snapshot(60)
            assertEquals(1, backup.outdoorRuns.size)
            assertEquals(23L, backup.dailySteps.single().steps)
            db.close(); db = open(name); store = OutdoorStore(db)
            assertEquals(r, store.runs.first().single())
            assertEquals(7L, store.steps(StepBaseline("2026-10-23", 5030, 1)))
            Repository(db).restore(backup)
            val restored = store.runs.first().single()
            assertEquals(OutdoorStatus.PAUSED, restored.status)
            assertEquals(r.points, restored.points)
            assertEquals(23L, store.days.first().single().steps)
            assertEquals(0L, store.steps(StepBaseline("2026-10-23", 9000, 1)))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun oldDatabaseAndLegacyRestoreKeepTrainingSafe() = runBlocking {
        val name = "outdoor-legacy-${newId()}.db"
        val db = open(name)
        try {
            val repo = Repository(db); repo.initialize()
            val exerciseCount = repo.exercises.first().size
            val store = OutdoorStore(db)
            store.steps(StepBaseline("2026-10-23", 10, 1))
            store.save(OutdoorRun(kind = OutdoorKind.RUN, startedAt = 1000))
            val before = repo.snapshot(60)
            val invalid = before.copy(dailySteps = listOf(DailySteps("2026-02-30", 100)))
            try { repo.restore(invalid); fail("invalid backup must not mutate the database") }
            catch (_: Exception) { }
            assertEquals(before, repo.snapshot(60))
            repo.restore(before.copy(version = 3, outdoorRuns = emptyList(), dailySteps = emptyList()))
            assertEquals(exerciseCount, repo.exercises.first().size)
            assertTrue(store.runs.first().isEmpty()); assertTrue(store.days.first().isEmpty())
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
