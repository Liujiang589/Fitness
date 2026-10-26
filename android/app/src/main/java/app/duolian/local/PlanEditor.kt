package app.duolian.local

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.duolian.local.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One editor shared by the home, plan library and quick menu. Saving completes before dismissal. */
@Composable internal fun PlanForm(p: Plan?, exercises: List<Exercise>, dismiss: () -> Unit,
    save: suspend (Plan) -> Unit) {
    val entryId = rememberSaveable(p?.id) { p?.id ?: newId() }
    var name by rememberSaveable { mutableStateOf(p?.name ?: "") }
    var ids by rememberSaveable { mutableStateOf(ArrayList(p?.exerciseIds ?: emptyList())) }
    var sets by rememberSaveable { mutableStateOf((p?.sets ?: 3).toString()) }
    var reps by rememberSaveable { mutableStateOf((p?.reps ?: 10).toString()) }
    var rest by rememberSaveable { mutableStateOf((p?.restSeconds ?: 0).toString()) }
    var schemeName by rememberSaveable { mutableStateOf((p?.scheme ?: SetScheme.STRAIGHT).name) }
    var supersetName by rememberSaveable { mutableStateOf((p?.superset ?: SupersetMode.NONE).name) }
    var weekdays by rememberSaveable { mutableStateOf(ArrayList(p?.weekdays ?: emptyList())) }
    var picker by rememberSaveable { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val scheme = SetScheme.valueOf(schemeName)
    val superset = SupersetMode.valueOf(supersetName)
    val changed = name != (p?.name ?: "") || ids != (p?.exerciseIds ?: emptyList<String>()) ||
        sets != (p?.sets ?: 3).toString() || reps != (p?.reps ?: 10).toString() ||
        rest != (p?.restSeconds ?: 0).toString() || scheme != (p?.scheme ?: SetScheme.STRAIGHT) ||
        superset != (p?.superset ?: SupersetMode.NONE) || weekdays.toSet() != (p?.weekdays ?: emptyList()).toSet()
    val close: () -> Unit = {
        if (!saving) {
            focus.clearFocus()
            when { picker -> picker = false; changed -> confirmLeave = true; else -> dismiss() }
        }
    }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false,
        decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = Page) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = close, enabled = !saving) { Text(if (picker) "返回" else "取消") }
                    Text(if (picker) "选择动作" else if (p == null) "创建计划" else "编辑计划",
                        Modifier.weight(1f).padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge)
                    Text(if (picker) "${ids.size} 已选" else "独练", color = Muted,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(end = 12.dp))
                }
                if (picker) {
                    PlanExercisePicker(exercises, ids, Modifier.weight(1f), toggle = { id ->
                        ids = ArrayList(if (id in ids) ids - id else ids + id)
                        error = null
                    })
                } else {
                    LazyColumn(Modifier.weight(1f).testTag("plan-editor-list"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Text(if (p == null) "安排一场，适合自己的训练。" else "让计划更适合现在的你。",
                                    style = MaterialTheme.typography.headlineSmall)
                                Text("先选想练的动作，再按自己的节奏调整。", color = Muted,
                                    style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        item { Panel {
                            AppField(name, { if (!saving) { name = it; error = null } }, "计划名称",
                                modifier = Modifier.testTag("plan-name"), placeholder = "例如：上肢推 · 周一")
                        } }
                        item {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("训练动作", style = MaterialTheme.typography.titleLarge)
                                    Text(if (ids.isEmpty()) "按你希望训练的顺序添加" else "${ids.size} 个动作 · 按下方顺序训练",
                                        color = Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(enabled = !saving, onClick = { focus.clearFocus(); picker = true }) { Text("＋ 添加动作") }
                            }
                        }
                        if (ids.isEmpty()) item {
                            Surface(onClick = { picker = true }, enabled = !saving,
                                color = Color.White, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AppSymbol(2, Blue, Modifier.size(30.dp))
                                    Text("今天，想练哪里？", style = MaterialTheme.typography.titleMedium)
                                    Text("从动作库挑选，支持按肌群筛选。", color = Muted)
                                    Text("选择训练动作 ›", color = Blue, style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        itemsIndexed(ids, key = { _, id -> id }) { index, id ->
                            val exercise = exercises.firstOrNull { it.id == id }
                            Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("%02d".format(index + 1), color = Blue,
                                            style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(40.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(exercise?.name ?: "动作已删除，请移除", style = MaterialTheme.typography.titleMedium)
                                            exercise?.let { Text("${it.muscle} · ${it.equipment}", color = Muted,
                                                style = MaterialTheme.typography.bodySmall) }
                                        }
                                    }
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(enabled = !saving && index > 0, modifier = Modifier.semantics {
                                            contentDescription = "上移${exercise?.name ?: id}" }, onClick = {
                                            ids = ArrayList(ids).also { it[index] = it[index - 1]; it[index - 1] = id }
                                        }) { Text("上移") }
                                        TextButton(enabled = !saving && index < ids.lastIndex, modifier = Modifier.semantics {
                                            contentDescription = "下移${exercise?.name ?: id}" }, onClick = {
                                            ids = ArrayList(ids).also { it[index] = it[index + 1]; it[index + 1] = id }
                                        }) { Text("下移") }
                                        TextButton(enabled = !saving, modifier = Modifier.semantics {
                                            contentDescription = "移除${exercise?.name ?: id}" }, onClick = { ids = ArrayList(ids - id) }) {
                                            Text("移除", color = Muted)
                                        }
                                    }
                                }
                            }
                        }
                        item { Panel {
                            Text("每个动作的默认安排", style = MaterialTheme.typography.titleMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                AppField(sets, { if (!saving) sets = it }, "组数", Modifier.weight(1f).testTag("plan-sets"),
                                    suffix = "组", keyboardType = KeyboardType.Number)
                                AppField(reps, { if (!saving) reps = it }, "每组次数", Modifier.weight(1f).testTag("plan-reps"),
                                    suffix = "次", keyboardType = KeyboardType.Number)
                            }
                            Text("开始训练后，各组的重量和次数仍可单独调整。", color = Muted,
                                style = MaterialTheme.typography.bodySmall)
                        } }
                        item { Panel {
                            Text("训练日", style = MaterialTheme.typography.titleMedium)
                            Text(if (weekdays.isEmpty()) "自由安排 · 想练时再出发" else "在选定日期显示到首页今日安排",
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { i, label ->
                                    val day = i + 1
                                    FilterChip(selected = day in weekdays, enabled = !saving,
                                        modifier = Modifier.semantics { contentDescription = "星期$label" },
                                        onClick = { weekdays = ArrayList(if (day in weekdays) weekdays - day else weekdays + day) },
                                        label = { Text(label) })
                                }
                            }
                        } }
                        item { Panel {
                            DetailDisclosure("进阶训练设置") {
                                Text("${scheme.label} · ${superset.label}", color = Muted,
                                    style = MaterialTheme.typography.bodySmall)
                                AppField(rest, { if (!saving) rest = it }, "组间休息", suffix = "秒",
                                    keyboardType = KeyboardType.Number, modifier = Modifier.testTag("plan-rest"))
                                Text("0 秒表示沿用“我的”里的默认组间休息。", color = Muted,
                                    style = MaterialTheme.typography.bodySmall)
                                FieldLabel("训练方案")
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SetScheme.entries.forEach { s -> ChoicePill(s.label, s == scheme, { if (!saving) schemeName = s.name }) }
                                }
                                FieldLabel("动作编排")
                                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SupersetMode.entries.forEach { s -> ChoicePill(s.label, s == superset, { if (!saving) supersetName = s.name }) }
                                }
                                Text("超级组按相邻两个动作配对，至少选择两个动作。", color = Muted,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        } }
                    }
                }
                Surface(color = Color.White) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        Button(enabled = !saving, onClick = saveClick@{
                            if (saving) return@saveClick
                            focus.clearFocus()
                            if (picker) picker = false else {
                                val plan = runCatching {
                                    require(name.isNotBlank()) { "给计划起个名字，再保存。" }
                                    require(ids.isNotEmpty()) { "先选择至少一个训练动作。" }
                                    Plan(entryId, name.trim(), ids.toList(),
                                        sets.toIntOrNull() ?: error("组数请输入1–100的整数。"),
                                        reps.toIntOrNull() ?: error("次数请输入0–1000的整数。"),
                                        scheme, rest.toIntOrNull() ?: error("休息请输入0–600的整数。"),
                                        superset, weekdays.sorted()).also { Rules.plan(it, exercises) }
                                }.getOrElse { error = it.message ?: "请检查计划内容。"; null }
                                if (plan != null) {
                                    saving = true; error = null
                                    scope.launch {
                                        try { save(plan); dismiss() }
                                        catch (cancel: CancellationException) { throw cancel }
                                        catch (failed: Exception) { error = failed.message ?: "保存失败，请重试；填写内容已保留。" }
                                        finally { saving = false }
                                    }
                                }
                            }
                        }, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("plan-save"), shape = RoundedCornerShape(18.dp)) {
                            if (saving) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                            Text(when { saving -> "  正在保存…"; picker -> "完成选择 · ${ids.size} 个动作";
                                p == null -> "创建计划"; else -> "保存修改" })
                        }
                    }
                }
            }
            if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false },
                title = { Text("放弃本次修改？") }, text = { Text("计划还没有保存，可以返回继续编辑。") },
                confirmButton = { TextButton(onClick = { confirmLeave = false; dismiss() }) { Text("放弃修改") } },
                dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text("继续编辑") } })
        }
    }
}

@Composable private fun PlanExercisePicker(exercises: List<Exercise>, ids: List<String>,
    modifier: Modifier = Modifier, toggle: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var muscle by rememberSaveable { mutableStateOf("全部") }
    val filtered = remember(exercises, query, muscle) { exercises.filter { e ->
        (muscle == "全部" || e.muscle == muscle) &&
            (query.isBlank() || listOf(e.name, e.muscle, e.equipment).any { it.contains(query.trim(), true) })
    } }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppField(query, { query = it }, placeholder = "搜索动作、肌群或器械",
            modifier = Modifier.padding(horizontal = 20.dp).testTag("plan-exercise-search"))
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("全部") + exercises.map { it.muscle }.distinct()).forEach { group ->
                ChoicePill(group, group == muscle, { muscle = group })
            }
        }
        Text("${filtered.size} 个可选动作 · 点选即加入计划", Modifier.padding(horizontal = 20.dp),
            color = Muted, style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.weight(1f).testTag("plan-exercise-list"), contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (filtered.isEmpty()) item { EmptyPanel(if (exercises.isEmpty()) "动作库暂时为空" else "没有匹配的动作",
                if (exercises.isEmpty()) "请先在动作页添加动作，再回来创建计划。" else "试试其他名称，或切换到“全部”肌群。") }
            items(filtered, key = { it.id }) { exercise ->
                val selected = exercise.id in ids
                Surface(color = if (selected) PaleBlue else Color.White, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().toggleable(selected, role = Role.Checkbox,
                        onValueChange = { toggle(exercise.id) }).padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AppSymbol(2, if (selected) Blue else Muted, Modifier.size(26.dp))
                        Column(Modifier.weight(1f)) {
                            Text(exercise.name, style = MaterialTheme.typography.titleMedium)
                            Text("${exercise.muscle} · ${exercise.equipment}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Checkbox(selected, onCheckedChange = null)
                    }
                }
            }
        }
    }
}
