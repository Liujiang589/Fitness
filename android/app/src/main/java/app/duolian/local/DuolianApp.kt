package app.duolian.local

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.duolian.local.data.AccountMigrator
import app.duolian.local.data.AccountSessionStore
import app.duolian.local.data.LocalDatabase
import app.duolian.local.data.Repository
import app.duolian.local.data.ScopedDatabase
import app.duolian.local.domain.AccountScope
import app.duolian.local.domain.AccountScopes
import kotlinx.coroutines.flow.map

private val Application.preferences by preferencesDataStore("preferences")

class DuolianApp : Application() {
    override fun onCreate() { super.onCreate(); AppCrashReport.install(this) }

    /** Guest database: same file name as every earlier release, so upgrades keep their records. */
    val database by lazy { Room.databaseBuilder(this, LocalDatabase::class.java, "duolian.db").build() }
    val repository by lazy { Repository(database) }
    val outdoor by lazy { app.duolian.local.data.OutdoorStore(database) }

    /** Account-scoped databases and the session ticket that selects one. */
    val scopedDatabase by lazy { ScopedDatabase(this) }
    val sessionStore by lazy { AccountSessionStore(this) }
    val migrator by lazy { AccountMigrator(filesDir) }

    @Volatile private var current: Pair<AccountScope, Repository>? = null

    /** Repository for a scope. Guest and accounts never share a database file. */
    fun repositoryFor(scope: AccountScope): Repository {
        current?.let { (s, r) -> if (s == scope) return r }
        val repo = Repository(scopedDatabase.database(scope))
        current = scope to repo
        return repo
    }

    fun logout(): AccountScope = AccountScopes.guest()

    private val restKey = intPreferencesKey("rest_seconds")
    val restSeconds by lazy { preferences.data.map { (it[restKey] ?: 60).coerceIn(0, 600) } }
    suspend fun setRest(seconds: Int) { require(seconds in 0..600); preferences.edit { it[restKey] = seconds } }
}
