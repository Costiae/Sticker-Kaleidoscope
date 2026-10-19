package app.stickerkaleidoscope.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.imageio.ImageIO

class DesktopLibraryClipboard(private val destination: Clipboard? = null) : LibraryClipboard {
    override suspend fun copy(files: List<OriginalFile>) {
        try {
            val data = withContext(Dispatchers.IO) { LibraryFileTransferable(files) }
            withContext(Dispatchers.Swing) {
                val clipboard = destination ?: Toolkit.getDefaultToolkit().systemClipboard
                if (destination == null && System.getProperty("os.name").startsWith("Windows")) {
                    (SystemFlavorMap.getDefaultFlavorMap() as? SystemFlavorMap)?.let { map ->
                        map.addUnencodedNativeForFlavor(LibraryFileTransferable.PNG, "PNG")
                        map.addUnencodedNativeForFlavor(LibraryFileTransferable.GIF, "GIF")
                    }
                }
                for (attempt in 0..2) {
                    try {
                        clipboard.setContents(data, null)
                        return@withContext
                    } catch (busy: IllegalStateException) {
                        if (attempt == 2) throw busy
                        delay(100)
                    }
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (failure: Exception) { throw LibraryException(LibraryError.CLIPBOARD, failure) }
    }
}

/** GIF exposes its original bytes and file, never a flattened imageFlavor. */
internal class LibraryFileTransferable(originals: List<OriginalFile>) : Transferable {
    private val files = originals.map { File(it.path) }
    private val single = originals.singleOrNull()
    private val animated = single?.format == "gif"
    private val bitmap: BufferedImage? = if (single != null && !animated) ImageIO.read(files.single()) else null
    private val encoded: ByteArray? = when {
        animated -> files.single().readBytes()
        bitmap != null -> ByteArrayOutputStream().use { output ->
            check(ImageIO.write(bitmap, "png", output))
            output.toByteArray()
        }
        else -> null
    }
    private val flavors = buildList {
        require(files.isNotEmpty() && files.all { it.isFile })
        add(DataFlavor.javaFileListFlavor)
        if (encoded != null) add(if (animated) GIF else PNG)
        if (bitmap != null) add(DataFlavor.imageFlavor)
    }

    override fun getTransferDataFlavors(): Array<DataFlavor> = flavors.toTypedArray()
    override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor in flavors
    override fun getTransferData(flavor: DataFlavor): Any = when {
        !isDataFlavorSupported(flavor) -> throw UnsupportedFlavorException(flavor)
        flavor == DataFlavor.javaFileListFlavor -> files.toList()
        flavor == DataFlavor.imageFlavor -> bitmap!!
        else -> ByteArrayInputStream(encoded!!)
    }

    companion object {
        val PNG = DataFlavor("image/png;class=java.io.InputStream", "PNG")
        val GIF = DataFlavor("image/gif;class=java.io.InputStream", "GIF")
    }
}
