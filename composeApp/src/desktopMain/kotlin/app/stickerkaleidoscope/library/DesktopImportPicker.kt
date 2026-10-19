package app.stickerkaleidoscope.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Files
import java.util.Locale
import javax.swing.JFileChooser
import app.stickerkaleidoscope.resources.*
import org.jetbrains.compose.resources.getString

class DesktopImportPicker : ImportPicker {
    override suspend fun chooseFiles(): List<String> = withContext(Dispatchers.Swing) {
        val dialog = FileDialog(null as Frame?, getString(Res.string.import_files), FileDialog.LOAD)
        try {
            dialog.isMultipleMode = true
            dialog.isVisible = true
            dialog.files.map { it.absolutePath }
        } finally { dialog.dispose() }
    }

    override suspend fun chooseDirectory(): List<String> {
        val directory = withContext(Dispatchers.Swing) {
            val title = getString(Res.string.import_folder)
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = title
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile.toPath() else null
        } ?: return emptyList()
        return withContext(Dispatchers.IO) {
            Files.walk(directory).use { files ->
                files.filter { Files.isRegularFile(it) && !Files.isSymbolicLink(it) }
                    .filter { it.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("png", "jpg", "jpeg", "gif", "webp") }
                    .map { it.toAbsolutePath().toString() }.toList()
            }
        }
    }
}
