package app.duolian.local

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.duolian.local.domain.*
import kotlinx.coroutines.delay

@Composable internal fun ActiveTraining(s:Session,vm:MainViewModel,busy:Boolean,ask:(String,()->Unit)->Unit){
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    var edit by remember{mutableStateOf<Pair<String,TrainingSet>?>(null)}
    val owner=LocalLifecycleOwner.current
    LaunchedEffect(s.id){while(true){now=System.currentTimeMillis();delay(250)}}
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
        Panel(color=PaleBlue){
            Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(Blue))
                Text(if(s.pausedAt==null)"正在训练" else "训练已暂停",Modifier.padding(start=8.dp).weight(1f),color=Blue,style=MaterialTheme.typography.labelLarge)
                TextButton(enabled=!busy,onClick=vm::togglePause){Text(if(s.pausedAt==null)"暂停" else "继续")}
            }
            Text(s.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text(clock(Rules.elapsedMs(s,now)),style=MaterialTheme.typography.displaySmall,fontWeight=FontWeight.Bold)
            val completed=s.movements.sumOf{it.sets.count{set->set.done}};val total=s.movements.sumOf{it.sets.size}
            Row{Stat("$completed / $total","完成组数",Modifier.weight(1f));Stat(number(Rules.volumeKg(s)),"训练容量 kg",Modifier.weight(1f))}
            LinearProgressIndicator(progress={if(total==0)0f else completed.toFloat()/total},modifier=Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),trackColor=Color.White)
            Text("容量 ${number(Rules.volumeKg(s))} kg · 休息 ${(Rules.restMs(s,now)+999)/1000}s",color=Blue,style=MaterialTheme.typography.bodyMedium)
            if(Rules.restMs(s,now)>0)OutlinedButton(onClick={vm.act{vm.repo.changeActive{it.copy(restDeadline=null,restRemainingMs=0)}}},modifier=Modifier.fillMaxWidth()){Text("跳过休息")}
        }
        s.movements.forEachIndexed{index,m->Panel{
            Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Page),contentAlignment=Alignment.Center){Text("%02d".format(index+1),fontWeight=FontWeight.Bold,color=Blue)}
                Text(m.name,Modifier.padding(start=10.dp).weight(1f,fill=false),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium)
                m.supersetGroup?.let{g->Surface(color=PaleBlue,shape=RoundedCornerShape(8.dp),modifier=Modifier.padding(start=8.dp)){
                    Text("超级组 ${('A'.code+g-1).toChar()}",Modifier.padding(horizontal=7.dp,vertical=3.dp),color=Blue,style=MaterialTheme.typography.labelSmall)}}
                Spacer(Modifier.weight(1f))
                Text("${m.sets.count{it.done}}/${m.sets.size}组",color=Muted,style=MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Text("组",Modifier.width(34.dp),style=MaterialTheme.typography.labelSmall,color=Muted)
                Text("重量 × 次数",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=Muted)
                Text("完成",Modifier.width(48.dp),style=MaterialTheme.typography.labelSmall,color=Muted)
            }
            m.sets.forEachIndexed{i,x->
                val kind=x.effectiveKind
                val badgeColor=when(kind){SetKind.WARMUP->Color(0xFFE59B42);SetKind.DROP->Color(0xFF7C5CE0);SetKind.AMRAP->Color(0xFFE5534B);SetKind.BACKOFF->Muted;SetKind.WORK->Muted}
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if(x.done)Color(0xFFEAF6F0) else Page).padding(horizontal=7.dp),verticalAlignment=Alignment.CenterVertically){
                    Text(if(kind==SetKind.WORK)(i+1).toString() else kind.short,Modifier.width(30.dp),color=badgeColor,style=MaterialTheme.typography.labelMedium)
                    TextButton(onClick={edit=m.id to x},enabled=s.pausedAt==null&&!busy,modifier=Modifier.weight(1f),contentPadding=PaddingValues(vertical=10.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Text("${kg(x.grams)} kg  ×  ${x.reps}",fontWeight=FontWeight.SemiBold)
                            x.rpe?.let{r->Text("  RPE $r",color=badgeColor,style=MaterialTheme.typography.labelMedium)}
                        }
                    }
                    TextButton(enabled=s.pausedAt==null&&!busy&&m.sets.size>1,onClick={ask("删除这一组？"){vm.act{vm.repo.removeSet(m.id,x.id)}}},contentPadding=PaddingValues(0.dp),modifier=Modifier.width(35.dp)){Text("−",color=Muted)}
                    Checkbox(x.done,{vm.complete(m.id,x.id)},enabled=s.pausedAt==null&&!busy,colors=CheckboxDefaults.colors(checkedColor=Color(0xFF38A376)))
                }
            }
            Row(Modifier.fillMaxWidth()){
                TextButton(enabled=s.pausedAt==null&&!busy,onClick={vm.act{vm.repo.addSet(m.id)}},modifier=Modifier.weight(1f)){Text("＋ 添加一组")}
                TextButton(enabled=s.pausedAt==null&&!busy&&m.sets.size<100,onClick={vm.addDrop(m.id)},modifier=Modifier.weight(1f)){Text("＋ 递减组")}
            }
            m.note.takeIf{it.isNotBlank()}?.let{Text(it,color=Muted,style=MaterialTheme.typography.bodySmall)}
        }}
        Button(enabled=!busy&&s.movements.any{m->m.sets.any{it.done}},onClick={ask("结束训练并保存历史？未完成的组不会计入容量。",vm::finish)},modifier=Modifier.fillMaxWidth(),contentPadding=PaddingValues(16.dp)){Text("结束并保存训练")}
        TextButton(enabled=!busy,onClick={ask("放弃此训练？当前记录将删除。"){vm.act{vm.repo.deleteSession(s.id)}}},modifier=Modifier.fillMaxWidth()){Text("放弃训练",color=Muted)}
    }
    edit?.let{(m,x)->SetForm(x,{edit=null}){updated->vm.act{vm.repo.updateSet(m,updated)}}}
}
