package app.duolian.local

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight

/** Measure both pieces at their actual scaled size; stack before either piece is squeezed. */
@Composable internal fun ResponsiveHeader(modifier: Modifier = Modifier,
    leading: @Composable () -> Unit, trailing: @Composable () -> Unit) {
    Layout(content = { Box { leading() }; Box { trailing() } }, modifier = modifier.fillMaxWidth()) { items, limits ->
        val loose = Constraints(maxWidth = limits.maxWidth)
        val first = items[0].measure(loose)
        val second = items[1].measure(loose)
        val gap = if (second.width == 0 && second.height == 0) 0 else 12.dp.roundToPx()
        val stacked = first.width + gap + second.width > limits.maxWidth
        val height = if (stacked) first.height + gap + second.height else maxOf(first.height, second.height)
        layout(limits.maxWidth, limits.constrainHeight(height)) {
            first.placeRelative(0, if (stacked) 0 else (height - first.height) / 2)
            second.placeRelative(if (stacked) 0 else limits.maxWidth - second.width,
                if (stacked) first.height + gap else (height - second.height) / 2)
        }
    }
}

@Composable internal fun textWidth(text: String, style: TextStyle): Dp {
    val measured = rememberTextMeasurer().measure(text, style = style, softWrap = false)
    return with(LocalDensity.current) { measured.size.width.toDp() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun HomePlanToolbar(section: Int, select: (Int) -> Unit, create: () -> Unit) {
    ResponsiveHeader(leading = {
        PageSegments(listOf("今天", "计划库"), section, select)
    }, trailing = { TextButton(onClick = create) { Text("＋ 创建计划") } })
}

@Composable internal fun PageSegments(labels: List<String>, selected: Int, select: (Int) -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            labels.forEachIndexed { index, label ->
                Surface(shape = RoundedCornerShape(12.dp), color = if (selected == index) PaleBlue else Color.Transparent) {
                    Text(label, Modifier.clip(RoundedCornerShape(12.dp))
                        .selectable(selected == index, role = Role.Tab, onClick = { select(index) })
                        .heightIn(min = 44.dp).wrapContentHeight().padding(horizontal = 14.dp, vertical = 10.dp),
                        color = if (selected == index) Blue else Muted, style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected == index) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}

/** Equal columns when text fits; full-width cards when narrow screens or large fonts need room. */
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ResponsiveTiles(minimumWidth: Dp, count: Int, modifier: Modifier = Modifier,
    content: @Composable (Int, Modifier) -> Unit) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val gap = 12.dp
        val columns = ((maxWidth + gap) / (minimumWidth + gap)).toInt().coerceIn(1, count)
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap), maxItemsInEachRow = columns) {
            repeat(count) { content(it, Modifier.weight(1f)) }
        }
    }
}
