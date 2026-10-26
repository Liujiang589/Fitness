package app.duolian.local.domain

/** In-memory recorder; monotonic time owns duration, persisted checkpoints own recovery. */
enum class OutdoorSignal(val title: String, val detail: String) {
    IDLE("准备出发", "可先开始计时计步，可靠路线就绪后计算里程"),
    APPROXIMATE("已找到大致位置", "网络位置仅供参考；计时计步继续，可靠路线就绪后计距"),
    SEARCHING("寻找 GPS", "时长已开始；收到可靠定位后绘制路线"),
    GOOD("定位正常", "正在记录路线，锁屏后仍会继续请求定位"),
    WEAK("GPS 信号较弱", "时长继续；缺失路线不补画直线"),
    INACCURATE("定位精度不足", "已过滤不可靠位置，等待信号恢复"),
    PAUSED("运动已暂停", "暂停期间不计时、不记录路线"),
    LOCATION_OFF("定位已关闭，运动暂停", "打开手机定位后，点击继续运动"),
    INTERRUPTED("发现未结束的运动", "已保留检查点；中断时间不计入，继续后另起路线"),
    LIMIT("路线点已达上限", "请结束保存，再开始一场新运动")
}

class OutdoorRecorder {
    var run: OutdoorRun? = null
        private set
    var signal = OutdoorSignal.IDLE
        private set
    var accuracy: Float? = null
        private set
    var speedMps: Double? = null
        private set
    var stationary: Boolean = false
        private set
    var filteredFixes: Int = 0
        private set
    var mapPosition: RoutePoint? = null
        private set
    private val speedFilter = OutdoorSpeedFilter()
    private var tickAt: Long? = null
    private var startedTick = 0L
    private var epochAnchor = 0L
    private var lastFixAt: Long? = null
    val lastFixTick: Long? get() = lastFixAt
    private var lastSampleAt: Long? = null
    private var previousFix: RoutePoint? = null
    private var previousCounter: Long? = null
    private var previousCounterAt: Long? = null
    private var broken = true

    fun recover(saved: OutdoorRun, tick: Long) {
        require(run == null && saved.status != OutdoorStatus.FINISHED)
        run = saved.copy(status = OutdoorStatus.PAUSED, segment = saved.segment + 1)
        tickAt = tick
        resetFixes()
        signal = OutdoorSignal.INTERRUPTED
    }

    fun start(kind: OutdoorKind, wall: Long, tick: Long) {
        check(run == null) { "已有未结束运动，请先继续或结束它" }
        run = OutdoorRun(kind = kind, startedAt = wall)
        tickAt = tick; startedTick = tick; epochAnchor = wall
        previousCounter = null; previousCounterAt = null; filteredFixes = 0; resetFixes()
        signal = OutdoorSignal.SEARCHING
    }

    fun advance(wall: Long, tick: Long) {
        val previousTick = tickAt
        run = run?.let { r ->
            val elapsed = r.elapsedMs + if (r.status == OutdoorStatus.ACTIVE && previousTick != null)
                (tick - previousTick).coerceAtLeast(0) else 0L
            r.copy(elapsedMs = elapsed, updatedAt = maxOf(r.updatedAt, wall, r.startedAt + elapsed,
                if (r.status == OutdoorStatus.ACTIVE) epochAnchor + tick - startedTick else r.updatedAt))
        }
        tickAt = tick
        if (run?.status == OutdoorStatus.ACTIVE) {
            if (lastFixAt?.let { tick - it > 10000 } != false) { speedMps = null; accuracy = null }
            if (tick - (lastFixAt ?: startedTick) > OutdoorTrackingQuality.GAP_MS && signal != OutdoorSignal.LIMIT) {
                disconnect(); signal = OutdoorSignal.WEAK
            }
        }
    }

    fun pause(wall: Long, tick: Long, reason: OutdoorSignal = OutdoorSignal.PAUSED) {
        advance(wall, tick)
        run = run?.copy(status = OutdoorStatus.PAUSED)
        disconnect(); previousCounter = null; previousCounterAt = null
        signal = reason
    }

    fun resume(wall: Long, tick: Long) {
        val r = checkNotNull(run) { "没有可继续的运动" }
        if (r.status == OutdoorStatus.ACTIVE) return
        // Never charge time spent paused, dead or rebooting to this workout.
        run = r.copy(status = OutdoorStatus.ACTIVE, updatedAt = maxOf(r.updatedAt, wall))
        tickAt = tick; startedTick = tick; epochAnchor = maxOf(r.updatedAt, wall)
        previousCounter = null; previousCounterAt = null; resetFixes()
        signal = OutdoorSignal.SEARCHING
    }

    fun finished(wall: Long, tick: Long): OutdoorRun? {
        advance(wall, tick)
        return run?.let { it.copy(status = OutdoorStatus.FINISHED, endedAt = it.updatedAt) }
    }

    fun clear() {
        run = null; tickAt = null; previousCounter = null; previousCounterAt = null; filteredFixes = 0
        resetFixes(); signal = OutdoorSignal.IDLE
    }

    /** Caller serializes commands and callbacks. Delete the checkpoint before releasing recovery state. */
    suspend fun discard(expectedId: String, deleteCheckpoint: suspend (String) -> Unit) {
        val current = checkNotNull(run) { "本次运动已结束，请返回查看当前状态" }
        check(current.id == expectedId && current.status != OutdoorStatus.FINISHED) {
            "运动记录已变化，请重新确认要放弃的记录"
        }
        deleteCheckpoint(current.id)
        clear()
    }

    fun enableStepCounting() { run = run?.copy(stepCounting = true) }

    fun stepCounter(counter: Long, wall: Long, tick: Long, sampleTick: Long = tick) {
        if (counter < 0 || sampleTick > tick || previousCounterAt?.let { sampleTick <= it } == true ||
            run?.status == OutdoorStatus.ACTIVE && sampleTick < startedTick) return
        advance(wall, tick)
        run = run?.let { r ->
            val delta = if (r.status == OutdoorStatus.ACTIVE)
                previousCounter?.let { (counter - it).coerceAtLeast(0) } ?: 0 else 0
            r.copy(steps = (r.steps + delta).coerceAtMost(10_000_000))
        }
        previousCounter = counter
        previousCounterAt = sampleTick
    }

    /** fixTick is the provider's elapsedRealtime timestamp, never the wall clock receipt time. */
    fun fix(latitude: Double, longitude: Double, precision: Float, fixTick: Long,
        tick: Long, wall: Long, reported: Double?, uncertainty: Double?) {
        if (run?.status != OutdoorStatus.ACTIVE || tick - fixTick !in 0..OutdoorTrackingQuality.MAX_FIX_AGE_MS ||
            fixTick < startedTick || lastSampleAt?.let { fixTick <= it } == true) return
        advance(wall, tick)
        lastSampleAt = fixTick
        var r = run!!
        var p = RoutePoint(latitude, longitude, epochAnchor + fixTick - startedTick, precision, r.segment)
        if (!OutdoorMath.valid(p) || precision > OutdoorTrackingQuality.MAX_ACCURACY_METERS) {
            filteredFixes++
            disconnect(); signal = OutdoorSignal.INACCURATE; return
        }
        if (r.points.size >= OutdoorMath.MAX_POINTS) {
            disconnect(); signal = OutdoorSignal.LIMIT; return
        }
        val groundSpeed = OutdoorTrackingQuality.reliableGroundSpeed(r.kind, reported, uncertainty)
        val still = OutdoorTrackingQuality.stationary(groundSpeed, uncertainty)
        // Reject a teleport and ground-speed-inconsistent displacement before changing either metric.
        val before = previousFix
        if (before != null && (!OutdoorTrackingQuality.plausible(before, p, r.kind, null) ||
            !still && !OutdoorTrackingQuality.plausible(before, p, r.kind, groundSpeed))) {
            filteredFixes++; speedMps = null; signal = OutdoorSignal.INACCURATE; return
        }
        val anchor = r.points.lastOrNull()
        // A stopped receiver can wander within its error radius. Keep the map anchor and distance fixed.
        if (still && anchor != null && anchor.segment == p.segment) {
            stationary = true; previousFix = p; lastFixAt = fixTick; accuracy = precision; broken = false
            speedMps = speedFilter.add(0.0, fixTick, stationary = true); signal = OutdoorSignal.GOOD
            mapPosition = OutdoorMapPosition.next(mapPosition ?: anchor,p,true)
            return
        }
        // After a long stop or lost fixes, resume a separate segment instead of drawing a shortcut.
        if (anchor != null && anchor.segment == p.segment && p.time - anchor.time > OutdoorTrackingQuality.GAP_MS) {
            disconnect(); r = run!!; p = p.copy(segment = r.segment)
        }
        val routeAnchor = r.points.lastOrNull()?.takeIf { it.segment == p.segment }
        if (routeAnchor != null && !OutdoorTrackingQuality.plausible(routeAnchor, p, r.kind, groundSpeed)) {
            filteredFixes++; speedMps = null; signal = OutdoorSignal.INACCURATE; return
        }
        val moved = routeAnchor == null || OutdoorMath.distance(routeAnchor, p) >= OutdoorTrackingQuality.movementThreshold(routeAnchor, p)
        val next = if (moved) OutdoorMath.append(r, p) else r
        run = next
        val speedPoint = p.copy(segment = next.segment)
        // Coordinate fallback uses the distance anchor, rather than tiny two-second GPS jitters.
        val candidate = groundSpeed ?: if (moved) OutdoorSpeed.metersPerSecond(r.kind, routeAnchor, speedPoint, null) else null
        speedMps = speedFilter.add(candidate, fixTick, still)
        stationary = still
        mapPosition = OutdoorMapPosition.next(mapPosition,speedPoint,still)
        previousFix = speedPoint; broken = false; lastFixAt = fixTick; accuracy = precision
        signal = OutdoorSignal.GOOD
    }

    private fun disconnect() {
        if (!broken) run = run?.copy(segment = run!!.segment + 1)
        broken = true; previousFix = null; speedMps = null; accuracy = null; stationary = false; mapPosition = null; speedFilter.reset()
    }
    private fun resetFixes() {
        disconnect(); lastFixAt = null; lastSampleAt = null
    }
}
