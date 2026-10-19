package app.stickerkaleidoscope

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension
import app.stickerkaleidoscope.resources.Res
import app.stickerkaleidoscope.resources.app_name
import org.jetbrains.compose.resources.stringResource
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import app.stickerkaleidoscope.library.DesktopLibraryRepository
import app.stickerkaleidoscope.library.DesktopImportPicker
import app.stickerkaleidoscope.library.LibraryController
import app.stickerkaleidoscope.library.DesktopLibraryClipboard

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = stringResource(Res.string.app_name),
        state = rememberWindowState(width = 1180.dp, height = 760.dp)
    ) {
        window.minimumSize = Dimension(960, 600)
        val scope = rememberCoroutineScope()
        val controller = remember(scope) {
            LibraryController(DesktopLibraryRepository(DesktopLibraryRepository.defaultRoot()), DesktopImportPicker(), scope, DesktopLibraryClipboard())
        }
        App(controller)
    }
}
