package app.duolian.local.data

import app.duolian.local.domain.AccountMigration
import app.duolian.local.domain.AccountScope
import app.duolian.local.domain.AccountScopes
import app.duolian.local.domain.Backup
import app.duolian.local.domain.Codec
import app.duolian.local.domain.MigrationDecision
import java.io.File

/**
 * Moves this device's guest records into the signed-in account.
 *
 * Order matters: decision → backup file on disk → restore into the account database.
 * The account database must be empty; merging two histories automatically is refused.
 */
class AccountMigrator(private val backupDir: File) {

    suspend fun migrate(
        guest: Repository,
        target: Repository,
        targetScope: AccountScope,
        restSeconds: Int,
        stamp: String
    ): MigrationDecision {
        val source = guest.snapshot(restSeconds)
        val destination = target.snapshot(restSeconds)
        val decision = AccountMigration.decide(
            guest = AccountScopes.guest(),
            target = targetScope,
            guestCounts = counts(source),
            targetCounts = counts(destination)
        )
        if (decision !is MigrationDecision.Allowed) return decision

        backupDir.mkdirs()
        val file = File(backupDir, AccountScopes.backupName(AccountScopes.guest(), stamp))
        file.writeText(Codec.encodeToString(Backup.serializer(), source))
        target.restore(source)
        return decision
    }

    private fun counts(b: Backup): Map<String, Int> = mapOf(
        "exercises" to b.exercises.size,
        "plans" to b.plans.size,
        "sessions" to b.sessions.size,
        "body" to b.body.size,
        "food" to b.food.size,
        "water" to b.water.size,
        "hiit" to b.hiitRuns.size
    )
}
