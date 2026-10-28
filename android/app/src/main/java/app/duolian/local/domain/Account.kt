package app.duolian.local.domain

import java.security.MessageDigest

/**
 * Account core that does not depend on any provider being configured yet.
 *
 * Rules encoded here (see docs/ACCOUNT_ELIGIBILITY_2026-10-29.md):
 * - A 独练 account has a stable server-issued uid; identities are attached to it, never to nicknames or avatars.
 * - An identity is (type, normalized value). Two identities are the same person only if the provider says so
 *   (same openid under the same appid, or the same verified phone number).
 * - Nothing in this file performs network calls; the server owns the real checks.
 */
enum class IdentityType { PHONE, WECHAT, QQ }

data class IdentityKey(val type: IdentityType, val value: String)

data class AccountProfile(
    val uid: String,
    val createdAt: Long,
    val identities: List<IdentityKey> = emptyList()
) {
    fun has(identity: IdentityKey) = identities.any { it == identity }
}

/** Why a login attempt produced a new account instead of attaching to an existing one. */
enum class LinkDecision { SAME_ACCOUNT, NEW_ACCOUNT, REQUIRE_EXPLICIT_LINK }

object Accounts {
    private val mainLandPhone = Regex("^1[3-9]\\d{9}$")

    /** Normalises a mainland mobile number; returns null for anything that is not an 11-digit mobile. */
    fun normalizePhone(raw: String): String? {
        val digits = raw.filter { it.isDigit() }
        val withoutCountry = when {
            digits.length == 13 && digits.startsWith("86") -> digits.substring(2)
            else -> digits
        }
        return withoutCountry.takeIf { mainLandPhone.matches(it) }
    }

    fun identity(type: IdentityType, raw: String): IdentityKey? {
        val value = when (type) {
            IdentityType.PHONE -> normalizePhone(raw)
            IdentityType.WECHAT, IdentityType.QQ -> raw.trim().lowercase().takeIf { it.isNotEmpty() && it.length <= 128 }
        } ?: return null
        return IdentityKey(type, value)
    }

    /**
     * Decides whether a verified identity attaches to [existing] or opens a new account.
     * [existing] is the account that already owns an identity of the same type (for example the phone number).
     * A different identity of another type never merges automatically: the user must confirm the link.
     */
    fun link(existing: AccountProfile?, identity: IdentityKey, explicitLink: Boolean): LinkDecision = when {
        existing == null -> LinkDecision.NEW_ACCOUNT
        existing.has(identity) -> LinkDecision.SAME_ACCOUNT
        explicitLink -> LinkDecision.SAME_ACCOUNT
        else -> LinkDecision.REQUIRE_EXPLICIT_LINK
    }

    fun attach(profile: AccountProfile, identity: IdentityKey): AccountProfile =
        if (profile.has(identity)) profile else profile.copy(identities = profile.identities + identity)

    fun detach(profile: AccountProfile, identity: IdentityKey): AccountProfile {
        val remaining = profile.identities - identity
        // The last identity cannot be removed; an account without a login method would be unreachable.
        return if (remaining.isEmpty()) profile else profile.copy(identities = remaining)
    }
}

/** Server-side verification-code policy. Client countdowns are cosmetic; these numbers are authoritative. */
data class CodePolicy(
    val ttlSeconds: Long = 300,
    val resendSeconds: Long = 60,
    val maxAttempts: Int = 5,
    val maxPerHour: Int = 5,
    val maxPerDay: Int = 10
)

enum class CodeRejection { EXPIRED, ALREADY_USED, ATTEMPT_LIMIT, WRONG_CODE, NOT_ISSUED }

sealed interface CodeResult {
    data object Ok : CodeResult
    data class Rejected(val reason: CodeRejection, val retryAfterSeconds: Long = 0) : CodeResult
}

sealed interface SendResult {
    data object Sent : SendResult
    data class TooSoon(val retryAfterSeconds: Long) : SendResult
    data object HourlyLimit : SendResult
    data object DailyLimit : SendResult
}

data class CodeState(
    val issuedAt: Long,
    val attempts: Int = 0,
    val consumed: Boolean = false,
    val lastSentAt: Long = 0,
    val sentLastHour: Int = 0,
    val sentToday: Int = 0
)

object VerificationCodes {
    fun canSend(state: CodeState?, now: Long, policy: CodePolicy = CodePolicy()): SendResult {
        if (state != null) {
            val elapsed = now - state.lastSentAt
            if (elapsed < policy.resendSeconds * 1000) {
                return SendResult.TooSoon(policy.resendSeconds - elapsed / 1000)
            }
            if (state.sentLastHour >= policy.maxPerHour) return SendResult.HourlyLimit
            if (state.sentToday >= policy.maxPerDay) return SendResult.DailyLimit
        }
        return SendResult.Sent
    }

    /** Hashes the code before storage or comparison; plain codes never leave the request handler. */
    fun hash(code: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((salt + ":" + code).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun verify(
        state: CodeState?,
        providedCode: String,
        expectedHash: String,
        salt: String,
        now: Long,
        policy: CodePolicy = CodePolicy()
    ): CodeResult {
        if (state == null) return CodeResult.Rejected(CodeRejection.NOT_ISSUED)
        if (state.consumed) return CodeResult.Rejected(CodeRejection.ALREADY_USED)
        if (now - state.issuedAt > policy.ttlSeconds * 1000) return CodeResult.Rejected(CodeRejection.EXPIRED)
        if (state.attempts >= policy.maxAttempts) return CodeResult.Rejected(CodeRejection.ATTEMPT_LIMIT)
        if (hash(providedCode.trim(), salt) != expectedHash) {
            return CodeResult.Rejected(CodeRejection.WRONG_CODE, retryAfterSeconds = policy.ttlSeconds)
        }
        return CodeResult.Ok
    }
}

/** Session rules shared by client and server; the server remains the only authority on validity. */
data class AccountSession(val uid: String, val refreshToken: String, val issuedAt: Long, val expiresAt: Long)

object AccountSessions {
    const val ACCESS_MINUTES: Long = 30
    const val REFRESH_DAYS: Long = 30

    fun newSession(uid: String, refreshToken: String, now: Long): AccountSession =
        AccountSession(uid, refreshToken, now, now + REFRESH_DAYS * 24 * 3600 * 1000)

    fun expired(session: AccountSession, now: Long) = now >= session.expiresAt

    /** Refresh a little before expiry so an in-flight request does not race the deadline. */
    fun needsRefresh(session: AccountSession, now: Long) =
        !expired(session, now) && session.expiresAt - now <= ACCESS_MINUTES * 60 * 1000 / 2
}
