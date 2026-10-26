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
