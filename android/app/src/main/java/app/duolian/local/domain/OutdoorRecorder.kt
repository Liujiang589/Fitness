package app.duolian.local.domain

/** In-memory recorder; monotonic time owns duration, persisted checkpoints own recovery. */
enum class OutdoorSignal(val title: String, val detail: String) {
    IDLE("准备出发", "到室外开阔处，开始后等待 GPS"),
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
    private var tickAt: Long? = null
    private var startedTick = 0L
    private var epochAnchor = 0L
    private var lastFixAt: Long? = null
    private var lastSampleAt: Long? = null
    private var previousFix: RoutePoint? = null
    private var previousCounter: Long? = null
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
        previousCounter = null; resetFixes()
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
            if (lastFixAt?.let { tick - it > 10000 } != false) speedMps = null
            if (tick - (lastFixAt ?: startedTick) > 30000 && signal != OutdoorSignal.LIMIT) {
                disconnect(); signal = OutdoorSignal.WEAK
            }
        }
    }

    fun pause(wall: Long, tick: Long, reason: OutdoorSignal = OutdoorSignal.PAUSED) {
        advance(wall, tick)
        run = run?.copy(status = OutdoorStatus.PAUSED)
        disconnect(); previousCounter = null
        signal = reason
    }

    fun resume(wall: Long, tick: Long) {
        val r = checkNotNull(run) { "没有可继续的运动" }
        if (r.status == OutdoorStatus.ACTIVE) return
        // Never charge time spent paused, dead or rebooting to this workout.
        run = r.copy(status = OutdoorStatus.ACTIVE, updatedAt = maxOf(r.updatedAt, wall))
        tickAt = tick; startedTick = tick; epochAnchor = maxOf(r.updatedAt, wall)
        previousCounter = null; resetFixes()
        signal = OutdoorSignal.SEARCHING
    }

    fun finished(wall: Long, tick: Long): OutdoorRun? {
        advance(wall, tick)
        return run?.let { it.copy(status = OutdoorStatus.FINISHED, endedAt = it.updatedAt) }
    }

    fun clear() {
        run = null; tickAt = null; previousCounter = null
        resetFixes(); signal = OutdoorSignal.IDLE
    }

    fun enableStepCounting() { run = run?.copy(stepCounting = true) }

    fun stepCounter(counter: Long, wall: Long, tick: Long, sampleTick: Long = tick) {
        if (counter < 0 || sampleTick > tick || run?.status == OutdoorStatus.ACTIVE && sampleTick < startedTick) return
        advance(wall, tick)
        run = run?.let { r ->
            val delta = if (r.status == OutdoorStatus.ACTIVE)
                previousCounter?.let { (counter - it).coerceAtLeast(0) } ?: 0 else 0
            r.copy(steps = (r.steps + delta).coerceAtMost(10_000_000))
        }
        previousCounter = counter
    }

    /** fixTick is the provider's elapsedRealtime timestamp, never the wall clock receipt time. */
    fun fix(latitude: Double, longitude: Double, precision: Float, fixTick: Long,
        tick: Long, wall: Long, reported: Double?, uncertainty: Double?) {
        if (run?.status != OutdoorStatus.ACTIVE || tick - fixTick !in 0..15000 ||
            fixTick < startedTick || lastSampleAt?.let { fixTick <= it } == true) return
        advance(wall, tick)
        lastSampleAt = fixTick
        val r = run!!
        val p = RoutePoint(latitude, longitude, epochAnchor + fixTick - startedTick, precision, r.segment)
        if (!OutdoorMath.valid(p)) {
            disconnect(); signal = OutdoorSignal.INACCURATE; return
        }
        if (r.points.size >= OutdoorMath.MAX_POINTS) {
            disconnect(); signal = OutdoorSignal.LIMIT; return
        }
        // Reject a teleport for both distance and speed, even if the provider reports ground speed.
        val before = previousFix
        if (before != null && before.segment == p.segment) {
            val seconds = (p.time - before.time) / 1000.0
            val limit = if (r.kind == OutdoorKind.WALK) 5.0 else 12.0
            if (seconds <= 0 || seconds <= 30 && OutdoorMath.distance(before, p) / seconds > limit) {
                speedMps = null; signal = OutdoorSignal.INACCURATE; return
            }
        }
        val next = OutdoorMath.append(r, p)
        // A rejected jump must not replace the trusted location used for speed.
        if (next.points.size == r.points.size && r.points.lastOrNull()?.let {
                it.segment == p.segment && OutdoorMath.distance(it, p) > 40
            } == true) {
            speedMps = null; signal = OutdoorSignal.INACCURATE; return
        }
        run = next
        val speedPoint = p.copy(segment = next.segment)
        speedMps = OutdoorSpeed.metersPerSecond(r.kind, previousFix, speedPoint, reported, uncertainty)
        previousFix = speedPoint; broken = false; lastFixAt = tick; accuracy = precision
        signal = OutdoorSignal.GOOD
    }

    private fun disconnect() {
        if (!broken) run = run?.copy(segment = run!!.segment + 1)
        broken = true; previousFix = null; speedMps = null; accuracy = null
    }
    private fun resetFixes() {
        disconnect(); lastFixAt = null; lastSampleAt = null
    }
}
