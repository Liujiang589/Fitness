package app.duolian.local

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable internal fun ActiveTraining(s:Session,vm:MainViewModel,sessions:List<Session>,exercises:List<Exercise>,busy:Boolean,ask:(String,()->Unit)->Unit,showGuide:(Exercise)->Unit){
    var edit by remember{mutableStateOf<Pair<String,TrainingSet>?>(null)}
    val next = remember(s.movements) { TrainingAssist.next(s) }
    val owner=LocalLifecycleOwner.current
    LaunchedEffect(s.restDeadline,s.pausedAt){val deadline=s.restDeadline
        if(s.pausedAt==null&&deadline!=null&&deadline>System.currentTimeMillis()){
            delay((deadline-System.currentTimeMillis()).coerceAtLeast(0))
            if(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))runCatching{
                val tone=ToneGenerator(AudioManager.STREAM_NOTIFICATION,60)
                try{tone.startTone(ToneGenerator.TONE_PROP_BEEP,250);delay(350)}finally{tone.release()}
            }
        }
    }
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)){
        ActiveTrainingHeader(s, vm, busy, next)
        next?.movement?.let { movement -> exercises.find { it.id == movement.exerciseId && it.name == movement.name } }
            ?.let { e -> MovementDiscovery(e) { showGuide(e) } }
        s.movements.forEach{m->Panel{
            val previous = remember(s.id, m.exerciseId, sessions) { TrainingAssist.previous(s, m.exerciseId, sessions) }
            Text(previous?.let { "上次 ${stamp(it.session.startedAt)}" } ?: "首次记录，按实际表现填写",
                color=Muted,style=MaterialTheme.typography.labelSmall)
            Row(verticalAlignment=Alignment.CenterVertically){
                ExerciseArt(exercises.find { it.id == m.exerciseId }?.muscle ?: "全身", Modifier.size(44.dp))
                Text(m.name,Modifier.padding(start=10.dp).weight(1f,fill=false),fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleMedium)
                m.supersetGroup?.let{g->Surface(color=PaleBlue,shape=RoundedCornerShape(8.dp),modifier=Modifier.padding(start=8.dp)){
                    Text("超级组 ${('A'.code+g-1).toChar()}",Modifier.padding(horizontal=7.dp,vertical=3.dp),color=Blue,style=MaterialTheme.typography.labelSmall)}}
                Spacer(Modifier.weight(1f))
                Text("${m.sets.count{it.done}}/${m.sets.size}组",color=Muted,style=MaterialTheme.typography.labelMedium)
            }
            exercises.find { it.id == m.exerciseId && it.name == m.name }?.let { e ->
                TextButton(onClick = { showGuide(e) }) { Text("查看动作示范与要领 ›") }
            }
            m.sets.forEachIndexed { i, x ->
                QuickSetRow(x, i, TrainingAssist.reference(m, i, previous?.movement), next?.set?.id == x.id,
                    enabled = s.pausedAt == null, busy = busy,
                    onEdit = { grams, reps -> vm.saveQuickSet(m.id, x.id, grams, reps) },
                    onComplete = { grams, reps -> vm.completeQuickSet(m.id, x.id, grams, reps) },
                    onUndo = { vm.undoQuickSet(m.id, x.id) }, onAdvanced = { edit = m.id to it },
                    onRemove = if (m.sets.size > 1) ({ ask("删除这一组？") { vm.act { vm.repo.removeSet(m.id, x.id) } } }) else null)
            }
            Row(Modifier.fillMaxWidth()){
                TextButton(enabled=s.pausedAt==null&&!busy,onClick={vm.act{vm.repo.addSet(m.id)}},modifier=Modifier.weight(1f)){Text("＋ 添加一组")}
                TextButton(enabled=s.pausedAt==null&&!busy&&m.sets.size<100,onClick={vm.addDrop(m.id)},modifier=Modifier.weight(1f)){Text("＋ 递减组")}
            }
            m.note.takeIf{it.isNotBlank()}?.let{Text(it,color=Muted,style=MaterialTheme.typography.bodySmall)}
        }}
        Button(enabled=!busy&&s.movements.any{m->m.sets.any{it.done}},onClick={ask("结束训练并保存历史？未完成的组不会计入容量。",vm::finish)},modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(16.dp)){Text("结束并保存训练")}
        TextButton(enabled=!busy,onClick={ask("放弃此训练？当前记录将删除。"){vm.act{vm.repo.deleteSession(s.id);vm.stopTimer()}}},modifier=Modifier.fillMaxWidth()){Text("放弃训练",color=Muted)}
    }
    edit?.let{(m,x)->SetForm(x,{edit=null}){updated->vm.act{vm.repo.updateSet(m,updated)}}}
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
            Text(if (resting) "放松呼吸，稍作休息" else "专注眼前这一组", color=OnDarkMuted,
                style=MaterialTheme.typography.bodyMedium)
            Text(if (resting) clock(Rules.restMs(s,now)) else clock(Rules.elapsedMs(s,now)),
                style=MaterialTheme.typography.displayMedium,fontWeight=FontWeight.SemiBold,color=Volt)
            Text(if (resting) "休息剩余" else "本次训练用时", color=OnDarkMuted,
                style=MaterialTheme.typography.labelMedium)
            val completed=s.movements.sumOf{it.sets.count{set->set.done}};val total=s.movements.sumOf{it.sets.size}
            var showStats by rememberSaveable(s.id) { mutableStateOf(false) }
            TextButton(onClick={showStats=!showStats},modifier=Modifier.fillMaxWidth()) {
                Text(if(showStats) "收起本次统计" else "查看本次统计",color=Volt)
            }
            if(showStats) Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                StatTile("$completed / $total","完成组数",Modifier.weight(1f),tint=Color.White)
                StatTile(number(Rules.volumeKg(s)),"训练容量 kg",Modifier.weight(1f),tint=Color.White)
                StatTile(clock(Rules.elapsedMs(s,now)),"总用时",Modifier.weight(1f),tint=Color.White)
            }
            LinearProgressIndicator(progress={if(total==0)0f else completed.toFloat()/total},modifier=Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),color=Volt,trackColor=DarkSeparator)
            Text(next?.let { "接下来 · ${it.movement.name} · 第 ${it.index + 1} 组" } ?: "全部组已完成，随时保存这次训练。",
                color=OnDarkMuted,style=MaterialTheme.typography.bodySmall)
            if(Rules.restMs(s,now)>0)OutlinedButton(onClick={vm.skipRest()},modifier=Modifier.fillMaxWidth()){Text("跳过休息",color=Volt)}
        }
}
