package app.duolian.local.domain

import kotlin.math.exp
import kotlin.math.max

/** Conservative gates for new GPS samples. Historical WGS84 tracks are never rewritten. */
object OutdoorTrackingQuality {
    const val MAX_ACCURACY_METERS = 20f
    const val MAX_FIX_AGE_MS = 8000L
    const val GAP_MS = 15000L

    fun reliableGroundSpeed(kind: OutdoorKind, speed: Double?, uncertainty: Double?): Double? =
        speed?.takeIf { it.isFinite() && it in 0.0..(if (kind == OutdoorKind.WALK) 5.0 else 12.0) &&
            (uncertainty == null || uncertainty.isFinite() && uncertainty in 0.0..1.0) }

    fun stationary(speed: Double?, uncertainty: Double?) = speed != null && speed < .35 &&
        uncertainty != null && uncertainty.isFinite() && uncertainty in 0.0..0.5

    fun plausible(before: RoutePoint, current: RoutePoint, kind: OutdoorKind, speed: Double?): Boolean {
        val seconds = (current.time - before.time) / 1000.0
        if (seconds <= 0) return false
        if (seconds > GAP_MS / 1000.0 || current.segment != before.segment) return true
        val distance = OutdoorMath.distance(before, current)
        if (distance / seconds > if (kind == OutdoorKind.WALK) 5.0 else 12.0) return false
        val noise = max(6.0, (before.accuracy + current.accuracy) * .5)
        return speed == null || distance <= speed * seconds + noise
    }

    fun movementThreshold(before: RoutePoint, current: RoutePoint) =
        max(4.0, (before.accuracy + current.accuracy) * .65)
}

/** Smooth the on-screen marker only; never feed these interpolated coordinates back into distance/GPX. */
object OutdoorMapPosition {
    fun next(previous: RoutePoint?, current: RoutePoint, stationary: Boolean): RoutePoint {
        if(previous==null || previous.segment!=current.segment || current.time-previous.time>15000) return current
        if(current.time<=previous.time)return previous
        if(stationary)return previous.copy(time=current.time,accuracy=current.accuracy)
        val alpha=1-exp(-(current.time-previous.time)/1500.0)
        return current.copy(latitude=previous.latitude+(current.latitude-previous.latitude)*alpha,
            longitude=previous.longitude+(current.longitude-previous.longitude)*alpha)
    }
}

/** Median rejects one-off spikes; a time-based low pass reduces pace flicker. Units remain m/s. */
class OutdoorSpeedFilter {
    private val samples = ArrayDeque<Pair<Long, Double>>()
    private var lastAt: Long? = null
    private var displayed: Double? = null
    fun reset() { samples.clear(); lastAt = null; displayed = null }
    fun add(speed: Double?, at: Long, stationary: Boolean = false): Double? {
        if (speed == null || !speed.isFinite() || speed < 0) return null
        if (lastAt?.let { at <= it } == true) return displayed
        if (lastAt?.let { at - it > OutdoorTrackingQuality.GAP_MS } == true) reset()
        if (stationary) { reset(); lastAt = at; displayed = 0.0; return 0.0 }
        samples.addLast(at to speed)
        while (samples.size > 5 || samples.firstOrNull()?.let { at - it.first > 8000 } == true) samples.removeFirst()
        val values = samples.map { it.second }.sorted()
        val median = values[(values.size - 1) / 2]
        val previous = displayed
        val alpha = lastAt?.let { 1 - exp(-(at - it) / 2500.0) } ?: 1.0
        displayed = if (previous == null) median else previous + alpha * (median - previous)
        lastAt = at
        return displayed
    }
}
