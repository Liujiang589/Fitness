package app.duolian.local

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.MotionEvent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.duolian.local.domain.RoutePoint
import com.amap.api.maps.*
import com.amap.api.maps.model.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/** No SDK initialization or network access before an explicit map choice. */
private object StreetMapConsent {
    private val state = MutableStateFlow(false)
    private var loaded = false
    fun flow(c: Context): MutableStateFlow<Boolean> {
        if (!loaded) {
            state.value = c.applicationContext.getSharedPreferences("street-map-consent", 0).getBoolean("amap-v1", false)
            loaded = true
        }
        return state
    }
    fun set(c: Context, accepted: Boolean) {
        c.applicationContext.getSharedPreferences("street-map-consent", 0).edit().putBoolean("amap-v1", accepted).apply()
        state.value = accepted
        if (!accepted) MapsInitializer.updatePrivacyAgree(c.applicationContext, false)
    }
}

@Composable internal fun StreetRouteMap(points: List<RoutePoint>, tracking: Boolean) {
    val context = LocalContext.current
    val agreed by remember { StreetMapConsent.flow(context) }.collectAsStateWithLifecycle()
    var consentDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    when {
        !BuildConfig.AMAP_CONFIGURED -> Surface(color = PaleBlue, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("街道地图暂未启用", style = MaterialTheme.typography.titleMedium)
                Text("此安装包尚未开启地图服务，GPS 运动记录仍可使用。", color = Muted)
                RouteSketch(points)
            }
        }
        Build.SUPPORTED_ABIS.none { it == "arm64-v8a" || it == "armeabi-v7a" } -> Surface(color = PaleBlue, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("此设备暂不支持街道地图", style = MaterialTheme.typography.titleMedium)
                Text("GPS 运动记录和路线导出仍可使用。", color = Muted)
                RouteSketch(points)
            }
        }
        !agreed -> Surface(color = PaleBlue, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("在街道地图上，看见你的每一步", style = MaterialTheme.typography.titleMedium)
                Text("开启高德地图，查看真实街道、当前位置和运动路线。地图需要联网，你可以单独选择是否开启。", color = Muted)
                Button(onClick = { consentDialog = true }) { Text("开启街道地图") }
                RouteSketch(points)
            }
        }
        else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Release the inline renderer while the fullscreen renderer is displayed.
            if (!expanded) key(retry) {
                NativeStreetMap(points, tracking, Modifier.fillMaxWidth().height(310.dp)) { retry++ }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("高德地图 · ${when { points.isEmpty() -> "开始运动后显示当前位置"; tracking -> "绿色路线随运动延伸"; else -> "已保存的运动路线" }}",
                    Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { expanded = true }) { Text("全屏地图") }
            }
            TextButton(onClick = { StreetMapConsent.set(context, false) }) { Text("关闭街道地图", color = Muted) }
        }
    }
    if (expanded && agreed) Dialog(onDismissRequest = { expanded = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = Page) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { expanded = false }) { Text("‹ 返回运动记录") }
                    Text("运动路线", style = MaterialTheme.typography.titleMedium)
                }
                key(retry) { NativeStreetMap(points, tracking, Modifier.fillMaxSize()) { retry++ } }
            }
        }
    }
    if (consentDialog) AlertDialog(onDismissRequest = { consentDialog = false }, title = { Text("启用高德街道地图") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("展示街道和运动路线时，将使用高德地图 SDK（北京高德图强科技有限公司）联网加载地图，可能处理位置、IP 地址、设备及应用信息。运动轨迹记录仍保存在本机；地图服务按高德隐私政策处理必要信息。关闭地图后仍可记录运动。")
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("https://lbs.amap.com/pages/privacy/"))) }) { Text("阅读高德隐私政策") }
        } }, confirmButton = { TextButton(onClick = {
            StreetMapConsent.set(context, true); consentDialog = false
        }) { Text("同意并开启地图") } }, dismissButton = { TextButton(onClick = { consentDialog = false }) { Text("暂不开启") } })
}

/** Own every native resource in one composition; do not use the SDK's separate location client. */
@Composable private fun NativeStreetMap(points: List<RoutePoint>, tracking: Boolean,
    modifier: Modifier, retry: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var loaded by remember { mutableStateOf(false) }
    var slow by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var follow by remember { mutableStateOf(tracking) }
    var fitRequest by remember { mutableIntStateOf(0) }
    val controller = remember(context) {
        runCatching {
            MapsInitializer.updatePrivacyShow(context.applicationContext, true, true)
            MapsInitializer.updatePrivacyAgree(context.applicationContext, true)
            MapsInitializer.setProtocol(MapsInitializer.HTTPS)
            StreetMapController(context)
        }.getOrElse { error = true; null }
    }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> controller?.resume()
                Lifecycle.Event.ON_PAUSE -> controller?.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) controller?.resume()
        onDispose { lifecycle.removeObserver(observer); controller?.destroy() }
    }
    LaunchedEffect(controller, error) { if (error) controller?.destroy() }
    LaunchedEffect(controller) {
        controller?.map?.setOnMapLoadedListener { loaded = true }
        delay(20000)
        if (!loaded) slow = true
    }
    LaunchedEffect(controller, points, tracking, follow, loaded, fitRequest) {
        if (loaded) slow = false
        if (controller != null) runCatching { controller.render(points, tracking, follow, fitRequest * 2 + if (loaded) 1 else 0) }
            .onFailure { error = true }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (controller != null && !error) AndroidView(factory = {
                controller.view.apply {
                    map.setOnMapTouchListener { event ->
                        if (event.actionMasked == MotionEvent.ACTION_MOVE) follow = false
                        parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP &&
                            event.actionMasked != MotionEvent.ACTION_CANCEL)
                    }
                }
            }, modifier = Modifier.fillMaxSize())
            if (error || slow) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), color = Page) {
                Column(Modifier.padding(12.dp)) {
                    Text(if (error) "地图暂时无法显示" else "地图加载较慢，请检查网络", color = Muted)
                    Text("运动数据仍可继续记录与保存。", style = MaterialTheme.typography.bodySmall, color = Muted)
                    TextButton(onClick = retry) { Text("重试地图") }
                }
            }
            if (!loaded && !error && !slow) Surface(Modifier.align(Alignment.TopCenter).padding(12.dp), color = Page) {
                Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text("街道地图加载中…", color = Muted)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (tracking) TextButton(enabled = points.isNotEmpty(), onClick = { follow = true; fitRequest++ }) {
                Text(if (follow) "正在跟随" else "跟随当前位置")
            }
            TextButton(enabled = points.isNotEmpty(), onClick = { follow = false; fitRequest++ }) { Text("查看全程") }
        }
    }
}

private class StreetMapController(context: Context) {
    val view = TextureMapView(context)
    val map: AMap
    private val converter = CoordinateConverter(context.applicationContext).from(CoordinateConverter.CoordType.GPS)
    private val converted = mutableMapOf<RoutePoint, LatLng>()
    private val lines = mutableMapOf<Int, Polyline>()
    private var start: Marker? = null
    private var end: Marker? = null
    private var lastPoints: List<RoutePoint>? = null
    private var previousFit = -1
    private var previousCount = -1
    private var wasTracking: Boolean? = null
    private var resumed = false
    private var destroyed = false
    init {
        view.onCreate(Bundle())
        map = view.map
        map.uiSettings.apply {
            isScaleControlsEnabled = true
            isZoomControlsEnabled = false
            isTiltGesturesEnabled = false
            isRotateGesturesEnabled = false
            // Keep the SDK's source logo, copyright and map approval information visible.
        }
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.8, 104.2), 3.5f))
    }
    fun resume() { if (!destroyed && !resumed) { view.onResume(); resumed = true } }
    fun pause() { if (!destroyed && resumed) { view.onPause(); resumed = false } }
    fun destroy() { if (!destroyed) { pause(); view.onDestroy(); destroyed = true; converted.clear(); lines.clear() } }

    fun render(points: List<RoutePoint>, tracking: Boolean, follow: Boolean, fitRequest: Int) {
        if (destroyed || points.isEmpty()) return
        val changed = lastPoints != points
        if (changed) {
            // Convert for display only. Never rewrite WGS84 storage or GPX coordinates as GCJ-02.
            points.forEach { p -> if (p !in converted) {
                converted[p] = converter.coord(LatLng(p.latitude, p.longitude)).convert()
                    ?: error("Coordinate conversion unavailable")
            } }
            val segments = points.groupBy { it.segment }
            lines.keys.filter { it !in segments }.toList().forEach { lines.remove(it)?.remove() }
            segments.forEach { (segment, samples) ->
                val positions = samples.map { converted.getValue(it) }
                if (positions.size >= 2) {
                    val existing = lines[segment]
                    if (existing == null) lines[segment] = map.addPolyline(PolylineOptions().addAll(positions)
                        .width(12f).color(android.graphics.Color.rgb(47, 123, 80)).zIndex(5f))
                    else existing.points = positions
                }
            }
            val first = converted.getValue(points.first())
            val last = converted.getValue(points.last())
            if (start == null) start = map.addMarker(MarkerOptions().position(first).title("起点")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)))
            if (end == null) end = map.addMarker(MarkerOptions().position(last).title(if (tracking) "当前位置" else "终点")
                .icon(BitmapDescriptorFactory.defaultMarker(if (tracking) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_RED)))
            else end?.position = last
            lastPoints = points
        }
        if (wasTracking != tracking) {
            end?.title = if (tracking) "当前位置" else "终点"
            end?.setIcon(BitmapDescriptorFactory.defaultMarker(if (tracking) BitmapDescriptorFactory.HUE_AZURE else BitmapDescriptorFactory.HUE_RED))
            wasTracking = tracking
        }
        if (follow && tracking && (previousCount != points.size || previousFit != fitRequest)) {
            map.animateCamera(CameraUpdateFactory.newLatLngZoom(converted.getValue(points.last()), 17f))
        } else if (previousFit != fitRequest || previousCount < 0) {
            if (points.size == 1) map.moveCamera(CameraUpdateFactory.newLatLngZoom(converted.getValue(points.first()), 17f))
            else {
                val bounds = LatLngBounds.builder()
                points.forEach { bounds.include(converted.getValue(it)) }
                val width = view.width.takeIf { it > 0 } ?: 500
                val height = view.height.takeIf { it > 0 } ?: 500
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(), width, height, 70))
            }
        }
        previousCount = points.size; previousFit = fitRequest
    }
}
