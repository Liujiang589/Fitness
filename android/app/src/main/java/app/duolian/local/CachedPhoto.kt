package app.duolian.local

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object PhotoCache {
    val images = object : LruCache<Int, Bitmap>(16 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap) = value.allocationByteCount / 1024
    }
}

/** Full-resolution bundled photo, decoded off the UI thread and reused across lazy-list entries. */
@Composable internal fun CachedPhoto(resource: Int, modifier: Modifier, alignment: Alignment = Alignment.CenterEnd) {
    val resources = LocalContext.current.applicationContext.resources
    val photo by produceState(PhotoCache.images.get(resource), resource, resources) {
        value = withContext(Dispatchers.IO) {
            PhotoCache.images.get(resource) ?: BitmapFactory.decodeResource(resources, resource)?.also {
                it.prepareToDraw(); PhotoCache.images.put(resource, it)
            }
        }
    }
    val bitmap = photo
    if (bitmap != null) Image(bitmap.asImageBitmap(), null, modifier, alignment, ContentScale.Crop)
    else Box(modifier) // Keep identical layout while decoding; text and buttons remain available.
}
