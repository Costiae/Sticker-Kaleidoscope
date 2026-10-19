package app.stickerkaleidoscope.library

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.stickerkaleidoscope.resources.*
import org.jetbrains.compose.resources.stringResource

internal fun List<Meme>.matching(query: String, filter: LibraryFilter): List<Meme> = filter { meme ->
    val inSection = when (filter) {
        LibraryFilter.ALL -> !meme.deleted
        LibraryFilter.FAVORITES -> !meme.deleted && meme.favorite
        LibraryFilter.UNCATEGORIZED -> !meme.deleted && meme.tags.isEmpty()
        LibraryFilter.TRASH -> meme.deleted
    }
    val term = query.trim()
    inSection && (term.isEmpty() || meme.title.contains(term, ignoreCase = true) ||
        meme.originalName.contains(term, ignoreCase = true) || meme.tags.any { it.contains(term, ignoreCase = true) })
}

@Composable
internal fun LibraryActions(controller: LibraryController, state: LibraryState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { controller.importFiles() }, enabled = !state.busy) {
            Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.import_files))
        }
        OutlinedButton(onClick = { controller.importDirectory() }, enabled = !state.busy) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(Res.string.import_folder))
        }
        IconButton(onClick = { controller.refresh() }, enabled = !state.busy) {
            Icon(Icons.Outlined.Refresh, stringResource(Res.string.refresh_library))
        }
    }
    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { reason ->
        Text(stringResource(reason.resource()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    state.report?.let { report ->
        Text(
            stringResource(Res.string.import_report, report.imported, report.duplicates, report.failures.size),
            style = MaterialTheme.typography.bodySmall
        )
        if (report.failures.isNotEmpty()) {
            var expanded by remember(report) { mutableStateOf(false) }
            TextButton(onClick = { expanded = !expanded }) {
                Text(stringResource(if (expanded) Res.string.hide_failures else Res.string.show_failures))
            }
            if (expanded) Column(Modifier.heightIn(max = 120.dp).verticalScroll(rememberScrollState())) {
                report.failures.forEach { failure ->
                    Text(
                        stringResource(Res.string.import_failure, failure.name, stringResource(failure.reason.resource())),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun LibraryError.resource() = when (this) {
    LibraryError.STORAGE -> Res.string.error_storage
    LibraryError.INVALID_IMAGE -> Res.string.error_invalid_image
    LibraryError.TOO_LARGE -> Res.string.error_too_large
    LibraryError.MISSING_FILE -> Res.string.error_missing_file
}

@Composable
internal fun MemeGrid(items: List<Meme>, selectedId: String?, controller: LibraryController, onSelect: (String) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(132.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(items, key = { it.id }) { meme ->
            Surface(
                color = if (selectedId == meme.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.clickable { onSelect(meme.id) }
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
}

@Composable
internal fun MemeProperties(meme: Meme?, controller: LibraryController, busy: Boolean) {
    if (meme == null) {
        Text(stringResource(Res.string.properties_hint), style = MaterialTheme.typography.bodySmall)
        return
    }
    var title by remember(meme.id, meme.title) { mutableStateOf(meme.title) }
    var tags by remember(meme.id, meme.tags) { mutableStateOf(meme.tags.joinToString(", ")) }
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MemeThumbnail(meme.id, controller, Modifier.fillMaxWidth().height(176.dp))
        Text(meme.originalName, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(Res.string.image_metadata, meme.format.uppercase(), meme.width, meme.height, meme.byteSize), style = MaterialTheme.typography.labelSmall)
        OutlinedTextField(title, { title = it }, label = { Text(stringResource(Res.string.meme_title)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy, singleLine = true)
        OutlinedTextField(tags, { tags = it }, label = { Text(stringResource(Res.string.meme_tags)) }, supportingText = { Text(stringResource(Res.string.tags_hint)) }, modifier = Modifier.fillMaxWidth(), enabled = !busy)
        Button(onClick = { controller.save(meme.id, title, tags.split(',', '，', '\n')) }, enabled = !busy) {
            Text(stringResource(Res.string.save_changes))
        }
        OutlinedButton(onClick = { controller.favorite(meme) }, enabled = !busy && !meme.deleted) {
            Icon(if (meme.favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (meme.favorite) Res.string.unfavorite else Res.string.favorite))
        }
        OutlinedButton(onClick = { controller.trash(meme) }, enabled = !busy) {
            Icon(if (meme.deleted) Icons.Outlined.RestoreFromTrash else Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(if (meme.deleted) Res.string.restore_meme else Res.string.move_to_trash))
        }
    }
}

@Composable
internal expect fun MemeThumbnail(id: String, controller: LibraryController, modifier: Modifier)
