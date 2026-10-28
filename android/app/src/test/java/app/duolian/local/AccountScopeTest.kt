package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class AccountScopeTest {
    private val uidA = "uid_aaaaaaaa1111"
    private val uidB = "uid_bbbbbbbb2222"

    @Test fun guestKeepsTheExistingDatabaseName() {
        assertEquals("duolian.db", AccountScopes.dbName(AccountScopes.guest()))
    }

    @Test fun eachAccountGetsItsOwnDatabaseFile() {
        val a = AccountScopes.dbName(AccountScopes.account(uidA))
        val b = AccountScopes.dbName(AccountScopes.account(uidB))
        assertNotEquals("duolian.db", a)
        assertNotEquals(a, b)
        assertTrue(a.startsWith("duolian-account-"))
        assertTrue(a.endsWith(".db"))
    }

    @Test fun databaseNameIsStableForTheSameUid() {
        assertEquals(
            AccountScopes.dbName(AccountScopes.account(uidA)),
            AccountScopes.dbName(AccountScopes.account(uidA))
        )
    }

    @Test fun fileNameDoesNotExposeTheServerUid() {
        val name = AccountScopes.dbName(AccountScopes.account(uidA))
        assertFalse(name.contains(uidA))
    }

    @Test fun invalidUidsAreRejected() {
        assertFalse(AccountScopes.isValidUid("short"))
        assertFalse(AccountScopes.isValidUid("has space here"))
        assertFalse(AccountScopes.isValidUid("-leading-dash"))
        assertNull(runCatching { AccountScopes.account("bad id") }.getOrNull())
        assertTrue(AccountScopes.isValidUid("a1234567"))
    }

    @Test fun backupNamesAreScoped() {
        val guest = AccountScopes.backupName(AccountScopes.guest(), "20261029")
        val account = AccountScopes.backupName(AccountScopes.account(uidA), "20261029")
        assertNotEquals(guest, account)
        assertTrue(guest.contains("guest"))
        assertTrue(account.contains("account-"))
    }

    @Test fun migrationNotNeededWhenGuestIsEmpty() {
        val decision = AccountMigration.decide(
            guest = AccountScopes.guest(),
            target = AccountScopes.account(uidA),
            guestCounts = mapOf("sessions" to 0, "food" to 0),
            targetCounts = emptyMap()
        )
        assertTrue(decision is MigrationDecision.NotNeeded)
    }

    @Test fun migrationAllowedIntoEmptyAccount() {
        val decision = AccountMigration.decide(
            guest = AccountScopes.guest(),
            target = AccountScopes.account(uidA),
            guestCounts = mapOf("sessions" to 12, "food" to 3),
            targetCounts = mapOf("sessions" to 0)
        )
        assertTrue(decision is MigrationDecision.Allowed)
        assertEquals(15, (decision as MigrationDecision.Allowed).recordCount)
    }

    @Test fun migrationBlockedWhenAccountAlreadyHasRecords() {
        val decision = AccountMigration.decide(
            guest = AccountScopes.guest(),
            target = AccountScopes.account(uidA),
            guestCounts = mapOf("sessions" to 12),
            targetCounts = mapOf("sessions" to 1)
        )
        assertTrue(decision is MigrationDecision.Blocked)
    }

    @Test fun migrationRequiresGuestSourceAndAccountTarget() {
        assertNull(runCatching {
            AccountMigration.decide(AccountScopes.account(uidA), AccountScopes.account(uidB), emptyMap(), emptyMap())
        }.getOrNull())
        assertNull(runCatching {
            AccountMigration.decide(AccountScopes.guest(), AccountScopes.guest(), emptyMap(), emptyMap())
        }.getOrNull())
    }

    @Test fun storedSessionRequiresValidUidAndToken() {
        val session = StoredSession(uidA, "refresh-token", 2_000)
        assertTrue(session.usable(1_000))
        assertFalse(session.usable(2_000))
        assertFalse(session.copy(refreshToken = "").usable(1_000))
        assertFalse(session.copy(uid = "bad id").usable(1_000))
    }

    @Test fun storedSessionRoundTripsAndRejectsTampering() {
        val raw = StoredSessions.encode(StoredSession(uidA, "token", 5_000))
        val decoded = StoredSessions.decode(raw)
        assertNotNull(decoded)
        assertEquals(uidA, decoded!!.uid)
        assertNull(StoredSessions.decode("not json"))
        assertNull(StoredSessions.decode("""{"uid":"x","refreshToken":"t","expiresAt":1}"""))
    }

    @Test fun sessionScopeMapsToTheSameDatabaseAsItsAccount() {
        val session = StoredSession(uidA, "token", 5_000)
        assertEquals(
            AccountScopes.dbName(AccountScopes.account(uidA)),
            AccountScopes.dbName(session.scope())
        )
    }
}
