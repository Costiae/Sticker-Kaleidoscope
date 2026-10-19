package app.stickerkaleidoscope.library

import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.test.*

/** Opt-in only. Copies user-authorized samples into a fresh, separate validation library. */
class RealLibrarySampleTest {
    @Test fun `validate explicitly provided sample directory without modifying sources`() = runBlocking<Unit> {
        val sourceValue = System.getenv("STICKER_SAMPLE_DIR")
        val targetValue = System.getenv("STICKER_SAMPLE_OUTPUT")
        assumeTrue("Set STICKER_SAMPLE_DIR and STICKER_SAMPLE_OUTPUT to opt in", sourceValue != null && targetValue != null)
        val source = Path.of(sourceValue!!).toRealPath()
        val target = Path.of(targetValue!!).toAbsolutePath().normalize()
        check(!target.startsWith(source) && !source.startsWith(target))
        check(!Files.exists(target.resolve("library.db"))) { "Validation requires a fresh output directory" }
        val files = Files.walk(source).use { stream ->
            stream.filter { Files.isRegularFile(it) && !Files.isSymbolicLink(it) }.sorted().toList()
        }
        assertTrue(files.isNotEmpty())
        val before = files.associateWith(::sha256)
        val repository = DesktopLibraryRepository(target)
        val started = System.nanoTime()
        val first = repository.importFiles(files.map(Path::toString))
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        Files.writeString(target.resolve("validation.md"), buildString {
            appendLine("# Sample library validation")
            appendLine("Files: ${files.size}")
            appendLine("Imported: ${first.imported}")
            appendLine("Duplicates: ${first.duplicates}")
            appendLine("Failures: ${first.failures.size}")
            appendLine("Import duration: $elapsedMs ms (this machine, this sample only)")
            first.failures.forEach { appendLine("- ${it.name}: ${it.reason}") }
        })
        assertTrue(first.failures.isEmpty(), first.failures.toString())
        assertEquals(before.values.toSet().size, first.imported)
        assertEquals(files.size, first.imported + first.duplicates)
        val restarted = DesktopLibraryRepository(target)
        val items = restarted.list()
        assertEquals(first.imported, items.size)
        for (meme in items) {
            val bytes = assertNotNull(restarted.thumbnail(meme.id))
            val thumbnail = assertNotNull(ImageIO.read(bytes.inputStream()))
            assertTrue(thumbnail.width in 1..384 && thumbnail.height in 1..384)
            val original = target.resolve("originals/${meme.hash.take(2)}/${meme.hash}.${meme.format}")
            assertEquals(meme.hash, sha256(original))
        }
        val duplicate = restarted.importFiles(files.map(Path::toString))
        assertEquals(0, duplicate.imported)
        assertEquals(files.size, duplicate.duplicates)
        assertTrue(duplicate.failures.isEmpty())
        val selected = items.first()
        restarted.update(selected.id, selected.title, listOf("sample-validation"))
        assertEquals(selected.id, restarted.list(query = "sample-validation").single().id)
        restarted.setFavorite(selected.id, true)
        assertEquals(selected.id, restarted.list(filter = LibraryFilter.FAVORITES).single().id)
        restarted.setDeleted(selected.id, true)
        assertEquals(selected.id, restarted.list(filter = LibraryFilter.TRASH).single().id)
        restarted.setDeleted(selected.id, false)
        restarted.setFavorite(selected.id, false)
        restarted.update(selected.id, selected.title, emptyList())
        assertEquals(items.size, restarted.list().size)
        assertEquals(before, files.associateWith(::sha256), "Source bytes changed")
        val afterFiles = Files.walk(source).use { stream ->
            stream.filter { Files.isRegularFile(it) && !Files.isSymbolicLink(it) }.sorted().toList()
        }
        assertEquals(files, afterFiles, "Source file list changed")
        Files.writeString(target.resolve("validation.md"), Files.readString(target.resolve("validation.md")) +
            "\nPassed: restart, all thumbnails, original hashes, repeat import, tag search, favorites, trash/restore, source integrity.\n")
    }

    private fun sha256(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
