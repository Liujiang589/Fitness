package app.duolian.local

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.duolian.local.domain.*
import app.duolian.local.timer.TimerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val owner = app as DuolianApp
    val repo = owner.repository
    val exercises = repo.exercises.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val plans = repo.plans.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val sessions = repo.sessions.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val body = repo.body.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val food = repo.food.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val water = repo.water.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val rest = owner.restSeconds.stateIn(viewModelScope, SharingStarted.Eagerly, 60)
    val hiitRuns = MutableStateFlow<List<HiitRun>>(emptyList())
    val autoBackupAt = MutableStateFlow<Long?>(null)
    val ready = MutableStateFlow(false)
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)
    val pendingImport = MutableStateFlow<Backup?>(null)
    private val operationLock = Mutex()
    init { act { repo.initialize(); hiitRuns.value = repo.hiitRuns(); ready.value = true; autoBackup() } }
    fun act(job: suspend () -> Unit) {
        viewModelScope.launch {
            operationLock.withLock {
                busy.value = true
                try { job() } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { message.value = e.message ?: "操作失败" }
                finally { busy.value = false }
            }
        }
    }
    fun setRest(seconds: Int) = act { owner.setRest(seconds) }
    fun complete(m: String, s: String) = act {
        val active = sessions.value.firstOrNull { it.endedAt == null }
        val fallback = active?.planId?.let { planId -> plans.value.firstOrNull { it.id == planId }?.restSeconds }
            ?.takeIf { it > 0 } ?: rest.value
        val after = repo.changeActive { Rules.complete(it, m, s, fallback, System.currentTimeMillis()) }
        syncRestTimer(after)
    }
    fun addDrop(movementId: String) = act { repo.addDropSet(movementId) }
    fun skipRest() = act {
        repo.changeActive { it.copy(restDeadline = null, restRemainingMs = 0) }
        TimerService.stop(getApplication())
    }
    /** Keeps the foreground notification in sync with the session's rest deadline. */
    private fun syncRestTimer(s: Session) {
        val deadline = s.restDeadline
        if (s.pausedAt == null && deadline != null && deadline > System.currentTimeMillis())
            TimerService.startRest(getApplication(), deadline)
        else TimerService.stop(getApplication())
    }
    fun startHiitTimer(template: HiitTemplate) = TimerService.startHiit(getApplication(), template)
    fun stopTimer() = TimerService.stop(getApplication())
    fun saveHiitRun(run: HiitRun) = act { repo.addHiitRun(run); hiitRuns.value = repo.hiitRuns() }
    /** Adds a built-in template as a normal editable plan. */
    fun applyTemplate(t: PlanTemplate) = act {
        val plan = repo.applyTemplate(t)
        message.value = "已添加计划「${plan.name}」，共 ${plan.exerciseIds.size} 个动作"
    }
    /** Applies a progression suggestion to the exercise's default weight for future sessions. */
    fun applySuggestion(exercise: Exercise, deltaGrams: Long) = act {
        val updated = exercise.copy(defaultGrams = (exercise.defaultGrams + deltaGrams).coerceIn(0, 1_000_000))
        Rules.exercise(updated)
        repo.saveExercise(updated)
        message.value = "已把「${updated.name}」默认重量调整为 ${kg(updated.defaultGrams)} kg"
    }
    fun togglePause() = act {
        val s = repo.changeActive { session ->
            val now = System.currentTimeMillis()
            if (session.pausedAt == null) Rules.pause(session, now) else Rules.resume(session, now)
        }
        syncRestTimer(s)
    }
    fun finish() = act {
        repo.changeActive { Rules.finish(it, System.currentTimeMillis()) }
        TimerService.stop(getApplication())
    }
    fun export(uri: Uri) = act {
        val json = Codec.encodeToString(repo.snapshot(rest.value))
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "数据超过 8 MiB，请先清理部分历史记录" }
        withContext(Dispatchers.IO) {
            val stream = owner.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入此位置")
            stream.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
        }; message.value = "备份已导出（格式 duolian-native v2）"
    }
    fun inspectImport(uri: Uri) = act {
        val text = withContext(Dispatchers.IO) {
            val stream = owner.contentResolver.openInputStream(uri) ?: error("无法读取文件")
            val bytes = stream.use { input ->
                val result = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    require(result.size() + n <= MAX_BACKUP_BYTES) { "备份不能超过 8 MiB" }
                    result.write(buffer, 0, n)
                }
                result.toByteArray()
            }
            require(bytes.size <= MAX_BACKUP_BYTES) { "备份不能超过 8 MiB" }
            bytes.toString(Charsets.UTF_8)
        }
        val b = Codec.decodeFromString<Backup>(text); Rules.backup(b); pendingImport.value = b
    }
    fun confirmImport() = act {
        val b = pendingImport.value ?: return@act
        // Keep the last pre-import snapshot even if an I/O error or a later replacement fails.
        val old = Codec.encodeToString(repo.snapshot(rest.value))
        withContext(Dispatchers.IO) {
            val file = android.util.AtomicFile(File(owner.filesDir, "pre-import-backup.json"))
            val stream = file.startWrite()
            try { stream.write(old.toByteArray(Charsets.UTF_8)); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
        }
        repo.restore(b)
        hiitRuns.value = repo.hiitRuns()
        pendingImport.value = null
        try { owner.setRest(b.restSeconds); message.value = "导入完成，替换前备份已保留" }
        catch (e: Exception) { message.value = "训练数据已导入；休息偏好更新失败，可在我的中重新设置" }
    }
    fun exportRecovery(uri: Uri) = act {
        withContext(Dispatchers.IO) {
            val file = File(owner.filesDir, "pre-import-backup.json")
            require(file.exists()) { "尚无导入前备份" }
            val out = owner.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入文件")
            out.use { dest -> file.inputStream().use { it.copyTo(dest) } }
        }; message.value = "导入前备份已导出"
    }
    fun exportAutoBackup(uri: Uri) = act {
        withContext(Dispatchers.IO) {
            val file = File(owner.filesDir, AUTO_BACKUP)
            require(file.exists()) { "尚无自动备份" }
            val out = owner.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入文件")
            out.use { dest -> file.inputStream().use { it.copyTo(dest) } }
        }; message.value = "自动备份已导出"
    }

    /** Writes at most one automatic snapshot per day and keeps yesterday's copy as a fallback. */
    private suspend fun autoBackup() {
        runCatching {
            withContext(Dispatchers.IO) {
                val target = File(owner.filesDir, AUTO_BACKUP)
                val today = LocalDate.now()
                if (target.exists()) {
                    val written = Instant.ofEpochMilli(target.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate()
                    autoBackupAt.value = target.lastModified()
                    if (!written.isBefore(today)) return@withContext
                    target.copyTo(File(owner.filesDir, "$AUTO_BACKUP.1"), overwrite = true)
                }
                val json = Codec.encodeToString(repo.snapshot(rest.value))
                if (json.toByteArray(Charsets.UTF_8).size > MAX_BACKUP_BYTES) return@withContext
                target.writeText(json, Charsets.UTF_8)
                autoBackupAt.value = target.lastModified()
            }
        }
    }
    companion object {
        const val MAX_BACKUP_BYTES = 8 * 1024 * 1024
        private const val AUTO_BACKUP = "auto-backup.json"
    }
}
