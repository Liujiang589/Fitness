package app.duolian.local.outdoor

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.*
import android.location.*
import android.os.*
import android.provider.Settings
import androidx.core.app.NotificationCompat
import app.duolian.local.DuolianApp
import app.duolian.local.MainActivity
import app.duolian.local.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

data class OutdoorLive(val running: Boolean = false, val daily: Boolean = false,
    val run: OutdoorRun? = null, val signal: OutdoorSignal = OutdoorSignal.IDLE, val error: String? = null,
    val speedMps: Double? = null, val accuracy: Float? = null, val busy: Boolean = false,
    val commandId: Long = 0, val stationary: Boolean = false, val filteredFixes: Int = 0,
    val stepReadAt: Long? = null, val fixAgeMs: Long? = null, val mapPosition: RoutePoint? = null,
    val preparation: OutdoorPreparation? = null, val preparationReady: Boolean = false)

/** Foreground session tracking; no boot receiver, silent GPS restart or always-on location. */
class OutdoorService : Service(), SensorEventListener, LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Mutex()
    private val store by lazy { (application as DuolianApp).outdoor }
    private val sensors by lazy { getSystemService(SensorManager::class.java) }
    private val locations by lazy { getSystemService(LocationManager::class.java) }
    private val recorder = OutdoorRecorder()
    private var preparation: OutdoorPreparation? = null
    private var registered = false
    private var stepReadAt: Long? = null
    private var locating = false
    private var daily = false
    private var loaded = false
    private var saveAt = 0L
    private var stopped = false
    private var error: String? = null
    private var commandId = 0L
    private var wakeLock: PowerManager.WakeLock? = null
    private val boot by lazy { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1) }
    private fun tick() = SystemClock.elapsedRealtime()
    private fun wall() = System.currentTimeMillis()

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "步行、跑步与日常计步", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) })
        daily = getSharedPreferences(PREFS, 0).getBoolean("daily", false) && canCount(this) && hasCounter(this)
        scope.launch {
            while (isActive) {
                delay(1000)
                lock.withLock {
                    if (stopped || !loaded) return@withLock
                    try {
                        recorder.advance(wall(), tick())
                        if(preparation!=null && (preparation!!.expired(tick()) || !canLocate(this@OutdoorService) || !gpsEnabled())) {
                            preparation=null;error="定位准备已结束，请到室外重新开始。"
                            syncResources()
                        }
                        if (registered && !canCount(this@OutdoorService)) {
                            sensors.unregisterListener(this@OutdoorService); registered = false; daily = false
                            saveOptions()
                        }
                        if (recorder.run?.status == OutdoorStatus.ACTIVE &&
                            (!canLocate(this@OutdoorService) || !gpsEnabled())) {
                            recorder.pause(wall(), tick(), OutdoorSignal.LOCATION_OFF)
                            error = if (!canLocate(this@OutdoorService)) "精确定位权限已关闭，请授权后继续" else null
                            syncResources(); persist(force = true)
                        }
                        maintainWakeLock()
                        if (tick() - saveAt >= CHECKPOINT_MS) {
                            persist(force = true)
                            getSystemService(NotificationManager::class.java).notify(NOTIFICATION, notification())
                        }
                        publish()
                        if (!canKeepForeground()) shutdown()
                    } catch (_: Exception) { failStorage() }
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action !in ACTIONS) { if (!loaded) stopSelf(); return START_NOT_STICKY }
        val incomingId = intent?.getLongExtra("command_id", 0) ?: 0
        val expectedRunId = intent?.getStringExtra("expected_run_id")
        try {
            if (action in START_ACTIONS) {
                require(action == DAILY_ON || canLocate(this)) { "请在系统设置中允许精确定位" }
                var types = if (Build.VERSION.SDK_INT >= 29 && action != DAILY_ON) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
                if (Build.VERSION.SDK_INT >= 34 && canCount(this) && hasCounter(this))
                    types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
                if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), types)
                else startForeground(NOTIFICATION, notification())
            }
        } catch (e: Exception) {
            error = e.message ?: "无法开始记录，请检查权限"
            commandId = incomingId
            if (!loaded) { live.value = OutdoorLive(error = error, commandId = commandId); stopSelf() } else publish()
            return START_NOT_STICKY
        }
        live.value = live.value.copy(busy = true, error = null)
        scope.launch { lock.withLock {
            var changed = false
            try {
                stopped = false
                if (!loaded) {
                    store.runs().filter { it.status != OutdoorStatus.FINISHED }.maxByOrNull { it.startedAt }
                        ?.let { recorder.recover(it, tick()) }
                    // Never charge steps collected while this process was absent to the new session.
                    store.clearBaseline(); loaded = true
                }
                error = null
                when (action) {
                    WALK, RUN -> {
                        check(recorder.run == null) { "已有未结束运动，请先继续或结束它" }
                        require(gpsEnabled()) { "请先打开手机定位，再到室外开始" }
                        changed = true
                        preparation=null
                        recorder.start(if (action == WALK) OutdoorKind.WALK else OutdoorKind.RUN, wall(), tick())
                    }
                    PREPARE_WALK, PREPARE_RUN -> {
                        check(recorder.run==null) { "请先继续或结束当前运动" }
                        require(gpsEnabled()) { "请先打开手机定位，再到室外准备" }
                        preparation=OutdoorPreparation(if(action==PREPARE_WALK)OutdoorKind.WALK else OutdoorKind.RUN,tick())
                    }
                    CANCEL_PREPARE -> preparation=null
                    RESUME -> {
                        require(gpsEnabled()) { "请先打开手机定位" }
                        changed = true
                        recorder.resume(wall(), tick())
                    }
                    PAUSE -> {
                        check(expectedRunId==null || recorder.run?.id==expectedRunId) { "运动记录已变化，请重新操作" }
                        changed = true; recorder.pause(wall(), tick())
                    }
                    FINISH -> {
                        check(expectedRunId == null || recorder.run?.id == expectedRunId) {
                            "运动记录已变化，请重新确认要保存的记录"
                        }
                        changed = true
                        // Commit first: a failed save must leave the recoverable record in memory.
                        recorder.finished(wall(), tick())?.let { store.save(it) }
                        recorder.clear()
                    }
                    DISCARD -> {
                        check(expectedRunId != null && recorder.run?.id == expectedRunId) {
                            "运动记录已变化，请重新确认要放弃的记录"
                        }
                        changed = true
                        recorder.discard(expectedRunId) {
                            store.discard(it)
                        }
                    }
                    DAILY_ON -> {
                        require(canCount(this@OutdoorService) && hasCounter(this@OutdoorService)) {
                            "此手机未提供计步传感器，或未授权身体活动权限"
                        }
                        if (!registered) store.clearBaseline()
                        changed = true
                        daily = true
                    }
                    DAILY_OFF -> { changed = true; daily = false }
                }
                saveOptions()
                // Toggling daily steps must not restart GPS or reset workout timing/steps.
                syncResources(); persist(force = true); commandId = incomingId; publish()
                if (canKeepForeground()) foregroundCurrent() else shutdown()
            } catch (e: Exception) {
                if(action==PREPARE_WALK || action==PREPARE_RUN)preparation=null
                if (changed) recorder.pause(wall(), tick())
                error = if (action == DISCARD && changed && recorder.run != null)
                    "放弃失败，本次记录已保留并暂停。请重试，或选择结束并保存。"
                else e.message ?: "无法记录，请检查权限后重试"
                runCatching { syncResources(); persist(force = true) }
                commandId = incomingId; publish()
                if (canKeepForeground()) runCatching { foregroundCurrent() }.onFailure { shutdown() }
                else shutdown()
            }
        } }
        return START_NOT_STICKY
    }

    private fun saveOptions() { getSharedPreferences(PREFS, 0).edit().putBoolean("daily", daily).apply() }
    private fun gpsEnabled() = locations.allProviders.contains(LocationManager.GPS_PROVIDER) &&
        locations.isProviderEnabled(LocationManager.GPS_PROVIDER)

    private fun syncResources() {
        val wantsGps = (recorder.run?.status == OutdoorStatus.ACTIVE || preparation!=null) && canLocate(this) && gpsEnabled()
        if (wantsGps && !locating) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                throw SecurityException("精确定位权限已关闭，请重新授权")
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, this, Looper.getMainLooper())
            locating = true
        } else if (!wantsGps && locating) { locations.removeUpdates(this); locating = false }
        val wantsCounter = (daily || recorder.run != null) && canCount(this) && hasCounter(this)
        if (wantsCounter && !registered) {
            registered = sensors.registerListener(this, sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER),
                SensorManager.SENSOR_DELAY_NORMAL, 0)
            if (!registered && daily) { daily = false; saveOptions(); error = "计步传感器暂不可用，可重试开启" }
        } else if (!wantsCounter && registered) { sensors.unregisterListener(this); registered = false }
        maintainWakeLock()
    }

    /** Bounded renewable lock only for a GPS workout, never for paused or daily-only sessions. */
    private fun maintainWakeLock() {
        if (locating && recorder.run?.status == OutdoorStatus.ACTIVE) {
            val resource = wakeLock ?: getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "独练:OutdoorGps")
                .apply { setReferenceCounted(false); wakeLock = this }
            if (!resource.isHeld) resource.acquire(10 * 60 * 1000L)
        } else releaseWakeLock()
    }
    private fun releaseWakeLock() { wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null }
    private suspend fun persist(force: Boolean = false) {
        if (force || tick() - saveAt >= CHECKPOINT_MS) {
            recorder.run?.let { store.save(it) }; saveAt = tick()
        }
    }
    private fun failStorage() {
        recorder.pause(wall(), tick())
        error = "保存失败，记录已暂停。请清理手机存储后重试；此前检查点已保留。"
        runCatching { syncResources() }; publish()
        runCatching { foregroundCurrent() }.onFailure { shutdown() }
    }

    override fun onLocationChanged(location: Location) {
        scope.launch { lock.withLock {
            if(!stopped && preparation!=null) {
                preparation=preparation?.accept(location.latitude,location.longitude,
                    if(location.hasAccuracy())location.accuracy else 999f,location.elapsedRealtimeNanos/1_000_000,
                    tick(),wall())
                publish();return@withLock
            }
            if (stopped || recorder.run?.status != OutdoorStatus.ACTIVE) return@withLock
            try {
                recorder.fix(location.latitude, location.longitude, if (location.hasAccuracy()) location.accuracy else 999f,
                    location.elapsedRealtimeNanos / 1_000_000, tick(), wall(),
                    location.speed.toDouble().takeIf { location.hasSpeed() },
                    location.speedAccuracyMetersPerSecond.toDouble().takeIf { location.hasSpeedAccuracy() })
                persist(); publish()
            } catch (_: Exception) { failStorage() }
        } }
    }
    override fun onProviderDisabled(provider: String) {
        if (provider != LocationManager.GPS_PROVIDER) return
        scope.launch { lock.withLock {
            if(preparation!=null) {
                preparation=null;error="手机定位已关闭，请开启后重新准备。"
                runCatching { syncResources() };publish()
                if(!canKeepForeground())shutdown()
            }
            if (recorder.run?.status == OutdoorStatus.ACTIVE) {
                recorder.pause(wall(), tick(), OutdoorSignal.LOCATION_OFF)
                runCatching { syncResources(); persist(force = true) }.onFailure { failStorage() }
                publish()
            }
        } }
    }
    override fun onProviderEnabled(provider: String) = Unit // Resume is always an explicit choice.
    @Deprecated("Legacy listener") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onSensorChanged(event: SensorEvent) {
        val counter = event.values.firstOrNull()?.takeIf { it.isFinite() && it >= 0 }?.toLong() ?: return
        val eventTick = event.timestamp / 1_000_000
        scope.launch { lock.withLock {
            if (stopped || !registered) return@withLock
            try {
                if (tick() - eventTick !in 0..15000) return@withLock
                val sampleWall = wall() - (tick() - eventTick)
                val date = java.time.Instant.ofEpochMilli(sampleWall).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
                store.steps(StepBaseline(date, counter, boot))
                stepReadAt = eventTick; recorder.enableStepCounting()
                recorder.stepCounter(counter, wall(), tick(), eventTick); persist(); publish()
            } catch (_: Exception) { failStorage() }
        } }
    }
    private fun publish() {
        live.value = OutdoorLive(!stopped, daily, recorder.run, recorder.signal, error,
            recorder.speedMps, recorder.accuracy, commandId = commandId, stationary = recorder.stationary,
            filteredFixes = recorder.filteredFixes, stepReadAt = stepReadAt.takeIf { registered },
            fixAgeMs = recorder.lastFixTick?.let { (tick() - it).coerceAtLeast(0) },mapPosition = recorder.mapPosition,
            preparation=preparation,preparationReady=preparation?.ready(tick())==true)
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java)
            .putExtra("open_outdoor", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val r = recorder.run
        val builder = NotificationCompat.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(r?.let { "独练 · ${it.kind.label}${if (it.status == OutdoorStatus.PAUSED) "已暂停" else "记录中"}" }
                ?: if(preparation!=null)"独练 · 准备${preparation!!.kind.label}" else "独练 · 日常计步已开启")
            .setContentText(r?.let { "%.2f km · %d 分钟 · %s".format(it.distanceMeters / 1000, it.elapsedMs / 60000, recorder.signal.title) }
                ?: if(preparation!=null)"正在准备GPS，尚未开始计时或保存路线" else "日常计步不使用 GPS · 点击查看")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
        if (r?.status == OutdoorStatus.ACTIVE) builder.addAction(0, "暂停运动", PendingIntent.getService(this, 1,
            Intent(this, OutdoorService::class.java).setAction(PAUSE), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        if (r != null) builder.addAction(0, "查看 / 结束", open)
        else if(preparation!=null)builder.addAction(0,"取消准备",PendingIntent.getService(this,3,
            Intent(this,OutdoorService::class.java).setAction(CANCEL_PREPARE),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        else builder.addAction(0, "关闭计步", PendingIntent.getService(this, 2,
            Intent(this, OutdoorService::class.java).setAction(DAILY_OFF), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        return builder.build()
    }
    private fun canKeepForeground() = daily && registered || (recorder.run != null || preparation!=null) && canLocate(this)
    private fun foregroundCurrent() {
        var types = if (Build.VERSION.SDK_INT >= 29 && (recorder.run != null || preparation!=null) && canLocate(this)) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        if (Build.VERSION.SDK_INT >= 34 && registered && canCount(this)) types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        if (Build.VERSION.SDK_INT >= 34 && types == 0) { shutdown(); return }
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION, notification(), types)
        else startForeground(NOTIFICATION, notification())
    }
    private fun shutdown() {
        stopped = true; releaseWakeLock()
        live.value = OutdoorLive(error = error, commandId = commandId)
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        sensors.unregisterListener(this); runCatching { locations.removeUpdates(this) }
        releaseWakeLock(); live.value = OutdoorLive(error = error, commandId = commandId)
        scope.cancel(); super.onDestroy()
    }
    companion object {
        const val WALK = "outdoor.WALK"; const val RUN = "outdoor.RUN"
        const val PAUSE = "outdoor.PAUSE"; const val RESUME = "outdoor.RESUME"
        const val FINISH = "outdoor.FINISH"; const val DAILY_ON = "outdoor.DAILY_ON"
        const val DISCARD = "outdoor.DISCARD"
        const val DAILY_OFF = "outdoor.DAILY_OFF"
        const val PREPARE_WALK="outdoor.PREPARE_WALK";const val PREPARE_RUN="outdoor.PREPARE_RUN"
        const val CANCEL_PREPARE="outdoor.CANCEL_PREPARE"
        private val START_ACTIONS = setOf(WALK, RUN, RESUME, DAILY_ON,PREPARE_WALK,PREPARE_RUN)
        private val ACTIONS = START_ACTIONS + setOf(PAUSE, FINISH, DISCARD, DAILY_OFF,CANCEL_PREPARE)
        private const val CHECKPOINT_MS = 5000L
        private const val CHANNEL = "outdoor-tracking"; private const val NOTIFICATION = 2301
        private const val PREFS = "outdoor-options"
        val live = MutableStateFlow(OutdoorLive())
        fun canLocate(c: Context) = c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        fun canCount(c: Context) = Build.VERSION.SDK_INT < 29 ||
            c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        fun hasCounter(c: Context) = c.getSystemService(SensorManager::class.java).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
        fun send(c: Context, action: String, commandId: Long = 0, expectedRunId: String? = null) {
            val intent = Intent(c, OutdoorService::class.java).setAction(action).putExtra("command_id", commandId)
                .putExtra("expected_run_id", expectedRunId)
            if (action in START_ACTIONS) c.startForegroundService(intent) else c.startService(intent)
        }
    }
}
