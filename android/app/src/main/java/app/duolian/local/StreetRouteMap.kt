package app.duolian.local

import android.animation.ValueAnimator
import android.view.animation.LinearInterpolator
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
import app.duolian.local.domain.OutdoorMath
import app.duolian.local.domain.OutdoorRouteChunks
import app.duolian.local.domain.RouteChunkKey
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

@Composable internal fun StreetRouteMap(points: List<RoutePoint>, tracking: Boolean, currentPosition: RoutePoint? = null, modifier: Modifier = Modifier, fillHeight: Boolean = false) {
    val context = LocalContext.current
    val agreed by remember { StreetMapConsent.flow(context) }.collectAsStateWithLifecycle()
    var consentDialog by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    when {
        !BuildConfig.AMAP_CONFIGURED -> Surface(modifier = modifier, color = PaleBlue, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("街道地图暂未启用", style = MaterialTheme.typography.titleMedium)
                Text("此安装包尚未开启地图服务，GPS 运动记录仍可使用。", color = Muted)
                RouteSketch(points)
            }
        }
        Build.SUPPORTED_ABIS.none { it == "arm64-v8a" || it == "armeabi-v7a" } -> Surface(modifier = modifier, color = PaleBlue, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("此设备暂不支持街道地图", style = MaterialTheme.typography.titleMedium)
                Text("GPS 运动记录和路线导出仍可使用。", color = Muted)
                RouteSketch(points)
            }
        }
        !agreed -> Surface(modifier = modifier, color = PaleBlue, shape = MaterialTheme.shapes.large) {
            Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("在街道地图上，看见你的每一步", style = MaterialTheme.typography.titleMedium)
                Text("开启高德地图，查看真实街道、当前位置和运动路线。地图需要联网，你可以单独选择是否开启。", color = Muted)
                Button(onClick = { consentDialog = true }) { Text("开启街道地图") }
                RouteSketch(points)
            }
        }
        else -> Column(modifier,verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Release the inline renderer while the fullscreen renderer is displayed.
            if (!expanded) key(retry) {
                NativeStreetMap(points, tracking, if(fillHeight)Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().height(310.dp),currentPosition) { retry++ }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("高德地图 · ${when { points.isEmpty() -> "开始运动后显示当前位置"; tracking -> "蓝色路线随运动延伸"; else -> "已保存的运动路线" }}",
                    Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = { expanded = true }) { Text("放大地图") }
                TextButton(onClick = { StreetMapConsent.set(context, false) }) { Text("关闭地图",color=Muted) }
            }
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
                key(retry) { NativeStreetMap(points, tracking, Modifier.fillMaxSize(),currentPosition) { retry++ } }
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
    modifier: Modifier, currentPosition: RoutePoint?, retry: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var loaded by remember { mutableStateOf(false) }
    var slow by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var follow by remember(tracking) { mutableStateOf(tracking) }
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
    LaunchedEffect(controller, points, currentPosition, tracking, follow, loaded, fitRequest) {
        if (loaded) slow = false
        if (controller != null) runCatching { controller.render(points, tracking, follow, fitRequest * 2 + if (loaded) 1 else 0,currentPosition) }
            .onFailure { error = true }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (controller != null && !error) AndroidView(factory = {
                controller.view.apply {
                    map.setOnMapTouchListener { event ->
                        if (event.actionMasked == MotionEvent.ACTION_MOVE) { follow = false;controller.map.stopAnimation() }
                        parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP &&
                            event.actionMasked != MotionEvent.ACTION_CANCEL)
                    }
                }
            }, modifier = Modifier.fillMaxSize())
            if(!error) Column(Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                if(tracking) FilledTonalButton(enabled=points.isNotEmpty() || currentPosition!=null,
                    onClick={follow=true;fitRequest++}) { Text(if(follow)"跟随中" else "回到当前位置") }
                FilledTonalButton(enabled=points.isNotEmpty(),onClick={follow=false;fitRequest++}) { Text("查看全程") }
            }
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
    }
}

private class StreetMapController(context: Context) {
    val view=TextureMapView(context)
    val map: AMap
    private val converter=CoordinateConverter(context.applicationContext).from(CoordinateConverter.CoordType.GPS)
    private val converted=mutableMapOf<RoutePoint,LatLng>()
    private val route=OutdoorRouteChunks()
    private val lines=mutableMapOf<RouteChunkKey,Polyline>()
    private var start: Marker?=null
    private var end: Marker?=null
    private var precisionCircle: Circle?=null
    private var lastCameraPoint: RoutePoint?=null
    private var lastMarkerPoint: RoutePoint?=null
    private var markerAnimation: ValueAnimator?=null
    private var previousFit=-1
    private var resumed=false
    private var destroyed=false
    init {
        view.onCreate(Bundle());map=view.map
        map.uiSettings.apply {
            isScaleControlsEnabled=true;isZoomControlsEnabled=false
            isTiltGesturesEnabled=false;isRotateGesturesEnabled=false
        }
        // The SDK logo, copyright and map approval information remain visible.
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(35.8,104.2),3.5f))
    }
    fun resume() { if(!destroyed && !resumed) {view.onResume();resumed=true} }
    fun pause() { if(!destroyed && resumed) {markerAnimation?.cancel();view.onPause();resumed=false} }
    fun destroy() { if(!destroyed) {pause();markerAnimation?.cancel();map.stopAnimation();view.onDestroy();destroyed=true;converted.clear();lines.clear()} }
    private fun coordinate(p: RoutePoint)=converted.getOrPut(p) {
        checkNotNull(converter.coord(LatLng(p.latitude,p.longitude)).convert())
    }
    private fun markerIcon(color: Int): BitmapDescriptor {
        val density=view.resources.displayMetrics.density
        val diameter=(26*density).toInt().coerceAtLeast(26)
        val bitmap=android.graphics.Bitmap.createBitmap(diameter,diameter,android.graphics.Bitmap.Config.ARGB_8888)
        val canvas=android.graphics.Canvas(bitmap);val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        val center=diameter/2f;paint.color=android.graphics.Color.WHITE
        canvas.drawCircle(center,center,center-1,paint);paint.color=color
        canvas.drawCircle(center,center,center*.68f,paint)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
    fun render(points: List<RoutePoint>,tracking: Boolean,follow: Boolean,fitRequest: Int,currentPosition: RoutePoint?) {
        if(destroyed)return
        val changes=route.update(points)
        if(changes.reset) {
            lines.values.forEach{it.remove()};lines.clear();converted.clear()
            start?.remove();start=null
        }
        for(chunk in changes.changed) if(chunk.points.size>=2) {
            val positions=chunk.points.map(::coordinate)
            val line=lines[chunk.key]
            if(line==null)lines[chunk.key]=map.addPolyline(PolylineOptions().addAll(positions)
                .width(5f*view.resources.displayMetrics.density).color(0xFF007AFF.toInt()).zIndex(5f))
            else line.points=positions
        }
        if(points.isNotEmpty() && start==null)start=map.addMarker(MarkerOptions().position(coordinate(points.first()))
            .anchor(.5f,.5f).title("起点").icon(markerIcon(0xFF32A66A.toInt())))
        val point=currentPosition.takeIf{tracking} ?: points.lastOrNull() ?: return
        val position=if(point in converted)converted.getValue(point) else
            checkNotNull(converter.coord(LatLng(point.latitude,point.longitude)).convert())
        if(end==null)end=map.addMarker(MarkerOptions().position(position).anchor(.5f,.5f)
            .icon(markerIcon(0xFF007AFF.toInt())).zIndex(10f))
        end?.title=if(!tracking)"终点" else if(currentPosition!=null)"当前位置" else "最近路线点"
        if(tracking && currentPosition!=null) {
            val circle=precisionCircle
            if(circle==null)precisionCircle=map.addCircle(CircleOptions().center(position).radius(point.accuracy.toDouble())
                .strokeWidth(1.5f).strokeColor(0x55007AFF).fillColor(0x16007AFF))
            else circle.radius=point.accuracy.toDouble()
        } else {precisionCircle?.remove();precisionCircle=null}
        if(lastMarkerPoint!=point) {
            markerAnimation?.cancel()
            val previous=lastMarkerPoint;val from=end!!.position
            val moving=from.latitude!=position.latitude || from.longitude!=position.longitude
            val smooth=moving && tracking && previous!=null && previous.segment==point.segment && point.time-previous.time in 1..8000
            if(smooth)markerAnimation=ValueAnimator.ofFloat(0f,1f).apply {
                duration=750;interpolator=LinearInterpolator()
                addUpdateListener { animation ->
                    val fraction=(animation.animatedValue as Float).toDouble()
                    val frame=LatLng(from.latitude+(position.latitude-from.latitude)*fraction,
                        from.longitude+(position.longitude-from.longitude)*fraction)
                    end?.position=frame;precisionCircle?.center=frame
                };start()
            } else {end?.position=position;precisionCircle?.center=position}
            lastMarkerPoint=point
        }
        val requested=previousFit!=fitRequest
        val moved=lastCameraPoint?.let{OutdoorMath.distance(it,point)>=2} ?: true
        if(follow && tracking && (moved || requested)) {
            val zoom=if(lastCameraPoint==null)17f else map.cameraPosition.zoom.coerceIn(15f,19f)
            map.stopAnimation();map.animateCamera(CameraUpdateFactory.newLatLngZoom(position,zoom),750L,null)
            lastCameraPoint=point
        } else if(requested) {
            if(points.size<2)map.moveCamera(CameraUpdateFactory.newLatLngZoom(position,17f))
            else {
                val bounds=LatLngBounds.builder();points.forEach{bounds.include(coordinate(it))}
                map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds.build(),view.width.coerceAtLeast(100),view.height.coerceAtLeast(100),60))
            }
        }
        previousFit=fitRequest
    }
}
