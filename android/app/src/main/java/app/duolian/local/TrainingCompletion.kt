package app.duolian.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.duolian.local.domain.Session
import app.duolian.local.domain.TrainingRecap

@Composable internal fun TrainingCompletionDialog(session: Session, dismiss: () -> Unit, history: () -> Unit) {
    val recap = remember(session) { TrainingRecap.from(session) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("这一练，已保存") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleMedium, color = Ink)
            Text("完成 ${recap.movements} 个动作、${recap.completed} 组。给自己一点恢复的时间。", color = Muted)
            MetricGrid(listOf(clock(recap.elapsedMs) to "训练用时", number(recap.volumeKg) to "训练容量 / kg"))
            if (recap.unfinished > 0) Text("${recap.unfinished} 组未完成，未计入训练容量。", color = Muted,
                style = MaterialTheme.typography.bodySmall)
            Text("每组明细已保存在训练历史中。", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }, confirmButton = { Button(onClick = dismiss) { Text("完成") } },
        dismissButton = { TextButton(onClick = history) { Text("查看历史") } })
}

/** Measure both values and labels at the actual font scale before choosing columns. */
@Composable internal fun MetricGrid(metrics: List<Pair<String, String>>) {
    if (metrics.isEmpty()) return
    val minimum = maxOf(96.dp, metrics.maxOf {
        maxOf(textWidth(it.first, MaterialTheme.typography.titleLarge),
            textWidth(it.second, MaterialTheme.typography.labelSmall)) + 20.dp
    })
    ResponsiveTiles(minimum, metrics.size) { index, area ->
        StatTile(metrics[index].first, metrics[index].second, area)
    }
}
