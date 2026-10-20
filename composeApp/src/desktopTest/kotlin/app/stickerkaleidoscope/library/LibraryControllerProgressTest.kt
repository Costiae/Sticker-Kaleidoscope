package app.stickerkaleidoscope.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LibraryControllerProgressTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun `import exposes determinate progress while first file is still running`() = runBlocking {
        val delegate = DesktopLibraryRepository(temporary.newFolder().toPath())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repository = object : LibraryRepository by delegate {
            override suspend fun importFiles(paths: List<String>): ImportReport {
                entered.complete(Unit)
                release.await()
                return ImportReport()
            }
        }
        val picker = object : ImportPicker {
            override suspend fun chooseFiles() = listOf("first.png", "second.png")
            override suspend fun chooseDirectory() = emptyList<String>()
        }
        val controller = LibraryController(repository, picker, CoroutineScope(SupervisorJob() + Dispatchers.Default))

        val job = controller.importFiles()!!
        entered.await()
        assertTrue(controller.state.value.busy)
        assertEquals(0f, controller.state.value.progress)
        assertEquals(0, controller.state.value.progressCurrent)
        assertEquals(2, controller.state.value.progressTotal)

        release.complete(Unit)
        job.join()
    }
}
