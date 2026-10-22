package app.duolian.local

import app.duolian.local.domain.*
import org.junit.Assert.*
import org.junit.Test

class ToolsTest {
    @Test fun plateLoadingUsesPairsOfPlates() {
        val loading = PlateMath.perSide(100.0)
        assertEquals(listOf(25.0, 15.0), loading.map { it.plateKg })
        assertTrue(PlateMath.isExact(100.0))
    }

    @Test fun heavierWeightRepeatsTheBiggestPlate() {
        val loading = PlateMath.perSide(140.0)
        assertEquals(listOf(25.0, 10.0), loading.map { it.plateKg })
        assertEquals(listOf(2, 1), loading.map { it.perSide })
    }

    @Test fun emptyBarHasNoPlates() {
        assertTrue(PlateMath.perSide(20.0).isEmpty())
        assertEquals(20.0, PlateMath.achievable(20.0), 0.001)
    }

    @Test fun weightBelowBarIsIgnored() {
        assertTrue(PlateMath.perSide(10.0).isEmpty())
    }

    @Test fun smallestPlateIsHalfOfOnePointTwoFivePair() {
        val loading = PlateMath.perSide(22.5)
        assertEquals(1.25, loading.single().plateKg, 0.001)
        assertEquals(1, loading.single().perSide)
    }

    @Test fun unreachableWeightIsDetected() {
        // 21 kg would need 0.5 kg per side, which the gym does not have.
        assertFalse(PlateMath.isExact(21.0))
        assertEquals(20.0, PlateMath.achievable(21.0), 0.001)
    }

    @Test fun oneRmTableDescendsFromNinetyFivePercent() {
        val rows = OneRmTable.rows(100_000)
        assertEquals(95, rows.first().first)
        assertEquals(95_000L, rows.first().second)
        assertEquals(65, rows.last().first)
        assertEquals(65_000L, rows.last().second)
        assertEquals(rows.size, OneRmTable.PERCENTS.size)
    }

    @Test fun plateMathRejectsNonFiniteInput() {
        assertTrue(PlateMath.perSide(Double.NaN).isEmpty())
    }
}
