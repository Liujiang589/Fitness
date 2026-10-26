package app.duolian.local.domain

/** Display-only position. It must never be passed to the route/distance/speed recorder. */
data class OutdoorPlace(val point: RoutePoint, val sampleTick: Long,
    val gcj02: Boolean = false, val cached: Boolean = false, val source: String = "系统网络定位") {
    fun fresh(now: Long) = now - sampleTick in 0..MAX_AGE_MS &&
        point.latitude.isFinite() && point.longitude.isFinite() &&
        point.latitude in -90.0..90.0 && point.longitude in -180.0..180.0 &&
        point.time > 0 && point.accuracy.isFinite() &&
        point.accuracy > 0 && point.accuracy <= MAX_ACCURACY && !(point.latitude == 0.0 && point.longitude == 0.0)
    companion object {
        const val MAX_AGE_MS = 30000L
        const val MAX_ACCURACY = 1000f
        fun choose(current: OutdoorPlace?, candidate: OutdoorPlace, now: Long): OutdoorPlace? {
            val before = current?.takeIf { it.fresh(now) }
            if (!candidate.fresh(now)) return before
            if (before != null) {
                if (candidate.sampleTick <= before.sampleTick || !before.cached && candidate.cached) return before
                if (now - before.sampleTick < 8000 && candidate.point.accuracy > before.point.accuracy * 2) return before
            }
            return candidate
        }
    }
}
