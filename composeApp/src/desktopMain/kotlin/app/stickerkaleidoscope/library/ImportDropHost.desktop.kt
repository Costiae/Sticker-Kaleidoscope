package app.stickerkaleidoscope.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.*
import androidx.compose.ui.unit.dp
import app.stickerkaleidoscope.resources.*
import org.jetbrains.compose.resources.stringResource
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.io.File

internal fun droppedPaths(transferable: Transferable): List<String> {
    if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) return emptyList()
    return (transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<*>)
        ?.filterIsInstance<File>()?.map { it.absolutePath }.orEmpty()
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
internal actual fun ImportDropHost(controller: LibraryController, content: @Composable () -> Unit) {
    val state by controller.state.collectAsState()
    val busy by rememberUpdatedState(state.busy)
    var hovered by remember { mutableStateOf(false) }
    val target = remember(controller) {
        object : DragAndDropTarget {
            override fun onEntered(event: DragAndDropEvent) { hovered = true }
            override fun onExited(event: DragAndDropEvent) { hovered = false }
            override fun onEnded(event: DragAndDropEvent) { hovered = false }
            override fun onDrop(event: DragAndDropEvent): Boolean {
                hovered = false
                if (busy) return false
                return try {
                    val paths = droppedPaths(event.awtTransferable)
                    paths.isNotEmpty() && controller.importPaths(paths) != null
                } catch (_: Exception) { false }
            }
        }
    }
    Box(Modifier.fillMaxSize().dragAndDropTarget(
        shouldStartDragAndDrop = { !busy && it.awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor) },
        target = target
    )) {
        content()
        if (hovered && !busy) Surface(
            Modifier.matchParentSize().border(2.dp, MaterialTheme.colorScheme.primary),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        ) {
            Box(contentAlignment = Alignment.Center) { Text(stringResource(Res.string.drop_to_import)) }
        }
    }
}
