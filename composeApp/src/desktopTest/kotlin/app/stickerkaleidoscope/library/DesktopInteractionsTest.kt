package app.stickerkaleidoscope.library

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.datatransfer.*
import java.awt.image.BufferedImage
import java.io.InputStream
import java.nio.file.Files
import java.sql.DriverManager
import javax.imageio.ImageIO
import kotlin.test.*

class DesktopInteractionsTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun fixture(name: String) = temporary.root.toPath().resolve(name).also { target ->
        javaClass.getResourceAsStream("/fixtures/$name")!!.use { Files.copy(it, target) }
    }

    @Test fun `static clipboard includes alpha preserving png image and original file flavors`() = runBlocking<Unit> {
        val file = fixture("transparent.webp")
        val clipboard = Clipboard("isolated-test")
        DesktopLibraryClipboard(clipboard).copy(listOf(OriginalFile(file.toString(), "webp")))
        val content = clipboard.getContents(null)
        assertTrue(content.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
        assertTrue(content.isDataFlavorSupported(DataFlavor.imageFlavor))
        val png = content.getTransferData(LibraryFileTransferable.PNG) as InputStream
        val image = png.use(ImageIO::read)
        assertTrue(image.colorModel.hasAlpha())
        assertTrue((image.getRGB(0, 0) ushr 24) in 1..254)
        assertEquals(listOf(file.toFile()), content.getTransferData(DataFlavor.javaFileListFlavor))
        assertFailsWith<UnsupportedFlavorException> { content.getTransferData(DataFlavor.stringFlavor) }
    }

    @Test fun `gif clipboard retains exact animation bytes without advertising a static image`() = runBlocking<Unit> {
        val file = fixture("animated.gif")
        val content = LibraryFileTransferable(listOf(OriginalFile(file.toString(), "gif")))
        assertFalse(content.isDataFlavorSupported(DataFlavor.imageFlavor))
        assertFalse(content.isDataFlavorSupported(LibraryFileTransferable.PNG))
        val bytes = (content.getTransferData(LibraryFileTransferable.GIF) as InputStream).use { it.readBytes() }
        assertContentEquals(Files.readAllBytes(file), bytes)
        assertContentEquals(bytes, (content.getTransferData(LibraryFileTransferable.GIF) as InputStream).use { it.readBytes() })
    }

    @Test fun `multiple clipboard items are a file list and can also be decoded as dropped files`() {
        val first = fixture("transparent.webp")
        val second = fixture("animated.gif")
        val content = LibraryFileTransferable(listOf(OriginalFile(first.toString(), "webp"), OriginalFile(second.toString(), "gif")))
        assertContentEquals(arrayOf(DataFlavor.javaFileListFlavor), content.transferDataFlavors)
        assertEquals(listOf(first.toString(), second.toString()), droppedPaths(content))
        assertTrue(droppedPaths(StringSelection("https://example.com/image.png")).isEmpty())
    }

    @Test fun `dropped directories expand recursively and normalize duplicate paths`() = runBlocking<Unit> {
        val folder = temporary.newFolder("incoming").toPath()
        val nested = Files.createDirectories(folder.resolve("nested"))
        val first = Files.copy(fixture("transparent.webp"), folder.resolve("renamed.png"))
        val second = Files.copy(fixture("animated.gif"), nested.resolve("reaction.gif"))
        Files.writeString(folder.resolve("readme.txt"), "ignored")
        val paths = DesktopImportPicker().expandPaths(listOf(folder.toString(), first.toString()))
        assertEquals(setOf(first.toString(), second.toString()), paths.toSet())
        assertEquals(2, paths.size)
    }

    @Test fun `rectangle selection works in every drag direction and supports additive selection`() {
        val bounds = listOf("a" to Rect(0f, 0f, 100f, 100f), "b" to Rect(110f, 0f, 210f, 100f), "c" to Rect(0f, 110f, 100f, 210f))
        val area = selectionRectangle(Offset(180f, 80f), Offset(20f, 20f))
        assertEquals(setOf("a", "b"), boxSelection(bounds, area, emptySet(), false))
        assertEquals(setOf("a", "b", "c"), boxSelection(bounds, area, setOf("c"), true))
        assertTrue(boxSelection(bounds, Rect(220f, 220f, 230f, 230f), setOf("a"), false).isEmpty())
        val order = listOf("a", "b", "c", "d")
        assertEquals(setOf("b", "c", "d"), clickSelection(order, setOf("b"), "b", "d", false, true))
        assertEquals(setOf("a", "b", "c", "d"), clickSelection(order, setOf("a"), "b", "d", true, true))
        assertEquals(emptySet(), clickSelection(order, setOf("b"), "b", "b", true, false))
    }

    @Test fun `batch favorites roll back together when journaling any selected item fails`() = runBlocking<Unit> {
        val root = temporary.newFolder("library").toPath()
        val repository = DesktopLibraryRepository(root)
        repository.importFiles(listOf(fixture("transparent.webp").toString(), fixture("animated.gif").toString()))
        val items = repository.list()
        DriverManager.getConnection("jdbc:sqlite:${root.resolve("library.db")}").use { db ->
            db.createStatement().use { it.execute("CREATE TRIGGER reject_second BEFORE INSERT ON changes WHEN NEW.meme_id='${items[1].id}' BEGIN SELECT RAISE(ABORT,'test failure'); END") }
        }
        assertFailsWith<LibraryException> { repository.setFavorites(items.map { it.id }, true) }
        assertEquals(items, repository.list())
    }

    @Test fun `controller copies selected originals without reloading and reports clipboard failures`() = runBlocking<Unit> {
        val repository = DesktopLibraryRepository(temporary.newFolder("library").toPath())
        repository.importFiles(listOf(fixture("transparent.webp").toString()))
        val clipboard = object : LibraryClipboard {
            var failure = false
            var copied = emptyList<OriginalFile>()
            override suspend fun copy(files: List<OriginalFile>) {
                if (failure) throw LibraryException(LibraryError.CLIPBOARD)
                copied = files
            }
        }
        val controller = LibraryController(repository, DesktopImportPicker(), CoroutineScope(coroutineContext), clipboard)
        controller.refresh()!!.join()
        val id = controller.state.value.items.single().id
        controller.copy(listOf(id, id))!!.join()
        assertEquals(1, clipboard.copied.size)
        assertEquals(1, controller.state.value.copiedCount)
        clipboard.failure = true
        controller.copy(listOf(id))!!.join()
        assertEquals(LibraryError.CLIPBOARD, controller.state.value.error)
        assertNull(controller.state.value.copiedCount)
        assertEquals(1, controller.state.value.items.size)
        assertFalse(controller.state.value.busy)
    }
}
