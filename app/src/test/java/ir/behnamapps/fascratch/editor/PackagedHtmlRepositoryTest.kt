package ir.behnamapps.fascratch.editor

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PackagedHtmlRepositoryTest {
    @Test fun persistsHtmlWithoutOverwritingAndPreparesDisposablePlaybackCopies() {
        val folder = Files.createTempDirectory("html-library-test").toFile()
        try {
            val source = File(folder, "game.html").apply { writeText("<html>game</html>") }
            val repository = PackagedHtmlRepository(File(folder, "library"))
            val first = repository.save(source)
            val second = repository.save(source)
            assertEquals("game.html", first.file.name)
            assertEquals("game (1).html", second.file.name)
            assertEquals(2, repository.listProjects().size)
            val playback = repository.preparePlayback(first, File(folder, "cache"))
            assertEquals(first.file.readText(), playback.readText())
            playback.parentFile!!.deleteRecursively()
            assertTrue(first.file.isFile)
            assertThrows(IllegalArgumentException::class.java) {
                repository.preparePlayback(SavedHtmlProject("external", source, 0), File(folder, "cache"))
            }
        } finally { folder.deleteRecursively() }
    }
    @Test fun listsOnlyCompleteHtmlFilesAndRejectsOtherSaveTypes() {
        val folder = Files.createTempDirectory("html-library-filter-test").toFile()
        try {
            val repository = PackagedHtmlRepository(folder)
            File(folder, ".pending.tmp").writeText("unfinished")
            File(folder, "notes.json").writeText("{}")
            File(folder, "folder.html").mkdir()
            assertTrue(repository.listProjects().isEmpty())
            assertThrows(IllegalArgumentException::class.java) { repository.save(File(folder, "notes.json")) }
        } finally { folder.deleteRecursively() }
    }
}
