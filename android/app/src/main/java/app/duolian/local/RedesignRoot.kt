package app.duolian.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.duolian.local.domain.*
import java.time.LocalDate

class MainActivity : ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        setContent{MaterialTheme(colorScheme=lightColorScheme(primary=Blue,onPrimary=Color.White,secondary=Blue,background=Page,surface=Color.White,onSurface=Ink,onBackground=Ink,surfaceVariant=Page),
            shapes=Shapes(small=RoundedCornerShape(10.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(22.dp),extraLarge=RoundedCornerShape(28.dp))){DuolianScreen()}}
    }
}
private data class Confirmation(val title:String,val action:()->Unit)

@Composable private fun DuolianScreen(vm:MainViewModel=viewModel()){
    val exercises by vm.exercises.collectAsStateWithLifecycle();val plans by vm.plans.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle();val body by vm.body.collectAsStateWithLifecycle()
    val food by vm.food.collectAsStateWithLifecycle();val water by vm.water.collectAsStateWithLifecycle()
    val rest by vm.rest.collectAsStateWithLifecycle();val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle();val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val hiitRuns by vm.hiitRuns.collectAsStateWithLifecycle()
    val autoBackupAt by vm.autoBackupAt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var tab by rememberSaveable{mutableIntStateOf(0)};var nutrition by rememberSaveable{mutableStateOf(false)}
    var exerciseForm by remember{mutableStateOf(false)};var editExercise by remember{mutableStateOf<Exercise?>(null)}
    var planForm by remember{mutableStateOf(false)};var editPlan by remember{mutableStateOf<Plan?>(null)}
    var detail by remember{mutableStateOf<Exercise?>(null)};var auxiliary by remember{mutableStateOf<String?>(null)}
    var editBody by remember{mutableStateOf<Body?>(null)};var editFood by remember{mutableStateOf<Food?>(null)}
    var confirm by remember{mutableStateOf<Confirmation?>(null)}
    val ask:(String,()->Unit)->Unit={title,action->confirm=Confirmation(title,action)}
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(message){message?.let{snackbar.showSnackbar(it);vm.message.value=null}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::export)}
    val recovery=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::exportRecovery)}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::inspectImport)}
    val auto=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::exportAutoBackup)}
    val photoPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        val current=detail
        if(uri!=null&&current!=null){
            runCatching{context.contentResolver.takePersistableUriPermission(uri,android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)}
            val updated=current.copy(mediaUri=uri.toString());detail=updated;vm.act{vm.repo.saveExercise(updated)}
        }
    }
    val a=ScreenActions(
        plan={editPlan=it;planForm=true},exercise={editExercise=it;exerciseForm=true},detail={detail=it},
        auxiliary={auxiliary=it;editFood=null;editBody=null},body={editBody=it;auxiliary="body"},
        food={editFood=it;auxiliary="food"},ask=ask,nutrition={tab=0;nutrition=true})
    val labels=listOf("训练","工具","动作","历史","我的")
    val icons=listOf(0,1,2,3,4)
    Scaffold(containerColor=Page,snackbarHost={SnackbarHost(snackbar)},bottomBar={
        Surface(shadowElevation=12.dp,color=Color.White,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)){
            NavigationBar(containerColor=Color.White,tonalElevation=0.dp){labels.forEachIndexed{i,label->NavigationBarItem(selected=tab==i,onClick={tab=i},
                icon={AppSymbol(icons[i],if(tab==i)Blue else Muted)},label={Text(label,fontWeight=if(tab==i)FontWeight.Bold else FontWeight.Normal)},
                colors=NavigationBarItemDefaults.colors(indicatorColor=PaleBlue,selectedTextColor=Blue,unselectedTextColor=Muted))}}
        }
    }){padding->Column(Modifier.fillMaxSize().padding(padding)){
        Row(Modifier.fillMaxWidth().padding(horizontal=22.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically){
            Column(Modifier.weight(1f)){
                if(tab==0){Text("${LocalDate.now().monthValue} 月 ${LocalDate.now().dayOfMonth} 日  ·  独练",color=Muted,style=MaterialTheme.typography.labelMedium);Spacer(Modifier.height(5.dp))}
                Text(if(tab==0)if(nutrition)"饮食手记" else "今天也要好好训练" else labels[tab],style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
            }
            if(tab==0)TextButton(onClick={nutrition=!nutrition}){Text(if(nutrition)"训练 ›" else "饮食 ›",fontWeight=FontWeight.Bold)}
            if(tab==4)Text("本地",color=Blue,style=MaterialTheme.typography.labelLarge)
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth(),color=Blue)
        if(!ready)Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){
            if(busy)CircularProgressIndicator(Modifier.size(26.dp));Text(if(busy)"正在打开本地数据…" else "本地数据尚未就绪",Modifier.padding(16.dp),color=Muted)
            if(!busy)Button(onClick={vm.act{vm.repo.initialize();vm.ready.value=true}}){Text("重试")}
        }} else when(tab){
            0->if(nutrition)NutritionPage(food,water,vm,a)else TrainingPage(vm,exercises,plans,sessions,food,water,busy,a)
            1->ToolsPage(sessions,vm,hiitRuns)
            2->ExercisePage(exercises,a)
            3->HistoryPage(sessions,exercises,plans,body,food,vm,a)
            else->ProfilePage(body,sessions,rest,busy,vm,a,{export.launch("Duolian-${LocalDate.now()}.json")},{import.launch(arrayOf("application/json","text/plain","application/octet-stream"))},{recovery.launch("Duolian-before-import.json")},{auto.launch("Duolian-auto-${LocalDate.now()}.json")},autoBackupAt)
        }
    }}
    if(exerciseForm)ExerciseForm(editExercise,{exerciseForm=false}){x->vm.act{vm.repo.saveExercise(x)}}
    if(planForm)PlanForm(editPlan,exercises,{planForm=false}){x->vm.act{vm.repo.savePlan(x)}}
    detail?.let{e->AlertDialog(onDismissRequest={detail=null},title={Text(e.name)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(e.mediaUri!=null)ExercisePhoto(e.mediaUri!!,Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(14.dp)))
        else ExerciseArt(e.muscle,Modifier.fillMaxWidth().height(140.dp))
        Text("${e.muscle} · ${e.equipment}"+(if(e.favorite)" · 已收藏" else ""),color=Blue)
        Text(e.notes.ifBlank{"可编辑补充你的动作要点。"})
        Text(if(e.mediaUri==null)"分类示意图；可添加自己的动作照片，也可以继续不配图。" else "已使用你选择的照片；图片保存在本机。",color=Muted,style=MaterialTheme.typography.bodySmall)
        Text("默认重量 ${kg(e.defaultGrams)} kg",fontWeight=FontWeight.SemiBold)
        Row{
            TextButton(onClick={photoPicker.launch(arrayOf("image/*"))}){Text(if(e.mediaUri==null)"添加图片" else "更换图片")}
            if(e.mediaUri!=null)TextButton(onClick={val updated=e.copy(mediaUri=null);detail=updated;vm.act{vm.repo.saveExercise(updated)}}){Text("移除图片",color=Muted)}
            TextButton(onClick={val updated=e.copy(favorite=!e.favorite);detail=updated;vm.act{vm.repo.saveExercise(updated)}}){Text(if(e.favorite)"取消收藏" else "收藏")}
        }
        TextButton(onClick={detail=null;ask("删除「${e.name}」？计划引用的动作需先从计划移除。"){vm.act{vm.repo.deleteExercise(e.id)}}}){Text("删除动作",color=MaterialTheme.colorScheme.error)}
    }},confirmButton={TextButton(onClick={detail=null;a.exercise(e)}){Text("编辑动作")}},dismissButton={TextButton(onClick={detail=null}){Text("关闭")}})}
    confirm?.let{c->AlertDialog(onDismissRequest={confirm=null},title={Text(c.title)},confirmButton={TextButton(enabled=!busy,onClick={c.action();confirm=null}){Text("确认")}},dismissButton={TextButton(onClick={confirm=null}){Text("取消")}})}
    pending?.let{b->AlertDialog(onDismissRequest={vm.pendingImport.value=null},title={Text("确认替换全部本地数据？")},text={Text("${b.exercises.size} 个动作、${b.plans.size} 份计划、${b.sessions.size} 场训练。现有数据会先保存为恢复备份。")},confirmButton={TextButton(enabled=!busy,onClick=vm::confirmImport){Text("备份并替换")}},dismissButton={TextButton(onClick={vm.pendingImport.value=null}){Text("取消")}})}
    val kind=auxiliary?.substringBefore(':');val auxDate=auxiliary?.split(':')?.let{if(it.size>2)it[2] else if(kind=="water"&&it.size>1)it[1] else LocalDate.now().toString()}?:LocalDate.now().toString()
    when(kind){
        "rest"->FieldsDialog("组间休息",listOf("秒（0–600）" to rest.toString()),{auxiliary=null}){v->val seconds=v[0].toInt();require(seconds in 0..600){"休息需为0–600秒"};vm.setRest(seconds)}
        "body"->FieldsDialog(if(editBody==null)"记录身体数据" else "编辑身体记录",listOf("日期" to(editBody?.date?:auxDate),"体重 kg" to(editBody?.let{kg(it.grams)}?:""),"体脂 %（可空）" to(editBody?.fatPercent?.toString()?:"")),{auxiliary=null}){v->val b=Body(id=editBody?.id?:newId(),date=v[0],grams=Rules.parseKg(v[1]),fatPercent=v[2].takeIf{it.isNotBlank()}?.toDouble());Rules.body(b);vm.act{vm.repo.saveBody(b)}}
        "food","meal"->FieldsDialog(if(editFood==null)"记录饮食" else "编辑饮食记录",listOf("日期" to(editFood?.date?:auxDate),"餐次：早餐/午餐/晚餐/加餐" to(editFood?.meal?:if(kind=="meal")auxiliary!!.split(':')[1] else "早餐"),"名称" to(editFood?.name?:""),"蛋白质 g" to(editFood?.protein?.toString()?:"0"),"碳水 g" to(editFood?.carbs?.toString()?:"0"),"脂肪 g" to(editFood?.fat?.toString()?:"0")),{auxiliary=null}){v->val f=Food(id=editFood?.id?:newId(),date=v[0],meal=v[1],name=v[2],protein=v[3].toDouble(),carbs=v[4].toDouble(),fat=v[5].toDouble());Rules.food(f);vm.act{vm.repo.saveFood(f)}}
        "water"->FieldsDialog("记录饮水",listOf("日期" to auxDate,"饮水 ml" to "250"),{auxiliary=null}){v->val w=Water(date=v[0],ml=v[1].toInt());Rules.water(w);vm.act{vm.repo.saveWater(w)}}
    }
}

@Composable private fun ProfilePage(body:List<Body>,sessions:List<Session>,rest:Int,busy:Boolean,vm:MainViewModel,a:ScreenActions,export:()->Unit,import:()->Unit,recovery:()->Unit,autoBackup:()->Unit,autoBackupAt:Long?){
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
        item{Panel{Row{Stat(sessions.count{it.endedAt!=null}.toString(),"累计训练",Modifier.weight(1f));Stat(body.firstOrNull()?.let{kg(it.grams)}?:"—","最近体重 kg",Modifier.weight(1f))}}}
        item{Section("身体记录","＋ 记录",{a.body(null)})}
        if(body.isEmpty())item{EmptyPanel("从今天开始记录变化","记录体重和体脂率，数据保存在这台手机。")}
        items(body,key={it.id}){b->Panel{Row{Column(Modifier.weight(1f)){Text("${kg(b.grams)} kg",fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge);Text("${b.date}  ${b.fatPercent?.let{"体脂 ${number(it)}%"}?:""}",color=Muted,style=MaterialTheme.typography.bodySmall)};TextButton(onClick={a.body(b)}){Text("编辑")};TextButton(onClick={a.ask("删除此身体记录？"){vm.act{vm.repo.deleteBody(b.id)}}}){Text("删除",color=Muted)}}}}
        item{Section("训练设置")}
        item{Panel{Row{Column(Modifier.weight(1f)){Text("默认组间休息",fontWeight=FontWeight.SemiBold);Text("${rest} 秒",color=Muted)};TextButton(onClick={a.auxiliary("rest")}){Text("修改")}};Text("组间休息与 HIIT 计时会显示通知，锁屏或切到后台也能看到。",style=MaterialTheme.typography.bodySmall,color=Muted)}}
        item{Section("数据与备份")}
        item{Panel{
            Button(onClick=export,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("导出全部数据")}
            OutlinedButton(onClick=import,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("导入备份（v1 / v2）")}
            Row{TextButton(onClick=recovery){Text("导入前备份")};TextButton(onClick=autoBackup){Text("自动备份")}}
            Text(if(autoBackupAt==null)"每日首次打开应用时自动备份一次（最多 8 MiB）。" else "每日自动备份：上次 ${stamp(autoBackupAt)}。",style=MaterialTheme.typography.bodySmall,color=Muted)
            Text("导入前会自动保留恢复快照；备份格式 duolian-native v2。",style=MaterialTheme.typography.bodySmall,color=Muted)
        }}
        item{Text("独练 0.4 开发版 · 本地训练手记",Modifier.padding(vertical=10.dp),style=MaterialTheme.typography.labelSmall,color=Muted)}
    }
}
