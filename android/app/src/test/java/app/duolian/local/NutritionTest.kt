package app.duolian.local

import app.duolian.local.domain.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class NutritionTest {
    private val rice = FoodCatalog.all.first { it.id == "usda_169757" }
    private fun portion(grams: Double) = NutritionEstimator.portion(rice, grams, "2026-10-22", "午餐")

    @Test fun cookedRice150GramsScalesEveryMacro() {
        val food = portion(150.0)
        assertEquals(4.035, food.protein, .00001)
        assertEquals(42.255, food.carbs, .00001)
        assertEquals(.42, food.fat, .00001)
        assertEquals(188.94, food.calories, .00001)
        assertEquals(150.0, food.grams!!, .00001)
        assertEquals(rice.id, food.catalogId)
    }
    @Test fun fractionalWeightKeepsPrecisionUntilDisplay() {
        assertEquals(rice.carbsPer100 * .375, portion(37.5).carbs, .000001)
    }
    @Test fun rawAndCookedRiceHaveDifferentBases() {
        val raw = FoodCatalog.all.first { it.id == "usda_169756" }
        assertTrue(NutritionEstimator.portion(raw, 100.0).carbs > portion(100.0).carbs * 2)
        assertTrue(rice.preparation.contains("熟重"))
        assertTrue(raw.preparation.contains("生重"))
    }
    @Test fun aliasesAndEnglishSearchAreSupported() {
        assertTrue(NutritionEstimator.search("西红柿").any { it.name == "番茄" })
        assertTrue(NutritionEstimator.search("CHICKEN breast").any { it.id == "usda_171077" })
        assertTrue(NutritionEstimator.search("不存在的混合菜123").isEmpty())
    }
    @Test fun allCatalogEntriesHaveTraceableIdsAndSaneNutrients() {
        assertEquals(47, FoodCatalog.all.size)
        assertEquals(47, FoodCatalog.all.map { it.id }.distinct().size)
        FoodCatalog.all.forEach {
            assertTrue(it.sourceUrl.startsWith("https://fdc.nal.usda.gov/food-details/"))
            assertTrue(it.source.contains(it.id.removePrefix("usda_")))
            Rules.food(NutritionEstimator.portion(it, 100.0))
        }
    }
    @Test fun oldFoodPayloadDecodesWithoutGuessingWeight() {
        val old = Codec.decodeFromString<Food>("""{"id":"old","date":"2026-10-22","meal":"早餐","name":"旧早餐","protein":20.0,"carbs":30.0,"fat":10.0}""")
        Rules.food(old)
        assertNull(old.grams)
        assertNull(NutritionEstimator.fromRecord(old))
        assertEquals(290.0, old.calories, .00001)
    }
    @Test fun snapshotRescalingUsesStoredValuesRatherThanCurrentCatalog() {
        val altered = portion(100.0).copy(protein = 12.0, carbs = 50.0, fat = 3.0)
        val spec = NutritionEstimator.fromRecord(altered)!!
        val resized = NutritionEstimator.portion(spec, 200.0)
        assertEquals(24.0, resized.protein, .00001)
        assertEquals(100.0, resized.carbs, .00001)
        assertEquals(6.0, resized.fat, .00001)
    }
    @Test fun customLabelIsReusableWithoutEnteringMacrosAgain() {
        val spec = FoodSpec("custom_yogurt", "我的酸奶", "包装标签 / 每100g", "自定义", 4.0, 6.0, 2.0, "用户录入包装标签")
        val first = NutritionEstimator.portion(spec, 150.0)
        val again = NutritionEstimator.portion(NutritionEstimator.fromRecord(first)!!, 250.0)
        assertEquals(10.0, again.protein, .00001)
        assertEquals(15.0, again.carbs, .00001)
        assertEquals(5.0, again.fat, .00001)
    }
    @Test fun newBackupRoundTripKeepsWeightSourceAndMacroSnapshot() {
        val backup = Backup(exercises = emptyList(), plans = emptyList(), sessions = emptyList(), body = emptyList(),
            food = listOf(portion(150.0)), water = emptyList())
        val restored = Codec.decodeFromString<Backup>(Codec.encodeToString(backup))
        Rules.backup(restored)
        assertEquals(backup, restored)
        assertEquals(3, restored.version)
    }
    @Test fun recentFoodsPreferLatestRecordAndDeduplicate() {
        val latest = portion(150.0).copy(protein = 30.0)
        val recent = NutritionEstimator.recent(listOf(latest, portion(100.0)))
        assertEquals(1, recent.size)
        assertEquals(20.0, recent.single().proteinPer100, .00001)
    }
    @Test fun invalidWeightsAreRejected() {
        listOf(0.0, -1.0, 3000.1, Double.NaN, Double.POSITIVE_INFINITY).forEach {
            assertTrue(runCatching { portion(it) }.isFailure)
        }
    }
    @Test fun invalidAndImpossibleCustomLabelsAreRejected() {
        val spec = FoodSpec("custom_bad", "标签", "包装", "自定义", 60.0, 60.0, 10.0, "自定义")
        assertTrue(runCatching { NutritionEstimator.portion(spec, 100.0) }.isFailure)
        assertTrue(runCatching { NutritionEstimator.portion(spec.copy(proteinPer100 = Double.NaN), 100.0) }.isFailure)
    }
    @Test fun invalidStoredWeightAndDateAreRejected() {
        assertTrue(runCatching { Rules.food(portion(100.0).copy(grams = Double.NaN)) }.isFailure)
        assertTrue(runCatching { NutritionEstimator.portion(rice, 100.0, "2026-02-30") }.isFailure)
    }
}
