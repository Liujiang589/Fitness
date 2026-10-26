package app.duolian.local

import org.junit.Assert.*
import org.junit.Test

/** Check bytecode resources without initializing Android-dependent SDK classes. */
class OutdoorSdkPackagingTest {
    private val loader = javaClass.classLoader!!
    @Test fun optionalGnssImplementationCannotBeReferencedWithoutBeingPackaged() {
        val fusion = loader.getResourceAsStream("com/amap/location/fusion/original/a/a.class")
            ?.use { it.readBytes().toString(Charsets.ISO_8859_1) }.orEmpty()
        if (fusion.contains("com/amap/ams/gnss/GnssSoftLocator"))
            assertNotNull("SDK references GnssSoftLocator but does not package it",
                loader.getResource("com/amap/ams/gnss/GnssSoftLocator.class"))
    }
    @Test fun locationClientAndMathDependencyAreAvailable() {
        assertNotNull(loader.getResource("com/amap/api/location/AMapLocationClient.class"))
        assertNotNull(loader.getResource("net/jafama/FastMath.class"))
    }
}
