package app.duolian.local

import app.duolian.local.domain.*
import app.duolian.local.outdoor.*
import org.junit.Assert.*
import org.junit.Test

class OutdoorExperienceTest {
    @Test fun preparingRequiresAFreshAccurateFixAndDoesNotCreateARecordedRun() {
        val recorder=OutdoorRecorder()
        val preparation=OutdoorPreparation(OutdoorKind.RUN,1000)
        assertFalse(preparation.ready(1000));assertNull(recorder.run)
        val ready=preparation.accept(30.0,120.0,8f,2000,2000,1700000000000)
        assertTrue(ready.ready(10000));assertFalse(ready.ready(10001))
        assertNull(recorder.run);assertNull(recorder.speedMps)
    }
    @Test fun staleImpreciseAndOutOfOrderFixesCannotMakePreparationReady() {
        val p=OutdoorPreparation(OutdoorKind.WALK,1000)
        assertFalse(p.accept(30.0,120.0,30f,2000,2000,10000).ready(2000))
        assertEquals(p,p.accept(30.0,120.0,8f,999,2000,10000))
        assertEquals(p,p.accept(30.0,120.0,8f,2000,11000,10000))
        val ready=p.accept(30.0,120.0,8f,3000,3000,10000)
        assertFalse(ready.accept(30.0,120.0,40f,4000,4000,11000).ready(4000))
        assertEquals(ready,ready.accept(30.1,120.1,8f,2000,4000,11000))
        assertFalse(p.expired(90999));assertTrue(p.expired(91000))
    }
    private fun points(count: Int,segment: Int=0)=List(count){i->RoutePoint(30.0+i*.00001,120.0,1000L+i*1000,5f,segment)}
    @Test fun aLongTrackAppendOnlyUploadsOneSmallTailInsteadOfTheEntireRoute() {
        val p=points(14000);val chunks=OutdoorRouteChunks()
        chunks.update(p)
        val update=chunks.update(p+RoutePoint(30.2,120.0,15000000,5f))
        assertFalse(update.reset);assertEquals(1,update.changed.size)
        assertTrue(update.changed.sumOf{it.points.size}<=64)
    }
    @Test fun fixedChunksShareTheirBoundaryAndNeverBridgeGpsGaps() {
        val chunks=OutdoorRouteChunks(4);val source=points(8)
        val first=chunks.update(source)
        assertEquals(3,first.changed.size)
        assertEquals(first.changed[0].points.last(),first.changed[1].points.first())
        val second=chunks.update(source+RoutePoint(31.0,121.0,12000,5f,1))
        assertEquals(1,second.changed.size);assertEquals(1,second.changed.single().points.size)
        assertEquals(1,second.changed.single().key.segment)
    }
    @Test fun replacingAnExistingTrackResetsStaleGeometryButEqualCheckpointsDoNot() {
        val chunks=OutdoorRouteChunks();val source=points(10)
        chunks.update(source)
        assertTrue(chunks.update(source.toList()).changed.isEmpty())
        val edited=source.toMutableList().apply{this[4]=this[4].copy(longitude=120.2)}
        assertTrue(chunks.update(edited).reset)
        assertTrue(chunks.update(emptyList()).reset)
    }
    @Test fun clockTicksDoNotInvalidateTheScreenShellDashboardOrMap() {
        val r=OutdoorRun(kind=OutdoorKind.RUN,startedAt=1000)
        val live=OutdoorLive(running=true,run=r,signal=OutdoorSignal.GOOD,fixAgeMs=1000)
        val later=live.copy(run=r.copy(elapsedMs=1000,updatedAt=2000),fixAgeMs=2000)
        assertEquals(live.shell(),later.shell())
        assertEquals(live.metrics(r),later.metrics(r))
        assertEquals(live.mapFrame(r),later.mapFrame(r))
    }
    @Test fun mapHidesStaleFixesAndNeverBorrowsAnotherWorkoutsSpeed() {
        val r=OutdoorRun(kind=OutdoorKind.RUN,startedAt=1000)
        val live=OutdoorLive(running=true,run=r,signal=OutdoorSignal.GOOD,fixAgeMs=11000,
            speedMps=3.0,mapPosition=points(1).first())
        assertNull(live.mapFrame(r).position)
        assertNull(live.metrics(r).speed)
        assertEquals(OutdoorSignal.WEAK,live.displaySignal(r.id))
        assertNull(live.metrics(r.copy(id="another")).speed)
    }
    @Test fun briefMissingSpeedHoldsTheLastReadingButNeverIndefinitelyOrAcrossReset() {
        val filter=OutdoorSpeedFilter();filter.add(3.0,1000)
        assertEquals(3.0,filter.add(null,3000)!!,0.0)
        assertNull(filter.add(null,7001))
        filter.reset();assertNull(filter.add(null,8000))
    }
}
