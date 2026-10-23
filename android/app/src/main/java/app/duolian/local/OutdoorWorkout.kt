package app.duolian.local

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.duolian.local.domain.*
import app.duolian.local.outdoor.OutdoorLive

@Composable internal fun OutdoorWorkout(run: OutdoorRun, live: OutdoorLive) {
    val recording = live.running && live.run?.status == OutdoorStatus.ACTIVE
    val signal = if (live.run?.id == run.id) live.signal else OutdoorSignal.INTERRUPTED
    val speed = live.speedMps.takeIf { recording }
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var keepScreen by rememberSaveable { mutableStateOf(false) }
    var batterySaver by remember { mutableStateOf(false) }
    var restricted by remember { mutableStateOf(false) }
    val online by rememberOutdoorNetwork()
    DisposableEffect(lifecycle, context) {
        fun refresh() {
            val power = context.getSystemService(PowerManager::class.java)
            batterySaver = power.isPowerSaveMode
            restricted = !power.isIgnoringBatteryOptimizations(context.packageName)
        }
        refresh()
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(view, keepScreen, recording) {
        val before = view.keepScreenOn
        view.keepScreenOn = keepScreen && recording
        onDispose { view.keepScreenOn = before }
    }
    Panel(color = Night) {
        Text(if (recording) "${run.kind.label} · 专注这一程" else "${run.kind.label} · 随时再出发",
            color = Color.White, style = MaterialTheme.typography.titleLarge)
        Text("已记录距离", color = Color(0xFFCED7C5), style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("%.2f".format(run.distanceMeters / 1000), color = Volt, fontWeight = FontWeight.Bold,
                fontSize = 56.sp, lineHeight = 62.sp)
            Text("公里", color = Color.White, modifier = Modifier.padding(bottom = 8.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            WorkoutMetric(clock(run.elapsedMs), "运动时长", Modifier.weight(1f))
            WorkoutMetric(if (run.kind == OutdoorKind.RUN) OutdoorSpeed.currentPace(speed)
                else speed?.let { "%.1f".format(it * 3.6) } ?: "—",
                if (run.kind == OutdoorKind.RUN) "平滑配速 /公里" else "平滑速度 公里/时", Modifier.weight(1f))
        }
        HorizontalDivider(color = Color(0xFF48523F))
        Text(if (run.stepCounting) "本次走跑 · ${run.steps} 步" else "本次步数 · 等待传感器读数或未启用",
            color = Color(0xFFCED7C5), style = MaterialTheme.typography.labelMedium)
    }
    Panel(color = if (signal == OutdoorSignal.GOOD) Color(0xFFE8EEDF) else PaleBlue) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(9.dp).background(if (signal == OutdoorSignal.GOOD) Color(0xFF36734B) else Muted, CircleShape))
            Text(signal.title, fontWeight = FontWeight.Bold)
            if (signal == OutdoorSignal.SEARCHING) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(if (recording && signal == OutdoorSignal.GOOD && live.stationary)
            "当前接近静止，已冻结路线锚点，避免把GPS漂移累加为路程。" else signal.detail,
            color = Muted, style = MaterialTheme.typography.bodyMedium)
        if (recording && signal == OutdoorSignal.GOOD) Text(
            "水平精度约 ${live.accuracy?.toInt() ?: "—"} 米 · ${live.fixAgeMs?.let { "${it / 1000} 秒前更新" } ?: "等待定位"}",
            color = Muted, style = MaterialTheme.typography.labelSmall)
        if (signal == OutdoorSignal.LOCATION_OFF) TextButton(onClick = {
            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }) { Text("打开定位设置") }
        if (!online) Text("网络未连接：新街道底图可能加载失败；GPS 仍可继续记录。", color = Muted,
            style = MaterialTheme.typography.bodySmall)
    }
    Panel {
        StreetRouteMap(run.points, tracking = recording,
            currentPosition=live.mapPosition.takeIf { recording && signal==OutdoorSignal.GOOD && (live.fixAgeMs ?: Long.MAX_VALUE)<=10000 })
    }
    Panel {
        DetailDisclosure("配速、步数与定位详情") {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Stat(OutdoorMath.pace(run), "平均配速", Modifier.weight(1f))
                Stat(speed?.let { "%.1f".format(it * 3.6) } ?: "—", "平滑速度 km/h", Modifier.weight(1f))
            }
            Text(if (run.stepCounting) "本次采集 ${run.steps} 步" else "本次尚未采集到计步数据", color = Muted)
            Text(live.accuracy?.takeIf { recording }?.let { "最近定位精度约 ${it.toInt()} 米" }
                ?: "暂无可靠的实时定位精度", color = Muted)
            Text("时长包含等待 GPS 和弱信号的时间，不包含暂停或进程中断时间；缺失路段不补算距离。", color = Muted,
                style = MaterialTheme.typography.bodySmall)
            Text("速度经短时间平滑，变化可能滞后数秒；GPS质量不足时显示—，不把失联当作静止。已跳过 ${live.filteredFixes} 个不可靠定位。",
                color = Muted, style = MaterialTheme.typography.bodySmall)
            Text("本次步数与当天累计分开统计；手机传感器可能成批上报。距离来自GPS，不按步数乘步长猜测。",
                color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(color = Hairline)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("运动页保持亮屏", fontWeight = FontWeight.SemiBold)
                Text("仅当前页面生效，关闭可节省电量", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = keepScreen, onCheckedChange = { keepScreen = it })
        }
        if (batterySaver || restricted) DetailDisclosure("锁屏记录与省电设置") {
            Text(if (batterySaver) "手机目前处于省电模式，定位可能受到限制。" else
                "如果锁屏后出现中断，可在系统中允许独练后台运行。", color = Muted)
            Text("记录通过持续通知运行；强制停止、重启或部分厂商的后台清理仍会中断。重新进入后可恢复检查点。", color = Muted,
                style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }) {
                Text("查看系统电池设置")
            }
        }
    }
}

@Composable private fun WorkoutMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier) {
        Text(value, color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, color = Color(0xFFCED7C5), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable internal fun WorkoutControls(recording: Boolean, busy: Boolean, toggle: () -> Unit, finish: () -> Unit) {
    Surface(shadowElevation = 8.dp, color = Color.White) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !busy, onClick = toggle, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                    Text(if (busy) "正在处理…" else if (recording) "暂停运动" else "继续运动")
                }
                OutlinedButton(enabled = !busy, onClick = finish, modifier = Modifier.weight(1f).heightIn(min = 54.dp)) {
                    Text("结束并保存")
                }
            }
            Text(if (recording) "退出此页后继续记录 · 通知栏可暂停" else "暂停期间不计时、不采集路线",
                color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Offline route overview; original WGS84 points stay local and segments never bridge gaps. */
@Composable internal fun RouteSketch(points: List<RoutePoint>) {
    if (points.isEmpty()) return
    val segments = remember(points) { points.groupBy { it.segment }.values.toList() }
    Canvas(Modifier.fillMaxWidth().height(180.dp).semantics { contentDescription = "已记录路线示意，非街道地图，断点分段显示" }) {
        val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
        val correction = kotlin.math.cos(Math.toRadians((minLat + maxLat) / 2)).coerceAtLeast(.01)
        val width = ((maxLon - minLon) * correction).coerceAtLeast(.00001)
        val height = (maxLat - minLat).coerceAtLeast(.00001)
        val padding = 22.dp.toPx()
        val scale = minOf((size.width - padding * 2) / width, (size.height - padding * 2) / height)
        fun position(p: RoutePoint) = Offset(
            (center.x + ((p.longitude - (minLon + maxLon) / 2) * correction * scale)).toFloat(),
            (center.y - ((p.latitude - (minLat + maxLat) / 2) * scale)).toFloat())
        segments.forEach { samples ->
            val path = Path()
            samples.forEachIndexed { index, point -> val p = position(point)
                if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            drawPath(path, Color(0xFF36734B), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))
        }
        drawCircle(Color(0xFF36734B), 6.dp.toPx(), position(points.first()))
        drawCircle(Blue, 5.dp.toPx(), position(points.last()))
    }
    Text("本机路线示意 · 非街道地图", color = Muted, style = MaterialTheme.typography.labelSmall)
}

@Composable private fun rememberOutdoorNetwork(): State<Boolean> {
    val context = LocalContext.current
    val available = remember { mutableStateOf(true) }
    DisposableEffect(context) {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        fun refresh() {
            available.value = manager.getNetworkCapabilities(manager.activeNetwork)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        }
        refresh()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = refresh()
            override fun onLost(network: Network) = refresh()
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = refresh()
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    return available
}
