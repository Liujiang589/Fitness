package app.duolian.local

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.duolian.local.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Only dismiss after persistence succeeds. Back/cancel protects a changed draft. */
@Composable internal fun RecordDialog(title: String, changed: Boolean, dismiss: () -> Unit,
    save: suspend () -> Unit, content: @Composable ColumnScope.(Boolean) -> Unit) {
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val close = { if (!saving) { if (changed) confirmLeave = true else dismiss() } }
    AlertDialog(onDismissRequest = close, properties = DialogProperties(dismissOnClickOutside = false),
        title = { Text(title) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content(saving)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall) }
            }
        }, confirmButton = {
            Button(enabled = !saving, onClick = {
                if (!saving) {
                    saving = true; error = null
                    scope.launch {
                        try { save(); dismiss() }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "保存未完成，请重试。输入已保留。" }
                        finally { saving = false }
                    }
                }
            }) { Text(if (saving) "保存中…" else "保存") }
        }, dismissButton = { TextButton(enabled = !saving, onClick = close) { Text("取消") } })
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false },
        title = { Text("放弃这次编辑？") }, text = { Text("尚未保存的修改将丢失。") },
        confirmButton = { TextButton(onClick = { confirmLeave = false; dismiss() }) { Text("放弃修改") } },
        dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("继续编辑") } })
}

@Composable internal fun FieldsDialog(title: String, fields: List<Pair<String, String>>, dismiss: () -> Unit,
    keyboards: List<KeyboardType> = emptyList(), multiline: Set<Int> = emptySet(), save: suspend (List<String>) -> Unit) {
    var values by rememberSaveable { mutableStateOf(fields.map { it.second }) }
    RecordDialog(title, values != fields.map { it.second }, dismiss, { save(values.toList()) }) { saving ->
        fields.forEachIndexed { i, f ->
            AppField(values[i], { v -> values = values.toMutableList().also { it[i] = v } }, f.first,
                keyboardType = keyboards.getOrElse(i) { KeyboardType.Text }, singleLine = i !in multiline,
                enabled = !saving)
        }
    }
}

@Composable internal fun ExerciseForm(e: Exercise?, dismiss: () -> Unit, save: suspend (Exercise) -> Unit) {
    val entryId = rememberSaveable(e?.id) { e?.id ?: newId() }
    FieldsDialog(if (e == null) "新增动作" else "编辑动作", listOf("名称" to (e?.name ?: ""),
        "肌群" to (e?.muscle ?: "胸部"), "器械" to (e?.equipment ?: "徒手"),
        "默认重量（kg）" to kg(e?.defaultGrams ?: 0), "动作说明" to (e?.notes ?: "")), dismiss,
        keyboards = listOf(KeyboardType.Text, KeyboardType.Text, KeyboardType.Text, KeyboardType.Decimal),
        multiline = setOf(4)) { v ->
        val x = e?.copy(name = v[0].trim(), muscle = v[1].trim(), equipment = v[2].trim(),
            defaultGrams = Rules.parseKg(v[3]), notes = v[4])
            ?: Exercise(entryId, v[0].trim(), v[1].trim(), v[2].trim(), Rules.parseKg(v[3]), v[4])
        Rules.exercise(x); save(x)
    }
}

@Composable internal fun SetForm(x: TrainingSet, dismiss: () -> Unit, save: suspend (TrainingSet) -> Unit) {
    var weight by rememberSaveable(x.id) { mutableStateOf(kg(x.grams)) }
    var reps by rememberSaveable(x.id) { mutableStateOf(x.reps.toString()) }
    var kind by rememberSaveable(x.id) { mutableStateOf(x.effectiveKind) }
    var rpe by rememberSaveable(x.id) { mutableStateOf(x.rpe?.toString() ?: "") }
    var rest by rememberSaveable(x.id) { mutableStateOf(x.restSeconds?.toString() ?: "") }
    val changed = weight != kg(x.grams) || reps != x.reps.toString() || kind != x.effectiveKind ||
        rpe != (x.rpe?.toString() ?: "") || rest != (x.restSeconds?.toString() ?: "")
    RecordDialog("编辑这一组", changed, dismiss, {
        val set = x.copy(grams = Rules.parseKg(weight), reps = reps.toIntOrNull() ?: error("次数需为整数"),
            rpe = rpe.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: error("RPE需为整数") },
            restSeconds = rest.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: error("休息秒数需为整数") }).withKind(kind)
        Rules.trainingSet(set); save(set)
    }) { saving ->
        ResponsiveTiles(130.dp, 2) { index, area ->
            if (index == 0) AppField(weight, { weight = it }, "重量", area, suffix = "kg",
                keyboardType = KeyboardType.Decimal, enabled = !saving)
            else AppField(reps, { reps = it }, "次数", area, suffix = "次",
                keyboardType = KeyboardType.Number, enabled = !saving)
        }
        FieldLabel("组类型")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SetKind.entries.forEach { k -> ChoicePill(k.short, kind == k, { if (!saving) kind = k }) }
        }
        AppField(rpe, { rpe = it }, "RPE（可空）", placeholder = "5–10",
            keyboardType = KeyboardType.Number, enabled = !saving)
        AppField(rest, { rest = it }, "本组休息（可空）", suffix = "秒",
            keyboardType = KeyboardType.Number, enabled = !saving)
        Text("RPE 10 表示当次力竭；休息留空则沿用计划或全局设置。", color = Muted,
            style = MaterialTheme.typography.bodySmall)
    }
}
