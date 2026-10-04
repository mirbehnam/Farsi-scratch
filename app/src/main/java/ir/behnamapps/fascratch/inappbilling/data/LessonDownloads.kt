package ir.behnamapps.fascratch.inappbilling.data

import android.content.Context
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

/** Foreground screen download, sequential, resumable, private; only verified files become playable. */
class LessonDownloads(context: Context, private val api: CourseApi) {
    private val root = File(context.noBackupFilesDir, "course-videos-${BuildConfig.FLAVOR}").apply { mkdirs() }
    fun completed(lesson: Lesson): File? = File(root, CoursePolicy.downloadKey(lesson)).takeIf { it.isFile && it.length() == lesson.bytes }

    suspend fun download(lesson: Lesson, access: CourseAccess?, progress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(lesson.isPreview || access?.courseId == lesson.courseId)
        val target = File(root, CoursePolicy.downloadKey(lesson))
        completed(lesson)?.let { return@withContext it }
        val partial = File(root, target.name + ".part")
        var offset = partial.takeIf { it.isFile }?.length() ?: 0L
        if (offset > lesson.bytes) { check(partial.delete()); offset = 0 }
        if (root.usableSpace < (lesson.bytes - offset) + 10 * 1024 * 1024) throw CourseFailure("فضای کافی برای دانلود این درس وجود ندارد.")
        if (offset < lesson.bytes) {
            val connection = if (lesson.isPreview) api.previewConnection(lesson)
                else api.connection("lessons/${CoursePolicy.uuid(lesson.id)}/purchased-video", requireNotNull(access).token)
            try {
                connection.setRequestProperty("Accept", "video/mp4")
                if (offset > 0) connection.setRequestProperty("Range", "bytes=$offset-")
                val status = connection.responseCode
                if (status == 401 || status == 403) throw CourseFailure("خرید باید دوباره تأیید شود.", status)
                if (status != 200 && status != 206) throw CourseFailure("دانلود درس انجام نشد؛ دوباره تلاش کنید.", status)
                if (status == 200) offset = 0 // Range ignored: restart, never concatenate two full files.
                else {
                    val range = connection.getHeaderField("Content-Range").orEmpty()
                    require(range == "bytes $offset-${lesson.bytes - 1}/${lesson.bytes}")
                }
                val declared = connection.getHeaderFieldLong("Content-Length", -1)
                if (declared >= 0) require(declared == lesson.bytes - offset)
                require(connection.contentType.orEmpty().substringBefore(';') == "video/mp4")
                RandomAccessFile(partial, "rw").use { output ->
                    output.setLength(offset); output.seek(offset)
                    var received = offset
                    var lastUpdate = 0L
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer)
                            if (count < 0) break
                            received += count
                            require(received <= lesson.bytes)
                            output.write(buffer, 0, count)
                            val now = System.nanoTime()
                            if (now - lastUpdate > 150_000_000) {
                                progress((received.toDouble() / lesson.bytes).toFloat().coerceAtMost(.99f)); lastUpdate = now
                            }
                        }
                    }
                    require(received == lesson.bytes)
                    output.fd.sync()
                }
            } catch (error: CourseFailure) { throw error }
            finally { connection.disconnect() }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        partial.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                currentCoroutineContext().ensureActive()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        if (!actual.equals(lesson.sha256, true)) {
            check(partial.delete())
            throw CourseFailure("فایل درس ناقص یا نامعتبر بود؛ دوباره دانلود کنید.")
        }
        check(partial.renameTo(target))
        progress(1f)
        target
    }

    fun delete(lesson: Lesson) {
        val target = File(root, CoursePolicy.downloadKey(lesson))
        if (target.exists()) check(target.delete())
        val partial = File(root, target.name + ".part")
        if (partial.exists()) check(partial.delete())
    }
}
