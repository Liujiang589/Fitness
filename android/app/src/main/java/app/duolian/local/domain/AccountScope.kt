package app.duolian.local.domain

import java.security.MessageDigest

/**
 * Decides which local database a session reads and writes.
 *
 * Guest use keeps the existing file name so upgrading never loses data. An account gets its own file,
 * so signing out of A and into B cannot show A's records even though both live on the same phone.
 * These rules are pure so they can be tested without Room or Android.
 */
data class AccountScope(val uid: String?) {
    val isGuest: Boolean get() = uid == null
}

object AccountScopes {
    const val GUEST_DB = "duolian.db"
    private val uidPattern = Regex("^[a-zA-Z0-9][a-zA-Z0-9_-]{7,63}$")

    fun guest() = AccountScope(null)

    fun account(uid: String): AccountScope {
        require(isValidUid(uid)) { "账号 ID 格式无效" }
        return AccountScope(uid)
    }

    fun isValidUid(uid: String) = uidPattern.matches(uid)

    /** Stable file name per uid; hashed so the path never exposes the server ID verbatim. */
    fun dbName(scope: AccountScope): String {
        val uid = scope.uid ?: return GUEST_DB
        require(isValidUid(uid)) { "账号 ID 格式无效" }
        val digest = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray())
        val suffix = digest.take(8).joinToString("") { "%02x".format(it) }
        return "duolian-account-$suffix.db"
    }

    /** Backup files from different scopes never collide, so restoring A cannot overwrite B. */
    fun backupName(scope: AccountScope, timestamp: String): String {
        val tag = scope.uid?.let { "account-" + dbName(scope).removePrefix("duolian-account-").removeSuffix(".db") }
            ?: "guest"
        return "auto-backup-$tag-$timestamp.json"
    }
}

/** Result of asking whether the guest records may be moved into the signed-in account. */
sealed interface MigrationDecision {
    /** Nothing to move: the guest database is empty or the account already has data. */
    data class NotNeeded(val reason: String) : MigrationDecision
    /** Safe to move; a backup must be written before the copy starts. */
    data class Allowed(val source: AccountScope, val target: AccountScope, val recordCount: Int) : MigrationDecision
    /** Target already has records; merging two histories automatically would be destructive. */
    data class Blocked(val reason: String) : MigrationDecision
}

object AccountMigration {
    /**
     * [guestCounts] and [targetCounts] are per-table record counts. Migration never merges into a
     * non-empty account, and never runs without an explicit user choice (this function only judges).
     */
    fun decide(
        guest: AccountScope,
        target: AccountScope,
        guestCounts: Map<String, Int>,
        targetCounts: Map<String, Int>
    ): MigrationDecision {
        require(guest.isGuest) { "迁移来源必须是本机使用" }
        require(!target.isGuest) { "迁移目标必须是已登录账号" }
        val guestTotal = guestCounts.values.sum()
        val targetTotal = targetCounts.values.sum()
        return when {
            guestTotal == 0 -> MigrationDecision.NotNeeded("本机没有可迁移的记录")
            targetTotal > 0 -> MigrationDecision.Blocked("该账号已有记录，自动合并可能覆盖历史，需要人工确认")
            else -> MigrationDecision.Allowed(guest, target, guestTotal)
        }
    }
}
