package app.duolian.local

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.duolian.local.data.LocalDatabase
import app.duolian.local.data.Repository
import kotlinx.coroutines.flow.map

private val Application.preferences by preferencesDataStore("preferences")
class DuolianApp : Application() {
    override fun onCreate() { super.onCreate(); AppCrashReport.install(this) }
    val database by lazy { Room.databaseBuilder(this, LocalDatabase::class.java, "duolian.db").build() }
    val repository by lazy { Repository(database) }
    val outdoor by lazy { app.duolian.local.data.OutdoorStore(database) }
    private val restKey = intPreferencesKey("rest_seconds")
    val restSeconds by lazy { preferences.data.map { (it[restKey] ?: 60).coerceIn(0, 600) } }
    suspend fun setRest(seconds: Int) { require(seconds in 0..600); preferences.edit { it[restKey] = seconds } }
}
