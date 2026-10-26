package app.duolian.local

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Detail controls only change presentation; stored values and calculations stay intact. */
@Composable internal fun DetailDisclosure(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(onClick = { expanded = !expanded }, color = Color.Transparent,
            shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                .semantics { stateDescription = if (expanded) "已展开" else "已收起" }) {
            Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(if (expanded) "收起$title" else "查看$title", color = Blue,
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Text(if (expanded) "⌃" else "⌄", color = Muted)
            }
        }
        if (expanded) content()
    }
}

/** Atmospheric editorial photography, never an exercise or portion-size instruction. */
@Composable internal fun LifestyleBanner(@DrawableRes image: Int, title: String, subtitle: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White) {
        Column {
            CachedPhoto(image, Modifier.fillMaxWidth().height(128.dp))
            Column(Modifier.fillMaxWidth().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, color = Ink, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable internal fun NutritionBreakdown(protein: Double, carbs: Double, fat: Double) {
    val values = listOf(protein * 4, carbs * 4, fat * 9)
    val colors = listOf(Blue, Color(0xFFC29D58), Color(0xFFB88372))
    val sum = values.sum()
    if (sum > 0) {
        Row(Modifier.fillMaxWidth().height(10.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            values.forEachIndexed { i, value ->
                if (value > 0) Surface(Modifier.weight((value / sum).toFloat().coerceAtLeast(.001f))
                    .fillMaxHeight(), color = colors[i], shape = RoundedCornerShape(5.dp)) {}
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            listOf("蛋白质", "碳水", "脂肪").forEachIndexed { i, label ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Surface(Modifier.size(7.dp), color = colors[i], shape = RoundedCornerShape(4.dp)) {}
                    Text(label, color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("色条表示已记录食物的供能构成", color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}
