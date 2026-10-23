package app.duolian.local

import app.duolian.local.domain.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import javax.xml.parsers.DocumentBuilderFactory

class OutdoorTest {
    private fun point(lat: Double = 30.0, lon: Double = 120.0, time: Long = 1000, accuracy: Float = 5f) =
        RoutePoint(lat, lon, time, accuracy)
    private fun run(kind: OutdoorKind = OutdoorKind.RUN) = OutdoorRun(id = "run1", kind = kind, startedAt = 1000)

    @Test fun firstReadingDoesNotImportSinceBootSteps() {
        assertEquals(0L, OutdoorMath.stepDelta(null, StepBaseline("2026-10-23", 50000, 1)))
    }
    @Test fun counterDeltaCountsExactlyOnce() {
        val a = StepBaseline("2026-10-23", 1200, 1)
        val b = a.copy(counter = 1217)
        assertEquals(17L, OutdoorMath.stepDelta(a, b))
        assertEquals(0L, OutdoorMath.stepDelta(b, b))
    }
    @Test fun midnightRebootAndResetCannotMakeHugeOrNegativeSteps() {
        val a = StepBaseline("2026-10-23", 1200, 1)
        assertEquals(0L, OutdoorMath.stepDelta(a, a.copy(date = "2026-10-24", counter = 1300)))
        assertEquals(0L, OutdoorMath.stepDelta(a, a.copy(boot = 2, counter = 5000)))
        assertEquals(0L, OutdoorMath.stepDelta(a, a.copy(counter = 3)))
    }
    @Test fun knownGeodesicDistanceIsInMeters() {
        assertEquals(111.195, OutdoorMath.distance(point(), point(lat = 30.001)), .01)
    }
    @Test fun realisticRouteAddsDistanceAndHoldsAccuratePoints() {
        var r = OutdoorMath.append(run(), point())
        r = OutdoorMath.append(r, point(lat = 30.0001, time = 6000))
        assertEquals(2, r.points.size)
        assertEquals(11.1195, r.distanceMeters, .01)
    }
    @Test fun stationarityInaccuracyAndImpossibleSpeedAreFiltered() {
        val r = OutdoorMath.append(run(), point())
        assertEquals(r, OutdoorMath.append(r, point(lat = 30.00001, time = 6000)))
        assertEquals(r, OutdoorMath.append(r, point(lat = 30.0001, time = 6000, accuracy = 100f)))
        assertEquals(r, OutdoorMath.append(r, point(lat = 30.01, time = 6000)))
        assertEquals(r, OutdoorMath.append(r, point(lat = Double.NaN, time = 6000)))
    }
    @Test fun lostSignalDoesNotBridgeUnobservedDistance() {
        val r = OutdoorMath.append(OutdoorMath.append(run(), point()), point(lat = 31.0, time = 60000))
        assertEquals(2, r.points.size)
        assertEquals(1, r.points.last().segment)
        assertEquals(0.0, r.distanceMeters, 0.0)
    }
    @Test fun pausedAndResumedRoutesAreSeparateSegments() {
        val r = OutdoorMath.append(run(), point())
        val paused = r.copy(status = OutdoorStatus.PAUSED)
        assertEquals(paused, OutdoorMath.append(paused, point(lat = 31.0, time = 6000)))
        val resumed = OutdoorMath.append(paused.copy(status = OutdoorStatus.ACTIVE, segment = 1), point(lat = 31.0, time = 6000))
        assertEquals(0.0, resumed.distanceMeters, 0.0)
        assertEquals(1, resumed.points.last().segment)
    }
    @Test fun staleDuplicateFixDoesNotChangeTrack() {
        val r = OutdoorMath.append(run(), point(time = 6000))
        assertEquals(r, OutdoorMath.append(r, point(time = 6000)))
        assertEquals(r, OutdoorMath.append(r, point(time = 1000)))
    }
    @Test fun paceUsesActiveDurationAndHasMinimumDistance() {
        assertEquals("—", OutdoorMath.pace(run().copy(distanceMeters = 50.0, elapsedMs = 10000)))
        assertEquals("5′00″ / km", OutdoorMath.pace(run().copy(distanceMeters = 2000.0, elapsedMs = 600000)))
    }
    @Test fun gpxExportsRealCoordinatesUtcAndDistinctSegments() {
        val r = run().copy(points = listOf(point(), point(lat = 31.0, time = 60000).copy(segment = 1)))
        val xml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(OutdoorMath.gpx(r).byteInputStream())
        assertEquals(2, xml.getElementsByTagName("trkseg").length)
        val first = xml.getElementsByTagName("trkpt").item(0) as org.w3c.dom.Element
        assertEquals("30.0", first.getAttribute("lat"))
        assertEquals("120.0", first.getAttribute("lon"))
        assertEquals("1970-01-01T00:00:01Z", xml.getElementsByTagName("time").item(0).textContent)
    }
    @Test fun versionFourRoundTripKeepsLocationsAndDays() {
        val r = run().copy(updatedAt = 6000, status = OutdoorStatus.FINISHED, endedAt = 6000,
            points = listOf(point()), elapsedMs = 5000)
        val b = Backup(exercises = emptyList(), plans = emptyList(), sessions = emptyList(), body = emptyList(),
            food = emptyList(), water = emptyList(), outdoorRuns = listOf(r), dailySteps = listOf(DailySteps("2026-10-23", 800)))
        val decoded = Codec.decodeFromString<Backup>(Codec.encodeToString(b))
        Rules.backup(decoded)
        assertEquals(b, decoded)
        assertEquals(4, decoded.version)
    }
    @Test fun oldBackupsDoNotInventOutdoorHistory() {
        val b = Codec.decodeFromString<Backup>("""{"version":3,"exercises":[],"plans":[],"sessions":[],"body":[],"food":[],"water":[]}""")
        Rules.backup(b)
        assertTrue(b.outdoorRuns.isEmpty()); assertTrue(b.dailySteps.isEmpty())
    }
    @Test(expected = IllegalArgumentException::class) fun invalidLocationBackupIsRejected() {
        OutdoorMath.validate(run().copy(points = listOf(point(lat = 120.0))))
    }
}
