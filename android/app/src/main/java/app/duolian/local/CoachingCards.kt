package app.duolian.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duolian.local.domain.*

@Composable internal fun CoachingCue(guide: ExerciseGuide) {
    Surface(color = PaleBlue, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text("先记住这一点", color = Blue, style = MaterialTheme.typography.labelMedium)
            Text(ExerciseCoaching.cue(guide), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable internal fun CoachingStages(guide: ExerciseGuide, phase: Int) {
    guide.stages.forEachIndexed { index, text ->
        Surface(color = if (phase == index) PaleBlue else Page, shape = RoundedCornerShape(16.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(guide.phaseNames[index], color = if (phase == index) Blue else Ink, fontWeight = FontWeight.SemiBold)
                Text(text, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable internal fun CoachingMistakes(guide: ExerciseGuide) {
    Surface(color = Color(0xFFFFF2E8), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("练习时留意", fontWeight = FontWeight.SemiBold)
            guide.mistakes.forEach { Text("• $it", color = Ink) }
            Text("先减少负重或缩小幅度，让动作重新稳定。", color = Muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable internal fun CoachingAlternatives(guide: ExerciseGuide, open: (ExerciseGuide) -> Unit) {
    val options = ExerciseCoaching.alternatives(guide)
    Text("按器械与动作能力选择", fontWeight = FontWeight.SemiBold)
    Text("先了解差异，再决定是否换练；新动作需要重新熟悉和选择重量。", color = Muted, style = MaterialTheme.typography.bodySmall)
    if (options.isEmpty()) {
        Text("当前没有整理合适的替代动作。可以先查看本动作要领，或在计划中选择其他训练内容。", color = Muted)
    }
    options.forEach { option ->
        val target = ExerciseCoaching.guide(option.kind)
        Surface(color = Page, shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(target.name, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    Text(target.equipment, color = Blue, style = MaterialTheme.typography.labelSmall)
                }
                Text(option.reason, color = Ink, style = MaterialTheme.typography.bodyMedium)
                Text(option.difference, color = Muted, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { open(target) }) { Text("查看${target.name}示范 ›") }
            }
        }
    }
}
