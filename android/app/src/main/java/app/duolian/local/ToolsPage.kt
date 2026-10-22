package app.duolian.local

import android.os.SystemClock
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duolian.local.domain.*
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val hiitPresets = listOf(
    HiitEngine.preset("经典 20/10 × 8", 20, 10, 8),
    HiitEngine.preset("Tabata 20/10 × 8", 20, 10, 8, prepare = 10),
    HiitEngine.preset("燃脂 30/15 × 10", 30, 15, 10),
    HiitEngine.preset("耐力 40/20 × 6", 40, 20, 6)
)

@Composable internal fun ToolsPage(sessions: List<Session>, vm: MainViewModel, hiitRuns: List<HiitRun>) {
    var weight by rememberSaveable { mutableStateOf("") }; var reps by rememberSaveable { mutableStateOf("5") }
    val w = weight.toDoubleOrNull(); val r = reps.toIntOrNull()
    val estimate = if (w != null && w.isFinite() && w > 0 && w <= 1000 && r != null && r in 1..30)
        Progression.oneRm((w * 1000).toLong(), r) else null
    val best = sessions.filter { it.endedAt != null }.flatMap { it.movements }.groupBy { it.exerciseId }
        .mapNotNull { (_, moves) ->
            val sets = moves.flatMap { it.sets }.filter { it.countsVolume }
            if (sets.isEmpty()) null else Triple(moves.first().name, sets.maxOf { it.grams },
                sets.sumOf { it.grams * it.reps } / 1000.0)
        }.sortedByDescending { it.third }
    LazyColumn(Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)) {
        item { HiitPanel(vm, hiitRuns) }
        item {
            Panel {
                Text("1RM 估算", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("用一次正式组记录，估算单次最大重量。", color = Muted, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(weight, { weight = it }, label = { Text("重量 kg") }, singleLine = true,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(reps, { reps = it }, label = { Text("次数 1–30") }, singleLine = true,
                        modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Text(if (estimate == null) "— kg" else "${kg(estimate)} kg",
                    style = MaterialTheme.typography.headlineLarge, color = Blue, fontWeight = FontWeight.Bold)
                Text("Epley 公式，仅为估算；次数越高偏差可能越大。", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item { Section("动作表现") }
        if (best.isEmpty()) item {
            EmptyPanel("等待第一条训练记录", "完成正式组后，这里会统计各动作的最大记录重量与总容量。")
        }
        items(best) { x ->
            Panel {
                Text(x.first, fontWeight = FontWeight.SemiBold)
                Row {
                    Stat(kg(x.second), "最大记录 kg", Modifier.weight(1f))
                    Stat(number(x.third), "累计容量 kg", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable private fun HiitPanel(vm: MainViewModel, runs: List<HiitRun>) {
    var presetIndex by rememberSaveable { mutableIntStateOf(0) }
    var work by rememberSaveable { mutableStateOf(hiitPresets[0].workSeconds.toString()) }
    var rest by rememberSaveable { mutableStateOf(hiitPresets[0].restSeconds.toString()) }
    var rounds by rememberSaveable { mutableStateOf(hiitPresets[0].rounds.toString()) }
    var prepare by rememberSaveable { mutableStateOf(hiitPresets[0].prepareSeconds.toString()) }
    var running by remember { mutableStateOf<HiitTemplate?>(null) }
    var startElapsed by remember { mutableStateOf(0L) }
    var startWall by remember { mutableStateOf(0L) }
    var now by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    var error by remember { mutableStateOf<String?>(null) }
    val busy by vm.busy.collectAsStateWithLifecycle()

    LaunchedEffect(running) {
        while (running != null) { now = SystemClock.elapsedRealtime(); delay(200) }
    }
    val template = running
    val phase = template?.let { HiitEngine.phaseAt(it, now - startElapsed) }

    fun finishRun(t: HiitTemplate, elapsed: Long) {
        val completed = HiitEngine.completedRounds(t, elapsed)
        vm.stopTimer()
        vm.saveHiitRun(HiitRun(templateId = t.id, name = t.name, startedAt = startWall,
            endedAt = System.currentTimeMillis(), completedRounds = completed,
            workSeconds = t.workSeconds, restSeconds = t.restSeconds, rounds = t.rounds))
        running = null
    }

    // The run ends on its own once the last interval elapses.
    LaunchedEffect(phase?.kind) {
        if (template != null && phase?.kind == HiitPhaseKind.DONE) {
            finishRun(template, now - startElapsed)
        }
    }

    Panel(color = if (template == null) Color.White else PaleBlue) {
        Text("间歇训练 HIIT", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (template == null) {
            Text("选择模板或自定义时间，计时会在后台继续并显示在通知栏。", color = Muted,
                style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                hiitPresets.forEachIndexed { i, p ->
                    FilterChip(selected = presetIndex == i, onClick = {
                        presetIndex = i
                        work = p.workSeconds.toString(); rest = p.restSeconds.toString()
                        rounds = p.rounds.toString(); prepare = p.prepareSeconds.toString()
                    }, label = { Text(p.name) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(work, { work = it }, label = { Text("训练 秒") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(rest, { rest = it }, label = { Text("休息 秒") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(rounds, { rounds = it }, label = { Text("轮数") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(prepare, { prepare = it }, label = { Text("准备 秒") }, modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            val candidate = HiitTemplate(name = hiitPresets[presetIndex].name,
                prepareSeconds = prepare.toIntOrNull() ?: -1, workSeconds = work.toIntOrNull() ?: -1,
                restSeconds = rest.toIntOrNull() ?: -1, rounds = rounds.toIntOrNull() ?: -1)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !busy, onClick = {
                runCatching {
                    Rules.hiitTemplate(candidate)
                    running = candidate
                    startElapsed = SystemClock.elapsedRealtime(); startWall = System.currentTimeMillis()
                    now = startElapsed; error = null
                    vm.startHiitTimer(candidate)
                }.onFailure { error = it.message ?: "参数无效" }
            }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                contentPadding = PaddingValues(13.dp)) { Text("开始间歇训练") }
            Text("训练时间 5–600 秒，休息 0–600 秒，1–100 轮。Android 13 及以上需要在系统设置中允许通知，才能看到倒计时通知。",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        } else {
            val kind = phase?.kind ?: HiitPhaseKind.PREPARE
            val color = when (kind) {
                HiitPhaseKind.WORK -> Color(0xFFE5534B); HiitPhaseKind.REST -> Color(0xFF2C8C63)
                HiitPhaseKind.PREPARE -> Color(0xFFE59B42); HiitPhaseKind.DONE -> Blue
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kind.label, color = color, fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(if (phase != null && phase.round > 0) "第 ${phase.round}/${template.rounds} 轮" else "${template.rounds} 轮",
                    color = Muted, style = MaterialTheme.typography.labelLarge)
            }
            Text("${phase?.remainingSeconds ?: 0}", style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold, color = color)
            LinearProgressIndicator(
                progress = { if (phase == null || phase.round == 0) 0f else phase.round.toFloat() / template.rounds },
                modifier = Modifier.fillMaxWidth().height(6.dp), color = color)
            Text("${template.workSeconds}s 训练 / ${template.restSeconds}s 休息 · ${template.rounds} 轮",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { finishRun(template, SystemClock.elapsedRealtime() - startElapsed) },
                modifier = Modifier.fillMaxWidth()) { Text("结束这次训练") }
            Text("退出应用或锁屏后计时与通知仍会继续。", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
    if (runs.isNotEmpty()) {
        // Reported separately so the panel stays focused on the timer itself.
        RecordPanel(runs)
    }
}

@Composable private fun RecordPanel(runs: List<HiitRun>) {
    val format = remember { DateTimeFormatter.ofPattern("MM-dd HH:mm") }
    Panel {
        Text("最近的间歇训练", fontWeight = FontWeight.SemiBold)
        runs.sortedByDescending { it.endedAt }.take(6).forEach { r ->
            val stamp = Instant.ofEpochMilli(r.startedAt).atZone(ZoneId.systemDefault()).format(format)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = MaterialTheme.typography.bodyMedium)
                    Text("$stamp · ${r.workSeconds}s/${r.restSeconds}s · 完成 ${r.completedRounds}/${r.rounds} 轮",
                        color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                Text("${(r.endedAt - r.startedAt) / 60000} min", color = Blue,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

