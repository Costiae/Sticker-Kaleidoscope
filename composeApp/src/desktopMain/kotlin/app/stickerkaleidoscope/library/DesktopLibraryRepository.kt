package app.stickerkaleidoscope.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.coroutines.coroutineContext

/** Managed originals are immutable. SQLite stores relative asset paths and deletion tombstones. */
class DesktopLibraryRepository(private val root: Path) : LibraryRepository {
    private val mutex = Mutex()

    private suspend fun <T> database(block: suspend (Connection) -> T): T = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                Files.createDirectories(root)
                DriverManager.getConnection("jdbc:sqlite:${root.resolve("library.db").toAbsolutePath()}").use { db ->
                    db.createStatement().use { statement ->
                        statement.execute("PRAGMA busy_timeout=5000")
                        statement.execute("PRAGMA foreign_keys=ON")
                        statement.execute("PRAGMA journal_mode=WAL")
                    }
                    initialize(db)
                    block(db)
                }
            } catch (failure: LibraryException) {
                throw failure
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                throw LibraryException(LibraryError.STORAGE, failure)
            }
        }
    }

    private fun initialize(db: Connection) {
        val version = db.createStatement().use { it.executeQuery("PRAGMA user_version").use { row -> row.next(); row.getInt(1) } }
        check(version <= 1) { "Unsupported database version: $version" }
        if (version == 1) return
        transaction(db) {
            db.createStatement().use { statement ->
                statement.execute("""CREATE TABLE memes (
                    id TEXT PRIMARY KEY, hash TEXT NOT NULL UNIQUE, title TEXT NOT NULL,
                    original_name TEXT NOT NULL, format TEXT NOT NULL,
                    width INTEGER NOT NULL, height INTEGER NOT NULL, byte_size INTEGER NOT NULL,
                    original_path TEXT NOT NULL, thumbnail_path TEXT NOT NULL,
                    favorite INTEGER NOT NULL DEFAULT 0, deleted INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, revision INTEGER NOT NULL DEFAULT 1
                )""")
                statement.execute("CREATE TABLE meme_tags (meme_id TEXT NOT NULL REFERENCES memes(id), tag TEXT NOT NULL, PRIMARY KEY(meme_id, tag))")
                statement.execute("""CREATE TABLE changes (
                    sequence INTEGER PRIMARY KEY AUTOINCREMENT, operation_id TEXT NOT NULL UNIQUE,
                    meme_id TEXT NOT NULL REFERENCES memes(id), operation TEXT NOT NULL,
                    revision INTEGER NOT NULL, created_at INTEGER NOT NULL
                )""")
                statement.execute("CREATE INDEX memes_active ON memes(deleted, created_at)")
                statement.execute("PRAGMA user_version=1")
            }
        }
    }

    override suspend fun list(query: String, filter: LibraryFilter): List<Meme> = database { db ->
        val condition = when (filter) {
            LibraryFilter.ALL -> "deleted=0"
            LibraryFilter.FAVORITES -> "deleted=0 AND favorite=1"
            LibraryFilter.UNCATEGORIZED -> "deleted=0 AND NOT EXISTS (SELECT 1 FROM meme_tags t WHERE t.meme_id=m.id)"
            LibraryFilter.TRASH -> "deleted=1"
        }
        // Literal substring matching: '%' and '_' in user text are not SQL wildcard operators.
        val search = query.trim()
        val pattern = "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        val sql = """SELECT m.* FROM memes m WHERE $condition AND (
            title LIKE ? ESCAPE '\' OR original_name LIKE ? ESCAPE '\' OR
            EXISTS(SELECT 1 FROM meme_tags t WHERE t.meme_id=m.id AND tag LIKE ? ESCAPE '\')
            ) ORDER BY created_at DESC, id"""
        db.prepareStatement(sql).use { statement ->
            (1..3).forEach { statement.setString(it, pattern) }
            statement.executeQuery().use { rows -> buildList { while (rows.next()) add(readMeme(db, rows)) } }
        }
    }

    private fun readMeme(db: Connection, row: ResultSet): Meme {
        val id = row.getString("id")
        val tags = db.prepareStatement("SELECT tag FROM meme_tags WHERE meme_id=? ORDER BY tag").use { statement ->
            statement.setString(1, id)
            statement.executeQuery().use { tags -> buildList { while (tags.next()) add(tags.getString(1)) } }
        }
        return Meme(id, row.getString("hash"), row.getString("title"), row.getString("original_name"),
            row.getString("format"), row.getInt("width"), row.getInt("height"), row.getLong("byte_size"),
            row.getBoolean("favorite"), row.getBoolean("deleted"), tags, row.getLong("created_at"), row.getLong("revision"))
    }

    override suspend fun importFiles(paths: List<String>): ImportReport = database { db ->
        var imported = 0
        var duplicates = 0
        val failures = mutableListOf<ImportFailure>()
        for (source in paths.distinct()) {
            coroutineContext.ensureActive()
            try {
                if (importOne(db, Path.of(source))) imported++ else duplicates++
            } catch (failure: LibraryException) {
                failures += ImportFailure(runCatching { Path.of(source).fileName.toString() }.getOrDefault(source), failure.reason)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failures += ImportFailure(runCatching { Path.of(source).fileName.toString() }.getOrDefault(source), LibraryError.STORAGE)
            }
        }
        ImportReport(imported, duplicates, failures)
    }

    private suspend fun importOne(db: Connection, source: Path): Boolean {
        if (!Files.isRegularFile(source)) throw LibraryException(LibraryError.MISSING_FILE)
        if (Files.size(source) > MAX_BYTES) throw LibraryException(LibraryError.TOO_LARGE)
        val staging = root.resolve("staging")
        Files.createDirectories(staging)
        val temporary = Files.createTempFile(staging, "import-", ".tmp")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var size = 0L
            Files.newInputStream(source).use { input ->
                Files.newOutputStream(temporary).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        if (size > MAX_BYTES) throw LibraryException(LibraryError.TOO_LARGE)
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            val existing = db.prepareStatement("SELECT id FROM memes WHERE hash=?").use { statement ->
                statement.setString(1, hash)
                statement.executeQuery().use { row -> if (row.next()) row.getString(1) else null }
            }
            // An import never silently restores a trashed item; restore is explicit.
            if (existing != null) return false
            val image = decode(temporary)
            val relativeOriginal = "originals/${hash.take(2)}/$hash.${image.format}"
            val relativeThumbnail = "thumbnails/${hash.take(2)}/$hash.png"
            val original = resolveManaged(relativeOriginal)
            val thumbnail = resolveManaged(relativeThumbnail)
            Files.createDirectories(original.parent)
            Files.createDirectories(thumbnail.parent)
            if (!Files.exists(original)) Files.move(temporary, original, StandardCopyOption.ATOMIC_MOVE)
            writeThumbnail(image.bitmap, thumbnail)
            val id = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            transaction(db) {
                db.prepareStatement("""INSERT INTO memes
                    (id,hash,title,original_name,format,width,height,byte_size,original_path,thumbnail_path,created_at,updated_at)
                    VALUES(?,?,?,?,?,?,?,?,?,?,?,?)""").use { statement ->
                    statement.setString(1, id)
                    statement.setString(2, hash)
                    statement.setString(3, source.fileName.toString().substringBeforeLast('.').take(200))
                    statement.setString(4, source.fileName.toString())
                    statement.setString(5, image.format)
                    statement.setInt(6, image.width)
                    statement.setInt(7, image.height)
                    statement.setLong(8, size)
                    statement.setString(9, relativeOriginal)
                    statement.setString(10, relativeThumbnail)
                    statement.setLong(11, now)
                    statement.setLong(12, now)
                    statement.executeUpdate()
                }
                recordChange(db, id, "created")
            }
            return true
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private data class Decoded(val bitmap: BufferedImage, val format: String, val width: Int, val height: Int)

    private fun decode(path: Path): Decoded {
        try {
            ImageIO.createImageInputStream(path.toFile()).use { input ->
                if (input == null) throw LibraryException(LibraryError.INVALID_IMAGE)
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) throw LibraryException(LibraryError.INVALID_IMAGE)
                val reader = readers.next()
                try {
                    reader.input = input
                    val format = reader.formatName.lowercase().let { if (it == "jpeg") "jpg" else it }
                    if (format !in setOf("png", "jpg", "gif", "webp")) throw LibraryException(LibraryError.INVALID_IMAGE)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION || width.toLong() * height > MAX_PIXELS) {
                        throw LibraryException(LibraryError.TOO_LARGE)
                    }
                    val parameters = reader.defaultReadParam
                    val sample = maxOf(width, height).div(THUMBNAIL_SIZE).coerceAtLeast(1)
                    parameters.setSourceSubsampling(sample, sample, 0, 0)
                    return Decoded(reader.read(0, parameters), format, width, height)
                } finally { reader.dispose() }
            }
        } catch (failure: LibraryException) { throw failure }
        catch (failure: Exception) { throw LibraryException(LibraryError.INVALID_IMAGE, failure) }
    }

    private fun writeThumbnail(source: BufferedImage, target: Path) {
        val scale = minOf(1.0, THUMBNAIL_SIZE.toDouble() / maxOf(source.width, source.height))
        val thumbnail = BufferedImage((source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), BufferedImage.TYPE_INT_ARGB)
        thumbnail.createGraphics().let { graphics ->
            try {
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                graphics.drawImage(source, 0, 0, thumbnail.width, thumbnail.height, null)
            } finally { graphics.dispose() }
        }
        val temporary = Files.createTempFile(target.parent, "thumbnail-", ".png")
        try {
            check(ImageIO.write(thumbnail, "png", temporary.toFile()))
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temporary) }
    }

    override suspend fun update(id: String, title: String, tags: List<String>) = database { db ->
        val normalized = tags.map { it.trim().take(80) }.filter { it.isNotEmpty() }.distinct().take(50)
        transaction(db) {
            db.prepareStatement("UPDATE memes SET title=?, updated_at=?, revision=revision+1 WHERE id=?").use {
                it.setString(1, title.trim().take(200)); it.setLong(2, System.currentTimeMillis()); it.setString(3, id)
                check(it.executeUpdate() == 1)
            }
            db.prepareStatement("DELETE FROM meme_tags WHERE meme_id=?").use { it.setString(1, id); it.executeUpdate() }
            db.prepareStatement("INSERT INTO meme_tags(meme_id,tag) VALUES(?,?)").use { statement ->
                normalized.forEach { tag -> statement.setString(1, id); statement.setString(2, tag); statement.addBatch() }
                statement.executeBatch()
            }
            recordChange(db, id, "updated")
        }
    }

    override suspend fun setFavorite(id: String, favorite: Boolean) = setFlag(id, "favorite", favorite, "updated")
    override suspend fun setDeleted(id: String, deleted: Boolean) = setFlag(id, "deleted", deleted, if (deleted) "deleted" else "restored")

    private suspend fun setFlag(id: String, column: String, value: Boolean, operation: String) = database { db ->
        transaction(db) {
            db.prepareStatement("UPDATE memes SET $column=?, updated_at=?, revision=revision+1 WHERE id=? AND $column<>?").use {
                it.setBoolean(1, value); it.setLong(2, System.currentTimeMillis()); it.setString(3, id); it.setBoolean(4, value)
                if (it.executeUpdate() > 0) recordChange(db, id, operation)
            }
        }
    }

    private fun recordChange(db: Connection, id: String, operation: String) {
        db.prepareStatement("""INSERT INTO changes(operation_id,meme_id,operation,revision,created_at)
            SELECT ?,id,?,revision,? FROM memes WHERE id=?""").use {
            it.setString(1, UUID.randomUUID().toString()); it.setString(2, operation)
            it.setLong(3, System.currentTimeMillis()); it.setString(4, id); it.executeUpdate()
        }
    }

    override suspend fun thumbnail(id: String): ByteArray? = database { db ->
        val paths = db.prepareStatement("SELECT original_path,thumbnail_path FROM memes WHERE id=?").use {
            it.setString(1, id)
            it.executeQuery().use { row -> if (row.next()) row.getString(1) to row.getString(2) else null }
        } ?: return@database null
        val target = resolveManaged(paths.second)
        if (!Files.exists(target)) writeThumbnail(decode(resolveManaged(paths.first)).bitmap, target)
        Files.readAllBytes(target)
    }

    private fun resolveManaged(relative: String): Path {
        val normalizedRoot = root.toAbsolutePath().normalize()
        val resolved = normalizedRoot.resolve(relative).normalize()
        check(resolved.startsWith(normalizedRoot)) { "Asset path escapes library" }
        return resolved
    }

    private fun <T> transaction(db: Connection, block: () -> T): T {
        db.autoCommit = false
        try {
            val result = block()
            db.commit()
            return result
        } catch (failure: Throwable) {
            db.rollback()
            throw failure
        } finally { db.autoCommit = true }
    }

    companion object {
        const val MAX_BYTES = 50L * 1024 * 1024
        private const val MAX_DIMENSION = 16384
        private const val MAX_PIXELS = 64_000_000L
        private const val THUMBNAIL_SIZE = 384
        fun defaultRoot(): Path = System.getenv("STICKER_DATA_DIR")?.takeIf { it.isNotBlank() }?.let(Path::of)
            ?: Path.of(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "StickerKaleidoscope", "library")
    }
}
