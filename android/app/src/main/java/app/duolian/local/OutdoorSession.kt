package app.duolian.local

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.duolian.local.domain.*
import app.duolian.local.outdoor.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun OutdoorLiveWorkout(fallback: OutdoorRun,modifier: Modifier=Modifier,
    source: StateFlow<OutdoorLive> = OutdoorService.live,
    mapRenderer: @Composable (OutdoorMapFrame,Modifier)->Unit = { frame,area ->
        StreetRouteMap(frame.points,frame.tracking,frame.position,area,fillHeight=true,currentIsGcj02=frame.positionGcj02)
    }) {
    var settings by rememberSaveable(fallback.id){mutableStateOf(false)}
    var keepScreen by rememberSaveable{mutableStateOf(false)}
    val recording by remember(fallback.id,source){source.map {
        it.running && it.run?.id==fallback.id && it.run.status==OutdoorStatus.ACTIVE
    }.distinctUntilChanged()}.collectAsStateWithLifecycle(false)
    val view=LocalView.current
    DisposableEffect(view,keepScreen,recording) {
        val previous=view.keepScreenOn;view.keepScreenOn=keepScreen && recording
        onDispose{view.keepScreenOn=previous}
    }
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact=maxHeight<420.dp || LocalDensity.current.fontScale >= 1.5f
        Column(if(compact)Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier.fillMaxSize(),
            verticalArrangement=Arrangement.spacedBy(8.dp)) {
            LiveSignal(fallback,source){settings=true}
            LiveDashboard(fallback,source)
            LiveMap(fallback,source,mapRenderer,if(compact)Modifier.fillMaxWidth().height(300.dp)
                else Modifier.fillMaxWidth().weight(1f))
        }
    }
    if(settings)ModalBottomSheet(onDismissRequest={settings=false},containerColor=Color.White) {
        val live by source.collectAsStateWithLifecycle()
        val run=live.run?.takeIf{it.id==fallback.id} ?: fallback
        val context=LocalContext.current
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=24.dp)
            .padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("运动设置",style=MaterialTheme.typography.titleLarge)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("保持亮屏",fontWeight=FontWeight.SemiBold)
                    Text("仅运动页记录时生效",color=Muted,style=MaterialTheme.typography.bodySmall)
                }
                Switch(keepScreen,{keepScreen=it})
            }
            HorizontalDivider(color=Hairline)
            Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                Stat(OutdoorMath.pace(run),"平均配速",Modifier.weight(1f))
                Stat(if(run.stepCounting)"${run.steps}" else "—","本次步数",Modifier.weight(1f))
            }
            Text(if(live.run?.id==fallback.id)live.signal.detail else OutdoorSignal.INTERRUPTED.detail,color=Muted)
            live.accuracy?.let{Text("定位精度约 ${it.toInt()} 米",color=Muted)}
            live.place?.let { hint -> Text("${hint.source} · 位置误差估计约 ${hint.point.accuracy.toInt()} 米",
                color=Muted,style=MaterialTheme.typography.bodySmall) }
            live.locationNote?.let { Text(it,color=Muted,style=MaterialTheme.typography.bodySmall) }
            DetailDisclosure("定位、步数与数据说明") {
                Text("距离来自可靠 GPS 采样。弱信号和暂停造成的缺失路段不补画直线，也不猜测距离。",color=Muted)
                Text("配速会短时平滑；定位失效时显示—。时长不包含暂停或进程中断时间。",color=Muted)
                Text("本次步数与日常步数分开，传感器可能成批上报；日常步数是独练已采集的部分。",color=Muted)
                Text("已跳过 ${live.filteredFixes} 个不可靠定位。",color=Muted)
            }
            DetailDisclosure("权限与锁屏记录") {
                Text("运动通过持续通知记录；系统省电、强制停止或后台清理可能造成中断。",color=Muted)
                TextButton(onClick={context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}")))}){Text("应用权限设置")}
                TextButton(onClick={context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))}){Text("手机定位设置")}
                TextButton(onClick={runCatching{context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))}}){Text("系统电池设置")}
            }
            TextButton(onClick={settings=false},modifier=Modifier.fillMaxWidth()){Text("返回运动")}
        }
    }
}

@Composable private fun LiveSignal(fallback: OutdoorRun,source: StateFlow<OutdoorLive>,settings: ()->Unit) {
    val signal by remember(fallback.id,source){source.map {
        it.displaySignal(fallback.id)
    }.distinctUntilChanged()}.collectAsStateWithLifecycle(OutdoorSignal.INTERRUPTED)
    Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(if(signal==OutdoorSignal.GOOD)Color(0xFF32A66A) else Muted,CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(signal.title,Modifier.weight(1f),color=Muted,style=MaterialTheme.typography.labelMedium)
        TextButton(onClick=settings){Text("运动设置")}
    }
}

@Composable private fun LiveDashboard(fallback: OutdoorRun,source: StateFlow<OutdoorLive>) {
    val metrics by remember(fallback.id,source){source.map { live ->
        live.run?.takeIf{it.id==fallback.id}?.let{live.metrics(it)}
    }.distinctUntilChanged()}.collectAsStateWithLifecycle(null)
    WorkoutDashboard(fallback.kind,metrics ?: OutdoorMetrics(fallback.distanceMeters,fallback.steps,fallback.stepCounting,null)) {
        LiveClock(fallback,source)
    }
}

@Composable internal fun WorkoutDashboard(kind: OutdoorKind,metrics: OutdoorMetrics,clockContent: @Composable ()->Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=4.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutdoorDistance(metrics.distance)
        val metricWidth = maxOf(128.dp, textWidth("00:00:00", MaterialTheme.typography.headlineMedium),
            textWidth("当前速度 公里/时", MaterialTheme.typography.labelMedium))
        ResponsiveTiles(metricWidth, 2) { index, area ->
            if (index == 0) Column(area,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                clockContent();Text("运动时长",color=Muted,style=MaterialTheme.typography.labelMedium)
            }
            else Column(area,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                val value=if(kind==OutdoorKind.RUN) {
                    val speed=metrics.speed
                    if(speed==null || speed<.3)"—" else {
                        val seconds=(1000/speed/5).roundToLong()*5
                        "%d′%02d″".format(seconds/60,seconds%60)
                    }
                } else metrics.speed?.let{"%.1f".format(it*3.6)} ?: "—"
                Text(value,color=Ink,style=MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings="tnum"))
                Text(if(kind==OutdoorKind.RUN)"当前配速 /公里" else "当前速度 公里/时",color=Muted,
                    style=MaterialTheme.typography.labelMedium)
            }
        }
        Text(if(metrics.stepCounting)"本次已采集 ${metrics.steps} 步" else "本次步数 · 等待传感器",
            color=Muted,style=MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun OutdoorDistance(meters: Double) {
    // Animate only towards a measured distance; no extrapolation or extra metres are recorded.
    val displayed by animateFloatAsState((meters/1000).toFloat(),tween(650),label="measured distance")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val style = MaterialTheme.typography.displayLarge.copy(fontWeight=FontWeight.SemiBold, fontFeatureSettings="tnum")
    val size = listOf(64, 52, 40, 32).firstOrNull { textWidth("888.88", style.copy(fontSize=it.sp)) <= maxWidth } ?: 32
    Column {
        Text("路线距离 · 公里",color=Muted,style=MaterialTheme.typography.labelMedium)
        Text("%.2f".format(displayed),color=Ink,fontSize=size.sp,lineHeight=(size+10).sp,
            style=style)
    }
    }
}

@Composable private fun LiveClock(fallback: OutdoorRun,source: StateFlow<OutdoorLive>) {
    val elapsed by remember(fallback.id,source){source.map {
        it.run?.takeIf{run->run.id==fallback.id}?.elapsedMs
    }.distinctUntilChanged()}.collectAsStateWithLifecycle(null)
    Text(clock(elapsed ?: fallback.elapsedMs),color=Ink,style=MaterialTheme.typography.headlineMedium.copy(fontFeatureSettings="tnum"))
}

@Composable private fun LiveMap(fallback: OutdoorRun,source: StateFlow<OutdoorLive>,
    renderer: @Composable (OutdoorMapFrame,Modifier)->Unit,modifier: Modifier) {
    val frame by remember(fallback.id,source){source.map { live ->
        live.run?.takeIf{it.id==fallback.id}?.let{live.mapFrame(it)}
    }.distinctUntilChanged()}.collectAsStateWithLifecycle(null)
    renderer(frame ?: OutdoorMapFrame(fallback.points,false,null),modifier.padding(horizontal=12.dp))
}

@Composable internal fun OutdoorPreparationScreen(busy: Boolean,modifier: Modifier,start: ()->Unit,cancel: ()->Unit) {
    val live by OutdoorService.live.collectAsStateWithLifecycle()
    val preparation=live.preparation ?: return
    val context=LocalContext.current
    OutdoorPreparationView(preparation.kind,preparation.startedTick,live.preparationReady,
        preparation.position?.accuracy,busy,modifier,start,cancel,allowUnreadyStart=true,
        place=live.place,locationNote=live.locationNote,
        canCountSteps=OutdoorService.canCount(context) && OutdoorService.hasCounter(context))
}

@Composable internal fun OutdoorPreparationView(kind: OutdoorKind,session: Long,ready: Boolean,accuracy: Float?,
    busy: Boolean,modifier: Modifier=Modifier,start: ()->Unit,cancel: ()->Unit,
    allowUnreadyStart: Boolean=false,place: OutdoorPlace?=null,locationNote: String?=null,
    canCountSteps: Boolean=true) {
    var counting by rememberSaveable(session){mutableStateOf(false)}
    var number by remember(session){mutableIntStateOf(3)}
    val currentReady by rememberUpdatedState(ready)
    val mayStartWithoutFix by rememberUpdatedState(allowUnreadyStart)
    val begin by rememberUpdatedState(start)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver{_,event->if(event==Lifecycle.Event.ON_PAUSE)counting=false}
        lifecycle.addObserver(observer);onDispose{lifecycle.removeObserver(observer)}
    }
    LaunchedEffect(counting) {
        if(counting) {
            for(i in 3 downTo 1) {number=i;delay(1000);if(!currentReady && !mayStartWithoutFix){counting=false;return@LaunchedEffect}}
            counting=false;begin()
        }
    }
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(22.dp)) {
        Spacer(Modifier.height(16.dp))
        Text("准备${kind.label}",style=MaterialTheme.typography.headlineLarge)
        Box(Modifier.size(154.dp).background(PaleBlue,CircleShape),contentAlignment=Alignment.Center) {
            if(counting)Text("$number",fontSize=88.sp,fontWeight=FontWeight.SemiBold,color=Blue)
            else if(ready)Text("GPS",fontSize=36.sp,fontWeight=FontWeight.SemiBold,color=Blue)
            else if(place!=null)Text("位置",fontSize=36.sp,fontWeight=FontWeight.SemiBold,color=Blue)
            else CircularProgressIndicator(Modifier.size(54.dp),color=Blue,strokeWidth=3.dp)
        }
        Text(when {counting->"准备出发";ready->"路线定位已就绪";place!=null->if(place.cached)"已显示最近位置" else "已找到当前位置";
            allowUnreadyStart->"正在定位，可先出发";else->"正在寻找 GPS"},style=MaterialTheme.typography.titleLarge)
        Text(when {ready->"位置误差估计约 ${accuracy?.toInt() ?: "—"} 米";place!=null->
            "${place.source} · 误差估计约 ${place.point.accuracy.toInt()} 米";
            else->"正在结合可用信号寻找位置。"},color=Muted)
        Text("准备期间不计时、不保存运动。",color=Muted,style=MaterialTheme.typography.bodyMedium)
        if(allowUnreadyStart && !ready)Text(if(canCountSteps)
            "可以先计时计步，可靠路线就绪后再计算里程。室内可能只有时长和步数。" else
            "可以先计时，可靠路线就绪后再计算里程。当前计步不可用，请检查身体活动权限或手机是否支持计步。",
            color=Muted,style=MaterialTheme.typography.bodySmall)
        locationNote?.let { Text(it,color=Muted,style=MaterialTheme.typography.bodySmall) }
        Button(enabled=(ready || allowUnreadyStart) && !busy && !counting,onClick={counting=true},modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)) {
            Text(if(counting)"倒数出发…" else if(ready || !allowUnreadyStart)"开始${kind.label}"
                else if(canCountSteps)"先开始计时与计步" else "先开始计时")
        }
        OutlinedButton(enabled=!busy,onClick={if(counting)counting=false else cancel()},
            modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)) {Text(if(counting)"取消倒数" else "取消准备")}
    }
}
