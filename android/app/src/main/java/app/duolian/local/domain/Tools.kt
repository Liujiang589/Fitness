package app.duolian.local.domain

/** Barbell plate maths used by the plate calculator tool. */
object PlateMath {
    /** Plates available in a typical commercial gym, heaviest first. */
    val PLATES_KG = listOf(25.0, 20.0, 15.0, 10.0, 5.0, 2.5, 1.25)
    const val BAR_KG = 20.0

    data class Loading(val plateKg: Double, val perSide: Int)

    /**
     * Greedy per-side loading for [totalKg] including the bar.
     * Unreachable remainders (< smallest plate pair) are simply left out.
     */
    fun perSide(totalKg: Double, barKg: Double = BAR_KG): List<Loading> {
        val perSide = (totalKg - barKg) / 2.0
        if (perSide <= 0.0 || !perSide.isFinite()) return emptyList()
        var remaining = perSide
        val result = mutableListOf<Loading>()
        for (plate in PLATES_KG) {
            val count = (remaining / plate + 1e-9).toInt()
            if (count > 0) {
                result += Loading(plate, count)
                remaining -= plate * count
            }
        }
        return result
    }

    /** Weight actually achievable with available plates, including the bar. */
    fun achievable(totalKg: Double, barKg: Double = BAR_KG): Double {
        val loaded = perSide(totalKg, barKg).sumOf { it.plateKg * it.perSide }
        return barKg + loaded * 2
    }

    /** True when the requested weight can be loaded exactly. */
    fun isExact(totalKg: Double, barKg: Double = BAR_KG): Boolean =
        kotlin.math.abs(achievable(totalKg, barKg) - totalKg) < 0.01
}

/** Percentage table derived from an estimated one-rep max. */
object OneRmTable {
    val PERCENTS = listOf(95, 90, 85, 80, 75, 70, 65)

    fun rows(oneRmGrams: Long): List<Pair<Int, Long>> =
        PERCENTS.map { percent -> percent to (oneRmGrams * percent / 100) }
}
