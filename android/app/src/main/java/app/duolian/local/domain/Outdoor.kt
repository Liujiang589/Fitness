package app.duolian.local.domain

import kotlinx.serialization.Serializable
import java.time.LocalDate
import kotlin.math.*

@Serializable enum class OutdoorKind(val label: String) { WALK("步行"), RUN("跑步") }
@Serializable enum class OutdoorStatus { ACTIVE, PAUSED, FINISHED }
@Serializable data class RoutePoint(val latitude: Double, val longitude: Double, val time: Long,
    val accuracy: Float, val segment: Int = 0)
@Serializable data class OutdoorRun(val id: String = newId(), val kind: OutdoorKind,
    val startedAt: Long, val updatedAt: Long = startedAt, val endedAt: Long? = null,
    val status: OutdoorStatus = OutdoorStatus.ACTIVE, val elapsedMs: Long = 0,
    val distanceMeters: Double = 0.0, val steps: Long = 0, val segment: Int = 0,
    val stepCounting: Boolean = false,
    val points: List<RoutePoint> = emptyList())
@Serializable data class DailySteps(val date: String, val steps: Long = 0)
@Serializable data class StepBaseline(val date: String, val counter: Long, val boot: Int)

object OutdoorMath {
    const val MAX_POINTS = 14400 // At a two-second interval: at least eight hours of accepted fixes.
    fun distance(a: RoutePoint, b: RoutePoint): Double {
        val lat = Math.toRadians(b.latitude - a.latitude)
        val lon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
            cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
        return 6371008.8 * 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }
    fun valid(p: RoutePoint): Boolean = p.latitude.isFinite() && p.longitude.isFinite() &&
        p.latitude in -90.0..90.0 && p.longitude in -180.0..180.0 &&
        p.accuracy.isFinite() && p.accuracy in 0f..35f && p.time > 0 && p.segment >= 0

    /** No straight-line distance across pauses, lost fixes or implausible jumps. */
    fun append(run: OutdoorRun, raw: RoutePoint): OutdoorRun {
        if (run.status != OutdoorStatus.ACTIVE || !valid(raw) || run.points.size >= MAX_POINTS) return run
        val last = run.points.lastOrNull()
        if (last != null && raw.time <= last.time) return run
        var p = raw.copy(segment = run.segment)
        var meters = 0.0
        if (last != null && last.segment == p.segment) {
            val seconds = (p.time - last.time) / 1000.0
            if (seconds > 30) p = p.copy(segment = run.segment + 1)
            else {
                meters = distance(last, p)
                if (meters / seconds > if (run.kind == OutdoorKind.WALK) 5.0 else 12.0) return run
                if (meters < max(4.0, (last.accuracy + p.accuracy) * .25)) return run
            }
        }
        return run.copy(points = run.points + p, segment = p.segment,
            distanceMeters = run.distanceMeters + meters)
    }
    /** The first counter is a baseline, never the device's entire since-boot total. */
    fun stepDelta(before: StepBaseline?, next: StepBaseline): Long =
        if (before == null || before.date != next.date || before.boot != next.boot || next.counter < before.counter) 0
        else (next.counter - before.counter).coerceAtLeast(0)

    fun validate(run: OutdoorRun) {
        require(run.id.matches(Regex("[a-zA-Z0-9_-]{1,80}")))
        require(run.startedAt in 1..10_000_000_000_000L && run.updatedAt >= run.startedAt)
        require(run.elapsedMs in 0..(run.updatedAt - run.startedAt) && run.steps in 0..10_000_000)
        require(run.distanceMeters.isFinite() && run.distanceMeters in 0.0..2_000_000.0)
        require(run.segment in 0..100000 && run.points.size <= MAX_POINTS)
        require((run.status == OutdoorStatus.FINISHED) == (run.endedAt != null))
        require(run.endedAt == null || run.endedAt == run.updatedAt)
        require(run.points.all { valid(it) && it.time in run.startedAt..run.updatedAt && it.segment <= run.segment })
        require(run.points.zipWithNext().all { (a, b) -> b.time > a.time && b.segment >= a.segment })
    }
    fun validate(day: DailySteps) {
        require(LocalDate.parse(day.date).toString() == day.date && day.steps in 0..10_000_000)
    }
    fun pace(run: OutdoorRun): String {
        if (run.distanceMeters < 100 || run.elapsedMs == 0L) return "—"
        val seconds = (run.elapsedMs / run.distanceMeters).roundToLong()
        return "%d′%02d″ / km".format(seconds / 60, seconds % 60)
    }
    fun gpx(run: OutdoorRun): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><gpx version=\"1.1\" creator=\"独练\" xmlns=\"http://www.topografix.com/GPX/1/1\"><trk><name>")
        append(run.kind.label); append("</name>")
        run.points.groupBy { it.segment }.values.forEach { points ->
            append("<trkseg>")
            points.forEach { p ->
                append("<trkpt lat=\""); append(p.latitude); append("\" lon=\""); append(p.longitude)
                append("\"><time>"); append(java.time.Instant.ofEpochMilli(p.time)); append("</time></trkpt>")
            }
            append("</trkseg>")
        }
        append("</trk></gpx>")
    }
}
