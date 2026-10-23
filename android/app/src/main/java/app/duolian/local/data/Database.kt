package app.duolian.local.data

import androidx.room.*
import app.duolian.local.domain.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "exercises") data class ExerciseRow(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "plans") data class PlanRow(@PrimaryKey val id: String, val payload: String)
// The aggregate retains independent exercise/set snapshots. A nullable unique slot enforces one active session.
@Entity(tableName = "sessions", indices = [Index(value = ["activeSlot"], unique = true)])
data class SessionRow(@PrimaryKey val id: String, val activeSlot: Int?, val payload: String)
@Entity(tableName = "body") data class BodyRow(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "food") data class FoodRow(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "water") data class WaterRow(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "metadata") data class MetaRow(@PrimaryKey val key: String, val value: String)

@Dao interface LocalDao {
    @Query("SELECT * FROM metadata WHERE `key` LIKE 'outdoor:%'") fun outdoorRows(): Flow<List<MetaRow>>
    @Query("SELECT * FROM metadata WHERE `key` LIKE 'outdoor:%'") suspend fun allOutdoorRows(): List<MetaRow>
    @Query("DELETE FROM metadata WHERE `key` LIKE 'outdoor:%'") suspend fun clearOutdoorRows()
    @Query("DELETE FROM metadata WHERE `key` = :key") suspend fun deleteMetadata(key: String)
    @Query("SELECT * FROM exercises ORDER BY rowid") fun exercises(): Flow<List<ExerciseRow>>
    @Query("SELECT * FROM plans ORDER BY rowid") fun plans(): Flow<List<PlanRow>>
    @Query("SELECT * FROM sessions ORDER BY rowid DESC") fun sessions(): Flow<List<SessionRow>>
    @Query("SELECT * FROM body ORDER BY rowid DESC") fun body(): Flow<List<BodyRow>>
    @Query("SELECT * FROM food ORDER BY rowid DESC") fun food(): Flow<List<FoodRow>>
    @Query("SELECT * FROM water ORDER BY rowid DESC") fun water(): Flow<List<WaterRow>>
    @Query("SELECT * FROM exercises ORDER BY rowid") suspend fun allExercises(): List<ExerciseRow>
    @Query("SELECT * FROM plans ORDER BY rowid") suspend fun allPlans(): List<PlanRow>
    @Query("SELECT * FROM sessions ORDER BY rowid DESC") suspend fun allSessions(): List<SessionRow>
    @Query("SELECT * FROM body") suspend fun allBody(): List<BodyRow>
    @Query("SELECT * FROM food") suspend fun allFood(): List<FoodRow>
    @Query("SELECT * FROM water") suspend fun allWater(): List<WaterRow>
    @Query("SELECT * FROM sessions WHERE activeSlot = 1 LIMIT 1") suspend fun active(): SessionRow?
    @Query("SELECT value FROM metadata WHERE `key` = :key") suspend fun metadata(key: String): String?
    @Upsert suspend fun put(x: ExerciseRow)
    @Upsert suspend fun put(x: PlanRow)
    @Upsert suspend fun put(x: SessionRow)
    @Upsert suspend fun put(x: BodyRow)
    @Upsert suspend fun put(x: FoodRow)
    @Upsert suspend fun put(x: WaterRow)
    @Upsert suspend fun put(x: MetaRow)
    @Query("DELETE FROM exercises WHERE id = :id") suspend fun deleteExercise(id: String)
    @Query("DELETE FROM plans WHERE id = :id") suspend fun deletePlan(id: String)
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun deleteSession(id: String)
    @Query("DELETE FROM body WHERE id = :id") suspend fun deleteBody(id: String)
    @Query("DELETE FROM food WHERE id = :id") suspend fun deleteFood(id: String)
    @Query("DELETE FROM water WHERE id = :id") suspend fun deleteWater(id: String)
    @Query("DELETE FROM exercises") suspend fun clearExercises()
    @Query("DELETE FROM plans") suspend fun clearPlans()
    @Query("DELETE FROM sessions") suspend fun clearSessions()
    @Query("DELETE FROM body") suspend fun clearBody()
    @Query("DELETE FROM food") suspend fun clearFood()
    @Query("DELETE FROM water") suspend fun clearWater()
}
@Database(entities = [ExerciseRow::class, PlanRow::class, SessionRow::class, BodyRow::class,
    FoodRow::class, WaterRow::class, MetaRow::class], version = 1, exportSchema = true)
abstract class LocalDatabase : RoomDatabase() { abstract fun dao(): LocalDao }
