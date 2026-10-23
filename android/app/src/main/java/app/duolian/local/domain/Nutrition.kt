package app.duolian.local.domain

import java.time.LocalDate
import java.util.Locale

/** Nutrients are grams per 100 g edible food, in the explicitly named preparation state. */
data class FoodSpec(
    val id: String, val name: String, val preparation: String, val category: String,
    val proteinPer100: Double, val carbsPer100: Double, val fatPer100: Double,
    val source: String, val sourceUrl: String = "", val aliases: String = ""
)
enum class FoodWeightState(val label: String) { RAW("生重"), COOKED("熟重"), DRY("干重"), OTHER("即食/其他") }
data class RecentPortion(val spec: FoodSpec, val grams: Double)

object NutritionEstimator {
    fun weightState(spec: FoodSpec): FoodWeightState = when {
        spec.preparation.startsWith("生重") -> FoodWeightState.RAW
        spec.preparation.startsWith("熟重") -> FoodWeightState.COOKED
        spec.preparation.startsWith("干重") -> FoodWeightState.DRY
        else -> FoodWeightState.OTHER
    }
    /** Normalize a label stated per serving to per 100g; never treat millilitres as grams. */
    fun label(id: String, name: String, protein: Double, carbs: Double, fat: Double, labelGrams: Double): FoodSpec {
        require(labelGrams.isFinite() && labelGrams in .1..3000.0) { "标签份量需为0.1–3000g" }
        require(listOf(protein, carbs, fat).all { it.isFinite() && it >= 0 } && protein + carbs + fat <= labelGrams * 1.01) {
            "请核对标签份量与营养单位"
        }
        return FoodSpec(id, name.trim(), "包装标签 / 每100g", "自定义", protein * 100 / labelGrams,
            carbs * 100 / labelGrams, fat * 100 / labelGrams, "用户录入标签 · 原份量${labelGrams}g")
    }
    fun recentPortions(records: List<Food>): List<RecentPortion> = records.sortedByDescending { it.date }
        .mapNotNull { food -> fromRecord(food)?.let { RecentPortion(it, food.grams!!) } }
        .distinctBy { it.spec.id }.take(12)

    fun search(query: String, foods: List<FoodSpec> = FoodCatalog.all): List<FoodSpec> {
        val terms = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotBlank() }
        return foods.filter { food ->
            val text = "${food.name} ${food.preparation} ${food.category} ${food.aliases}".lowercase(Locale.ROOT)
            terms.all { it in text }
        }
    }

    fun portion(spec: FoodSpec, grams: Double, date: String = LocalDate.now().toString(),
        meal: String = "早餐", id: String = newId()): Food {
        require(grams.isFinite() && grams in 0.1..3000.0) { "食物重量需为0.1–3000g" }
        require(listOf(spec.proteinPer100, spec.carbsPer100, spec.fatPer100).all {
            it.isFinite() && it in 0.0..100.0
        }) { "每100g营养素需为0–100g" }
        require(spec.proteinPer100 + spec.carbsPer100 + spec.fatPer100 <= 101.0) {
            "请检查标签单位：每100g营养素合计不能超过100g"
        }
        val scale = grams / 100.0
        return Food(id, date, meal, spec.name, spec.proteinPer100 * scale,
            spec.carbsPer100 * scale, spec.fatPer100 * scale, grams, spec.id, spec.source,
            spec.preparation).also(Rules::food)
    }

    /** Reuse the stored nutrient snapshot, so editing grams never silently rebases old records. */
    fun fromRecord(food: Food): FoodSpec? {
        val grams = food.grams?.takeIf { it.isFinite() && it > 0 } ?: return null
        val id = food.catalogId ?: return null
        return FoodSpec(id, food.name, food.preparation ?: "按原记录", "最近使用",
            food.protein * 100 / grams, food.carbs * 100 / grams, food.fat * 100 / grams,
            food.nutritionSource ?: "原记录营养快照",
            sourceUrl = if (id.startsWith("usda_")) "https://fdc.nal.usda.gov/food-details/${id.removePrefix("usda_")}/nutrients" else "")
    }

    fun recent(records: List<Food>): List<FoodSpec> = records
        .mapNotNull(::fromRecord).distinctBy { it.id }.take(12)
}
