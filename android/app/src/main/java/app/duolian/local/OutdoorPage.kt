package app.duolian.local

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.duolian.local.domain.*
import app.duolian.local.outdoor.OutdoorService
import app.duolian.local.outdoor.OutdoorShell
import app.duolian.local.outdoor.shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import kotlin.math.*

class OutdoorViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as DuolianApp).outdoor
    val ready = MutableStateFlow(false)
    val settlingRunId = MutableStateFlow<String?>(null)
    val runs = store.runs.onEach { rows ->
        ready.value = true
        settlingRunId.value?.let { id ->
            if (rows.none { it.id == id && it.status != OutdoorStatus.FINISHED }) settlingRunId.value = null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val days = store.days.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val message = MutableStateFlow<String?>(null)
    val notice = MutableStateFlow<String?>(null)
    val working = MutableStateFlow(false)
    val completedRunId = MutableStateFlow<String?>(null)
    fun cancelPreparation() = viewModelScope.launch {
        // A back press can race the command acknowledgement. Cancel after that command settles.
        working.first { !it }
        command(OutdoorService.CANCEL_PREPARE)
    }
    fun command(action: String, expectedRunId: String? = null, onSuccess: () -> Unit = {}) {
        if (working.value) return
        working.value = true; message.value = null; notice.value = null
        viewModelScope.launch {
            try {
                val id = android.os.SystemClock.elapsedRealtimeNanos()
                val finishingId = if (action == OutdoorService.FINISH) expectedRunId ?:
                    OutdoorService.live.value.run?.id ?: runs.value.firstOrNull { it.status != OutdoorStatus.FINISHED }?.id else null
                OutdoorService.send(getApplication(), action, id, expectedRunId)
                val result = withTimeout(10000) { OutdoorService.live.first { it.commandId == id && !it.busy } }
                result.error?.let { message.value = it }
                if (result.error == null && action in listOf(OutdoorService.FINISH, OutdoorService.DISCARD)) {
                    settlingRunId.value = (finishingId ?: expectedRunId)?.takeIf { runId ->
                        runs.value.any { it.id == runId && it.status != OutdoorStatus.FINISHED }
                    }
                }
                if (result.error == null && finishingId != null) completedRunId.value = finishingId
                if (result.error == null && action == OutdoorService.DISCARD) {
                    completedRunId.value = null
                    notice.value = "本次运动已放弃，未保存到运动足迹；当天已采集的步数保留。"
                }
                if (result.error == null && action in listOf(OutdoorService.WALK, OutdoorService.RUN))
                    completedRunId.value = null
                if(result.error==null)onSuccess()
            } catch (_: Exception) { message.value = "操作未确认完成，请检查当前记录状态后重试" }
            finally { working.value = false }
        }
    }
    fun delete(run: OutdoorRun) = viewModelScope.launch {
        message.value = null; notice.value = null
        runCatching { require(run.status == OutdoorStatus.FINISHED); store.delete(run.id) }
            .onSuccess {
                if (completedRunId.value == run.id) completedRunId.value = null
                notice.value = "记录已删除"
            }
            .onFailure { message.value = "删除失败，请重试" }
    }
    fun export(id: String, uri: Uri) = viewModelScope.launch {
        message.value = null; notice.value = null
        runCatching {
            val run = runs.value.firstOrNull { it.id == id } ?: error("记录不存在")
            withContext(Dispatchers.IO) {
                val output = getApplication<Application>().contentResolver.openOutputStream(uri, "wt")
                    ?: error("无法写入文件")
                output.bufferedWriter().use { it.write(OutdoorMath.gpx(run)) }
            }
            notice.value = "路线已导出为 GPX（包含精确位置）"
        }.onFailure { message.value = it.message ?: "导出失败" }
    }
}

@Composable internal fun OutdoorEntry(open: () -> Unit, vm: OutdoorViewModel = viewModel()) {
    val days by vm.days.collectAsStateWithLifecycle()
    val saved by remember(vm) { vm.runs.map { runs -> runs.firstOrNull { it.status != OutdoorStatus.FINISHED }
        ?.let { it.kind to it.status } }.distinctUntilChanged() }.collectAsStateWithLifecycle(null)
    val live by remember { OutdoorService.live.map { state -> state.run?.let { it.kind to it.status } }
        .distinctUntilChanged() }.collectAsStateWithLifecycle(null)
    val unfinished = live ?: saved
    val today = rememberToday()
    Surface(onClick = open, color = Color.White, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text("步行与跑步", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (unfinished != null) "${unfinished!!.first.label}${if (live?.second == OutdoorStatus.ACTIVE) "记录中" else "待继续"} · 点击查看" else
                    (days.firstOrNull { it.date == today }?.steps?.takeIf { it > 0 }?.let { "今天已采集 $it 步 · 查看运动足迹" }
                        ?: "记录路线、距离与配速 · 按需开启"), color = Muted,
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
            }
            Text("›", color = Blue, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable private fun rememberToday(): String {
    var today by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) { while (true) { today = LocalDate.now().toString(); delay(10000) } }
    return today
}

@Composable internal fun OutdoorPage(dismiss: () -> Unit, vm: OutdoorViewModel = viewModel()) {
    val context = LocalContext.current
    val runs by vm.runs.collectAsStateWithLifecycle()
    val days by vm.days.collectAsStateWithLifecycle()
    val live by remember { OutdoorService.live.map { it.shell() }.distinctUntilChanged() }
        .collectAsStateWithLifecycle(OutdoorShell())
    val message by vm.message.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val working by vm.working.collectAsStateWithLifecycle()
    val completedId by vm.completedRunId.collectAsStateWithLifecycle()
    val settlingId by vm.settlingRunId.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val completed = runs.firstOrNull { it.id == completedId && it.status == OutdoorStatus.FINISHED }
    val today = rememberToday()
    val active = live.session?.let { head -> runs.firstOrNull { it.id==head.id } ?: head.fallback() }
        ?: runs.firstOrNull { it.status != OutdoorStatus.FINISHED && it.id != settlingId }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingAction by rememberSaveable { mutableStateOf<String?>(null) }
    var consent by remember { mutableStateOf<String?>(null) }
    var remove by remember { mutableStateOf<OutdoorRun?>(null) }
    var exit by rememberSaveable(active?.id) { mutableStateOf<WorkoutExit?>(null) }
    var exportId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedKind by rememberSaveable { mutableStateOf(OutdoorKind.WALK) }
    var preparationRequested by rememberSaveable { mutableStateOf(false) }
    val consentPreferences=remember(context){context.getSharedPreferences("outdoor-consent",0)}
    val locationActions=setOf(OutdoorService.WALK,OutdoorService.RUN,OutdoorService.RESUME,
        OutdoorService.PREPARE_WALK,OutdoorService.PREPARE_RUN)
    fun leave() {
        if(live.preparingKind!=null || preparationRequested)vm.cancelPreparation()
        dismiss()
    }
    LaunchedEffect(completed?.id) { if (completed != null) listState.animateScrollToItem(0) }
    LaunchedEffect(active?.id) { listState.scrollToItem(0) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) exportId?.let { vm.export(it, uri) }; exportId = null
    }
    fun perform(action: String) {
        val locationAction = action in locationActions
        if (locationAction && !OutdoorService.canLocate(context)) vm.message.value = "需要精确定位才能记录路线。可在手机设置中授权后重试。"
        else if (action == OutdoorService.DAILY_ON && !OutdoorService.canCount(context)) vm.message.value = "未获得身体活动权限，暂时无法计步。"
        else {
            if(action==OutdoorService.PREPARE_WALK || action==OutdoorService.PREPARE_RUN)preparationRequested=true
            vm.command(action)
        }
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        pendingAction?.let { perform(it) }; pendingAction = null
    }
    fun request(action: String) {
        val needed = buildList {
            if (action in locationActions && !OutdoorService.canLocate(context)) {
                add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= 29 && OutdoorService.hasCounter(context) && !OutdoorService.canCount(context))
                add(Manifest.permission.ACTIVITY_RECOGNITION)
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isEmpty()) perform(action) else { pendingAction = action; permissions.launch(needed.toTypedArray()) }
    }
    Dialog(onDismissRequest = ::leave, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = Page, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = ::leave) { Text(if (active == null) "‹ 返回" else "‹ 收起") }
                    Text("步行与跑步", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(if(live.preparingKind!=null)"准备出发" else if (active == null) "户外运动" else if (live.session?.status == OutdoorStatus.ACTIVE) "记录中" else "已暂停",
                        color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                if(active!=null) {
                    (message ?: live.error)?.let { Text(it,Modifier.padding(horizontal=20.dp),color=MaterialTheme.colorScheme.error) }
                    OutdoorLiveWorkout(active,Modifier.weight(1f))
                } else if(live.preparingKind!=null) {
                    OutdoorPreparationScreen(working || live.busy,Modifier.weight(1f),
                        start={vm.command(if(live.preparingKind==OutdoorKind.RUN)OutdoorService.RUN else OutdoorService.WALK)},
                        cancel={vm.cancelPreparation()})
                } else {
                LazyColumn(Modifier.weight(1f), state = listState, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (completed != null) item { Panel(color = SuccessTint) {
                        Text("这一程，已为你保存", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("完成了 %.2f 公里${completed.kind.label} · %s".format(completed.distanceMeters / 1000, clock(completed.elapsedMs)))
                        RouteSketch(completed.points)
                        if (completed.points.isEmpty()) Text("本次未采集到可靠路线，仅保存了时长与已采集步数。", color = Muted)
                        TextButton(onClick = { vm.completedRunId.value = null }) { Text("收起本次总结") }
                    } }
                    item { Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("户外运动", color = Ink, style = MaterialTheme.typography.titleLarge)
                                Text("按自己的节奏出发。", color = Muted, modifier = Modifier.padding(top = 6.dp))
                            }
                            OutdoorEmblem()
                        }
                    } }
                    if (!ready) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                    notice?.let { text -> item { Panel(color = SuccessTint) {
                        Text(text, color = Ink)
                        TextButton(onClick = { vm.notice.value = null }) { Text("收起提示") }
                    } } }
                    (message ?: live.error)?.let { text -> item { Panel {
                        Text(text, color = MaterialTheme.colorScheme.error)
                        DetailDisclosure("检查权限与定位设置") {
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${context.packageName}"))) }) { Text("应用权限设置") }
                            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("手机定位设置") }
                        }
                    } } }
                    item { Panel {
                        Text("今天，想怎么出发？",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            OutdoorKind.entries.forEach { kind ->
                                FilterChip(selected=selectedKind==kind,onClick={selectedKind=kind},
                                    label={Text(kind.label)},modifier=Modifier.weight(1f),enabled=!working)
                            }
                        }
                        Text(if(selectedKind==OutdoorKind.RUN)"找回自己的节奏，专注这一程。" else "轻松走一走，让每一步都有迹可循。",color=Muted)
                        Button(enabled=ready && !working,onClick={
                            val action=if(selectedKind==OutdoorKind.RUN)OutdoorService.PREPARE_RUN else OutdoorService.PREPARE_WALK
                            if(consentPreferences.getBoolean("gps-v1",false))request(action) else consent=action
                        },modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)) { Text("准备${selectedKind.label}") }
                        Text("先等 GPS 就绪，再倒数出发。准备期间不计时、不生成运动记录。",color=Muted,
                            style=MaterialTheme.typography.bodySmall)
                    } }
                    item { Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("当天已采集步数", fontWeight = FontWeight.SemiBold)
                                Text("${days.firstOrNull { it.date == today }?.steps ?: 0} 步", color = Ink,
                                    style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                                Text(if (live.daily) if (!live.stepReceived) "已开启 · 等待传感器首条读数" else
                                    "传感器已接入 · 可能延迟数秒成批更新" else "按需开启，日常计步不使用 GPS", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Switch(checked = live.daily, onCheckedChange = { enabled ->
                                if (enabled) consent = OutdoorService.DAILY_ON else vm.command(OutdoorService.DAILY_OFF)
                            }, enabled = ready && !working && OutdoorService.hasCounter(context))
                        }
                        Text(if (!OutdoorService.hasCounter(context)) "此手机没有计步传感器；仍可使用 GPS 记录步行和跑步。" else
                            "这里是独练开启期间已采集的当天步数，不等于手机健康应用的全天总步数。手机重启、强制停止或省电清理造成的缺失不补算。", color = Muted, style = MaterialTheme.typography.bodySmall)
                        DetailDisclosure("近 7 天步数") {
                            (0L..6L).forEach { offset ->
                                val date = LocalDate.parse(today).minusDays(offset).toString()
                                val day = days.firstOrNull { it.date == date }
                                Row(Modifier.fillMaxWidth()) {
                                    Text(date, Modifier.weight(1f), color = Muted)
                                    Text(day?.let { "${it.steps} 步" } ?: "未采集")
                                }
                            }
                        }
                    } }
                    item { Section("运动足迹") }
                    val history = runs.filter { it.status == OutdoorStatus.FINISHED }
                    if (ready && history.isEmpty()) item { EmptyPanel("第一段旅程，从现在开始", "选择保存的步行或跑步会出现在这里，放弃的记录不会保留。") }
                    items(history, key = { it.id }) { r -> Panel {
                        Text("${r.kind.label} · ${stamp(r.startedAt)}", fontWeight = FontWeight.SemiBold)
                        Text("%.2f km · %s".format(r.distanceMeters / 1000, clock(r.elapsedMs)), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { selected = if (selected == r.id) null else r.id }) {
                            Text(if (selected == r.id) "收起路线与详情" else "查看路线与详情")
                        }
                        if (selected == r.id) {
                            StreetRouteMap(r.points, tracking = false)
                            Text("平均速度 ${OutdoorSpeed.averageKmh(r)?.let { "%.1f km/h".format(it) } ?: "—"}", color = Muted)
                            Text("平均配速 ${OutdoorMath.pace(r)} · " + if (r.stepCounting) "已采集 ${r.steps} 步" else "本次未启用步数采集", color = Muted)
                            DetailDisclosure("位置坐标与记录口径") {
                                r.points.firstOrNull()?.let { p -> Text("起点 %.5f, %.5f".format(p.latitude, p.longitude), color = Muted) }
                                r.points.lastOrNull()?.let { p -> Text("终点 %.5f, %.5f".format(p.latitude, p.longitude), color = Muted) }
                                Text("距离依据可靠定位点估算；弱信号和暂停的缺失路段不补算。", color = Muted)
                            }
                            Row {
                                TextButton(enabled = r.points.isNotEmpty(), onClick = {
                                    exportId = r.id; exporter.launch("独练-${r.kind.label}-${r.startedAt}.gpx")
                                }) { Text("导出 GPX 路线") }
                                TextButton(onClick = { remove = r }) { Text("删除记录", color = Muted) }
                            }
                        }
                    } }
                    item { Text("运动定位在你开始后采集，暂停时停止。轨迹记录保存在本机；街道地图由高德联网提供，开启前可查看隐私说明。GPX 和完整备份包含位置，请自行选择分享对象。", color = Muted, style = MaterialTheme.typography.bodySmall) }
                }
                }
                active?.let { r ->
                    val recording = live.running && live.session?.status == OutdoorStatus.ACTIVE
                    WorkoutControls(recording, working || live.busy, toggle = {
                        if (recording) vm.command(OutdoorService.PAUSE) else request(OutdoorService.RESUME)
                    }, finish = {
                        if(recording)vm.command(OutdoorService.PAUSE,r.id){exit=WorkoutExit.SAVE} else exit=WorkoutExit.SAVE
                    }, discard = {
                        if(recording)vm.command(OutdoorService.PAUSE,r.id){exit=WorkoutExit.DISCARD} else exit=WorkoutExit.DISCARD
                    })
                }
            }
        }
        consent?.let { action -> AlertDialog(onDismissRequest = { consent = null },
            title = { Text(if (action == OutdoorService.DAILY_ON) "开启日常计步" else "开启运动路线记录") },
            text = { Text(if (action == OutdoorService.DAILY_ON) "使用身体活动权限和手机计步传感器统计步数，在通知栏持续提示。不会开启 GPS，可随时关闭。" else
                "准备时先使用精确定位等待 GPS，不计时、不保存路线。确认开始后记录运动，锁屏或切换应用时通过持续通知继续；取消准备、暂停或结束后停止运动定位。轨迹保存在本机。") },
            confirmButton = { TextButton(onClick = { consent = null; vm.message.value = null
                if(action!=OutdoorService.DAILY_ON)consentPreferences.edit().putBoolean("gps-v1",true).apply()
                request(action) }) { Text("同意并开始") } },
            dismissButton = { TextButton(onClick = { consent = null }) { Text("取消") } }) }
        if (exit != null && active != null) WorkoutExitDialog(active, exit!!,
            recording = live.running && live.session?.status == OutdoorStatus.ACTIVE, busy = working || live.busy,
            dismiss = { exit = null }, confirm = {
                vm.command(if (exit == WorkoutExit.DISCARD) OutdoorService.DISCARD else OutdoorService.FINISH, active.id)
                exit = null
            })
        remove?.let { r -> AlertDialog(onDismissRequest = { remove = null }, title = { Text("删除这次运动和位置轨迹？") },
            confirmButton = { TextButton(onClick = { vm.delete(r); remove = null }) { Text("删除") } },
            dismissButton = { TextButton(onClick = { remove = null }) { Text("取消") } }) }
    }
}

@Composable private fun OutdoorEmblem() {
    Canvas(Modifier.size(74.dp).semantics { contentDescription = "户外路线装饰图" }) {
        drawCircle(PaleBlue, style = Stroke(7.dp.toPx()))
        val path = Path().apply {
            moveTo(center.x - 14.dp.toPx(), center.y + 15.dp.toPx())
            cubicTo(center.x + 20.dp.toPx(), center.y + 10.dp.toPx(), center.x - 20.dp.toPx(), center.y - 14.dp.toPx(), center.x + 14.dp.toPx(), center.y - 15.dp.toPx())
        }
        drawPath(path, Blue, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Blue, 4.dp.toPx(), center + Offset(-14.dp.toPx(), 15.dp.toPx()))
        drawCircle(Blue, 4.dp.toPx(), center + Offset(14.dp.toPx(), -15.dp.toPx()))
    }
}
