package app.duolian.local

import app.duolian.local.domain.*
import app.duolian.local.outdoor.*
import org.junit.Assert.*
import org.junit.Test

class OutdoorPlaceTest {
    private fun hint(tick: Long=1000,precision: Float=80f,gcj02: Boolean=true,cached: Boolean=false) =
        OutdoorPlace(RoutePoint(30.1,120.1,1700000000000,precision),tick,gcj02,cached)
    @Test fun oldFutureInvalidAndMeaninglessPlacesAreNotDisplayed() {
        val p=hint()
        assertTrue(p.fresh(31000));assertFalse(p.fresh(31001));assertFalse(p.fresh(999))
        for(accuracy in listOf(Float.NaN,Float.POSITIVE_INFINITY,0f,-1f,1001f))
            assertFalse(hint(precision=accuracy).fresh(2000))
        assertFalse(p.copy(point=p.point.copy(latitude=0.0,longitude=0.0)).fresh(2000))
    }
    @Test fun receivingAnOldCachedPositionDoesNotRefreshItsSampleTime() {
        assertNull(OutdoorPlace.choose(null,hint(cached=true),40000))
        assertNotNull(OutdoorPlace.choose(null,hint(cached=true),2000))
    }
    @Test fun cacheAndOutOfOrderCallbacksCannotReplaceAFreshResult() {
        val p=hint(2000)
        assertEquals(p,OutdoorPlace.choose(p,hint(1000),3000))
        assertEquals(p,OutdoorPlace.choose(p,hint(2500,cached=true),3000))
        assertEquals(p,OutdoorPlace.choose(p,hint(1000,gcj02=false),3000))
    }
    @Test fun transientCoarseFallbackDoesNotImmediatelyMoveThePositionFartherAway() {
        val p=hint(1000,20f)
        assertEquals(p,OutdoorPlace.choose(p,hint(2000,500f),2000))
        assertEquals(500f,OutdoorPlace.choose(p,hint(10000,500f),10000)!!.point.accuracy,0f)
        assertNull(OutdoorPlace.choose(p,hint(1000,5000f),40000))
    }
    @Test fun networkPositionIsDisplayOnlyAndDoesNotCreateDistanceSpeedOrGpxPoints() {
        val recorder=OutdoorRecorder();recorder.start(OutdoorKind.WALK,10000,1000)
        val run=recorder.run!!
        val live=OutdoorLive(running=true,run=run,signal=OutdoorSignal.SEARCHING,place=hint())
        assertEquals(hint().point,live.mapFrame(run).position)
        assertTrue(live.mapFrame(run).positionGcj02)
        assertEquals(0.0,live.metrics(run).distance,0.0);assertNull(live.metrics(run).speed)
        assertTrue(run.points.isEmpty());assertEquals(run,recorder.run)
        assertFalse(OutdoorMath.gpx(run).contains("<trkpt"))
        assertEquals(OutdoorSignal.APPROXIMATE,live.displaySignal(run.id))
    }
    @Test fun preciseGpsPositionTakesPriorityAndKeepsItsWgs84CoordinateContract() {
        val run=OutdoorRun(kind=OutdoorKind.RUN,startedAt=1000)
        val gps=RoutePoint(31.2,121.5,10000,5f)
        val live=OutdoorLive(running=true,run=run,signal=OutdoorSignal.GOOD,fixAgeMs=1000,
            mapPosition=gps,place=hint())
        assertEquals(gps,live.mapFrame(run).position);assertFalse(live.mapFrame(run).positionGcj02)
        assertEquals(OutdoorSignal.GOOD,live.displaySignal(run.id))
    }
    @Test fun staleGpsFallsBackToNetworkPositionWithoutKeepingItsSpeed() {
        val run=OutdoorRun(kind=OutdoorKind.RUN,startedAt=1000)
        val live=OutdoorLive(running=true,run=run,signal=OutdoorSignal.GOOD,fixAgeMs=12000,
            speedMps=5.0,mapPosition=RoutePoint(31.0,121.0,10000,5f),place=hint())
        assertEquals(hint().point,live.mapFrame(run).position)
        assertNull(live.metrics(run).speed)
        assertEquals(OutdoorSignal.APPROXIMATE,live.displaySignal(run.id))
    }
    @Test fun pausedOrOtherSessionsCannotBorrowTheNetworkPosition() {
        val run=OutdoorRun(kind=OutdoorKind.WALK,startedAt=1000)
        val live=OutdoorLive(running=true,run=run,signal=OutdoorSignal.SEARCHING,place=hint())
        assertNull(live.mapFrame(run.copy(id="other")).position)
        assertNull(live.copy(run=run.copy(status=OutdoorStatus.PAUSED)).mapFrame(run).position)
        assertNull(live.copy(running=false).mapFrame(run).position)
    }
    @Test fun systemNetworkLocationsAreNotMistakenForAlreadyOffsetAmapCoordinates() {
        val run=OutdoorRun(kind=OutdoorKind.WALK,startedAt=1000)
        val live=OutdoorLive(running=true,run=run,signal=OutdoorSignal.SEARCHING,place=hint(gcj02=false))
        assertNotNull(live.mapFrame(run).position);assertFalse(live.mapFrame(run).positionGcj02)
    }
}
