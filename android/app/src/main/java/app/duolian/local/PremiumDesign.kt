package app.duolian.local

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import app.duolian.local.domain.*
import java.time.LocalDate

@Composable internal fun DuolianTheme(content: @Composable () -> Unit) {
    fun type(size: Int, height: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
        fontFamily = DuolianFont, fontSize = size.sp, lineHeight = height.sp,
        fontWeight = weight, letterSpacing = 0.sp, fontFeatureSettings = "tnum")
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Blue, onPrimary = Color.White,
            primaryContainer = PaleBlue, onPrimaryContainer = Ink,
            secondary = Blue, secondaryContainer = PaleBlue, onSecondaryContainer = Ink,
            tertiary = Blue, background = Page, onBackground = Ink, surface = Color.White,
            onSurface = Ink, surfaceVariant = Page, onSurfaceVariant = Muted,
            outline = Muted, outlineVariant = Hairline,
            surfaceContainerHighest = Page, surfaceTint = Color.Transparent),
        typography = Typography(
            displayLarge = type(52, 60, FontWeight.SemiBold), displayMedium = type(44, 52, FontWeight.SemiBold),
            displaySmall = type(36, 44, FontWeight.SemiBold), headlineLarge = type(32, 40, FontWeight.SemiBold),
            headlineMedium = type(28, 36, FontWeight.SemiBold), headlineSmall = type(24, 32, FontWeight.SemiBold),
            titleLarge = type(22, 30, FontWeight.SemiBold), titleMedium = type(18, 26, FontWeight.SemiBold),
            titleSmall = type(16, 24, FontWeight.SemiBold), bodyLarge = type(17, 26),
            bodyMedium = type(15, 23), bodySmall = type(13, 20), labelLarge = type(15, 22, FontWeight.Medium),
            labelMedium = type(13, 19, FontWeight.Medium), labelSmall = type(12, 18)),
        shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(16.dp),
            large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(24.dp)), content = content)
}

@Composable internal fun Eyebrow(text: String, color: Color = Muted) {
    Text(text, color = color, style = MaterialTheme.typography.labelMedium)
}

@Composable internal fun PremiumHeader(tab: Int, nutrition: Boolean, onNutrition: () -> Unit,
    onQuickRecord: () -> Unit) {
    val today = rememberTrainingDate()
    val titles = listOf("今天", "工具", "动作", "训练记录", "我的")
    val date = "${today.monthValue}月${today.dayOfMonth}日 · 周${listOf("一","二","三","四","五","六","日")[today.dayOfWeek.value-1]}"
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Eyebrow(if (tab == 0) date else "独练")
        ResponsiveHeader(leading = {
            Text(if (tab == 0 && nutrition) "饮食" else titles[tab],
                style = MaterialTheme.typography.headlineLarge, color = Ink)
        }, trailing = {
            if (tab == 0) {
                Surface(onClick = onNutrition, shape = RoundedCornerShape(16.dp), color = Color.White) {
                    Text(if (nutrition) "返回训练" else "饮食记录", Modifier.heightIn(min = 48.dp).wrapContentHeight()
                        .padding(horizontal = 16.dp, vertical = 10.dp), color = Blue, style = MaterialTheme.typography.labelLarge)
                }
            } else IconButton(onClick = onQuickRecord, modifier = Modifier.background(Color.White, CircleShape)
                .semantics { contentDescription = "快捷记录" }) {
                AppSymbol(9, Ink, Modifier.size(20.dp))
            }
        })
    }
}

@Composable internal fun PremiumNavigation(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("训练", "工具", "动作", "历史", "我的")
    Surface(color = Color.White, modifier = Modifier.fillMaxWidth()) {
        Column {
            HorizontalDivider(color = Hairline, thickness = .5.dp)
            Row(Modifier.fillMaxWidth().navigationBarsPadding().selectableGroup().padding(horizontal = 8.dp, vertical = 6.dp)) {
                labels.forEachIndexed { i, label ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .selectable(selected = selected == i, role = Role.Tab, onClick = { onSelect(i) })
                        .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        val color = if (selected == i) Blue else Muted
                        AppSymbol(listOf(2, 1, 0, 3, 4)[i], color, Modifier.size(21.dp))
                        Text(label, color = color, style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selected == i) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

/** An actual user plan or a clear rest/first-use state; no invented workout. */
@Composable internal fun TrainingSpotlight(focus: HomeFocus, busy: Boolean, action: () -> Unit) {
    Surface(shape = RoundedCornerShape(22.dp), color = Color.White) {
        Column {
            CachedPhoto(R.drawable.training_daylight, Modifier.fillMaxWidth().height(136.dp), Alignment.TopEnd)
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).background(Blue, CircleShape))
                    Text(focus.label, color = Blue,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp))
                }
                Text(focus.title.replace('\n', ' '), color = Ink,
                    style = MaterialTheme.typography.headlineMedium)
                Text(focus.description,
                    color = Muted, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = action,
                    enabled = !busy || focus.plan == null, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(
                        containerColor = Blue, contentColor = Color.White)) {
                    Text(focus.action, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WeeklyMomentum(history: List<Session>) {
    val today = rememberTrainingDate()
    val monday = today.minusDays(today.dayOfWeek.value - 1L)
    val thisWeek = androidx.compose.runtime.remember(history, monday) { history.filter { Analytics.dateOf(it) in monday..monday.plusDays(6) } }
    val trained = androidx.compose.runtime.remember(thisWeek) { thisWeek.map { Analytics.dateOf(it) }.toSet() }
    val dayWidth = maxOf(28.dp, textWidth("30", MaterialTheme.typography.labelLarge) + 8.dp)
    Panel {
        ResponsiveHeader(leading = {
            Text("本周节奏", style = MaterialTheme.typography.titleMedium)
        }, trailing = {
            Text("本周 ${trained.size} 天", color = Blue, style = MaterialTheme.typography.labelMedium)
        })
        BoxWithConstraints(Modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        val available = with(density) { maxWidth.roundToPx() }
        val gapPixels = with(density) { 3.dp.roundToPx() }
        val minimumPixels = with(density) { dayWidth.roundToPx() }
        val columns = if (available >= minimumPixels * 7 + gapPixels * 6) 7 else 4
        // Divide whole pixels downwards so seven rounded cells cannot overflow by a few pixels.
        val cellWidth = with(density) {
            ((available - gapPixels * (columns - 1)) / columns).coerceAtLeast(minimumPixels).toDp()
        }
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), maxItemsInEachRow = columns) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, label ->
                val date = monday.plusDays(index.toLong())
                val done = date in trained
                val current = date == today
                Column(Modifier.width(cellWidth), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
                    Box(Modifier.fillMaxWidth().heightIn(min = 34.dp).clip(CircleShape)
                        .background(if (current) Blue else if (done) PaleBlue else Color.Transparent)
                        .padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                        Text(date.dayOfMonth.toString(), color = if (current) Color.White else Ink,
                            style = MaterialTheme.typography.labelLarge)
                    }
                    Box(Modifier.size(4.dp).background(if (done) Blue else Color.Transparent, CircleShape))
                }
            }
        }
        }
        Text(if (thisWeek.isEmpty()) "这一周，从一次适合自己的训练开始。" else "已记录的每一次训练，都是自己的积累。",
            color = Muted, style = MaterialTheme.typography.bodySmall)
        DetailDisclosure("本周数据") {
            val values = listOf(thisWeek.size.toString(), number(Analytics.totalVolume(thisWeek)), Analytics.streak(history).toString())
            val labels = listOf("本周训练 / 次", "本周容量 / kg", "连续训练 / 天")
            val minimum = maxOf(88.dp, values.maxOf { textWidth(it, MaterialTheme.typography.headlineSmall) },
                labels.maxOf { textWidth(it, MaterialTheme.typography.labelSmall) })
            ResponsiveTiles(minimum, 3) { index, area ->
                MomentumMetric(values[index], labels[index], area)
            }
            Text("休息也是训练的一部分，无需追求连续打卡。", color = Muted,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun MomentumMetric(value: String, label: String, modifier: Modifier) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, color = Ink, style = MaterialTheme.typography.headlineSmall)
        Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable internal fun ProgressOverview(sessions: List<Session>) {
    val recent = sessions.filter { Analytics.dateOf(it) >= LocalDate.now().minusDays(29) }
    Panel {
        Eyebrow("训练回顾", Blue)
        Text("看见每一次进步", color = Ink,
            style = MaterialTheme.typography.titleLarge)
        Text(if (recent.isEmpty()) "先留下一次训练，再慢慢看见变化。" else "近 30 天，留下了 ${recent.size} 次训练的足迹。",
            color = Muted, style = MaterialTheme.typography.bodyMedium)
        Text("记录、趋势与分析，按你需要查看。", color = Muted,
            style = MaterialTheme.typography.bodySmall)
    }
}
