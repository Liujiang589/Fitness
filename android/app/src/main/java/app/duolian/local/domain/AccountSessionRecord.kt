package app.duolian.local.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the client may persist between launches. It deliberately holds no profile data:
 * the uid plus a refresh token are enough to restore a session, and nothing here is training data.
 */
@Serializable
data class StoredSession(
    val uid: String,
    val refreshToken: String,
    val expiresAt: Long
) {
    fun scope(): AccountScope = AccountScopes.account(uid)

    fun usable(now: Long) = expiresAt > now && refreshToken.isNotBlank() && AccountScopes.isValidUid(uid)
}

object StoredSessions {
    private val json = Json { ignoreUnknownKeys = true }

    /** Rejects malformed or tampered storage instead of trusting whatever is on disk. */
    fun decode(raw: String): StoredSession? = runCatching {
        json.decodeFromString<StoredSession>(raw)
    }.getOrNull()?.takeIf { AccountScopes.isValidUid(it.uid) && it.refreshToken.isNotBlank() }

    fun encode(session: StoredSession): String {
        require(AccountScopes.isValidUid(session.uid)) { "账号 ID 格式无效" }
        require(session.refreshToken.isNotBlank()) { "刷新令牌不能为空" }
        return json.encodeToString(StoredSession.serializer(), session)
    }
}
