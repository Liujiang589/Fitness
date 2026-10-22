package app.duolian.local

import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.duolian.local.domain.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable internal fun FieldsDialog(title: String, fields: List<Pair<String, String>>, dismiss: () -> Unit, save: (List<String>) -> Unit) {
    var values by remember { mutableStateOf(fields.map { it.second }) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            fields.forEachIndexed { i, f ->
                AppField(values[i], { v -> values = values.toMutableList().also { it[i] = v } }, f.first)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching { save(values) }.onSuccess { dismiss() }
        .onFailure { error = it.message ?: "输入无效" } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
@Composable internal fun ExerciseForm(e: Exercise?, dismiss: () -> Unit, save: (Exercise) -> Unit) {
    FieldsDialog(if (e == null) "新增动作" else "编辑动作", listOf("名称" to (e?.name ?: ""),
        "肌群" to (e?.muscle ?: "胸部"), "器械" to (e?.equipment ?: "徒手"),
        "默认重量（kg）" to kg(e?.defaultGrams ?: 0), "动作说明" to (e?.notes ?: "")), dismiss) { v ->
        // Keep favourite state and any attached photo when only the text fields change.
        val x = e?.copy(name = v[0].trim(), muscle = v[1].trim(), equipment = v[2].trim(),
            defaultGrams = Rules.parseKg(v[3]), notes = v[4])
            ?: Exercise(newId(), v[0].trim(), v[1].trim(), v[2].trim(), Rules.parseKg(v[3]), v[4])
        Rules.exercise(x); save(x)
    }
}
@Composable internal fun SetForm(x: TrainingSet, dismiss: () -> Unit, save: (TrainingSet) -> Unit) {
    var weight by remember { mutableStateOf(kg(x.grams)) }
    var reps by remember { mutableStateOf(x.reps.toString()) }
    var kind by remember { mutableStateOf(x.effectiveKind) }
    var rpe by remember { mutableStateOf(x.rpe?.toString() ?: "") }
    var rest by remember { mutableStateOf(x.restSeconds?.toString() ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("编辑这一组") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppField(weight, { weight = it }, "重量", Modifier.weight(1f), suffix = "kg",
                    keyboardType = KeyboardType.Decimal)
                AppField(reps, { reps = it }, "次数", Modifier.weight(1f), suffix = "次",
                    keyboardType = KeyboardType.Number)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                FieldLabel("组类型")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SetKind.entries.forEach { k ->
                        ChoicePill(k.short, kind == k, { kind = k })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppField(rpe, { rpe = it }, "RPE（可空）", Modifier.weight(1f), placeholder = "5–10",
                    keyboardType = KeyboardType.Number)
                AppField(rest, { rest = it }, "本组休息（可空）", Modifier.weight(1f), suffix = "秒",
                    keyboardType = KeyboardType.Number)
            }
            Text("RPE 10 表示当次力竭；休息留空则沿用计划或全局设置。", color = Muted,
                style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching {
        val set = x.copy(grams = Rules.parseKg(weight), reps = reps.toInt(),
            rpe = rpe.trim().takeIf { it.isNotEmpty() }?.toInt(),
            restSeconds = rest.trim().takeIf { it.isNotEmpty() }?.toInt()).withKind(kind)
        Rules.trainingSet(set); save(set)
    }.onSuccess { dismiss() }.onFailure { error = it.message ?: "输入无效" } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
@Composable internal fun PlanForm(p: Plan?, exercises: List<Exercise>, dismiss: () -> Unit, save: (Plan) -> Unit) {
    var name by remember { mutableStateOf(p?.name ?: "") }
    var ids by remember { mutableStateOf(p?.exerciseIds ?: emptyList()) }
    var sets by remember { mutableStateOf((p?.sets ?: 3).toString()) }
    var reps by remember { mutableStateOf((p?.reps ?: 10).toString()) }
    var scheme by remember { mutableStateOf(p?.scheme ?: SetScheme.STRAIGHT) }
    var superset by remember { mutableStateOf(p?.superset ?: SupersetMode.NONE) }
    var rest by remember { mutableStateOf((p?.restSeconds ?: 0).toString()) }
    var weekdays by remember { mutableStateOf(p?.weekdays ?: emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (p == null) "创建计划" else "编辑计划") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            AppField(name, { name = it }, "计划名称", placeholder = "例如 上肢推 / 全身基础")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppField(sets, { sets = it }, "组数", Modifier.weight(1f), suffix = "组",
                    keyboardType = KeyboardType.Number)
                AppField(reps, { reps = it }, "次数", Modifier.weight(1f), suffix = "次",
                    keyboardType = KeyboardType.Number)
                AppField(rest, { rest = it }, "休息", Modifier.weight(1f), suffix = "秒",
                    keyboardType = KeyboardType.Number)
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                FieldLabel("训练方案")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SetScheme.entries.forEach { s -> ChoicePill(s.label, scheme == s, { scheme = s }) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                FieldLabel("动作编排")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SupersetMode.entries.forEach { s -> ChoicePill(s.label, superset == s, { superset = s }) }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                FieldLabel("排期（可选，用于首页「今日安排」）")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, label ->
                        val day = i + 1
                        ChoicePill(label, day in weekdays, {
                            weekdays = if (day in weekdays) weekdays - day else weekdays + day
                        })
                    }
                }
            }
            if (ids.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FieldLabel("已选动作 · 按训练顺序")
                    ids.forEachIndexed { index, id ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Page)
                            .padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d".format(index + 1), color = Blue, fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(28.dp))
                            Text(exercises.find { it.id == id }?.name ?: "已删除动作",
                                Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            TextButton(enabled = index > 0, contentPadding = PaddingValues(horizontal = 8.dp),
                                onClick = { ids = ids.toMutableList().also { val old = it[index - 1]; it[index - 1] = id; it[index] = old } }) {
                                Text("上移", style = MaterialTheme.typography.labelSmall)
                            }
                            TextButton(contentPadding = PaddingValues(horizontal = 8.dp),
                                onClick = { ids = ids - id }) {
                                Text("移除", color = Muted, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            AppField(query, { query = it }, "添加动作", placeholder = "搜索名称 / 肌群 / 器械")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercises.filter {
                    query.isBlank() || it.name.contains(query, true) || it.muscle.contains(query, true) ||
                        it.equipment.contains(query, true)
                }.take(12).forEach { e ->
                    SelectRow(e.name, "${e.muscle} · ${e.equipment}", e.id in ids) {
                        ids = if (e.id in ids) ids - e.id else ids + e.id
                    }
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching {
        val plan = Plan(p?.id ?: newId(), name.trim(), ids, sets.toInt(), reps.toInt(),
            scheme, rest.toInt(), superset, weekdays)
        Rules.plan(plan, exercises); save(plan)
    }.onSuccess { dismiss() }.onFailure { error = it.message ?: "输入无效" } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
