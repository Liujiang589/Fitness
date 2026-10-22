package app.duolian.local

import android.media.ToneGenerator
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            fields.forEachIndexed { i, f -> OutlinedTextField(values[i], { v -> values = values.toMutableList().also { it[i] = v } },
                label = { Text(f.first) }, modifier = Modifier.fillMaxWidth()) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching { save(values) }.onSuccess { dismiss() }
        .onFailure { error = it.message ?: "输入无效" } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
@Composable internal fun ExerciseForm(e: Exercise?, dismiss: () -> Unit, save: (Exercise) -> Unit) {
    FieldsDialog(if (e == null) "新增动作" else "编辑动作", listOf("名称" to (e?.name ?: ""),
        "肌群" to (e?.muscle ?: "胸部"), "器械" to (e?.equipment ?: "徒手"),
        "默认重量 kg" to kg(e?.defaultGrams ?: 0), "动作说明" to (e?.notes ?: "")), dismiss) { v ->
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
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(weight, { weight = it }, label = { Text("重量 kg") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(reps, { reps = it }, label = { Text("次数") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("组类型", style = MaterialTheme.typography.labelMedium, color = Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SetKind.entries.forEach { k ->
                    FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.short) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rpe, { rpe = it }, label = { Text("RPE 5–10") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(rest, { rest = it }, label = { Text("本组休息 秒") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("RPE 10 表示当次力竭，留空即可不记录；本组休息留空则用计划或全局设置。",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
    var error by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (p == null) "创建计划" else "编辑计划") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("计划名称") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(sets, { sets = it }, label = { Text("默认组数") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(reps, { reps = it }, label = { Text("默认次数") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(rest, { rest = it }, label = { Text("休息 秒") }, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Text("训练方案", style = MaterialTheme.typography.labelMedium, color = Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SetScheme.entries.forEach { s -> FilterChip(selected = scheme == s, onClick = { scheme = s }, label = { Text(s.label) }) }
            }
            Text("动作编排", style = MaterialTheme.typography.labelMedium, color = Muted)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                SupersetMode.entries.forEach { s -> FilterChip(selected = superset == s, onClick = { superset = s }, label = { Text(s.label) }) }
            }
            Text("金字塔按组自动增减 5% 重量；递减组按 80% 追加组；超级组组间只休息 ${Rules.SupersetRestSeconds} 秒。",
                style = MaterialTheme.typography.bodySmall, color = Muted)
            Text("已选 ${ids.size} 个动作 · 按训练顺序")
            ids.forEachIndexed { index, id -> Row {
                Text("${index + 1}. ${exercises.find { it.id == id }?.name ?: "已删除动作"}", Modifier.weight(1f))
                TextButton(enabled = index > 0, onClick = { ids = ids.toMutableList().also { val old = it[index - 1]; it[index - 1] = id; it[index] = old } }) { Text("上移") }
                TextButton(onClick = { ids = ids - id }) { Text("移除") }
            } }
            Text("选择动作")
            OutlinedTextField(query, { query = it }, label = { Text("搜索动作 / 肌群 / 器械") }, singleLine = true)
            exercises.filter { query.isBlank() || it.name.contains(query, true) || it.muscle.contains(query, true) || it.equipment.contains(query, true) }.forEach { e -> Row {
                Checkbox(e.id in ids, { checked -> ids = if (checked) ids + e.id else ids - e.id }); Text(e.name)
            } }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = { runCatching {
        val plan = Plan(p?.id ?: newId(), name.trim(), ids, sets.toInt(), reps.toInt(),
            scheme, rest.toInt(), superset)
        Rules.plan(plan, exercises); save(plan)
    }.onSuccess { dismiss() }.onFailure { error = it.message ?: "输入无效" } }) { Text("保存") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("取消") } })
}
