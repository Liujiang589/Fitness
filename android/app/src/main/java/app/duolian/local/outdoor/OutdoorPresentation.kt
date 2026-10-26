package app.duolian.local.outdoor

import app.duolian.local.domain.*

/** Clock ticks must not invalidate the screen shell, history list or native map. */
data class OutdoorSessionHead(val id: String,val kind: OutdoorKind,val startedAt: Long,val status: OutdoorStatus) {
    fun fallback()=OutdoorRun(id=id,kind=kind,startedAt=startedAt,status=status)
}
data class OutdoorShell(val session: OutdoorSessionHead?=null,val running: Boolean=false,
    val daily: Boolean=false,val busy: Boolean=false,val error: String?=null,val stepReceived: Boolean=false,
    val preparingKind: OutdoorKind?=null)
fun OutdoorLive.shell()=OutdoorShell(run?.let{OutdoorSessionHead(it.id,it.kind,it.startedAt,it.status)},
    running,daily,busy,error,stepReadAt!=null,preparation?.kind)

data class OutdoorMetrics(val distance: Double,val steps: Long,val stepCounting: Boolean,val speed: Double?)
fun OutdoorLive.metrics(fallback: OutdoorRun): OutdoorMetrics {
    val current=run?.takeIf{it.id==fallback.id} ?: fallback
    val recording=running && run?.id==fallback.id && run.status==OutdoorStatus.ACTIVE
    return OutdoorMetrics(current.distanceMeters,current.steps,current.stepCounting,speedMps.takeIf {
        recording && signal==OutdoorSignal.GOOD && (fixAgeMs ?: Long.MAX_VALUE)<=10000
    })
}
fun OutdoorLive.displaySignal(id: String)=when {
    run?.id!=id -> OutdoorSignal.INTERRUPTED
    signal==OutdoorSignal.GOOD && (fixAgeMs ?: Long.MAX_VALUE)>10000 -> OutdoorSignal.WEAK
    else -> signal
}
data class OutdoorMapFrame(val points: List<RoutePoint>,val tracking: Boolean,val position: RoutePoint?)
fun OutdoorLive.mapFrame(fallback: OutdoorRun): OutdoorMapFrame {
    val current=run?.takeIf{it.id==fallback.id} ?: fallback
    val tracking=running && run?.id==fallback.id && run.status==OutdoorStatus.ACTIVE
    return OutdoorMapFrame(current.points,tracking,mapPosition.takeIf {
        tracking && signal==OutdoorSignal.GOOD && (fixAgeMs ?: Long.MAX_VALUE)<=10000
    })
}
