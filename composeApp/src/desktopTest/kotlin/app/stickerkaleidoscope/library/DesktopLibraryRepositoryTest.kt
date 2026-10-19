package app.stickerkaleidoscope.library

import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.RandomAccessFile
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import javax.imageio.ImageIO
import kotlin.test.*

class DesktopLibraryRepositoryTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun image(name: String = "cat.png", color: Color = Color.ORANGE, format: String = "png"): Path {
        val path = temporary.root.toPath().resolve(name)
        val bitmap = BufferedImage(40, 24, BufferedImage.TYPE_INT_RGB)
        val graphics = bitmap.createGraphics()
        try { graphics.color = color; graphics.fillRect(0, 0, 40, 24) } finally { graphics.dispose() }
        check(ImageIO.write(bitmap, format, path.toFile()))
        return path
    }

    @Test fun `import preserves originals deduplicates by bytes and persists across repository instances`() = runBlocking<Unit> {
        val root = temporary.newFolder("library").toPath()
        val source = image("猫猫.png")
        val duplicate = temporary.root.toPath().resolve("renamed.jpg")
        Files.copy(source, duplicate)
        val repo = DesktopLibraryRepository(root)
        val report = repo.importFiles(listOf(source.toString(), duplicate.toString()))
        assertEquals(1, report.imported)
        assertEquals(1, report.duplicates)
        assertTrue(report.failures.isEmpty())
        val meme = DesktopLibraryRepository(root).list().single()
        assertEquals("猫猫", meme.title)
        assertEquals(40, meme.width)
        assertEquals(24, meme.height)
        assertEquals("png", meme.format)
        val original = root.resolve("originals/${meme.hash.take(2)}/${meme.hash}.png")
        assertContentEquals(Files.readAllBytes(source), Files.readAllBytes(original))
        assertEquals(64, meme.hash.length)
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)).joinToString("") { "%02x".format(it) }
        assertEquals(expectedHash, meme.hash)
        // Removing a source never removes the managed original.
        Files.delete(source)
        assertNotNull(repo.thumbnail(meme.id))
    }

    @Test fun `bad or missing files do not prevent later valid imports`() = runBlocking<Unit> {
        val root = temporary.newFolder().toPath()
        val broken = temporary.newFile("broken.png").toPath()
        Files.writeString(broken, "not an image")
        val huge = temporary.newFile("oversized.png")
        RandomAccessFile(huge, "rw").use { it.setLength(DesktopLibraryRepository.MAX_BYTES + 1) }
        val repo = DesktopLibraryRepository(root)
        val report = repo.importFiles(listOf(broken.toString(), temporary.root.resolve("missing.png").path, huge.path, image().toString()))
        assertEquals(1, report.imported)
        assertEquals(listOf(LibraryError.INVALID_IMAGE, LibraryError.MISSING_FILE, LibraryError.TOO_LARGE), report.failures.map { it.reason })
        assertEquals(1, repo.list().size)
        Files.list(root.resolve("staging")).use { assertEquals(0L, it.count()) }
    }

    @Test fun `edits favorites literal search and trash survive restart with transactional change records`() = runBlocking<Unit> {
        val root = temporary.newFolder().toPath()
        val repo = DesktopLibraryRepository(root)
        repo.importFiles(listOf(image().toString(), image("dog.jpg", Color.BLUE, "jpg").toString()))
        val cat = repo.list().single { it.format == "png" }
        repo.update(cat.id, "  100%_猫  ", listOf(" 无语 ", "猫", "猫", ""))
        repo.setFavorite(cat.id, true)
        repo.setFavorite(cat.id, true) // Retry is a no-op, including the outbox.
        val restarted = DesktopLibraryRepository(root)
        val saved = restarted.list(filter = LibraryFilter.FAVORITES).single()
        assertEquals("100%_猫", saved.title)
        assertEquals(setOf("无语", "猫"), saved.tags.toSet())
        assertEquals(3L, saved.revision)
        assertEquals(cat.id, restarted.list(query = "无语").single().id)
        assertEquals(cat.id, restarted.list(query = "%_").single().id)
        assertTrue(restarted.list(query = "' OR 1=1 --").isEmpty())
        assertEquals(1, restarted.list(filter = LibraryFilter.UNCATEGORIZED).size)
        restarted.setDeleted(cat.id, true)
        assertEquals(1, restarted.list().size)
        assertEquals(cat.id, restarted.list(filter = LibraryFilter.TRASH).single().id)
        val repeatImport = restarted.importFiles(listOf(image().toString()))
        assertEquals(1, repeatImport.duplicates)
        assertEquals(1, restarted.list(filter = LibraryFilter.TRASH).size)
        restarted.setDeleted(cat.id, false)
        assertEquals(2, restarted.list().size)
        assertTrue(restarted.list(filter = LibraryFilter.TRASH).isEmpty())
        DriverManager.getConnection("jdbc:sqlite:${root.resolve("library.db")}").use { db ->
            db.createStatement().use { statement ->
                statement.executeQuery("SELECT operation, revision FROM changes WHERE meme_id='${cat.id}' ORDER BY sequence").use { rows ->
                    val changes = buildList { while (rows.next()) add(rows.getString(1) to rows.getLong(2)) }
                    assertEquals(listOf("created" to 1L, "updated" to 2L, "updated" to 3L, "deleted" to 4L, "restored" to 5L), changes)
                }
            }
        }
    }

    @Test fun `missing thumbnails are regenerated and animation originals remain untouched`() = runBlocking<Unit> {
        val root = temporary.newFolder().toPath()
        val repo = DesktopLibraryRepository(root)
        val gif = temporary.root.toPath().resolve("reaction.gif")
        javaClass.getResourceAsStream("/fixtures/animated.gif")!!.use { Files.copy(it, gif) }
        repo.importFiles(listOf(gif.toString()))
        val meme = repo.list().single()
        val thumbnail = root.resolve("thumbnails/${meme.hash.take(2)}/${meme.hash}.png")
        Files.delete(thumbnail)
        assertNotNull(repo.thumbnail(meme.id))
        assertTrue(Files.exists(thumbnail))
        assertContentEquals(Files.readAllBytes(gif), Files.readAllBytes(root.resolve("originals/${meme.hash.take(2)}/${meme.hash}.gif")))
        assertEquals("gif", meme.format)
        ImageIO.createImageInputStream(root.resolve("originals/${meme.hash.take(2)}/${meme.hash}.gif").toFile()).use { input ->
            val reader = ImageIO.getImageReaders(input).next()
            try { reader.input = input; assertEquals(2, reader.getNumImages(true)) } finally { reader.dispose() }
        }
    }

    @Test fun `future database schema is rejected without modifying version`() = runBlocking<Unit> {
        val root = temporary.newFolder().toPath()
        DriverManager.getConnection("jdbc:sqlite:${root.resolve("library.db")}").use { db -> db.createStatement().use { it.execute("PRAGMA user_version=99") } }
        val failure = assertFailsWith<LibraryException> { DesktopLibraryRepository(root).list() }
        assertEquals(LibraryError.STORAGE, failure.reason)
        DriverManager.getConnection("jdbc:sqlite:${root.resolve("library.db")}").use { db ->
            db.createStatement().use { it.executeQuery("PRAGMA user_version").use { row -> row.next(); assertEquals(99, row.getInt(1)) } }
        }
    }

    @Test fun `failure to journal a change rolls back the metadata edit`() = runBlocking<Unit> {
        val root = temporary.newFolder().toPath()
        val repo = DesktopLibraryRepository(root)
        repo.importFiles(listOf(image().toString()))
        val before = repo.list().single()
        DriverManager.getConnection("jdbc:sqlite:${root.resolve("library.db")}").use { db ->
            db.createStatement().use { it.execute("CREATE TRIGGER reject_change BEFORE INSERT ON changes BEGIN SELECT RAISE(ABORT, 'simulated failure'); END") }
        }
        assertFailsWith<LibraryException> { repo.update(before.id, "changed", listOf("new tag")) }
        assertEquals(before, repo.list().single())
    }

    @Test fun `webp with transparency is decoded and gets a bounded png thumbnail`() = runBlocking<Unit> {
        val source = temporary.root.toPath().resolve("transparent.webp")
        javaClass.getResourceAsStream("/fixtures/transparent.webp")!!.use { Files.copy(it, source) }
        val repo = DesktopLibraryRepository(temporary.newFolder().toPath())
        val report = repo.importFiles(listOf(source.toString()))
        assertEquals(1, report.imported)
        assertTrue(report.failures.isEmpty())
        val meme = repo.list().single()
        assertEquals("webp", meme.format)
        assertEquals(32, meme.width)
        val thumbnail = ImageIO.read(repo.thumbnail(meme.id)!!.inputStream())
        assertTrue(thumbnail.colorModel.hasAlpha())
        assertTrue((thumbnail.getRGB(0, 0) ushr 24) in 1..254)
        assertTrue(thumbnail.width <= 384 && thumbnail.height <= 384)
    }
}
