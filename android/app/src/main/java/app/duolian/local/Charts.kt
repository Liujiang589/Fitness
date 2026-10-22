package app.duolian.local

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val AccentFallback = Color(0xFFE8EEF7)

/** Simple bar chart with labels under the bars; values are normalised to the largest entry. */
@Composable internal fun BarChart(values: List<Float>, labels: List<String>, modifier: Modifier = Modifier,
    color: Color = Blue, labelEvery: Int = 1) {
    val max = values.maxOrNull()?.coerceAtLeast(0f) ?: 0f
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(96.dp)) {
            val count = values.size
            if (count == 0) return@Canvas
            val slot = size.width / count
            val barWidth = slot * 0.52f
            values.forEachIndexed { i, value ->
                val ratio = if (max <= 0f) 0f else (value / max).coerceIn(0f, 1f)
                val height = (size.height * 0.9f * ratio).coerceAtLeast(if (value > 0f) 3f else 1.5f)
                val left = slot * i + (slot - barWidth) / 2
                drawRoundRect(
                    color = if (value > 0f) color else AccentFallback,
                    topLeft = Offset(left, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth * 0.32f))
            }
        }
        if (labels.isNotEmpty()) {
            // Only sampled labels are laid out, so long ranges never squeeze dates onto two lines.
            val shown = labels.indices.filter { it % labelEvery == 0 || it == labels.lastIndex }
            Row(Modifier.fillMaxWidth().padding(top = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                shown.forEach { i ->
                    Text(text = labels[i], style = MaterialTheme.typography.labelSmall,
                        color = Muted, maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** Line chart with a soft area fill; values may be empty or constant. */
@Composable internal fun LineChart(values: List<Float>, modifier: Modifier = Modifier, color: Color = Blue) {
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val min = values.minOrNull() ?: 0f
        val max = values.maxOrNull() ?: 0f
        val span = (max - min).let { if (it <= 0.0001f) 1f else it }
        fun point(i: Int): Offset {
            val x = if (values.size == 1) size.width / 2 else size.width * i / (values.size - 1)
            val ratio = (values[i] - min) / span
            return Offset(x, size.height - 8.dp.toPx() - ratio * (size.height - 16.dp.toPx()))
        }
        val line = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until values.size) lineTo(point(i).x, point(i).y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(point(values.lastIndex).x, size.height)
            lineTo(point(0).x, size.height)
            close()
        }
        drawPath(area, color.copy(alpha = 0.12f))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        values.indices.forEach { i ->
            drawCircle(color, radius = 1.8.dp.toPx(), center = point(i))
        }
    }
}

/** Protein / carbs / fat split as a ring with a calorie total in the middle. */
@Composable internal fun MacroRing(protein: Double, carbs: Double, fat: Double, modifier: Modifier = Modifier) {
    val parts = listOf(protein * 4, carbs * 4, fat * 9)
    val total = parts.sum()
    val colors = listOf(Color(0xFFE59B42), Color(0xFF2582F5), Color(0xFF7C5CE0))
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = Size(this.size.minDimension * 0.14f, this.size.minDimension * 0.14f)
            val inset = stroke.width / 2
            val arcSize = Size(this.size.width - stroke.width, this.size.height - stroke.width)
            drawArc(AccentFallback, 0f, 360f, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke.width, cap = StrokeCap.Round))
            if (total <= 0.0) return@Canvas
            var start = -90f
            parts.forEachIndexed { i, part ->
                val sweep = (part / total * 360f).toFloat()
                if (sweep > 0f) {
                    drawArc(colors[i], start, sweep - 2f, false, Offset(inset, inset), arcSize,
                        style = Stroke(stroke.width, cap = StrokeCap.Round))
                }
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (total <= 0.0) "0" else number(total), style = MaterialTheme.typography.titleMedium,
                color = Ink)
            Text("kcal", style = MaterialTheme.typography.labelSmall, color = Muted)
        }
    }
}

/** Horizontal share bar used for muscle-group volume distribution. */
@Composable internal fun ShareBar(share: Float, modifier: Modifier = Modifier, color: Color = Blue) {
    Box(modifier.clip(RoundedCornerShape(6.dp)).background(AccentFallback)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(share.coerceIn(0f, 1f))
            .clip(RoundedCornerShape(6.dp)).background(color))
    }
}
