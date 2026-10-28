package app.duolian.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.duolian.local.data.LocalDatabase
import app.duolian.local.data.SessionRow
import app.duolian.local.domain.AccountScopes
import app.duolian.local.domain.Codec
import app.duolian.local.domain.Movement
import app.duolian.local.domain.Session
import app.duolian.local.domain.TrainingSet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the storage guarantee behind "sign out of A and into B cannot see A's records".
 * Runs on a device/emulator with throwaway database files; it never touches duolian.db.
 */
@RunWith(AndroidJUnit4::class)
class AccountIsolationTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val files = mutableListOf<String>()
    private val dbs = mutableListOf<LocalDatabase>()

    private fun open(name: String): LocalDatabase {
        val db = Room.databaseBuilder(context, LocalDatabase::class.java, name).build()
        dbs += db
        files += name
        return db
    }

    private fun session(id: String) = Session(
        id = id, title = "隔离测试", startedAt = 1_000L, movements = listOf(
            Movement(id = "m-$id", exerciseId = "squat", name = "深蹲", sets = listOf(
                TrainingSet(id = "s-$id", grams = 20_000, reps = 5, done = true)
            ))
        )
    )

    private suspend fun LocalDatabase.putSession(s: Session) =
        dao().put(SessionRow(s.id, null, Codec.encodeToString(Session.serializer(), s)))

    @Test fun accountDatabasesDoNotShareRecords() = runBlocking {
        val a = open(AccountScopes.dbName(AccountScopes.account("uid_aaaaaaaa1111")))
        val b = open(AccountScopes.dbName(AccountScopes.account("uid_bbbbbbbb2222")))

        a.putSession(session("a1"))
        b.putSession(session("b1"))

        assertEquals(listOf("a1"), a.dao().allSessions().map { it.id })
        assertEquals(listOf("b1"), b.dao().allSessions().map { it.id })
    }

    @Test fun sessionInOneAccountIsInvisibleFromAnother() = runBlocking {
        val a = open(AccountScopes.dbName(AccountScopes.account("uid_cccccccc3333")))
        val b = open(AccountScopes.dbName(AccountScopes.account("uid_dddddddd4444")))

        a.putSession(session("only-a"))

        assertTrue(b.dao().allSessions().isEmpty())
        assertTrue(b.dao().allFood().isEmpty())
        assertTrue(b.dao().allBody().isEmpty())
    }

    @Test fun guestDatabaseStaysSeparateFromAccountDatabase() = runBlocking {
        val guest = open(AccountScopes.GUEST_DB)
        val account = open(AccountScopes.dbName(AccountScopes.account("uid_eeeeeeee5555")))

        guest.putSession(session("guest-only"))

        assertTrue(account.dao().allSessions().isEmpty())
        assertEquals(listOf("guest-only"), guest.dao().allSessions().map { it.id })
    }

    @After fun cleanup() {
        dbs.forEach { runCatching { it.close() } }
        files.forEach { name ->
            context.databaseList().filter { it == name || it.startsWith("$name-") }
                .forEach { context.deleteDatabase(it) }
        }
    }
}
