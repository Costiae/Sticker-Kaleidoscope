package app.stickerkaleidoscope

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.FilterNone
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import app.stickerkaleidoscope.resources.*
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import java.awt.MouseInfo
import java.awt.Point

@Composable
internal fun WindowScope.DesktopTitleBar(state: WindowState, onClose: () -> Unit) {
    val maximized = state.placement == WindowPlacement.Maximized
    val toggleMaximized by rememberUpdatedState<() -> Unit>({
        state.placement = if (state.placement == WindowPlacement.Maximized) WindowPlacement.Floating else WindowPlacement.Maximized
    })
    Row(
        Modifier.fillMaxWidth().height(36.dp).background(MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 12.dp)
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { toggleMaximized() }) }
                    .pointerInput(Unit) {
                        var startPointer: Point? = null
                        var startWindow: Point? = null
                        detectDragGestures(
                            onDragStart = {
                                startPointer = MouseInfo.getPointerInfo()?.location
                                startWindow = window.location
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                if (state.placement == WindowPlacement.Floating) {
                                    val pointer = MouseInfo.getPointerInfo()?.location
                                    val origin = startWindow
                                    val start = startPointer
                                    if (pointer != null && origin != null && start != null) {
                                        window.setLocation(origin.x + pointer.x - start.x, origin.y + pointer.y - start.y)
                                    }
                                }
                            },
                            onDragEnd = { startPointer = null; startWindow = null },
                            onDragCancel = { startPointer = null; startWindow = null }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Image(painterResource(Res.drawable.app_icon), contentDescription = null, modifier = Modifier.size(20.dp))
                Text(stringResource(Res.string.app_name), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        WindowControl(Icons.Outlined.Remove, stringResource(Res.string.window_minimize), onClick = { state.isMinimized = true })
        WindowControl(
            if (maximized) Icons.Outlined.FilterNone else Icons.Outlined.CropSquare,
            stringResource(if (maximized) Res.string.window_restore else Res.string.window_maximize),
            onClick = toggleMaximized
        )
        WindowControl(Icons.Outlined.Close, stringResource(Res.string.window_close), destructive = true, onClick = onClose)
    }
}

@Composable
private fun WindowControl(icon: ImageVector, label: String, destructive: Boolean = false, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        hovered && destructive -> Color(0xFFC42B1C)
        hovered -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    Box(
        Modifier.size(46.dp, 36.dp).background(background).hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp),
            tint = if (hovered && destructive) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
