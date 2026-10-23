package app.duolian.local

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.duolian.local.domain.*
import java.time.LocalDate

@Composable internal fun DuolianTheme(content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White,
            primaryContainer = PaleBlue, onPrimaryContainer = Ink,
            secondary = Blue, secondaryContainer = PaleBlue, onSecondaryContainer = Ink,
            tertiary = Blue, background = Page, onBackground = Ink, surface = Color.White,
            onSurface = Ink, surfaceVariant = Page, onSurfaceVariant = Muted,
            outline = Color(0xFF9BA38F), outlineVariant = Hairline,
            surfaceContainerHighest = PaleBlue, surfaceTint = Color.Transparent),
        typography = base.copy(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
                fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-1).sp),
            headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
            titleLarge = base.titleLarge.copy(fontSize = 20.sp, lineHeight = 28.sp,
                fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelSmall = base.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp)),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(24.dp), extraLarge = RoundedCornerShape(28.dp)), content = content)
}

@Composable internal fun Eyebrow(text: String, color: Color = Muted) {
    Text(text, color = color, fontSize = 10.sp, lineHeight = 16.sp,
        letterSpacing = 1.8.sp, fontWeight = FontWeight.Bold)
}

@Composable internal fun PremiumHeader(tab: Int, nutrition: Boolean, onNutrition: () -> Unit,
    onQuickRecord: () -> Unit) {
    val today = rememberTrainingDate()
    val titles = listOf("今天，按自己的节奏。", "训练工具", "探索动作", "看见每一次进步", "我的训练空间")
    val subtitles = listOf("独练 / DAILY TRAINING", "YOUR TRAINING ESSENTIALS",
        "THE MOVEMENT LIBRARY", "PROGRESS / YOUR JOURNEY", "YOUR PERSONAL SPACE")
    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(subtitles[tab])
            Spacer(Modifier.weight(1f))
            if (tab == 0) Text("${today.monthValue}.${"%02d".format(today.dayOfMonth)}",
                color = Muted, style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (tab == 0 && nutrition) "饮食手记" else titles[tab], Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall, color = Ink)
            if (tab == 0) {
                Surface(onClick = onNutrition, shape = CircleShape, color = Color.White,
                    border = BorderStroke(1.dp, Hairline)) {
                    Text(if (nutrition) "训练 ↗" else "饮食 ↗", Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        color = Ink, style = MaterialTheme.typography.labelMedium)
                }
            } else IconButton(onClick = onQuickRecord, modifier = Modifier.background(Color.White, CircleShape)
                .semantics { contentDescription = "快捷记录" }) {
                AppSymbol(9, Ink, Modifier.size(20.dp))
            }
        }
    }
}

@Composable internal fun PremiumNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("训练", "工具", "动作", "历史", "我的")
    Surface(color = Page, modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Surface(color = Night, shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth().selectableGroup().padding(horizontal = 5.dp, vertical = 6.dp)) {
                labels.forEachIndexed { i, label ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(22.dp))
                        .background(if (selected == i) Volt else Color.Transparent)
                        .selectable(selected = selected == i, role = Role.Tab, onClick = { onSelect(i) })
                        .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val color = if (selected == i) Night else Color(0xFFADB5A6)
                        AppSymbol(listOf(2, 1, 0, 3, 4)[i], color, Modifier.size(21.dp))
                        Text(label, color = color, style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected == i) FontWeight.Bold else FontWeight.Medium)
                    }
                }
            }
        }
    }
}

/** An actual user plan or a clear rest/first-use state; no invented workout. */
@Composable internal fun TrainingSpotlight(focus: HomeFocus, busy: Boolean, action: () -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = Color(0xFFF1F0E7)) {
        Box(Modifier.fillMaxWidth().heightIn(min = 290.dp)) {
            CachedPhoto(R.drawable.training_daylight, Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(
                Color(0xFFF4F2E8), Color(0xFFF4F2E8).copy(alpha = .88f), Color.Transparent))))
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(
                Color.Transparent, Color.Transparent, Color(0xFFF4F2E8).copy(alpha = .95f)))))
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(Blue, CircleShape))
                    Text(focus.label, color = Blue,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(focus.title, color = Ink,
                    style = MaterialTheme.typography.headlineLarge, maxLines = 3,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(.74f))
                Text(focus.description,
                    color = Ink, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Button(onClick = action,
                    enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(
                        containerColor = Night, contentColor = Volt)) {
                    Text(focus.action, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("↗", fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable internal fun WeeklyMomentum(history: List<Session>) {
    val today = rememberTrainingDate()
    val monday = today.minusDays(today.dayOfWeek.value - 1L)
    val thisWeek = androidx.compose.runtime.remember(history, monday) { history.filter { Analytics.dateOf(it) in monday..monday.plusDays(6) } }
    val trained = androidx.compose.runtime.remember(thisWeek) { thisWeek.map { Analytics.dateOf(it) }.toSet() }
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("保持你的节奏", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Text("本周 ${trained.size} 天", color = Blue, style = MaterialTheme.typography.labelMedium)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                val date = monday.plusDays(index.toLong())
                val done = date in trained
                val current = date == today
                Surface(Modifier.weight(1f), shape = RoundedCornerShape(17.dp),
                    color = if (current) Night else if (done) PaleBlue else Color.White,
                    border = BorderStroke(1.dp, if (current) Night else Hairline)) {
                    Column(Modifier.padding(vertical = 11.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(label, color = if (current) Color(0xFFC4CCBC) else Muted,
                            style = MaterialTheme.typography.labelSmall)
                        Text(if (done) "✓" else date.dayOfMonth.toString(),
                            color = if (current) Volt else Ink, fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleSmall)
                        Box(Modifier.size(4.dp).background(if (done || current) {
                            if (current) Volt else Blue
                        } else Color.Transparent, CircleShape))
                    }
                }
            }
        }
        Text(if (thisWeek.isEmpty()) "这一周，从一次适合自己的训练开始。" else "已记录的每一次训练，都是自己的积累。",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        DetailDisclosure("本周数据") {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MomentumMetric(thisWeek.size.toString(), "本周训练 / 次", Modifier.weight(1f))
                MomentumMetric(number(Analytics.totalVolume(thisWeek)), "本周容量 / kg", Modifier.weight(1f))
                MomentumMetric(Analytics.streak(history).toString(), "连续训练 / 天", Modifier.weight(1f))
            }
            Text("休息也是训练的一部分，无需追求连续打卡。", color = Muted,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun MomentumMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = Ink, fontSize = 25.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable internal fun ProgressOverview(sessions: List<Session>) {
    val recent = sessions.filter { Analytics.dateOf(it) >= LocalDate.now().minusDays(29) }
    Panel(color = Night) {
        Eyebrow("EVERY REP COUNTS", Volt)
        Text("每一次努力，都有迹可循。", color = Color.White,
            style = MaterialTheme.typography.titleLarge)
        Text(if (recent.isEmpty()) "先留下一次训练，再慢慢看见变化。" else "近 30 天，留下了 ${recent.size} 次训练的足迹。",
            color = Color(0xFFD5DACF), style = MaterialTheme.typography.bodyMedium)
        Text("记录、趋势与分析，按你需要查看。", color = Color(0xFFB3BDA9),
            style = MaterialTheme.typography.bodySmall)
    }
}
