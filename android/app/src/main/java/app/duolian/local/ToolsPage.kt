package app.duolian.local

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duolian.local.domain.*
import app.duolian.local.timer.TimerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong

private val hiitPresets = listOf(
    HiitEngine.preset("经典", 20, 10, 8),
    HiitEngine.preset("Tabata", 20, 10, 8, prepare = 10),
    HiitEngine.preset("燃脂", 30, 15, 10),
    HiitEngine.preset("耐力", 40, 20, 6)
)

private fun mmss(ms: Long): String {
    val total = ((ms + 999) / 1000).coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable internal fun ToolsPage(sessions: List<Session>, vm: MainViewModel, hiitRuns: List<HiitRun>) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun jumpTo(index: Int) { scope.launch { listState.animateScrollToItem(index) } }

    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ToolGrid(onRest = { jumpTo(1) }, onHiit = { jumpTo(2) }, onOneRm = { jumpTo(3) }, onPlate = { jumpTo(4) }) }
        item { RestTimerCard() }
        item { HiitCard(vm) }
        item { OneRmCard() }
        item { PlateCard() }
        item { PerformanceCard(sessions) }
        if (hiitRuns.isNotEmpty()) item { HiitHistoryCard(hiitRuns) }
    }
}

@Composable private fun ToolGrid(onRest: () -> Unit, onHiit: () -> Unit, onOneRm: () -> Unit, onPlate: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(1, "组间计时", "休息倒计时", Modifier.weight(1f), onRest)
            ToolTile(5, "间歇训练", "HIIT 计时", Modifier.weight(1f), onHiit)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolTile(7, "1RM 估算", "极限重量与配重", Modifier.weight(1f), onOneRm)
            ToolTile(6, "杠铃片", "每侧怎么加", Modifier.weight(1f), onPlate)
        }
    }
}

@Composable private fun ToolTile(symbol: Int, title: String, subtitle: String,
    modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(PaleBlue),
                    contentAlignment = Alignment.Center) { AppSymbol(symbol, Blue) }
                Spacer(Modifier.weight(1f))
                Text("↗", color = Muted, style = MaterialTheme.typography.titleLarge)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable private fun RestTimerCard() {
    val context = LocalContext.current
    var seconds by rememberSaveable { mutableIntStateOf(60) }
    var deadline by remember { mutableStateOf<Long?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val remaining = ((deadline ?: 0L) - now).coerceAtLeast(0L)
    val running = deadline != null

    LaunchedEffect(deadline) {
        while (deadline != null) {
            now = System.currentTimeMillis()
            if (now >= (deadline ?: 0L)) { deadline = null; break }
            delay(150)
        }
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("组间计时", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            Text(if (running) "计时中" else "${seconds}s", color = Blue,
                style = MaterialTheme.typography.labelLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircleProgress(
                progress = if (running) remaining / (seconds * 1000f).coerceAtLeast(1f) else 0f,
                modifier = Modifier.size(118.dp), color = Blue) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mmss(if (running) remaining else seconds * 1000L),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(if (running) "剩余" else "准备", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !running, onClick = { seconds = (seconds - 15).coerceAtLeast(15) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("−15") }
                    OutlinedButton(enabled = !running, onClick = { seconds = (seconds + 15).coerceAtMost(900) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text("+15") }
                }
                Button(onClick = {
                    if (running) { deadline = null; TimerService.stop(context) }
                    else {
                        val target = System.currentTimeMillis() + seconds * 1000L
                        now = System.currentTimeMillis(); deadline = target
                        TimerService.startRest(context, target)
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
                    Text(if (running) "停止计时" else "开始休息")
                }
            }
        }
        Text("锁屏或切到后台后会在通知栏继续倒计时。", color = Muted,
            style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun HiitCard(vm: MainViewModel) {
    val busy by vm.busy.collectAsStateWithLifecycle()
    var presetIndex by rememberSaveable { mutableIntStateOf(0) }
    var work by rememberSaveable { mutableStateOf("20") }
    var rest by rememberSaveable { mutableStateOf("10") }
    var rounds by rememberSaveable { mutableStateOf("8") }
    var prepare by rememberSaveable { mutableStateOf("5") }
    var custom by rememberSaveable { mutableStateOf(false) }
    var running by remember { mutableStateOf<HiitTemplate?>(null) }
    var startElapsed by remember { mutableStateOf(0L) }
    var startWall by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(running) {
        while (running != null) { now = System.currentTimeMillis(); delay(200) }
    }
    val template = running
    val phase = template?.let { HiitEngine.phaseAt(it, now - startWall) }

    fun finish(t: HiitTemplate) {
        val elapsed = System.currentTimeMillis() - startWall
        val completed = HiitEngine.completedRounds(t, elapsed)
        vm.stopTimer()
        vm.saveHiitRun(HiitRun(templateId = t.id, name = t.name, startedAt = startWall,
            endedAt = System.currentTimeMillis(), completedRounds = completed,
            workSeconds = t.workSeconds, restSeconds = t.restSeconds, rounds = t.rounds))
        running = null
    }

    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("间歇训练", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            Text("HIIT", color = Muted, style = MaterialTheme.typography.labelLarge)
        }
        if (template == null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                hiitPresets.forEachIndexed { i, p ->
                    FilterChip(selected = presetIndex == i, onClick = {
                        presetIndex = i
                        work = p.workSeconds.toString(); rest = p.restSeconds.toString()
                        rounds = p.rounds.toString(); prepare = p.prepareSeconds.toString()
                    }, label = { Text("${p.name} ${p.workSeconds}/${p.restSeconds}") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${work}s 训练 · ${rest}s 休息 · ${rounds} 轮", fontWeight = FontWeight.SemiBold)
                    Text("总时长 ${mmss(HiitEngine.totalMs(HiitTemplate(name = "x",
                        prepareSeconds = prepare.toIntOrNull() ?: 0, workSeconds = work.toIntOrNull() ?: 20,
                        restSeconds = rest.toIntOrNull() ?: 10, rounds = rounds.toIntOrNull() ?: 1)))}",
                        color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                TextButton(onClick = { custom = !custom }) { Text(if (custom) "收起" else "自定义") }
            }
            if (custom) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppField(work, { work = it }, "训练时间", Modifier.weight(1f), suffix = "秒",
                        keyboardType = KeyboardType.Number)
                    AppField(rest, { rest = it }, "休息时间", Modifier.weight(1f), suffix = "秒",
                        keyboardType = KeyboardType.Number)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppField(rounds, { rounds = it }, "轮数", Modifier.weight(1f), suffix = "轮",
                        keyboardType = KeyboardType.Number)
                    AppField(prepare, { prepare = it }, "准备时间", Modifier.weight(1f), suffix = "秒",
                        keyboardType = KeyboardType.Number)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            val candidate = HiitTemplate(name = hiitPresets[presetIndex].name,
                prepareSeconds = prepare.toIntOrNull() ?: -1, workSeconds = work.toIntOrNull() ?: -1,
                restSeconds = rest.toIntOrNull() ?: -1, rounds = rounds.toIntOrNull() ?: -1)
            Button(enabled = !busy, onClick = {
                runCatching {
                    Rules.hiitTemplate(candidate)
                    running = candidate
                    startWall = System.currentTimeMillis(); now = startWall; error = null
                    vm.startHiitTimer(candidate)
                }.onFailure { error = it.message ?: "参数无效" }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(13.dp)) { Text("开始间歇训练") }
        } else {
            val kind = phase?.kind ?: HiitPhaseKind.PREPARE
            val accent = when (kind) {
                HiitPhaseKind.WORK -> Color(0xFFE5534B); HiitPhaseKind.REST -> Color(0xFF2C8C63)
                HiitPhaseKind.PREPARE -> Color(0xFFE59B42); HiitPhaseKind.DONE -> Blue
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircleProgress(progress = if (template.rounds == 0) 0f else (phase?.round ?: 0).toFloat() / template.rounds,
                    modifier = Modifier.size(128.dp), color = accent) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${phase?.remainingSeconds ?: 0}", style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold, color = accent)
                        Text(kind.label, color = accent, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Column(Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if ((phase?.round ?: 0) > 0) "第 ${phase!!.round}/${template.rounds} 轮" else "${template.rounds} 轮",
                        fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text("${template.workSeconds}s 训练 / ${template.restSeconds}s 休息", color = Muted,
                        style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { finish(template) }) { Text("结束训练") }
                }
            }
            Text("计时在通知栏继续，可锁屏。", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun OneRmCard() {
    var kilos by rememberSaveable { mutableStateOf(100.0) }
    var reps by rememberSaveable { mutableIntStateOf(5) }
    val oneRm = Progression.oneRm((kilos * 1000).roundToLong().coerceAtLeast(0), reps)
    Panel {
        Text("1RM 估算", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stepper("重量", kg(oneRmWeight(kilos)), "kg", Modifier.weight(1f),
                onMinus = { kilos = (kilos - 2.5).coerceAtLeast(2.5) },
                onPlus = { kilos = (kilos + 2.5).coerceAtMost(500.0) })
            Stepper("次数", reps.toString(), "次", Modifier.weight(1f),
                onMinus = { reps = (reps - 1).coerceAtLeast(1) },
                onPlus = { reps = (reps + 1).coerceAtMost(30) })
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(kg(oneRm), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = Blue)
            Text(" kg", color = Muted, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text("按 $reps 次 × ${kg(oneRmWeight(kilos))} kg 估算", color = Muted,
            style = MaterialTheme.typography.bodySmall)
        HorizontalDivider(color = Page)
        DetailDisclosure("强度配重表") {
        OneRmTable.rows(oneRm).chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (percent, grams) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(Page).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$percent%", color = Muted, style = MaterialTheme.typography.labelSmall)
                        Text(kg(grams), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        }
    }
}

private fun oneRmWeight(kilos: Double): Long = (kilos * 1000).roundToLong()

@Composable private fun PlateCard() {
    var target by rememberSaveable { mutableStateOf(60.0) }
    val loading = PlateMath.perSide(target)
    Panel {
        Text("杠铃片计算", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${number(target)} kg", style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, color = Ink)
                Text("含 ${number(PlateMath.BAR_KG)} kg 空杆", color = Muted,
                    style = MaterialTheme.typography.labelSmall)
            }
            OutlinedButton(onClick = { target = (target - 2.5).coerceAtLeast(20.0) }) { Text("−2.5") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = { target = (target + 2.5).coerceAtMost(400.0) }) { Text("+2.5") }
        }
        HorizontalDivider(color = Page)
        if (loading.isEmpty()) {
            Text("空杆，或重量低于空杆。", color = Muted, style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("每侧装载", color = Muted, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                loading.forEach { item ->
                    Column(Modifier.clip(RoundedCornerShape(12.dp)).background(PaleBlue).padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${number(item.plateKg)}", fontWeight = FontWeight.Bold, color = Blue)
                        Text("×${item.perSide}", color = Muted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(if (PlateMath.isExact(target)) "实际 ${number(PlateMath.achievable(target))} kg，正好装出。"
                else "实际可装 ${number(PlateMath.achievable(target))} kg（与目标差 ${number(target - PlateMath.achievable(target))} kg）。",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun Stepper(label: String, value: String, unit: String, modifier: Modifier = Modifier,
    onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Page).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onMinus, contentPadding = PaddingValues(4.dp)) { Text("−") }
            Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            TextButton(onClick = onPlus, contentPadding = PaddingValues(4.dp)) { Text("+") }
        }
        Text(unit, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun PerformanceCard(sessions: List<Session>) {
    val best = sessions.filter { it.endedAt != null }.flatMap { it.movements }.groupBy { it.exerciseId }
        .mapNotNull { (_, moves) ->
            val sets = moves.flatMap { it.sets }.filter { it.countsVolume }
            if (sets.isEmpty()) null else Triple(moves.first().name, sets.maxOf { it.grams },
                sets.sumOf { it.grams * it.reps } / 1000.0)
        }.sortedByDescending { it.third }
    val top = best.firstOrNull()?.third ?: 1.0
    Panel {
        DetailDisclosure("动作表现") {
        if (best.isEmpty()) {
            Text("完成正式组后，这里会按动作统计容量与最大重量。", color = Muted,
                style = MaterialTheme.typography.bodyMedium)
            return@DetailDisclosure
        }
        best.take(6).forEach { (name, grams, volume) ->
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${kg(grams)} kg", color = Blue, style = MaterialTheme.typography.labelLarge)
                }
                ShareBar((volume / top).toFloat(), Modifier.fillMaxWidth().height(7.dp))
                Text("${number(volume)} kg 容量", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    }
}

@Composable private fun HiitHistoryCard(runs: List<HiitRun>) {
    val format = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    Panel {
        Text("最近的间歇训练", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        runs.sortedByDescending { it.endedAt }.take(5).forEach { r ->
            val stamp = Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).format(format)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.bodyMedium)
                    Text("$stamp · 完成 ${r.completedRounds}/${r.rounds} 轮", color = Muted,
                        style = MaterialTheme.typography.labelSmall)
                }
                Text("${(r.endedAt - r.startedAt) / 60000} min", color = Blue,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
