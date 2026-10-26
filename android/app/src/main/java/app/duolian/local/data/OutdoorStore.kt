package app.duolian.local.data

import androidx.room.withTransaction
import app.duolian.local.domain.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString

/** One metadata row per activity/date: compatible with existing installations, no destructive migration. */
class OutdoorStore(private val db: LocalDatabase) {
    private val dao = db.dao()
    val runs = dao.outdoorRows().map { rows -> rows.filter { it.key.startsWith(RUN) } }.distinctUntilChanged()
        .map { rows -> rows.map { Codec.decodeFromString<OutdoorRun>(it.value) }.sortedByDescending { it.startedAt } }
        .flowOn(Dispatchers.Default)
    val days = dao.outdoorRows().map { rows -> rows.filter { it.key.startsWith(DAY) } }.distinctUntilChanged()
        .map { rows -> rows.map { Codec.decodeFromString<DailySteps>(it.value) }.sortedByDescending { it.date } }
        .flowOn(Dispatchers.Default)
    suspend fun runs() = withContext(Dispatchers.Default) { dao.allOutdoorRows().filter { it.key.startsWith(RUN) }
        .map { Codec.decodeFromString<OutdoorRun>(it.value) } }
    suspend fun days() = dao.allOutdoorRows().filter { it.key.startsWith(DAY) }
        .map { Codec.decodeFromString<DailySteps>(it.value) }
    suspend fun save(run: OutdoorRun) = withContext(Dispatchers.Default) {
        OutdoorMath.validate(run); dao.put(MetaRow(RUN + run.id, Codec.encodeToString(run)))
    }
    suspend fun delete(id: String) = dao.deleteMetadata(RUN + id)
    suspend fun discard(id: String) = db.withTransaction {
        dao.metadata(RUN + id)?.let { value ->
            check(Codec.decodeFromString<OutdoorRun>(value).status != OutdoorStatus.FINISHED) {
                "这次运动已经保存，请在运动足迹中管理"
            }
        }
        dao.deleteMetadata(RUN + id)
    }
    suspend fun clearBaseline() = dao.deleteMetadata(BASELINE)
    suspend fun steps(next: StepBaseline): Long = db.withTransaction {
        val before = dao.metadata(BASELINE)?.let { Codec.decodeFromString<StepBaseline>(it) }
        val delta = OutdoorMath.stepDelta(before, next)
        val day = dao.metadata(DAY + next.date)?.let { Codec.decodeFromString<DailySteps>(it) }
            ?: DailySteps(next.date)
        dao.put(MetaRow(DAY + next.date, Codec.encodeToString(day.copy(steps = (day.steps + delta).coerceAtMost(10_000_000)))))
        dao.put(MetaRow(BASELINE, Codec.encodeToString(next)))
        delta
    }
    suspend fun replace(runs: List<OutdoorRun>, days: List<DailySteps>) {
        dao.clearOutdoorRows(); clearBaseline()
        runs.forEach { save(it) }
        days.forEach { dao.put(MetaRow(DAY + it.date, Codec.encodeToString(it))) }
    }
    private companion object {
        const val RUN = "outdoor:run:"
        const val DAY = "outdoor:day:"
        const val BASELINE = "step-baseline"
    }
}
