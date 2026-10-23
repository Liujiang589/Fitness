package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class OutdoorSpeedTest {
    private fun point(lat: Double = 30.0, time: Long = 1000, segment: Int = 0) = RoutePoint(lat, 120.0, time, 5f, segment)
    @Test fun groundSpeedDoesNotNeedTwoRoutePoints() {
        assertEquals(1.4, OutdoorSpeed.metersPerSecond(OutdoorKind.WALK, null, point(), 1.4, .2)!!, 0.0)
        assertEquals(0.0, OutdoorSpeed.metersPerSecond(OutdoorKind.RUN, null, point(), 0.0, .2)!!, 0.0)
    }
    @Test fun uncertainImpossibleOrNonfiniteGroundSpeedIsNotDisplayed() {
        assertNull(OutdoorSpeed.metersPerSecond(OutdoorKind.WALK, null, point(), 20.0))
        assertNull(OutdoorSpeed.metersPerSecond(OutdoorKind.WALK, null, point(), Double.NaN))
        assertNull(OutdoorSpeed.metersPerSecond(OutdoorKind.RUN, null, point(), 3.0, 10.0))
    }
    @Test fun validShortDisplacementIsFallbackAndJitterIsNotMotion() {
        assertEquals(2.2239, OutdoorSpeed.metersPerSecond(OutdoorKind.RUN, point(), point(30.0001, 6000), null)!!, .001)
        assertEquals(0.0, OutdoorSpeed.metersPerSecond(OutdoorKind.RUN, point(), point(30.00001, 6000), null)!!, .0)
    }
    @Test fun pauseSignalGapAndDuplicateTimestampCannotProduceSpeed() {
        assertNull(OutdoorSpeed.metersPerSecond(OutdoorKind.RUN, point(), point(30.0001, 6000, 1), null))
        assertNull(OutdoorSpeed.metersPerSecond(OutdoorKind.RUN, point(), point(30.0001, 60000), null))
        assertNull(OutdoorSpeed.metersPerSecond(OutdoorKind.RUN, point(), point(30.0001), null))
    }
    @Test fun invalidFixOrTeleportCannotProduceSpeed() {
        assertNull(OutdoorSpeed.metersPerSecond(OutdoorKind.RUN, point(), point(130.0, 6000), 1.0))
        assertNull(OutdoorSpeed.metersPerSecond(OutdoorKind.RUN, point(), point(31.0, 6000), null))
    }
    @Test fun realtimePaceDoesNotShowInfinityWhenStopped() {
        assertEquals("—", OutdoorSpeed.currentPace(null)); assertEquals("—", OutdoorSpeed.currentPace(0.0))
        assertEquals("—", OutdoorSpeed.currentPace(Double.POSITIVE_INFINITY))
        assertEquals("5′00″", OutdoorSpeed.currentPace(1000.0 / 300))
    }
    @Test fun averageSpeedUsesActiveTimeAndStaysUnknownForEmptyRecord() {
        val r = OutdoorRun(kind = OutdoorKind.RUN, startedAt = 1000)
        assertNull(OutdoorSpeed.averageKmh(r))
        assertEquals(12.0, OutdoorSpeed.averageKmh(r.copy(distanceMeters = 1000.0, elapsedMs = 300000))!!, .0)
    }
}
