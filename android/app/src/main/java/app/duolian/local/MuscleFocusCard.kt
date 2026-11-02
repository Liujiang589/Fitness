package app.duolian.local

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.duolian.local.domain.*

private val PrimaryMuscle = Color(0xFFD97866)
private val SecondaryMuscle = Color(0xFFDDB56A)

@Composable internal fun MuscleFocusCard(guide: ExerciseGuide, compact: Boolean = false) {
    val profile = remember(guide.kind) { AnatomyMesh.profile(guide.kind) }
    val primary = remember(profile) { MuscleDiagram.names(profile.primary) }
    val secondary = remember(profile) { MuscleDiagram.names(profile.secondary) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!compact) Text("这次主要练哪里", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            MuscleBodyMap(profile, Modifier.width(if (compact) 100.dp else 132.dp).height(if (compact) 116.dp else 150.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("主要 · $primary", color = Color(0xFFAD4D3E), style = MaterialTheme.typography.bodySmall)
                if (secondary.isNotEmpty()) Text("辅助 · $secondary", color = Color(0xFF80601F), style = MaterialTheme.typography.bodySmall)
                if (!compact) Text("珊瑚色主要，浅金色辅助", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (!compact) Text("分区用于理解动作，不代表个人实际发力强度或恢复程度。", color = Muted,
            style = MaterialTheme.typography.labelSmall)
    }
}

@Composable internal fun MuscleBodyMap(profile: MuscleProfile, modifier: Modifier = Modifier) {
    val outline = remember { PathParser().parsePathString(MuscleDiagram.outline).toPath() }
    val regions = remember { MuscleDiagram.regions.map { it to PathParser().parsePathString(it.path).toPath() } }
    Column(modifier) {
        Canvas(Modifier.weight(1f).fillMaxWidth().semantics {
            contentDescription = "正背面肌群图。主要：${MuscleDiagram.names(profile.primary)}。辅助：${MuscleDiagram.names(profile.secondary).ifEmpty { "未单列" }}"
        }) {
            val factor = minOf(size.width / 216f, size.height / 220f)
            translate((size.width - factor * 216f) / 2, (size.height - factor * 220f) / 2) {
                scale(factor, factor, pivot = Offset.Zero) {
                    listOf(BodySide.FRONT, BodySide.BACK).forEachIndexed { index, side ->
                        translate(index * 116f, 0f) {
                            drawOval(Color(0xFFDCE1E7), Offset(40f, 3f), Size(20f, 26f))
                            drawPath(outline, Color(0xFFDCE1E7))
                            regions.filter { it.first.side == side }.forEach { (region, path) ->
                                val color = when (region.part) {
                                    in profile.primary -> PrimaryMuscle
                                    in profile.secondary -> SecondaryMuscle
                                    else -> Color(0xFFC9D0D9)
                                }
                                drawPath(path, color)
                                if (region.mirrored) scale(-1f, 1f, pivot = Offset(50f, 110f)) { drawPath(path, color) }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            Text("正面", color = Muted, style = MaterialTheme.typography.labelSmall)
            Text("背面", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
