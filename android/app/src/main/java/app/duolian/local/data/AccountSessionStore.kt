package app.duolian.local.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.duolian.local.domain.StoredSession
import app.duolian.local.domain.StoredSessions
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.accountSessionData by preferencesDataStore("account_session")

/**
 * Persists only the session ticket (uid + refresh token + expiry). No profile, no training data.
 * A corrupt or tampered value is treated as "signed out" instead of crashing on launch.
 */
class AccountSessionStore(private val context: Context) {
    private val key = stringPreferencesKey("session")

    val session: Flow<StoredSession?> =
        context.accountSessionData.data.map { prefs -> prefs[key]?.let(StoredSessions::decode) }

    suspend fun save(session: StoredSession) {
        context.accountSessionData.edit { it[key] = StoredSessions.encode(session) }
    }

    suspend fun clear() {
        context.accountSessionData.edit { it.remove(key) }
    }
}
