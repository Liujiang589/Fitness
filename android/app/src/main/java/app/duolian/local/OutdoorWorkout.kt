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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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

@Composable internal fun WorkoutControls(recording: Boolean, busy: Boolean, toggle: () -> Unit,
    finish: () -> Unit, discard: () -> Unit) {
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (recording) "收起页面仍会记录\n暂停可暂时停止采集" else "已暂停\n继续后才恢复记录",
                    modifier = Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.labelSmall)
                TextButton(enabled = !busy, onClick = discard, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("放弃本次记录", color = if (busy) Muted else MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

internal enum class WorkoutExit { SAVE, DISCARD }

/** Dismissal never resumes a paused workout or changes what has been recorded. */
@Composable internal fun WorkoutExitDialog(run: OutdoorRun, exit: WorkoutExit, recording: Boolean,
    busy: Boolean, dismiss: () -> Unit, confirm: () -> Unit) {
    val discarding = exit == WorkoutExit.DISCARD
    AlertDialog(onDismissRequest = { if (!busy) dismiss() },
        title = { Text(if (discarding) "放弃这次${run.kind.label}？" else "保存这次${run.kind.label}？") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("已记录 %.2f 公里 · %s".format(run.distanceMeters / 1000, clock(run.elapsedMs)), fontWeight = FontWeight.SemiBold)
                Text(if (discarding) "本次路线、时长和运动步数将被移除，不会出现在运动足迹中。当天已采集的步数保留。" else
                    if (run.points.isEmpty()) "目前没有可靠路线，保存后仍可查看已记录的时长和步数。" else
                        "结束定位，将本次路线、时长和已采集步数保存到运动足迹。")
                Text(if (recording) "确认前，运动仍在记录。" else "当前已暂停；返回运动页不会自动继续记录。",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = confirm) {
            Text(if (discarding) "放弃，不保存" else "结束并保存",
                color = if (discarding && !busy) MaterialTheme.colorScheme.error else Color.Unspecified)
        } },
        dismissButton = { TextButton(enabled = !busy, onClick = dismiss) { Text("返回运动页") } })
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
