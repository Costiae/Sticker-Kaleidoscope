package app.stickerkaleidoscope.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image as SkiaImage

@Composable
internal actual fun MemeThumbnail(id: String, controller: LibraryController, modifier: Modifier) {
    val bitmap by produceState<ImageBitmap?>(null, id, controller) {
        value = try {
            val bytes = controller.thumbnail(id)
            withContext(Dispatchers.IO) { bytes?.let { SkiaImage.makeFromEncoded(it).toComposeImageBitmap() } }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it, null, Modifier.matchParentSize(), contentScale = ContentScale.Fit) }
            ?: Icon(Icons.Outlined.BrokenImage, null)
    }
}
