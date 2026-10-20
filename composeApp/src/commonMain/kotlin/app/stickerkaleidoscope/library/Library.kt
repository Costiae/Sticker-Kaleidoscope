package app.stickerkaleidoscope.library

data class Meme(
    val id: String,
    val hash: String,
    val title: String,
    val originalName: String,
    val format: String,
    val width: Int,
    val height: Int,
    val byteSize: Long,
    val favorite: Boolean,
    val deleted: Boolean,
    val tags: List<String>,
    val createdAt: Long,
    val revision: Long
)

enum class LibraryFilter { ALL, FAVORITES, UNCATEGORIZED, TRASH }
enum class LibraryError { STORAGE, INVALID_IMAGE, TOO_LARGE, MISSING_FILE, CLIPBOARD }
class LibraryException(val reason: LibraryError, cause: Throwable? = null) : Exception(reason.name, cause)

data class ImportFailure(val name: String, val reason: LibraryError)
data class ImportReport(val imported: Int = 0, val duplicates: Int = 0, val failures: List<ImportFailure> = emptyList())
data class OriginalFile(val path: String, val format: String)

interface LibraryClipboard {
    suspend fun copy(files: List<OriginalFile>)
}

interface LibraryRepository {
    suspend fun list(query: String = "", filter: LibraryFilter = LibraryFilter.ALL): List<Meme>
    suspend fun importFiles(paths: List<String>): ImportReport
    suspend fun update(id: String, title: String, tags: List<String>)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun setDeleted(id: String, deleted: Boolean)
    suspend fun thumbnail(id: String): ByteArray?
    suspend fun originals(ids: List<String>): List<OriginalFile>
    suspend fun setFavorites(ids: List<String>, favorite: Boolean)
    suspend fun setDeleted(ids: List<String>, deleted: Boolean)
    suspend fun setTags(ids: List<String>, tags: List<String>)
}

interface ImportPicker {
    suspend fun chooseFiles(): List<String>
    suspend fun chooseDirectory(): List<String>
    suspend fun expandPaths(paths: List<String>): List<String> = paths
}
