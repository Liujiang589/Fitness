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
class StorageTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun open(name: String) = Room.databaseBuilder(context, LocalDatabase::class.java, name).build()
    @Test fun trainingBodyAndNutritionSurviveDatabaseReopen() = runBlocking {
        val name = "reopen-${newId()}.db"
        var db = open(name)
        try {
            var repo = Repository(db); repo.initialize()
            val p = repo.plans.first().first(); repo.start(p.id, 1000)
            repo.changeActive { s -> Rules.complete(s, s.movements[0].id, s.movements[0].sets[0].id, 60, 2000) }
            repo.changeActive { Rules.pause(it, 12000) }
            repo.saveBody(Body(grams = 71000)); repo.saveFood(Food(meal = "早餐", name = "测试", protein = 20.0, carbs = 30.0, fat = 5.0))
            repo.saveWater(Water(ml = 250)); db.close()
            db = open(name); repo = Repository(db)
            val s = repo.sessions.first().single()
            assertEquals(12000L, s.pausedAt!!); assertEquals(50000L, Rules.restMs(s, 90000))
            assertEquals(400.0, Rules.volumeKg(s), 0.001)
            assertEquals(71000L, repo.body.first().single().grams)
            assertEquals("测试", repo.food.first().single().name); assertEquals(250, repo.water.first().single().ml)
            repo.changeActive { Rules.resume(it, 32000) }; repo.changeActive { Rules.finish(it, 33000) }
            assertEquals(12000L, Rules.elapsedMs(repo.sessions.first().single(), 99999))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun invalidImportPreservesExistingData() = runBlocking {
        val name = "atomic-${newId()}.db"; val db = open(name)
        try {
            val repo = Repository(db); repo.initialize(); val before = repo.snapshot(60)
            val invalid = before.copy(plans = listOf(Plan(name = "错误计划", exerciseIds = listOf("missing"))))
            try { repo.restore(invalid); fail("invalid backup accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(before, repo.snapshot(60))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun editingAndDeletingSourcesPreservesFinishedHistory() = runBlocking {
        val name = "snapshot-${newId()}.db"; val db = open(name)
        try {
            val repo = Repository(db); repo.initialize(); val p = repo.plans.first().first()
            repo.start(p.id, 1000); repo.changeActive { s -> Rules.complete(s, s.movements[0].id, s.movements[0].sets[0].id, 0, 2000) }
            repo.changeActive { Rules.finish(it, 3000) }
            val old = repo.sessions.first().single()
            repo.deletePlan(p.id); old.movements.forEach { repo.deleteExercise(it.exerciseId) }
            assertEquals(old, repo.sessions.first().single())
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun secondActiveTrainingIsRejected() = runBlocking {
        val name = "active-${newId()}.db"; val db = open(name)
        try {
            val repo = Repository(db); repo.initialize(); val p = repo.plans.first().first()
            repo.start(p.id)
            try { repo.start(p.id); fail("second active session accepted") } catch (_: IllegalArgumentException) { }
            assertEquals(1, repo.sessions.first().size)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
