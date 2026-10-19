package app.stickerkaleidoscope.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.test.*

class LibraryControllerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `controller imports edits and restores through the same API used by the UI`() = runBlocking<Unit> {
        val file = temporary.newFile("猫.png")
        ImageIO.write(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", file)
        val repository = DesktopLibraryRepository(temporary.newFolder().toPath())
        val picker = object : ImportPicker {
            override suspend fun chooseFiles() = listOf(file.absolutePath)
            override suspend fun chooseDirectory() = emptyList<String>()
        }
        val controller = LibraryController(repository, picker, CoroutineScope(coroutineContext))
        controller.refresh()!!.join()
        assertTrue(controller.state.value.loaded)
        assertTrue(controller.state.value.items.isEmpty())
        controller.importFiles()!!.join()
        assertEquals(1, controller.state.value.report?.imported)
        val meme = controller.state.value.items.single()
        controller.save(meme.id, "收到", listOf("猫", "回复"))!!.join()
        assertEquals(meme.id, controller.state.value.items.matching("回复", LibraryFilter.ALL).single().id)
        assertTrue(controller.state.value.items.matching("", LibraryFilter.UNCATEGORIZED).isEmpty())
        controller.favorite(controller.state.value.items.single())!!.join()
        assertEquals(1, controller.state.value.items.matching("", LibraryFilter.FAVORITES).size)
        controller.trash(controller.state.value.items.single())!!.join()
        assertTrue(controller.state.value.items.matching("", LibraryFilter.ALL).isEmpty())
        assertEquals(1, controller.state.value.items.matching("", LibraryFilter.TRASH).size)
        controller.trash(controller.state.value.items.single())!!.join()
        assertEquals(1, controller.state.value.items.matching("收到", LibraryFilter.ALL).size)
        assertFalse(controller.state.value.busy)
        assertNull(controller.state.value.error)
        controller.importDirectory()!!.join() // Cancelling a picker is harmless.
        assertEquals(1, controller.state.value.items.size)
        assertNull(controller.state.value.report)
    }

    @Test fun `storage errors remain actionable without discarding a previously loaded library`() = runBlocking<Unit> {
        val root = temporary.newFolder().toPath()
        val delegate = DesktopLibraryRepository(root)
        var fail = false
        val repository = object : LibraryRepository by delegate {
            override suspend fun list(query: String, filter: LibraryFilter): List<Meme> {
                if (fail) throw LibraryException(LibraryError.STORAGE)
                return delegate.list(query, filter)
            }
        }
        val picker = object : ImportPicker {
            override suspend fun chooseFiles() = emptyList<String>()
            override suspend fun chooseDirectory() = emptyList<String>()
        }
        val controller = LibraryController(repository, picker, CoroutineScope(coroutineContext))
        controller.refresh()!!.join()
        fail = true
        controller.refresh()!!.join()
        assertTrue(controller.state.value.loaded)
        assertFalse(controller.state.value.busy)
        assertEquals(LibraryError.STORAGE, controller.state.value.error)
        fail = false
        controller.refresh()!!.join()
        assertNull(controller.state.value.error)
    }
}
