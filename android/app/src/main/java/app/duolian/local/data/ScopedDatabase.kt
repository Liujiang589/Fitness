package app.duolian.local.data

import android.content.Context
import androidx.room.Room
import app.duolian.local.domain.AccountScope
import app.duolian.local.domain.AccountScopes

/**
 * Opens one database per account scope.
 *
 * Guest keeps the historical file (`duolian.db`) so an upgrade never loses records.
 * Account files are separate; closing a scope releases it before another is opened, which is what makes
 * "sign out of A, sign in as B" unable to read A's rows.
 */
class ScopedDatabase(private val context: Context) {
    @Volatile private var opened: Pair<AccountScope, LocalDatabase>? = null

    @Synchronized
    fun database(scope: AccountScope): LocalDatabase {
        opened?.let { (current, db) -> if (current == scope) return db else db.close() }
        val db = Room.databaseBuilder(context, LocalDatabase::class.java, AccountScopes.dbName(scope)).build()
        opened = scope to db
        return db
    }

    @Synchronized
    fun close() {
        opened?.second?.close()
        opened = null
    }

    /** Files that coexist on this device, for diagnostics and for clearing one account in isolation. */
    fun existingDatabaseNames(): List<String> =
        context.databaseList()
            .filter { it == AccountScopes.GUEST_DB || it.startsWith("duolian-account-") }
            .sorted()
}
