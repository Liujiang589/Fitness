package app.duolian.local

import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keep CPU work outside composition. A changed input gets fresh state, never an old result. */
@Composable internal fun <T> rememberBackgroundResult(vararg keys: Any?, compute: () -> T): T? {
    val result = remember(*keys) { mutableStateOf<T?>(null) }
    LaunchedEffect(result) {
        result.value = withContext(Dispatchers.Default) { compute() }
    }
    return result.value
}
