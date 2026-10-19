package app.stickerkaleidoscope.library

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.stickerkaleidoscope.resources.*
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.floor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun MemeGrid(
    items: List<Meme>, selectedIds: Set<String>, focusedId: String?, controller: LibraryController,
    onSelectionChanged: (Set<String>, String?) -> Unit
) {
    val grid = rememberLazyGridState()
    val focus = remember { FocusRequester() }
    val currentSelection by rememberUpdatedState(selectedIds)
    val anchor by rememberUpdatedState(focusedId)
    val changeSelection by rememberUpdatedState(onSelectionChanged)
    var boxStart by remember { mutableStateOf<Offset?>(null) }
    var boxEnd by remember { mutableStateOf<Offset?>(null) }
    var menuPosition by remember { mutableStateOf<Offset?>(null) }
    var boxBase by remember { mutableStateOf(emptySet<String>()) }
    var boxAdditive by remember { mutableStateOf(false) }
    val orderedIds = remember(items) { items.map { it.id } }
    val picked = items.filter { it.id in selectedIds }
    val copyLabel = stringResource(Res.string.copy_to_clipboard)
    val selectionColor = MaterialTheme.colorScheme.primary

    fun bounds() = grid.layoutInfo.visibleItemsInfo.map { item ->
        item.key.toString() to Rect(item.offset.x.toFloat(), item.offset.y.toFloat(),
            (item.offset.x + item.size.width).toFloat(), (item.offset.y + item.size.height).toFloat())
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(pluralStringResource(Res.plurals.selected_count, selectedIds.size, selectedIds.size), style = MaterialTheme.typography.labelSmall)
            IconButton(onClick = { controller.copy(selectedIds.toList()) }, enabled = picked.isNotEmpty() && !controller.state.value.busy) {
                Icon(Icons.Outlined.ContentCopy, copyLabel, Modifier.size(18.dp))
            }
            TextButton(onClick = { onSelectionChanged(emptySet(), null) }, enabled = selectedIds.isNotEmpty()) {
                Text(stringResource(Res.string.clear_selection))
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val density = LocalDensity.current
        val columns = maxOf(1, floor((maxWidth.value - 48f + 12f) / (132f + 12f)).toInt())
        val geometry = with(density) {
            SelectionGridGeometry(columns, maxWidth.roundToPx(), 156.dp.roundToPx(), 12.dp.roundToPx(), 24.dp.roundToPx())
        }
        fun scrollY() = geometry.scrollOffset(grid.firstVisibleItemIndex, grid.firstVisibleItemScrollOffset)
        LaunchedEffect(boxStart, boxEnd, boxBase, boxAdditive, orderedIds, geometry) {
            val start = boxStart
            val end = boxEnd
            if (start != null && end != null) {
                snapshotFlow { scrollY() }.collect { scroll ->
                    val ids = geometry.select(orderedIds, start, end, scroll, boxBase, boxAdditive)
                    changeSelection(ids, ids.firstOrNull())
                }
            }
        }
        Box(Modifier.fillMaxSize().testTag("meme-grid").clipToBounds()
            .background(MaterialTheme.colorScheme.surface)
            .focusRequester(focus)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) false else when {
                    event.isCtrlPressed && event.key == Key.A -> { onSelectionChanged(orderedIds.toSet(), orderedIds.firstOrNull()); true }
                    event.isCtrlPressed && event.key == Key.C -> { controller.copy(selectedIds.toList()); true }
                    event.key == Key.Escape -> { onSelectionChanged(emptySet(), null); true }
                    event.key == Key.Delete && picked.isNotEmpty() && picked.none { it.deleted } -> { controller.trash(selectedIds.toList(), true); true }
                    else -> false
                }
            }
            .focusable()
            .pointerInput(orderedIds, geometry) {
                var press: Offset? = null
                var pressContent: Offset? = null
                var initialSelection = emptySet<String>()
                var control = false
                var shift = false
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val position = event.changes.firstOrNull()?.position ?: continue
                        val hit = bounds().firstOrNull { (_, rect) -> rect.contains(position) }?.first
                        when (event.type) {
                            PointerEventType.Press -> {
                                focus.requestFocus()
                                if (event.buttons.isSecondaryPressed) {
                                    press = null
                                    pressContent = null
                                    boxStart = null
                                    boxEnd = null
                                    if (hit != null && hit !in currentSelection) changeSelection(setOf(hit), hit)
                                    menuPosition = position
                                    event.changes.forEach { it.consume() }
                                } else if (event.buttons.isPrimaryPressed) {
                                    menuPosition = null
                                    press = position
                                    pressContent = position + Offset(0f, scrollY())
                                    initialSelection = currentSelection
                                    control = event.keyboardModifiers.isCtrlPressed
                                    shift = event.keyboardModifiers.isShiftPressed
                                    boxBase = initialSelection
                                    boxAdditive = control
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            PointerEventType.Move -> {
                                val start = press
                                if (start != null && event.buttons.isPrimaryPressed) {
                                    if (boxStart != null || (position - start).getDistance() > viewConfiguration.touchSlop) {
                                        boxStart = pressContent
                                        boxEnd = position
                                        val ids = geometry.select(orderedIds, pressContent!!, position, scrollY(), initialSelection, control)
                                        changeSelection(ids, ids.firstOrNull())
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                            PointerEventType.Release -> {
                                if (press != null && !event.buttons.isPrimaryPressed) {
                                    if (boxStart != null) {
                                        val ids = geometry.select(orderedIds, boxStart!!, position, scrollY(), initialSelection, control)
                                        changeSelection(ids, ids.firstOrNull())
                                    } else {
                                        if (hit != null) {
                                            val ids = clickSelection(orderedIds, initialSelection, anchor, hit, control, shift)
                                            changeSelection(ids, hit.takeIf { it in ids } ?: ids.firstOrNull())
                                            if (!control && !shift) controller.copy(listOf(hit))
                                        } else if (!control) changeSelection(emptySet(), null)
                                    }
                                    press = null
                                    pressContent = null
                                    boxStart = null
                                    boxEnd = null
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                }
            }
        ) {
            LazyVerticalGrid(
                state = grid, columns = GridCells.Fixed(columns),
                verticalArrangement = Arrangement.spacedBy(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(24.dp), modifier = Modifier.fillMaxSize().testTag("meme-grid-scroll")
            ) {
                items(items, key = { it.id }) { meme ->
                    Surface(
                        color = if (meme.id in selectedIds) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.height(156.dp).semantics {
                            selected = meme.id in selectedIds
                            onClick(label = copyLabel) { onSelectionChanged(setOf(meme.id), meme.id); controller.copy(listOf(meme.id)); true }
                        }
                    ) {
                        Column(Modifier.padding(8.dp)) {
                            MemeThumbnail(meme.id, controller, Modifier.fillMaxWidth().height(112.dp))
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(meme.title, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                if (meme.favorite) Icon(Icons.Outlined.Star, stringResource(Res.string.favorite), Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
            val start = boxStart
            val end = boxEnd
            if (start != null && end != null) Canvas(Modifier.matchParentSize()) {
                val rect = selectionRectangle(start - Offset(0f, scrollY()), end)
                drawRect(selectionColor.copy(alpha = 0.15f), rect.topLeft, rect.size)
                drawRect(selectionColor, rect.topLeft, rect.size, style = Stroke(1.dp.toPx()))
            }
            menuPosition?.let { point ->
                Box(Modifier.offset { IntOffset(point.x.toInt(), point.y.toInt()) }) {
                    DropdownMenu(expanded = true, onDismissRequest = { menuPosition = null }) {
                        DropdownMenuItem(text = { Text(copyLabel) }, enabled = picked.isNotEmpty(), onClick = { menuPosition = null; controller.copy(selectedIds.toList()) })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.favorite)) }, enabled = picked.isNotEmpty() && picked.none { it.deleted }, onClick = { menuPosition = null; controller.favorite(selectedIds.toList(), true) })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.unfavorite)) }, enabled = picked.isNotEmpty() && picked.none { it.deleted }, onClick = { menuPosition = null; controller.favorite(selectedIds.toList(), false) })
                        DropdownMenuItem(text = { Text(stringResource(if (picked.isNotEmpty() && picked.all { it.deleted }) Res.string.restore_meme else Res.string.move_to_trash)) }, enabled = picked.isNotEmpty(), onClick = { menuPosition = null; controller.trash(selectedIds.toList(), !picked.all { it.deleted }) })
                        HorizontalDivider()
                        DropdownMenuItem(text = { Text(stringResource(Res.string.select_all)) }, onClick = { menuPosition = null; onSelectionChanged(orderedIds.toSet(), orderedIds.firstOrNull()) })
                        DropdownMenuItem(text = { Text(stringResource(Res.string.clear_selection)) }, enabled = picked.isNotEmpty(), onClick = { menuPosition = null; onSelectionChanged(emptySet(), null) })
                    }
                }
            }
        }
        }
    }
}
