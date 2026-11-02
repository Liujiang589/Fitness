package app.duolian.local

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.duolian.local.domain.*
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun QuickSetRow(set: TrainingSet, index: Int, previous: TrainingSet?, next: Boolean,
    enabled: Boolean, busy: Boolean, onEdit: (Long, Int) -> Unit, onComplete: (Long, Int) -> Unit,
    onUndo: () -> Unit, onAdvanced: (TrainingSet) -> Unit, onRemove: (() -> Unit)?) {
    var weight by rememberSaveable(set.id) { mutableStateOf(kg(set.grams)) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.reps.toString()) }
    var baseWeight by remember(set.id) { mutableLongStateOf(set.grams) }
    var baseReps by remember(set.id) { mutableIntStateOf(set.reps) }
    val focus = LocalFocusManager.current
    val haptic = LocalHapticFeedback.current
    var menu by remember(set.id) { mutableStateOf(false) }
    LaunchedEffect(busy, enabled) { if (busy || !enabled) menu = false }
    LaunchedEffect(set.grams, set.reps, set.done) {
        if (set.done || weight == kg(baseWeight)) weight = kg(set.grams)
        if (set.done || reps == baseReps.toString()) reps = set.reps.toString()
        baseWeight = set.grams; baseReps = set.reps
    }
    val parsed = runCatching {
        val grams = Rules.parseKg(weight)
        val count = reps.toIntOrNull() ?: error("次数需为整数")
        Rules.trainingSet(set.copy(grams = grams, reps = count))
        grams to count
    }
    val values = parsed.getOrNull()
    fun changeDraft(newWeight: String, newReps: String) {
        weight = newWeight; reps = newReps
        if (enabled && !busy && !set.done) runCatching {
            val grams = Rules.parseKg(newWeight)
            val count = newReps.toIntOrNull() ?: error("次数需为整数")
            Rules.trainingSet(set.copy(grams = grams, reps = count))
            onEdit(grams, count)
        }
    }
    val minimumField = maxOf(84.dp, textWidth("100", MaterialTheme.typography.bodyLarge) +
        textWidth("kg", MaterialTheme.typography.labelSmall) + 24.dp)
    @Composable fun Fields(modifier: Modifier) {
        Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            CompactSetField(weight, { changeDraft(it, reps) }, "kg", "第${index + 1}组重量", enabled && !busy && !set.done,
                Modifier.weight(1f), KeyboardType.Decimal)
            Text("×", color = Muted)
            CompactSetField(reps, { changeDraft(weight, it) }, "次", "第${index + 1}组次数", enabled && !busy && !set.done,
                Modifier.weight(1f), KeyboardType.Number)
        }
    }
    @Composable fun Completion() {
        Checkbox(set.done, {
            focus.clearFocus()
            if (set.done) onUndo() else values?.let { (grams, count) ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onComplete(grams, count)
            }
        }, enabled = enabled && !busy && (set.done || values != null),
            modifier = Modifier.semantics { contentDescription = "第${index + 1}组${if (set.done) "撤销完成" else "完成"}" },
            colors = CheckboxDefaults.colors(checkedColor = Blue))
    }
    @Composable fun SetMenu() {
        Box {
            TextButton(onClick = { menu = true }, enabled = enabled && !busy,
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .semantics { contentDescription = "第${index + 1}组更多选项" }, contentPadding = PaddingValues(4.dp)) {
                Text("${index + 1} ···", color = if (next) Blue else Muted, style = MaterialTheme.typography.labelMedium)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (previous != null && !set.done) DropdownMenuItem(text = { Text("沿用上次重量和次数") },
                    onClick = { menu = false; changeDraft(kg(previous.grams), previous.reps.toString()) })
                DropdownMenuItem(text = { Text("组类型 / RPE / 休息") }, enabled = values != null, onClick = {
                    menu = false; focus.clearFocus()
                    values?.let { (grams, count) ->
                        val edited = set.copy(grams = grams, reps = count)
                        if (!set.done) onEdit(grams, count)
                        onAdvanced(edited)
                    }
                })
                if (onRemove != null) DropdownMenuItem(text = { Text("删除这一组", color = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; onRemove() })
            }
        }
    }
    Surface(color = if (set.done) PaleBlue else Color.White, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (next) 1.5.dp else 1.dp, if (next) Blue else Hairline)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (set.done) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        SetMenu()
                        Column(Modifier.weight(1f)) {
                            Text("${kg(set.grams)} kg × ${set.reps} 次", style = MaterialTheme.typography.bodyLarge)
                            Text("已完成 · ${set.effectiveKind.label}", color = Muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Completion()
                    }
                } else if (maxWidth < minimumField * 2 + 116.dp) {
                    Column {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SetMenu()
                            Text(if (next) "当前组 · ${set.effectiveKind.label}" else set.effectiveKind.label, Modifier.weight(1f), color = Blue,
                                style = MaterialTheme.typography.labelMedium)
                            Completion()
                        }
                        Fields(Modifier.fillMaxWidth())
                    }
                } else Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SetMenu()
                    Fields(Modifier.weight(1f))
                    Completion()
                }
            }
                Column {
                    if (!set.done && previous != null) Text("上次 ${kg(previous.grams)} kg × ${previous.reps} 次",
                        color = Muted, style = MaterialTheme.typography.labelSmall)
                    if (!set.done && parsed.isFailure) Text(parsed.exceptionOrNull()?.message ?: "请检查输入",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    else if (!set.done && values != null && (values.first != set.grams || values.second != set.reps))
                        Text("正在保存输入…", color = Muted, style = MaterialTheme.typography.labelSmall)
                    set.rpe?.let { Text("RPE $it", color = Muted, style = MaterialTheme.typography.labelSmall) }
                }
        }
    }
}

@Composable private fun CompactSetField(value: String, change: (String) -> Unit, suffix: String,
    description: String, enabled: Boolean, modifier: Modifier, keyboard: KeyboardType) {
    Surface(modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(11.dp), color = Color.White) {
        BasicTextField(value, change, enabled = enabled, singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center), cursorBrush = SolidColor(Blue),
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            decorationBox = { inner -> Row(Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { inner() }
                Text(suffix, color = Muted, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 3.dp))
            } })
    }
}
