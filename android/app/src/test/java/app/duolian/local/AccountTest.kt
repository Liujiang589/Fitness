package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class AccountTest {
    private fun profile(uid: String = "u1", vararg ids: IdentityKey) =
        AccountProfile(uid, 0, ids.toList())

    @Test fun phoneNormalisationAcceptsMainlandMobile() {
        assertEquals("13800138000", Accounts.normalizePhone("138 0013 8000"))
        assertEquals("13800138000", Accounts.normalizePhone("+86 138-0013-8000"))
        assertEquals("13800138000", Accounts.normalizePhone("8613800138000"))
    }

    @Test fun phoneNormalisationRejectsInvalidNumbers() {
        assertNull(Accounts.normalizePhone("12345"))
        assertNull(Accounts.normalizePhone("23800138000"))
        assertNull(Accounts.normalizePhone("138001380001"))
        assertNull(Accounts.normalizePhone(""))
    }

    @Test fun wechatIdentityIsCaseFoldedAndTrimmed() {
        assertEquals(IdentityKey(IdentityType.WECHAT, "oabc"), Accounts.identity(IdentityType.WECHAT, " OAbc "))
        assertNull(Accounts.identity(IdentityType.WECHAT, "   "))
    }

    @Test fun sameIdentityKeepsAccount() {
        val phone = Accounts.identity(IdentityType.PHONE, "13800138000")!!
        assertEquals(LinkDecision.SAME_ACCOUNT, Accounts.link(profile(ids = arrayOf(phone)), phone, explicitLink = false))
    }

    @Test fun differentIdentityRequiresExplicitLink() {
        val phone = Accounts.identity(IdentityType.PHONE, "13800138000")!!
        val wechat = Accounts.identity(IdentityType.WECHAT, "oabc")!!
        assertEquals(LinkDecision.REQUIRE_EXPLICIT_LINK, Accounts.link(profile(ids = arrayOf(phone)), wechat, explicitLink = false))
        assertEquals(LinkDecision.SAME_ACCOUNT, Accounts.link(profile(ids = arrayOf(phone)), wechat, explicitLink = true))
    }

    @Test fun unknownIdentityCreatesNewAccount() {
        val qq = Accounts.identity(IdentityType.QQ, "ABCDEF")!!
        assertEquals(LinkDecision.NEW_ACCOUNT, Accounts.link(null, qq, explicitLink = false))
    }

    @Test fun attachIsIdempotentAndDetachKeepsLastIdentity() {
        val phone = Accounts.identity(IdentityType.PHONE, "13800138000")!!
        val qq = Accounts.identity(IdentityType.QQ, "abcdef")!!
        val once = Accounts.attach(profile(uid = "u9"), phone)
        assertEquals(once, Accounts.attach(once, phone))
        assertEquals(setOf(phone, qq), Accounts.attach(once, qq).identities.toSet())
        assertEquals(once, Accounts.detach(Accounts.attach(once, qq), qq))
        assertEquals(once, Accounts.detach(once, phone))
    }

    private val policy = CodePolicy(ttlSeconds = 300, resendSeconds = 60, maxAttempts = 3, maxPerHour = 5, maxPerDay = 10)

    @Test fun sendBlockedUntilResendWindowPasses() {
        val state = CodeState(issuedAt = 0, lastSentAt = 0)
        val blocked = VerificationCodes.canSend(state, now = 30_000, policy = policy)
        assertTrue(blocked is SendResult.TooSoon)
        assertEquals(30, (blocked as SendResult.TooSoon).retryAfterSeconds)
        assertEquals(SendResult.Sent, VerificationCodes.canSend(state, now = 61_000, policy = policy))
    }

    @Test fun hourlyAndDailySendLimits() {
        val busyHour = CodeState(issuedAt = 0, lastSentAt = 0, sentLastHour = 5, sentToday = 5)
        assertEquals(SendResult.HourlyLimit, VerificationCodes.canSend(busyHour, now = 61_000, policy = policy))
        val busyDay = CodeState(issuedAt = 0, lastSentAt = 0, sentLastHour = 1, sentToday = 10)
        assertEquals(SendResult.DailyLimit, VerificationCodes.canSend(busyDay, now = 61_000, policy = policy))
    }

    @Test fun codeExpiresAfterTtl() {
        val hash = VerificationCodes.hash("123456", "salt")
        val state = CodeState(issuedAt = 0)
        assertEquals(CodeResult.Ok, VerificationCodes.verify(state, "123456", hash, "salt", now = 299_000, policy = policy))
        val expired = VerificationCodes.verify(state, "123456", hash, "salt", now = 301_000, policy = policy)
        assertEquals(CodeResult.Rejected(CodeRejection.EXPIRED), expired)
    }

    @Test fun wrongCodeAndAttemptLimit() {
        val hash = VerificationCodes.hash("123456", "salt")
        assertEquals(
            CodeResult.Rejected(CodeRejection.WRONG_CODE, retryAfterSeconds = 300),
            VerificationCodes.verify(CodeState(issuedAt = 0), "000000", hash, "salt", now = 1_000, policy = policy)
        )
        val exhausted = CodeState(issuedAt = 0, attempts = 3)
        assertEquals(
            CodeResult.Rejected(CodeRejection.ATTEMPT_LIMIT),
            VerificationCodes.verify(exhausted, "123456", hash, "salt", now = 1_000, policy = policy)
        )
    }

    @Test fun consumedCodeCannotBeReused() {
        val hash = VerificationCodes.hash("123456", "salt")
        val state = CodeState(issuedAt = 0, consumed = true)
        assertEquals(
            CodeResult.Rejected(CodeRejection.ALREADY_USED),
            VerificationCodes.verify(state, "123456", hash, "salt", now = 1_000, policy = policy)
        )
    }

    @Test fun missingCodeIsRejected() {
        assertEquals(
            CodeResult.Rejected(CodeRejection.NOT_ISSUED),
            VerificationCodes.verify(null, "123456", "deadbeef", "salt", now = 1_000, policy = policy)
        )
    }

    @Test fun hashIsSaltedAndStable() {
        assertEquals(VerificationCodes.hash("123456", "a"), VerificationCodes.hash("123456", "a"))
        assertNotEquals(VerificationCodes.hash("123456", "a"), VerificationCodes.hash("123456", "b"))
        assertNotEquals("123456", VerificationCodes.hash("123456", "a"))
    }

    @Test fun sessionExpiryAndRefreshWindow() {
        val now = 1_000_000L
        val session = AccountSessions.newSession("uid-1", "refresh", now)
        assertFalse(AccountSessions.expired(session, now + 1_000))
        assertTrue(AccountSessions.needsRefresh(session, session.expiresAt - 60_000))
        assertFalse(AccountSessions.needsRefresh(session, now))
        assertTrue(AccountSessions.expired(session, session.expiresAt))
    }
}
