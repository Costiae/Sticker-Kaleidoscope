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
enum class LibraryError { STORAGE, INVALID_IMAGE, TOO_LARGE, MISSING_FILE }
class LibraryException(val reason: LibraryError, cause: Throwable? = null) : Exception(reason.name, cause)

data class ImportFailure(val name: String, val reason: LibraryError)
data class ImportReport(val imported: Int = 0, val duplicates: Int = 0, val failures: List<ImportFailure> = emptyList())

interface LibraryRepository {
    suspend fun list(query: String = "", filter: LibraryFilter = LibraryFilter.ALL): List<Meme>
    suspend fun importFiles(paths: List<String>): ImportReport
    suspend fun update(id: String, title: String, tags: List<String>)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun setDeleted(id: String, deleted: Boolean)
    suspend fun thumbnail(id: String): ByteArray?
}

interface ImportPicker {
    suspend fun chooseFiles(): List<String>
    suspend fun chooseDirectory(): List<String>
}
