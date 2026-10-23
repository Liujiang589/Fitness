package app.duolian.local.domain

import kotlin.math.max

/** Prefer GPS ground speed; use coordinate-derived speed only across a short, continuous gap. */
object OutdoorSpeed {
    fun metersPerSecond(kind: OutdoorKind, previous: RoutePoint?, current: RoutePoint,
        reported: Double?, uncertainty: Double? = null): Double? {
        if (!OutdoorMath.valid(current)) return null
        val limit = if (kind == OutdoorKind.WALK) 5.0 else 12.0
        if (reported != null && reported.isFinite() && reported in 0.0..limit &&
            (uncertainty == null || uncertainty.isFinite() && uncertainty in 0.0..1.5)) return reported
        val before = previous ?: return null
        if (!OutdoorMath.valid(before) || before.segment != current.segment) return null
        val seconds = (current.time - before.time) / 1000.0
        if (seconds !in 1.0..15.0) return null
        val distance = OutdoorMath.distance(before, current)
        if (distance < max(4.0, (before.accuracy + current.accuracy) * .5)) return 0.0
        return (distance / seconds).takeIf { it.isFinite() && it in 0.0..limit }
    }
    fun currentPace(speed: Double?): String {
        if (speed == null || !speed.isFinite() || speed < .3) return "—"
        val seconds = kotlin.math.round(1000 / speed).toLong()
        return "%d′%02d″".format(seconds / 60, seconds % 60)
    }
    fun averageKmh(run: OutdoorRun): Double? =
        if (run.elapsedMs <= 0 || run.distanceMeters < 1) null else run.distanceMeters / run.elapsedMs * 3600
}
