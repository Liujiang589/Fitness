package app.duolian.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import app.duolian.local.domain.*

@Composable internal fun ToolsPage(sessions:List<Session>){
    var weight by rememberSaveable{mutableStateOf("")};var reps by rememberSaveable{mutableStateOf("5")}
    val w=weight.toDoubleOrNull();val r=reps.toIntOrNull()
    val estimate=if(w!=null&&w.isFinite()&&w>0&&w<=1000&&r!=null&&r in 1..30)if(r==1)w else w*(1+r/30.0) else null
    val best=sessions.filter{it.endedAt!=null}.flatMap{it.movements}.groupBy{it.exerciseId}.mapNotNull{(_,moves)->
        val sets=moves.flatMap{it.sets}.filter{it.done&&!it.warmup}
        if(sets.isEmpty())null else Triple(moves.first().name,sets.maxOf{it.grams},sets.sumOf{it.grams*it.reps}/1000.0)
    }.sortedByDescending{it.third}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
        item{Panel{
            Text("1RM 估算",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Text("用一次正式组记录，估算单次最大重量。",color=Muted,style=MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
                OutlinedTextField(weight,{weight=it},label={Text("重量 kg")},singleLine=true,modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
                OutlinedTextField(reps,{reps=it},label={Text("次数 1–30")},singleLine=true,modifier=Modifier.weight(1f),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            }
            Text(if(estimate==null)"— kg" else "${number(estimate)} kg",style=MaterialTheme.typography.headlineLarge,color=Blue,fontWeight=FontWeight.Bold)
            Text("Epley 公式，仅为估算；次数越高偏差可能越大。",color=Muted,style=MaterialTheme.typography.bodySmall)
        }}
        item{Section("动作表现")}
        if(best.isEmpty())item{EmptyPanel("等待第一条训练记录","完成正式组后，这里会统计各动作的最大记录重量与总容量。")}
        items(best){x->Panel{
            Text(x.first,fontWeight=FontWeight.SemiBold)
            Row{Stat(kg(x.second),"最大记录 kg",Modifier.weight(1f));Stat(number(x.third),"累计容量 kg",Modifier.weight(1f))}
        }}
    }
}
