package app.stickerkaleidoscope.library

import androidx.compose.runtime.Composable

@Composable
internal expect fun ImportDropHost(controller: LibraryController, content: @Composable () -> Unit)
