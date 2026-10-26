package app.duolian.local.domain

/** Ephemeral GPS warm-up: it never creates an activity, advances a timer or writes a checkpoint. */
data class OutdoorPreparation(val kind: OutdoorKind, val startedTick: Long,
    val position: RoutePoint? = null, val fixTick: Long? = null) {
    fun ready(now: Long) = position != null && fixTick?.let { now-it in 0..8000 } == true
    fun expired(now: Long) = now-startedTick >= 90000
    fun accept(latitude: Double, longitude: Double, accuracy: Float, sample: Long, now: Long, wall: Long): OutdoorPreparation {
        val point=RoutePoint(latitude,longitude,wall,accuracy)
        if(sample<startedTick || now-sample !in 0..OutdoorTrackingQuality.MAX_FIX_AGE_MS ||
            fixTick?.let { sample<=it } == true) return this
        if(!OutdoorMath.valid(point) || accuracy>OutdoorTrackingQuality.MAX_ACCURACY_METERS)
            return copy(position=null,fixTick=sample)
        return copy(position=point,fixTick=sample)
    }
}

/** Fixed route chunks overlap by one point; an appended fix only changes the live tail. */
data class RouteChunkKey(val segment: Int,val index: Int)
data class RouteChunk(val key: RouteChunkKey,val points: List<RoutePoint>)
data class RouteChunkUpdate(val reset: Boolean,val changed: List<RouteChunk>)
class OutdoorRouteChunks(private val capacity: Int = 64) {
    init { require(capacity>=2) }
    private var previous: List<RoutePoint> = emptyList()
    private val chunks=linkedMapOf<RouteChunkKey,MutableList<RoutePoint>>()
    private var segment: Int?=null
    private var chunkIndex=0
    fun update(points: List<RoutePoint>): RouteChunkUpdate {
        if(points===previous)return RouteChunkUpdate(false,emptyList())
        val append=previous.isNotEmpty() && points.size>previous.size &&
            points.first()==previous.first() && points[previous.lastIndex]==previous.last()
        if(!append && points==previous) { previous=points;return RouteChunkUpdate(false,emptyList()) }
        val reset=!append
        val offset=if(append)previous.size else 0
        if(reset) { chunks.clear();segment=null;chunkIndex=0 }
        val dirty=linkedSetOf<RouteChunkKey>()
        for(i in offset until points.size) {
            val point=points[i]
            if(segment!=point.segment) { segment=point.segment;chunkIndex=0 }
            var key=RouteChunkKey(point.segment,chunkIndex)
            val current=chunks.getOrPut(key){mutableListOf()}
            if(current.size>=capacity) {
                chunkIndex++;key=RouteChunkKey(point.segment,chunkIndex)
                chunks[key]=mutableListOf(current.last())
            }
            chunks.getValue(key).add(point);dirty+=key
        }
        previous=points
        return RouteChunkUpdate(reset,dirty.map { RouteChunk(it,chunks.getValue(it).toList()) })
    }
}
