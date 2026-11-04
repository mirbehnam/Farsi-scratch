package ir.behnamapps.fascratch.editor

import java.io.File
import java.util.UUID

data class SavedHtmlProject(val name: String, val file: File, val modifiedAt: Long)

class PackagedHtmlRepository(private val directory: File) {
    init { check(directory.isDirectory || directory.mkdirs()) { "Cannot create HTML library" } }

    fun listProjects(): List<SavedHtmlProject> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension.equals("html", true) }
        .map { SavedHtmlProject(it.nameWithoutExtension, it, it.lastModified()) }
        .sortedByDescending { it.modifiedAt }

    @Synchronized fun save(source: File): SavedHtmlProject {
        require(source.isFile && source.extension.equals("html", true)) { "Expected a completed HTML file" }
        val temporary = File.createTempFile("pending-", ".tmp", directory)
        try {
            source.inputStream().use { input -> temporary.outputStream().use { input.copyTo(it) } }
            for (index in 0..9999) {
                val name = if (index == 0) "${source.nameWithoutExtension}.html" else "${source.nameWithoutExtension} ($index).html"
                val destination = File(directory, name)
                if (!destination.exists()) {
                    check(temporary.renameTo(destination)) { "Cannot commit saved HTML" }
                    return SavedHtmlProject(destination.nameWithoutExtension, destination, destination.lastModified())
                }
            }
            error("Too many HTML files with this name")
        } finally { temporary.delete() }
    }

    fun requireOwned(project: SavedHtmlProject) {
        require(project.file.canonicalFile.parentFile == directory.canonicalFile &&
            project.file.isFile && project.file.extension.equals("html", true)) { "HTML project is outside the private library" }
    }

    fun preparePlayback(project: SavedHtmlProject, cache: File): File {
        requireOwned(project)
        val folder = File(cache, UUID.randomUUID().toString())
        check(folder.mkdirs()) { "Cannot create private HTML playback cache" }
        try {
            return project.file.copyTo(File(folder, project.file.name))
        } catch (error: Exception) {
            folder.deleteRecursively()
            throw error
        }
    }
}
