package app.stickerkaleidoscope.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LibraryState(
    val items: List<Meme> = emptyList(),
    val busy: Boolean = false,
    val loaded: Boolean = false,
    val error: LibraryError? = null,
    val report: ImportReport? = null
)

/** UI orchestration only: file, database and image work stay behind platform interfaces. */
class LibraryController(
    private val repository: LibraryRepository,
    private val picker: ImportPicker,
    private val scope: CoroutineScope
) {
    private val mutableState = MutableStateFlow(LibraryState())
    val state = mutableState.asStateFlow()

    fun refresh() = execute { }
    fun importFiles() = execute { import(picker.chooseFiles()) }
    fun importDirectory() = execute { import(picker.chooseDirectory()) }
    fun save(id: String, title: String, tags: List<String>) = execute { repository.update(id, title, tags) }
    fun favorite(meme: Meme) = execute { repository.setFavorite(meme.id, !meme.favorite) }
    fun trash(meme: Meme) = execute { repository.setDeleted(meme.id, !meme.deleted) }
    suspend fun thumbnail(id: String) = repository.thumbnail(id)

    private suspend fun import(paths: List<String>) {
        if (paths.isNotEmpty()) {
            val report = repository.importFiles(paths)
            mutableState.update { it.copy(report = report) }
        }
    }

    private fun execute(operation: suspend () -> Unit): Job? {
        if (mutableState.value.busy) return null
        mutableState.update { it.copy(busy = true, error = null, report = null) }
        return scope.launch {
            try {
                operation()
                val items = repository.list(filter = LibraryFilter.ALL) + repository.list(filter = LibraryFilter.TRASH)
                mutableState.update { it.copy(items = items, loaded = true) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: LibraryException) {
                mutableState.update { it.copy(error = failure.reason) }
            } catch (_: Exception) {
                mutableState.update { it.copy(error = LibraryError.STORAGE) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
