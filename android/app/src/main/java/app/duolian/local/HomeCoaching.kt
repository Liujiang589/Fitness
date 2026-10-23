package app.duolian.local

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.duolian.local.domain.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import java.time.LocalDate

/** Refresh on returning to the app and across midnight, without running a background date ticker. */
@Composable internal fun rememberTrainingDate(): LocalDate {
    val owner = LocalLifecycleOwner.current
    val today by produceState(LocalDate.now(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { value = LocalDate.now(); delay(30000) }
        }
    }
    return today
}

@Composable internal fun MovementDiscovery(exercise: Exercise, open: () -> Unit) {
    val guide = ExerciseGuides.find(exercise) ?: return
    Surface(onClick = open, color = PaleBlue, shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ExerciseMotionCanvas(guide, remember { mutableFloatStateOf(.46f) },
                Modifier.size(88.dp).clip(RoundedCornerShape(18.dp)), detailed=false, showTrajectory=false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("先看懂，再练起来", color = Blue, style = MaterialTheme.typography.labelMedium)
                Text(exercise.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(ExerciseCoaching.cue(guide), color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("动态示范与要领 ›", color = Blue, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable internal fun HomeLifeLinks(nutrition: () -> Unit, body: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(onClick = nutrition, color = androidx.compose.ui.graphics.Color.White,
            shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("好好吃饭", fontWeight = FontWeight.Bold)
                Text("饮食手记 ›", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(onClick = body, color = androidx.compose.ui.graphics.Color.White,
            shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("关心身体", fontWeight = FontWeight.Bold)
                Text("留下身体记录 ›", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
