package app.duolian.local

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.duolian.local.domain.*
import kotlinx.coroutines.delay

/** Each set is a lazy item: long plans no longer compose every field at once. */
@Composable internal fun ActiveTraining(s: Session, vm: MainViewModel, sessions: List<Session>,
    exercises: List<Exercise>, busy: Boolean, ask: (String, () -> Unit) -> Unit, showGuide: (Exercise) -> Unit) {
    var edit by remember { mutableStateOf<Pair<String, TrainingSet>?>(null) }
    val next = remember(s.movements) { TrainingAssist.next(s) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(s.restDeadline, s.pausedAt) {
        val deadline = s.restDeadline
        if (s.pausedAt == null && deadline != null && deadline > System.currentTimeMillis()) {
            delay((deadline - System.currentTimeMillis()).coerceAtLeast(0))
            if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) runCatching {
                val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 60)
                try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250); delay(350) } finally { tone.release() }
            }
        }
    }
    val listState = rememberLazyListState()
    val known = remember(exercises) { exercises.associateBy { it.id } }
    val references = remember(s.id, sessions) {
        s.movements.associate { it.id to TrainingAssist.previous(s, it.exerciseId, sessions) }
    }
    LazyColumn(Modifier.fillMaxSize(), state = listState,
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item(key = "training-header", contentType = "header") { ActiveTrainingHeader(s, vm, busy, next) }
        next?.movement?.let { movement -> known[movement.exerciseId]?.takeIf { it.name == movement.name } }
            ?.let { exercise -> item(key = "movement-preview", contentType = "guide") {
                MovementDiscovery(exercise) { showGuide(exercise) }
            } }
        s.movements.forEachIndexed { movementIndex, m ->
            val previous = references[m.id]
            item(key = "movement-${m.id}", contentType = "movement-header") {
                Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Eyebrow("动作 ${movementIndex + 1} / ${s.movements.size}")
                    ResponsiveHeader(leading = {
                        Text(m.name, style = MaterialTheme.typography.titleLarge)
                    }, trailing = {
                        Text("${m.sets.count { it.done }}/${m.sets.size} 组", color = Blue,
                            style = MaterialTheme.typography.labelMedium)
                    })
                    m.supersetGroup?.let { Text("超级组 ${('A'.code + it - 1).toChar()} · 按顺序交替完成",
                        color = Blue, style = MaterialTheme.typography.bodySmall) }
                    Text(previous?.let { "上次 ${stamp(it.session.startedAt)}" } ?: "首次记录，按实际表现填写",
                        color = Muted, style = MaterialTheme.typography.bodySmall)
                    known[m.exerciseId]?.takeIf { it.name == m.name }?.let { e ->
                        TextButton(onClick = { showGuide(e) }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("查看动作示范与要领 ›")
                        }
                    }
                }
            }
            itemsIndexed(m.sets, key = { _, set -> "set-${m.id}-${set.id}" }, contentType = { _, _ -> "training-set" }) { i, x ->
                QuickSetRow(x, i, TrainingAssist.reference(m, i, previous?.movement), next?.set?.id == x.id,
                    enabled = s.pausedAt == null, busy = busy,
                    onEdit = { grams, reps -> vm.saveQuickSet(s.id, m.id, x.id, grams, reps) },
                    onComplete = { grams, reps -> vm.completeQuickSet(m.id, x.id, grams, reps) },
                    onUndo = { vm.undoQuickSet(m.id, x.id) }, onAdvanced = { edit = m.id to it },
                    onRemove = if (m.sets.size > 1) ({ ask("删除这一组？") { vm.removeTrainingSet(s.id, m.id, x.id) } }) else null)
            }
            item(key = "movement-footer-${m.id}", contentType = "movement-footer") {
                Column {
                    Row(Modifier.fillMaxWidth()) {
                        TextButton(enabled = s.pausedAt == null && !busy && m.sets.size < 100,
                            onClick = { vm.act { vm.repo.addSet(m.id) } }, modifier = Modifier.weight(1f)) { Text("＋ 添加一组") }
                        TextButton(enabled = s.pausedAt == null && !busy && m.sets.size < 100,
                            onClick = { vm.addDrop(m.id) }, modifier = Modifier.weight(1f)) { Text("＋ 递减组") }
                    }
                    m.note.takeIf { it.isNotBlank() }?.let { Text(it, color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item(key = "finish-training", contentType = "finish") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = !busy && s.movements.any { m -> m.sets.any { it.done } },
                    onClick = { ask("结束训练并保存历史？未完成的组不会计入容量。", vm::finish) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp)) { Text("结束并保存训练") }
                TextButton(enabled = !busy, onClick = {
                    ask("放弃此训练？当前记录将删除。") { vm.discardTraining(s.id) }
                }, modifier = Modifier.fillMaxWidth()) { Text("放弃训练", color = Muted) }
            }
        }
    }
    edit?.let { (movement, set) -> SetForm(set, { edit = null }) { vm.saveSetEdits(movement, it) } }
}


@Composable private fun ActiveTrainingHeader(s: Session, vm: MainViewModel, busy: Boolean, next: SetTarget?) {
    val owner = LocalLifecycleOwner.current
    val now by produceState(System.currentTimeMillis(), owner, s.id) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { value = System.currentTimeMillis(); delay(1000) }
        }
    }
        Panel(color=Night){
            Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Volt))
                Text(if(s.pausedAt==null)"正在训练" else "训练已暂停",Modifier.padding(start=8.dp).weight(1f),color=Volt,style=MaterialTheme.typography.labelLarge)
                TextButton(enabled=!busy,onClick=vm::togglePause){Text(if(s.pausedAt==null)"暂停" else "继续",color=Volt)}
            }
            Text(s.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,color=Color.White)
            val resting = Rules.restMs(s,now) > 0
            Text(if (s.pausedAt != null) "计时已暂停，准备好再继续" else if (resting) "放松呼吸，稍作休息" else "专注眼前这一组", color=OnDarkMuted,
                style=MaterialTheme.typography.bodyMedium)
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val value = if (resting) clock(Rules.restMs(s,now)) else clock(Rules.elapsedMs(s,now))
                val style = listOf(MaterialTheme.typography.displayMedium, MaterialTheme.typography.headlineLarge,
                    MaterialTheme.typography.headlineSmall, MaterialTheme.typography.bodyLarge)
                    .firstOrNull { textWidth(value, it) <= maxWidth } ?: MaterialTheme.typography.bodyLarge
                Text(value, style=style, fontWeight=FontWeight.SemiBold, color=Volt)
            }
            Text(if (resting) "休息剩余" else "本次训练用时", color=OnDarkMuted,
                style=MaterialTheme.typography.labelMedium)
            val completed=s.movements.sumOf{it.sets.count{set->set.done}};val total=s.movements.sumOf{it.sets.size}
            var showStats by rememberSaveable(s.id) { mutableStateOf(false) }
            TextButton(onClick={showStats=!showStats},modifier=Modifier.fillMaxWidth()) {
                Text(if(showStats) "收起本次统计" else "查看本次统计",color=Volt)
            }
            if(showStats) MetricGrid(listOf("$completed / $total" to "完成组数",
                number(Rules.volumeKg(s)) to "训练容量 kg", clock(Rules.elapsedMs(s,now)) to "总用时"))
            LinearProgressIndicator(progress={if(total==0)0f else completed.toFloat()/total},modifier=Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),color=Volt,trackColor=DarkSeparator)
            Text(next?.let { "接下来 · ${it.movement.name} · 第 ${it.index + 1} 组" } ?: "全部组已完成，随时保存这次训练。",
                color=OnDarkMuted,style=MaterialTheme.typography.bodySmall)
            if(Rules.restMs(s,now)>0)OutlinedButton(enabled=!busy && s.pausedAt==null,onClick={vm.skipRest()},modifier=Modifier.fillMaxWidth()){Text("跳过休息",color=Volt)}
        }
}
