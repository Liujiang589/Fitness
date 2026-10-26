package app.duolian.local

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.serialization.encodeToString
import java.time.LocalDate

class MainActivity : ComponentActivity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(0xFFF5F5F7.toInt(), 0xFFF5F5F7.toInt()),
            navigationBarStyle = SystemBarStyle.light(0xFFF5F5F7.toInt(), 0xFFF5F5F7.toInt()))
        setContent{DuolianTheme { DuolianScreen(openOutdoor = intent.getBooleanExtra("open_outdoor", false)) }}
    }
}
private data class Confirmation(val title:String,val action:()->Unit)
private val EditedPlanSaver = listSaver<Plan?, String>(
    save = { plan -> plan?.let { listOf(Codec.encodeToString(it)) } ?: emptyList() },
    restore = { saved -> saved.firstOrNull()?.let { Codec.decodeFromString<Plan>(it) } })

@Composable private fun DuolianScreen(vm:MainViewModel=viewModel(), openOutdoor: Boolean = false){
    val exercises by vm.exercises.collectAsStateWithLifecycle();val plans by vm.plans.collectAsStateWithLifecycle()
    val sessions by vm.sessions.collectAsStateWithLifecycle();val body by vm.body.collectAsStateWithLifecycle()
    val food by vm.food.collectAsStateWithLifecycle();val water by vm.water.collectAsStateWithLifecycle()
    val rest by vm.rest.collectAsStateWithLifecycle();val ready by vm.ready.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle();val message by vm.message.collectAsStateWithLifecycle()
    val pending by vm.pendingImport.collectAsStateWithLifecycle()
    val hiitRuns by vm.hiitRuns.collectAsStateWithLifecycle()
    val autoBackupAt by vm.autoBackupAt.collectAsStateWithLifecycle()
    val updater: AppUpdateViewModel = viewModel()
    val updateState by updater.state.collectAsStateWithLifecycle()
    val outdoorBlocksPrompt by remember { app.duolian.local.outdoor.OutdoorService.live
        .map { it.run != null || it.busy }.distinctUntilChanged() }.collectAsStateWithLifecycle(false)
    val context = LocalContext.current
    val notifyPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            notifyPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    var tab by rememberSaveable{mutableIntStateOf(0)};var nutrition by rememberSaveable{mutableStateOf(false)}
    var exerciseForm by remember{mutableStateOf(false)};var editExercise by remember{mutableStateOf<Exercise?>(null)}
    var planForm by rememberSaveable{mutableStateOf(false)}
    var editPlan by rememberSaveable(stateSaver = EditedPlanSaver){mutableStateOf<Plan?>(null)}
    var detail by remember{mutableStateOf<Exercise?>(null)};var auxiliary by remember{mutableStateOf<String?>(if(openOutdoor) "outdoor" else null)}
    var editBody by remember{mutableStateOf<Body?>(null)};var editFood by remember{mutableStateOf<Food?>(null)}
    var confirm by remember{mutableStateOf<Confirmation?>(null)}
    val ask:(String,()->Unit)->Unit={title,action->confirm=Confirmation(title,action)}
    val snackbar=remember{SnackbarHostState()}
    LaunchedEffect(message){message?.let{snackbar.showSnackbar(it);vm.message.value=null}}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::export)}
    val recovery=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::exportRecovery)}
    val import=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){it?.let(vm::inspectImport)}
    val auto=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){it?.let(vm::exportAutoBackup)}
    val a=ScreenActions(
        plan={editPlan=it;planForm=true},exercise={editExercise=it;exerciseForm=true},detail={detail=it},
        auxiliary={auxiliary=it;editFood=null;editBody=null},body={editBody=it;auxiliary="body"},
        food={editFood=it;auxiliary="food"},ask=ask,nutrition={tab=0;nutrition=true})
    var quickMenu by remember { mutableStateOf(false) }
    BackHandler(enabled = tab == 0 && nutrition) { nutrition = false }
    Scaffold(modifier = Modifier.imePadding(), containerColor = Page, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
        PremiumNavigation(tab) { tab = it; if (it == 0) nutrition = false }
    }) { padding -> Column(Modifier.fillMaxSize().padding(padding)) {
        Box {
            PremiumHeader(tab, nutrition, { nutrition = !nutrition }, { quickMenu = true })
            DropdownMenu(expanded = quickMenu, onDismissRequest = { quickMenu = false }) {
                DropdownMenuItem(text = { Text("记录饮食") }, onClick = { quickMenu = false; tab = 0; nutrition = true })
                DropdownMenuItem(text = { Text("记录饮水") }, onClick = { quickMenu = false; a.auxiliary("water:${LocalDate.now()}") })
                DropdownMenuItem(text = { Text("记录体重") }, onClick = { quickMenu = false; a.body(null) })
                DropdownMenuItem(text = { Text("创建计划") }, onClick = { quickMenu = false; a.plan(null) })
                DropdownMenuItem(text = { Text("步行与跑步") }, onClick = { quickMenu = false; a.auxiliary("outdoor") })
            }
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
            else->ProfilePage(body,sessions,rest,busy,vm,a,{export.launch("独练-${LocalDate.now()}.json")},{import.launch(arrayOf("application/json","text/plain","application/octet-stream"))},{recovery.launch("独练-导入前备份.json")},{auto.launch("独练-自动备份-${LocalDate.now()}.json")},autoBackupAt,updateState){if(updateState.downloading || updateState.file!=null)updater.open() else updater.check(true)}
        }
    }}
    AppUpdateHost(updater, updateState, canPrompt = ready && sessions.none { it.endedAt == null } &&
        !outdoorBlocksPrompt && !exerciseForm && !planForm && detail == null && auxiliary == null && confirm == null && pending == null)
    if(exerciseForm)ExerciseForm(editExercise,{exerciseForm=false}){x->vm.act{vm.repo.saveExercise(x)}}
    if(planForm)PlanForm(editPlan,exercises,{planForm=false}){x->vm.savePlanEdits(x)}
    detail?.let { e -> ExerciseGuideDialog(e,busy,
        dismiss={detail=null},
        edit={detail=null;a.exercise(e)},
        favorite={val updated=e.copy(favorite=!e.favorite);detail=updated;vm.act{vm.repo.saveExercise(updated)}},
        delete={detail=null;ask("删除「${e.name}」？计划引用的动作需先从计划移除。") {vm.act{vm.repo.deleteExercise(e.id)}}},
        readOnly=sessions.any { it.endedAt == null }) }
    confirm?.let{c->AlertDialog(onDismissRequest={confirm=null},title={Text(c.title)},confirmButton={TextButton(enabled=!busy,onClick={c.action();confirm=null}){Text("确认")}},dismissButton={TextButton(onClick={confirm=null}){Text("取消")}})}
    pending?.let{b->AlertDialog(onDismissRequest={vm.pendingImport.value=null},title={Text("确认替换全部本地数据？")},text={Text("${b.exercises.size} 个动作、${b.plans.size} 份计划、${b.sessions.size} 场训练。现有数据会先保存为恢复备份。")},confirmButton={TextButton(enabled=!busy,onClick=vm::confirmImport){Text("备份并替换")}},dismissButton={TextButton(onClick={vm.pendingImport.value=null}){Text("取消")}})}
    val kind=auxiliary?.substringBefore(':');val auxDate=auxiliary?.split(':')?.let{if(it.size>2)it[2] else if(kind=="water"&&it.size>1)it[1] else LocalDate.now().toString()}?:LocalDate.now().toString()
    when(kind){
        "outdoor" -> OutdoorPage({ auxiliary = null })
        "rest"->FieldsDialog("组间休息",listOf("秒（0–600）" to rest.toString()),{auxiliary=null}){v->val seconds=v[0].toInt();require(seconds in 0..600){"休息需为0–600秒"};vm.setRest(seconds)}
        "body"->FieldsDialog(if(editBody==null)"记录身体数据" else "编辑身体记录",listOf("日期" to(editBody?.date?:auxDate),"体重 kg" to(editBody?.let{kg(it.grams)}?:""),"体脂 %（可空）" to(editBody?.fatPercent?.toString()?:"")),{auxiliary=null}){v->val b=Body(id=editBody?.id?:newId(),date=v[0],grams=Rules.parseKg(v[1]),fatPercent=v[2].takeIf{it.isNotBlank()}?.toDouble());Rules.body(b);vm.act{vm.repo.saveBody(b)}}
        "food","meal" -> FoodEntryDialog(editFood, auxDate,
            if (kind == "meal") auxiliary!!.split(':')[1] else "早餐", food, busy,
            dismiss = { auxiliary = null }, save = { entry -> vm.act { vm.repo.saveFood(entry); auxiliary = null } })
        "water"->FieldsDialog("记录饮水",listOf("日期" to auxDate,"饮水 ml" to "250"),{auxiliary=null}){v->val w=Water(date=v[0],ml=v[1].toInt());Rules.water(w);vm.act{vm.repo.saveWater(w)}}
    }
}

@Composable private fun ProfilePage(body:List<Body>,sessions:List<Session>,rest:Int,busy:Boolean,vm:MainViewModel,a:ScreenActions,export:()->Unit,import:()->Unit,recovery:()->Unit,autoBackup:()->Unit,autoBackupAt:Long?,updateState:AppUpdateState,checkUpdate:()->Unit){
    val finished = sessions.filter{it.endedAt!=null}
    LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(start=18.dp,end=18.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(15.dp)){
        item{Panel{
            Row(verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(56.dp).clip(androidx.compose.foundation.shape.CircleShape).background(PaleBlue),
                    contentAlignment=Alignment.Center){
                    Text("独",color=Blue,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                }
                Column(Modifier.weight(1f).padding(start=14.dp)){
                    Text("独练者",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleLarge,color=Ink)
                    Text("专注每一次，成为更好的自己",color=Muted,style=MaterialTheme.typography.bodySmall)
                }
            }
        }}
        item { Panel { DetailDisclosure("训练积累") {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                StatTile(finished.size.toString(),"累计训练",Modifier.weight(1f),accent=Blue)
                StatTile(number(Analytics.totalVolume(finished)),"累计容量 kg",Modifier.weight(1f))
                StatTile(Analytics.streak(finished).toString(),"连续天数",Modifier.weight(1f))
            }
        } } }
        item{Section("身体记录","＋ 记录",{a.body(null)})}
        if(body.isEmpty())item{EmptyPanel("从今天开始记录变化","记录体重和体脂率，数据保存在这台手机。")}
        items(body,key={it.id}){b->Panel{Row{Column(Modifier.weight(1f)){Text("${kg(b.grams)} kg",fontWeight=FontWeight.SemiBold,style=MaterialTheme.typography.titleLarge);Text("${b.date}  ${b.fatPercent?.let{"体脂 ${number(it)}%"}?:""}",color=Muted,style=MaterialTheme.typography.bodySmall)};TextButton(onClick={a.body(b)}){Text("编辑")};TextButton(onClick={a.ask("删除此身体记录？"){vm.act{vm.repo.deleteBody(b.id)}}}){Text("删除",color=Muted)}}}}
        item{Section("训练设置")}
        item{GroupCard{
            IconSettingRow(1,Blue,"默认组间休息","组间与 HIIT 计时会显示在通知栏",trailing="${rest} 秒"){a.auxiliary("rest")}
        }}
        item{Section("数据与备份")}
        item{GroupCard{
            IconSettingRow(0,Color(0xFF2C8C63),"导出全部数据","duolian-native v4 · 含运动位置",enabled=!busy,onClick=export)
            HorizontalDivider(color=Page)
            IconSettingRow(0,Blue,"导入备份","支持 v1–v4，导入前自动留快照",enabled=!busy,onClick=import)
            HorizontalDivider(color=Page)
            IconSettingRow(0,Color(0xFFE59B42),"导入前备份","最近一次导入前的恢复文件",onClick=recovery)
            HorizontalDivider(color=Page)
            IconSettingRow(0,Color(0xFF7C5CE0),"自动备份",
                if(autoBackupAt==null)"每日首次打开自动备份（上限 32 MiB，含位置）" else "上次 ${stamp(autoBackupAt)}",
                onClick=autoBackup)
        }}
        item{Section("关于")}
        item{GroupCard{
            IconSettingRow(8,Muted,"独练 · 本地训练手记","训练数据保存在本机，无需账号",
                trailing="V${BuildConfig.VERSION_NAME}")
            HorizontalDivider(color=Page)
            IconSettingRow(8,Blue,"检查更新",when {
                updateState.downloading -> "更新正在下载，点击查看进度"
                updateState.file!=null -> "新版已下载，点击安装"
                updateState.checking -> "正在检查新版本…"
                else -> "联网检查新版，训练记录保留在本机"
            },onClick=checkUpdate)
            HorizontalDivider(color=Page)
            CrashReportEntry()
        }}
        item { AuthorContactCard() }
    }
}
