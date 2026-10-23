package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class OutdoorQualityTest {
    @Test fun mapMarkerSmoothingIsBoundedAndNeverUsedAsDistanceInput() {
        val a=RoutePoint(30.0,120.0,1000,5f)
        val b=a.copy(latitude=30.0001,time=3000)
        val m=OutdoorMapPosition.next(a,b,false)
        assertTrue(m.latitude>a.latitude && m.latitude<b.latitude)
        assertEquals(b.time,m.time)
        assertEquals(b,OutdoorMapPosition.next(null,b,false))
    }
    @Test fun stationaryMarkerStaysFixedAndNewSegmentsResetSmoothing() {
        val a=RoutePoint(30.0,120.0,1000,5f)
        val b=a.copy(latitude=30.0001,time=3000)
        assertEquals(a.latitude,OutdoorMapPosition.next(a,b,true).latitude,.0)
        val next=b.copy(segment=1)
        assertEquals(next,OutdoorMapPosition.next(a,next,true))
    }
    private val epoch=1_700_000_000_000L
    private fun recorder()=OutdoorRecorder().apply { start(OutdoorKind.WALK,epoch,1000) }
    private fun OutdoorRecorder.sample(t: Long, meters: Double, speed: Double? = 1.2, accuracy: Float = 5f,
        uncertainty: Double? = .2) = fix(30.0+meters/111195.0,120.0,accuracy,t,t,epoch+t-1000,speed,uncertainty)

    @Test fun stationaryGroundSpeedCannotTurnGpsWanderIntoWalkingDistance() {
        val r=recorder(); r.sample(1000,0.0,0.0)
        (1..50).forEach { n -> r.sample(1000+n*2000L, if(n%2==0)4.0 else -4.0,0.0) }
        assertEquals(0.0,r.run!!.distanceMeters,.001)
        assertEquals(1,r.run!!.points.size)
        assertEquals(0.0,r.speedMps!!,.0); assertTrue(r.stationary)
    }
    @Test fun moderateAccuracyIsRejectedBeforeItCanCreateAFalseRoute() {
        val r=recorder(); r.sample(1000,0.0)
        r.sample(3000,20.0,1.0,accuracy=30f)
        assertEquals(OutdoorSignal.INACCURATE,r.signal)
        assertEquals(0.0,r.run!!.distanceMeters,.001)
        assertEquals(1,r.filteredFixes); assertNull(r.speedMps)
    }
    @Test fun jumpThatPassesTheAbsoluteSpeedLimitButContradictsGroundSpeedIsRejected() {
        val r=recorder(); r.sample(1000,0.0)
        r.sample(5000,18.0,1.0) // 4.5m/s passes the former walking limit, but not a credible 1m/s fix.
        assertEquals(1,r.run!!.points.size)
        assertEquals(0.0,r.run!!.distanceMeters,.001)
        assertEquals(OutdoorSignal.INACCURATE,r.signal)
        r.sample(7000,7.0,1.2)
        assertEquals(2,r.run!!.points.size)
        assertEquals(7.0,r.run!!.distanceMeters,.02)
    }
    @Test fun genuineSteadyWalkingStillAccumulatesWithinASensibleTolerance() {
        val r=recorder()
        (0..30).forEach { n -> r.sample(1000+n*2000L,n*2.4,1.2) }
        assertEquals(72.0,r.run!!.distanceMeters,7.3) // small movements wait until they clear uncertainty.
        assertEquals(1.2,r.speedMps!!,.01)
    }
    @Test fun signalGapStartsNewSegmentWithoutStraightLineDistance() {
        val r=recorder(); r.sample(1000,0.0)
        r.sample(18000,100.0)
        assertEquals(0.0,r.run!!.distanceMeters,.0)
        assertNotEquals(r.run!!.points.first().segment,r.run!!.points.last().segment)
    }
    @Test fun oldFixCannotLookFreshJustBecauseDeliveryWasDelayed() {
        val r=recorder()
        r.fix(30.0,120.0,5f,1000,10000,epoch+9000,1.0,.2)
        assertTrue(r.run!!.points.isEmpty()); assertNull(r.speedMps)
    }
    @Test fun speedFilterRejectsOneOffSpikeAndResetsAcrossLongGaps() {
        val f=OutdoorSpeedFilter()
        f.add(2.0,1000); f.add(2.0,3000)
        assertEquals(2.0,f.add(9.0,5000)!!,.001)
        assertEquals(2.0,f.add(2.0,7000)!!,.001)
        assertEquals(4.0,f.add(4.0,30000)!!,.001)
        assertEquals(0.0,f.add(0.0,32000,stationary=true)!!,.001)
    }
    @Test fun invalidOrUncertainGroundSpeedDoesNotBecomeStationary() {
        assertNull(OutdoorTrackingQuality.reliableGroundSpeed(OutdoorKind.WALK,Double.NaN,.1))
        assertNull(OutdoorTrackingQuality.reliableGroundSpeed(OutdoorKind.WALK,2.0,2.0))
        assertFalse(OutdoorTrackingQuality.stationary(0.0,2.0))
        assertFalse(OutdoorTrackingQuality.stationary(null,.1))
    }
    @Test fun outOfOrderStepBatchCannotResetBaselineAndOvercountLaterSteps() {
        val r=recorder()
        r.stepCounter(100,epoch,1000)
        r.stepCounter(110,epoch+4000,5000)
        r.stepCounter(105,epoch+5000,6000,sampleTick=3000)
        r.stepCounter(115,epoch+6000,7000)
        assertEquals(15L,r.run!!.steps)
    }
    @Test fun longStationaryPeriodCannotBeBridgedWhenMovementResumes() {
        val r=recorder(); r.sample(1000,0.0,0.0)
        (1..10).forEach { r.sample(1000+it*2000L,4.0,0.0) }
        r.sample(23000,6.0,1.2)
        assertEquals(0.0,r.run!!.distanceMeters,.0)
        assertEquals(2,r.run!!.points.size)
        assertNotEquals(r.run!!.points.first().segment,r.run!!.points.last().segment)
    }
}
