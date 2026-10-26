package app.duolian.local

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.duolian.local.domain.*

private val FoodSpecSaver = listSaver<FoodSpec?, Any>(
    save = { spec -> spec?.let { listOf(it.id, it.name, it.preparation, it.category,
        it.proteinPer100, it.carbsPer100, it.fatPer100, it.source, it.sourceUrl, it.aliases) } ?: emptyList() },
    restore = { saved -> if (saved.isEmpty()) null else FoodSpec(saved[0] as String, saved[1] as String,
        saved[2] as String, saved[3] as String, saved[4] as Double, saved[5] as Double,
        saved[6] as Double, saved[7] as String, saved[8] as String, saved[9] as String) })

@Composable internal fun FoodEntryDialog(existing: Food?, date: String, meal: String,
    recent: List<Food>, busy: Boolean, dismiss: () -> Unit, save: (Food) -> Unit) {
    val originalSpec = remember(existing) { existing?.let(NutritionEstimator::fromRecord) }
    val entryId = rememberSaveable(existing?.id) { existing?.id ?: newId() }
    var selected by rememberSaveable(stateSaver = FoodSpecSaver) { mutableStateOf(originalSpec) }
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("全部") }
    var weightFilter by rememberSaveable { mutableStateOf("全部状态") }
    var labelBasis by rememberSaveable { mutableStateOf("每100g") }
    var labelGrams by rememberSaveable { mutableStateOf("100") }
    var showAll by rememberSaveable { mutableStateOf(false) }
    var grams by rememberSaveable { mutableStateOf(existing?.grams?.toString()?.removeSuffix(".0") ?: "100") }
    var entryDate by rememberSaveable { mutableStateOf(existing?.date ?: date) }
    var entryMeal by rememberSaveable { mutableStateOf(existing?.meal ?: meal) }
    var mode by rememberSaveable { mutableStateOf(if (existing != null && originalSpec == null) "历史记录" else "食物库") }
    var customName by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var protein by rememberSaveable { mutableStateOf(existing?.protein?.toString() ?: "") }
    var carbs by rememberSaveable { mutableStateOf(existing?.carbs?.toString() ?: "") }
    var fat by rememberSaveable { mutableStateOf(existing?.fat?.toString() ?: "") }
    val customId = rememberSaveable { "custom_${newId()}" }
    var error by remember { mutableStateOf<String?>(null) }
    val uriHandler = LocalUriHandler.current
    val maxHeight = (LocalConfiguration.current.screenHeightDp * .88f).dp
    val recentFoods = remember(recent) { NutritionEstimator.recentPortions(recent) }
    val matches = remember(query, category, weightFilter) {
        NutritionEstimator.search(query).filter { (category == "全部" || it.category == category) &&
            (weightFilter == "全部状态" || NutritionEstimator.weightState(it).label == weightFilter) }
    }
    fun buildEntry(): Food {
        return when (mode) {
            "历史记录" -> requireNotNull(existing).copy(date = entryDate, meal = entryMeal,
                name = customName.trim(), protein = protein.toDouble(), carbs = carbs.toDouble(), fat = fat.toDouble())
                .also(Rules::food)
            else -> {
                val spec = if (mode == "自定义标签") NutritionEstimator.label(customId, customName,
                    protein.toDouble(), carbs.toDouble(), fat.toDouble(),
                    if (labelBasis == "每100g") 100.0 else labelGrams.toDoubleOrNull() ?: kotlin.error("请输入标签每份克数"))
                else selected ?: kotlin.error("请先选择食物，并核对生熟状态")
                NutritionEstimator.portion(spec, grams.toDoubleOrNull() ?: kotlin.error("请输入有效克数"),
                    entryDate, entryMeal, entryId)
            }
        }
    }
    val preview = runCatching { buildEntry() }.getOrNull()
    Dialog(onDismissRequest = { if (!busy) dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).heightIn(max = maxHeight),
            shape = RoundedCornerShape(28.dp), color = Color.White) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Eyebrow("FOOD / SMART LOG")
                    Text(if (existing == null) "食物与重量，交给独练算" else "编辑饮食记录",
                        style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    AppField(entryDate, { entryDate = it }, "日期", placeholder = "YYYY-MM-DD")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        listOf("早餐", "午餐", "晚餐", "加餐").forEach { name ->
                            ChoicePill(name, entryMeal == name, { entryMeal = name })
                        }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf("食物库", "自定义标签") + if (existing != null && originalSpec == null) listOf("历史记录") else emptyList())
                            .forEach { name -> ChoicePill(name, mode == name, {
                                mode = name; error = null
                                if (name == "自定义标签") {
                                    protein = ""; carbs = ""; fat = ""
                                }
                                if (name == "历史记录") {
                                    customName = existing?.name.orEmpty()
                                    protein = existing?.protein.toString(); carbs = existing?.carbs.toString(); fat = existing?.fat.toString()
                                }
                            }) }
                    }
                    if (mode == "食物库") {
                        val spec = selected
                        if (spec == null) {
                            AppField(query, { query = it; showAll = false }, "选择食物", placeholder = "例如 米饭、鸡胸肉、香蕉")
                            if (query.isBlank() && recentFoods.isNotEmpty()) {
                                FieldLabel("最近份量 · 可直接复用后调整")
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    recentFoods.forEach { portion -> val item = portion.spec
                                        ChoicePill("${item.name} · ${number(portion.grams)}g · ${item.preparation}", false, {
                                            selected = item; grams = portion.grams.toString().removeSuffix(".0"); error = null
                                        })
                                    }
                                }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (listOf("全部状态") + FoodWeightState.entries.map { it.label }).forEach { name ->
                                    ChoicePill(name, weightFilter == name, { weightFilter = name; showAll = false })
                                }
                            }
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                (listOf("全部") + FoodCatalog.all.map { it.category }.distinct()).forEach { name ->
                                    ChoicePill(name, category == name, { category = name; showAll = false })
                                }
                            }
                            if (matches.isEmpty()) Text("没有匹配条目。试试食材名称；包装食品可录入一次标签，以后从最近使用中直接选择。",
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                            (if (showAll) matches else matches.take(8)).forEach { item ->
                                SelectRow(item.name, "${item.preparation} · ${item.category}",
                                    selected = false, onClick = { selected = item; error = null })
                            }
                            if (!showAll && matches.size > 8) TextButton(onClick = { showAll = true }) {
                                Text("展开其余 ${matches.size - 8} 个条目")
                            }
                        } else {
                            Surface(color = PaleBlue, shape = RoundedCornerShape(18.dp)) {
                                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(spec.name, fontWeight = FontWeight.SemiBold)
                                    Text(spec.preparation, color = Blue, style = MaterialTheme.typography.bodySmall)
                                    Text("按此状态的可食部克数估算，生熟重量不自动互换。", color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Text(spec.source, color = Muted, style = MaterialTheme.typography.labelSmall)
                                    Row {
                                        TextButton(onClick = { selected = null; query = "" }) { Text("重新选择") }
                                        if (spec.sourceUrl.isNotBlank()) TextButton(onClick = {
                                            runCatching { uriHandler.openUri(spec.sourceUrl) }
                                                .onFailure { error = "未能打开来源页面" }
                                        }) { Text("查看来源 ↗") }
                                    }
                                }
                            }
                        }
                    } else {
                        AppField(customName, { customName = it }, "食物名称", placeholder = "例如 我的低脂酸奶")
                        if (mode == "自定义标签") {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("每100g", "每份").forEach { basis -> ChoicePill(basis, labelBasis == basis, { labelBasis = basis }) }
                            }
                            if (labelBasis == "每份") AppField(labelGrams, { labelGrams = it }, "包装上每份的重量", suffix = "g", keyboardType = KeyboardType.Decimal)
                        }
                        FieldLabel(if (mode == "历史记录") "原记录总营养 · 保留旧记录数值" else "按包装上的${labelBasis}数值填写一次")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppField(protein, { protein = it }, "蛋白质", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                            AppField(carbs, { carbs = it }, "碳水", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                            AppField(fat, { fat = it }, "脂肪", Modifier.weight(1f), suffix = "g", keyboardType = KeyboardType.Decimal)
                        }
                        if (mode == "自定义标签") Text("每份标签由独练按克数换算；每100ml不能直接当作每100g。", color = Muted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (mode != "历史记录") {
                        AppField(grams, { grams = it; error = null }, "这次吃了多少（可食部）", suffix = "g",
                            keyboardType = KeyboardType.Decimal)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            listOf(50, 100, 150, 200).forEach { value ->
                                ChoicePill("${value}g", grams.toDoubleOrNull() == value.toDouble(), { grams = value.toString() })
                            }
                        }
                    }
                    if (preview != null) {
                        Surface(color = PaleBlue, shape = RoundedCornerShape(18.dp)) {
                            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("已按这份重量计算", color = Blue, fontWeight = FontWeight.SemiBold)
                                Text("预计摄入 ≈ ${number(preview.calories)} kcal", color = Ink,
                                    style = MaterialTheme.typography.titleMedium)
                                NutritionBreakdown(preview.protein, preview.carbs, preview.fat)
                                DetailDisclosure("本份营养明细") {
                                    Text("蛋白质 ${number(preview.protein)}g · 碳水 ${number(preview.carbs)}g · 脂肪 ${number(preview.fat)}g",
                                        color = Ink, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                    Text("按食物成分与实际克数估算。请核对生熟状态，烹调油另记；热量按蛋白质×4＋碳水×4＋脂肪×9计算。",
                        color = Muted, style = MaterialTheme.typography.labelSmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = dismiss, enabled = !busy, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(enabled = !busy, modifier = Modifier.weight(1f), onClick = {
                        runCatching { buildEntry() }.onSuccess(save).onFailure { error = it.message ?: "请检查输入" }
                    }) { Text(if (busy) "正在保存" else "保存饮食") }
                }
            }
        }
    }
}
