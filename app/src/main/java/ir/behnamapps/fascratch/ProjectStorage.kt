package ir.behnamapps.fascratch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.PixelCopy
import android.webkit.JavascriptInterface
import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream

data class SavedScratchProject(
    val name: String,
    val projectFile: File,
    val previewFile: File?,
    val modifiedAt: Long
)

class ProjectRepository(private val activity: MainActivity) {
    private val projectsDirectory = File(activity.filesDir, "scratch_projects").apply { mkdirs() }

    fun listProjects(): List<SavedScratchProject> =
        projectsDirectory.listFiles()
            .orEmpty()
            .filter { it.isFile && it.extension.equals("sb3", ignoreCase = true) }
            .map { file ->
                val preview = File(projectsDirectory, "${file.nameWithoutExtension}.png")
                SavedScratchProject(
                    name = file.nameWithoutExtension,
                    projectFile = file,
                    previewFile = preview.takeIf(File::exists),
                    modifiedAt = file.lastModified()
                )
            }
            .sortedByDescending(SavedScratchProject::modifiedAt)

    fun createIncomingFile(): File =
        File.createTempFile("scratch_", ".sb3", activity.cacheDir)

    fun deleteProject(project: SavedScratchProject): Boolean {
        project.previewFile?.delete()
        File(project.projectFile.parentFile, "${project.projectFile.nameWithoutExtension}.png").delete()
        return project.projectFile.delete()
    }

    fun openProjectForWeb(encodedPath: String): WebResourceResponse? {
        val fileName = Uri.decode(encodedPath).substringAfterLast('/')
        val file = File(projectsDirectory, fileName)
        val isInsideProjectsDirectory = runCatching {
            file.canonicalFile.parentFile == projectsDirectory.canonicalFile
        }.getOrDefault(false)
        if (!isInsideProjectsDirectory || !file.isFile || !file.extension.equals("sb3", true)) return null
        return WebResourceResponse(
            "application/x.scratch.sb3",
            null,
            FileInputStream(file)
        )
    }

    fun commitProject(incomingFile: File, requestedName: String): SavedScratchProject {
        val cleanBaseName = requestedName
            .removeSuffix(".sb3")
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(100)
            .ifBlank { "پروژه اسکرچ" }
        val projectFile = uniqueProjectFile(cleanBaseName)

        if (!incomingFile.renameTo(projectFile)) {
            incomingFile.copyTo(projectFile, overwrite = true)
            incomingFile.delete()
        }

        return SavedScratchProject(
            name = projectFile.nameWithoutExtension,
            projectFile = projectFile,
            previewFile = null,
            modifiedAt = projectFile.lastModified()
        )
    }

    fun captureProjectPreview(project: SavedScratchProject, onComplete: (Boolean) -> Unit) {
        val rootView = activity.window.decorView
        if (rootView.width <= 0 || rootView.height <= 0) {
            onComplete(false)
            return
        }

        val bitmap = Bitmap.createBitmap(rootView.width, rootView.height, Bitmap.Config.ARGB_8888)
        val previewFile = File(projectsDirectory, "${project.projectFile.nameWithoutExtension}.png")

        fun saveBitmap(): Boolean = runCatching {
            FileOutputStream(previewFile).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }.isSuccess

        fun drawFallback() {
            val canvas = Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            rootView.draw(canvas)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PixelCopy.request(
                activity.window,
                bitmap,
                { result ->
                    if (result != PixelCopy.SUCCESS) drawFallback()
                    val saved = saveBitmap()
                    bitmap.recycle()
                    onComplete(saved)
                },
                Handler(Looper.getMainLooper())
            )
        } else {
            drawFallback()
            val saved = saveBitmap()
            bitmap.recycle()
            onComplete(saved)
        }
    }

    private fun uniqueProjectFile(baseName: String): File {
        var candidate = File(projectsDirectory, "$baseName.sb3")
        var suffix = 2
        while (candidate.exists()) {
            candidate = File(projectsDirectory, "$baseName ($suffix).sb3")
            suffix++
        }
        return candidate
    }
}

class ScratchProjectSaveBridge(private val activity: MainActivity) {
    private var incomingFile: File? = null
    private var output: FileOutputStream? = null
    private var projectName: String = "پروژه اسکرچ.sb3"
    private var savingSprite = false
    private var expectedSpriteBytes = 0L
    private var receivedSpriteBytes = 0L

    @JavascriptInterface
    @Synchronized
    fun beginSave(name: String) {
        clearIncoming()
        savingSprite = false
        projectName = name
        incomingFile = activity.projectRepository.createIncomingFile()
        output = FileOutputStream(incomingFile!!)
    }

    @JavascriptInterface
    @Synchronized
    fun beginSpriteSave(name: String, expectedBytes: Long) {
        clearIncoming()
        savingSprite = true
        expectedSpriteBytes = expectedBytes
        receivedSpriteBytes = 0L
        projectName = name
        incomingFile = activity.projectRepository.createIncomingFile()
        output = FileOutputStream(incomingFile!!)
    }

    @JavascriptInterface
    fun projectLoaderReady() {
        activity.runOnUiThread { activity.onScratchProjectLoaderReady() }
    }

    @JavascriptInterface
    fun projectLoadStarted() {
        activity.runOnUiThread { activity.onDirectProjectLoadStarted() }
    }

    @JavascriptInterface
    @Synchronized
    fun appendChunk(base64Chunk: String) {
        val bytes = Base64.decode(base64Chunk, Base64.DEFAULT)
        output?.write(bytes)
    }

    @JavascriptInterface
    @Synchronized
    fun appendSpriteChunk(base64Chunk: String) {
        val bytes = Base64.decode(base64Chunk, Base64.DEFAULT)
        output?.write(bytes)
        receivedSpriteBytes += bytes.size
    }

    @JavascriptInterface
    @Synchronized
    fun finishSave() {
        output?.flush()
        output?.close()
        output = null
        val completedFile = incomingFile ?: return
        incomingFile = null
        activity.runOnUiThread { activity.finishProjectSave(completedFile, projectName) }
    }

    @JavascriptInterface
    @Synchronized
    fun finishSpriteSave() {
        if (!savingSprite) return
        output?.flush()
        output?.close()
        output = null
        val completedFile = incomingFile ?: return
        incomingFile = null
        savingSprite = false
        val isComplete = completedFile.length() > 0L &&
            receivedSpriteBytes == completedFile.length() &&
            (expectedSpriteBytes <= 0L || expectedSpriteBytes == completedFile.length())
        expectedSpriteBytes = 0L
        receivedSpriteBytes = 0L
        activity.runOnUiThread {
            if (isComplete) activity.finishSpriteSave(completedFile, projectName)
            else activity.failSpriteSave(completedFile)
        }
    }

    @Synchronized
    fun dispose() {
        clearIncoming()
    }

    private fun clearIncoming() {
        runCatching { output?.close() }
        output = null
        incomingFile?.delete()
        incomingFile = null
        savingSprite = false
        expectedSpriteBytes = 0L
        receivedSpriteBytes = 0L
    }
}
