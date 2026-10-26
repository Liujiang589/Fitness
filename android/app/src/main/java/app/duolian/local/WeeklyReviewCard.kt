package app.duolian.local

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.duolian.local.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable internal fun WeeklyReviewCard(sessions: List<Session>, food: List<Food>) {
    Panel {
        Text("这一周，留下了什么", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("训练、走跑与饮食，按已记录的内容回看。", color = Muted, style = MaterialTheme.typography.bodySmall)
        DetailDisclosure("近七天回顾") { WeeklyReviewDetail(sessions, food) }
    }
}

@Composable private fun WeeklyReviewDetail(sessions: List<Session>, food: List<Food>, vm: OutdoorViewModel = viewModel()) {
    val runs by vm.runs.collectAsStateWithLifecycle()
    val ready by vm.ready.collectAsStateWithLifecycle()
    val finished = remember(runs) { runs.filter { it.status == OutdoorStatus.FINISHED } }
    val today = rememberTrainingDate()
    val review by produceState<WeeklyReview?>(null, sessions, finished, food, today) {
        value = withContext(Dispatchers.Default) { WeeklyReviews.build(sessions, finished, food, today) }
    }
    val report = review
    if (!ready || report == null) { LinearProgressIndicator(Modifier.fillMaxWidth()); return }
    WeeklyReviewContent(report)
}

@Composable internal fun WeeklyReviewContent(report: WeeklyReview) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("${report.from} — ${report.to}", color = Muted, style = MaterialTheme.typography.labelSmall)
        Text("练了什么", fontWeight = FontWeight.SemiBold)
        Text("力量训练 ${report.strength.sessions} 次，分布在 ${report.strength.days} 天；走跑记录 ${report.outdoorCount} 次。")
        Text("和前七天相比：力量训练 ${report.previous.sessions} → ${report.strength.sessions} 次。", color = Muted)
        Text("哪里有进步", fontWeight = FontWeight.SemiBold)
        if (report.progress.isEmpty()) Text("暂未找到可比较的同重量次数进步；数据少或主动恢复也很正常。", color = Muted)
        report.progress.forEach { p -> Text("${p.name}：同记录重量 ${kg(p.grams)} kg，正式组 ${p.previousReps} → ${p.reps} 次。") }
        Text("下一步怎么安排", fontWeight = FontWeight.SemiBold)
        Text(report.nextStep, color = Muted)
        Text(if (report.foodDays == 0) "近七天尚无饮食记录，不推断实际摄入。" else
            "饮食有记录 ${report.foodDays} 天，仅汇总已记录条目，不判断是否吃够。", color = Muted)
        DetailDisclosure("统计口径与详细数值") {
            Text("力量训练活动时长 ${report.strength.minutes} 分钟；记录容量 ${number(report.strength.volumeKg)} kg。", color = Muted)
            Text("走跑距离 ${number(report.outdoorMeters / 1000)} km；仅统计已结束的运动。", color = Muted)
            report.averageRecordedCalories?.let { Text("有记录日期的条目平均 ≈ ${number(it)} kcal/日，不代表完整全天摄入。", color = Muted) }
            Text("比较连续的两个七天区间；不计未结束训练和热身组进步。次数进步只比较同一动作名称、同记录重量的正式组，不等同于体能或体型结论。", color = Muted,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}
