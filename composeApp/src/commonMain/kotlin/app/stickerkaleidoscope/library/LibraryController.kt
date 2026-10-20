package app.stickerkaleidoscope.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class LibraryState(
    val items: List<Meme> = emptyList(),
    val busy: Boolean = false,
    val loaded: Boolean = false,
    val error: LibraryError? = null,
    val report: ImportReport? = null,
    val copiedCount: Int? = null,
    val progress: Float? = null,
    val progressText: String? = null
)

/** UI orchestration only: file, database and image work stay behind platform interfaces. */
class LibraryController(
    private val repository: LibraryRepository,
    private val picker: ImportPicker,
    private val scope: CoroutineScope,
    private val clipboard: LibraryClipboard? = null,
    private val aiAnalyzer: AiAnalyzer? = null
) {
    private val mutableState = MutableStateFlow(LibraryState())
    val state = mutableState.asStateFlow()

    fun refresh() = execute { }
    fun importFiles() = chooseAndImport { picker.chooseFiles() }
    fun importDirectory() = chooseAndImport { picker.chooseDirectory() }
    fun importPaths(paths: List<String>) = chooseAndImport { picker.expandPaths(paths) }
    fun save(id: String, title: String, tags: List<String>) = execute { repository.update(id, title, tags) }
    fun favorite(meme: Meme) = execute { repository.setFavorite(meme.id, !meme.favorite) }
    fun trash(meme: Meme) = execute { repository.setDeleted(meme.id, !meme.deleted) }
    fun favorite(ids: List<String>, value: Boolean) = execute { repository.setFavorites(ids, value) }
    fun trash(ids: List<String>, value: Boolean) = execute { repository.setDeleted(ids, value) }
    fun tags(ids: List<String>, tags: List<String>) = execute { repository.setTags(ids, tags) }
    fun analyze(ids: List<String>, overwrite: Boolean = false) = execute {
        val analyzer = aiAnalyzer ?: throw LibraryException(LibraryError.STORAGE)
        val existing = repository.list().associateBy { it.id }
        repository.originals(ids).forEachIndexed { index, file ->
            if (!overwrite && existing[ids[index]]?.tags?.isNotEmpty() == true) return@forEachIndexed
            val tags = analyzer.analyze(file)
            if (tags.isNotEmpty()) repository.setTags(listOf(ids[index]), tags)
        }
    }
    fun copy(ids: List<String>) = execute(reload = false) {
        val files = repository.originals(ids)
        if (files.isNotEmpty()) {
            val destination = clipboard ?: throw LibraryException(LibraryError.CLIPBOARD)
            destination.copy(files)
            mutableState.update { it.copy(copiedCount = files.size) }
        }
    }
    suspend fun thumbnail(id: String) = repository.thumbnail(id)

    private fun chooseAndImport(choose: suspend () -> List<String>): Job? = scope.launch {
        try {
            val paths = choose()
            if (paths.isNotEmpty()) execute { import(paths) }?.join()
        } catch (_: CancellationException) {
            throw CancellationException()
        }
    }

    private suspend fun import(paths: List<String>) {
        if (paths.isNotEmpty()) {
            var report = ImportReport()
            paths.forEachIndexed { index, path ->
                val next = repository.importFiles(listOf(path))
                report = ImportReport(report.imported + next.imported, report.duplicates + next.duplicates, report.failures + next.failures)
                val displayName = path.substringAfterLast('/').substringAfterLast('/')
                mutableState.update { it.copy(progress = (index + 1).toFloat() / paths.size, progressText = "${index + 1}/${paths.size}: $displayName", report = report) }
                yield()
            }
        }
    }

    private fun execute(reload: Boolean = true, operation: suspend () -> Unit): Job? {
        if (mutableState.value.busy) return null
        mutableState.update { it.copy(busy = true, progress = null, progressText = null, error = null, report = null, copiedCount = null) }
        return scope.launch {
            try {
                withContext(Dispatchers.Default) { operation() }
                if (reload) {
                    val items = withContext(Dispatchers.Default) {
                        repository.list(filter = LibraryFilter.ALL) + repository.list(filter = LibraryFilter.TRASH)
                    }
                    mutableState.update { it.copy(items = items, loaded = true) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: LibraryException) {
                mutableState.update { it.copy(error = failure.reason) }
            } catch (_: Exception) {
                mutableState.update { it.copy(error = LibraryError.STORAGE) }
            } finally {
                mutableState.update { it.copy(busy = false, progress = null, progressText = null) }
            }
        }
    }
}

