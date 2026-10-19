package app.stickerkaleidoscope.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class MemeGridInputTest {
    @get:Rule val ui = createComposeRule()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var selected by mutableStateOf(emptySet<String>())
    private var focused by mutableStateOf<String?>(null)
    private val memes = (0 until 96).map { index ->
        Meme("meme-$index", "hash-$index", "Meme $index", "$index.png", "png", 16, 16, 100,
            false, false, emptyList(), index.toLong(), 1)
    }
    private val repository = object : LibraryRepository {
        override suspend fun list(query: String, filter: LibraryFilter) = memes
        override suspend fun importFiles(paths: List<String>) = ImportReport()
        override suspend fun update(id: String, title: String, tags: List<String>) = Unit
        override suspend fun setFavorite(id: String, favorite: Boolean) = Unit
        override suspend fun setDeleted(id: String, deleted: Boolean) = Unit
        override suspend fun thumbnail(id: String): ByteArray? = null
        override suspend fun originals(ids: List<String>) = emptyList<OriginalFile>()
        override suspend fun setFavorites(ids: List<String>, favorite: Boolean) = Unit
        override suspend fun setDeleted(ids: List<String>, deleted: Boolean) = Unit
    }

    @After fun dispose() { scope.cancel() }

    private fun content() {
        val picker = object : ImportPicker {
            override suspend fun chooseFiles() = emptyList<String>()
            override suspend fun chooseDirectory() = emptyList<String>()
        }
        val controller = LibraryController(repository, picker, scope)
        ui.setContent {
            MaterialTheme {
                Box(Modifier.size(480.dp, 480.dp)) {
                    MemeGrid(memes, selected, focused, controller) { ids, id -> selected = ids; focused = id }
                }
            }
        }
    }

    @Test fun `unselected blank gutter captures a marquee without first clicking a card`() {
        content()
        ui.onNodeWithTag("meme-grid").performMouseInput {
            moveTo(Offset(2f, 2f))
            press()
            moveTo(Offset(width - 2f, 170f))
            release()
        }
        ui.runOnIdle {
            assertTrue("meme-0" in selected)
            assertTrue(selected.size >= 3)
        }
    }

    @Test fun `holding marquee while wheeling retains disposed rows and recomputes on reverse scrolling`() {
        content()
        val grid = ui.onNodeWithTag("meme-grid")
        grid.performMouseInput {
            moveTo(Offset(2f, 2f))
            press()
            moveTo(Offset(width - 2f, height - 8f))
        }
        var initially = emptySet<String>()
        ui.runOnIdle { initially = selected; assertTrue(initially.isNotEmpty()) }
        grid.performMouseInput { scroll(600f) }
        ui.waitForIdle()
        var extended = emptySet<String>()
        ui.runOnIdle {
            extended = selected
            assertTrue(extended.containsAll(initially))
            assertTrue(extended.size > initially.size, "Wheel must extend the content rectangle while the mouse stays still")
        }
        grid.performMouseInput { scroll(-600f) }
        ui.waitForIdle()
        var beforeRelease = emptySet<String>()
        ui.runOnIdle { assertTrue(selected.size < extended.size); beforeRelease = selected }
        grid.performMouseInput { release() }
        ui.runOnIdle { assertEquals(beforeRelease, selected) }
    }
}
