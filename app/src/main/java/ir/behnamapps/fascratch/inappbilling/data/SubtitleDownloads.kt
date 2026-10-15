package ir.behnamapps.fascratch.inappbilling.data

import android.content.Context
import android.util.AtomicFile
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

/** Optional sidecar: private, immutable video/subtitle hashes, bounded UTF-8, no impact on video playback. */
class SubtitleDownloads(context: Context, private val api: CourseApi) {
    private val root = File(context.noBackupFilesDir, "course-subtitles-${BuildConfig.FLAVOR}")
    private fun file(lesson: Lesson): AtomicFile {
        val sub = requireNotNull(lesson.subtitle); require(SubtitlePolicy.valid(sub)); CoursePolicy.downloadKey(lesson)
        return AtomicFile(File(root, "${lesson.id}-${lesson.sha256}-${sub.sha256}.srt"))
    }
    private fun verified(lesson: Lesson, bytes: ByteArray): SubtitleTrack {
        val sub = requireNotNull(lesson.subtitle)
        require(bytes.size == sub.bytes && MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) } == sub.sha256)
        return SubtitleTrack.parse(bytes)
    }
    suspend fun cached(lesson: Lesson): SubtitleTrack? = withContext(Dispatchers.IO) {
        if (lesson.subtitle == null) return@withContext null
        lock.withLock { runCatching { file(lesson).openRead().use { verified(lesson, it.readBytesBounded(SubtitleTrack.MAX_BYTES)) } }.getOrNull() }
    }
    suspend fun download(lesson: Lesson, access: CourseAccess?): SubtitleTrack = withContext(Dispatchers.IO) {
        lock.withLock {
            val target = file(lesson)
            runCatching { target.openRead().use { verified(lesson, it.readBytesBounded(SubtitleTrack.MAX_BYTES)) } }.getOrNull()?.let { return@withLock it }
            val connection = api.subtitleConnection(lesson, access)
            val bytes = try {
                if (connection.responseCode != 200) throw CourseFailure("دریافت زیرنویس انجام نشد؛ اینترنت یا دسترسی درس را بررسی کنید.", connection.responseCode)
                connection.inputStream.use { it.readBytesBounded(SubtitleTrack.MAX_BYTES) }
            } finally { connection.disconnect() }
            val track = verified(lesson, bytes)
            check(root.isDirectory || root.mkdirs())
            val stream = target.startWrite()
            try { stream.write(bytes); target.finishWrite(stream) } catch (e: Exception) { target.failWrite(stream); throw e }
            // Bound optional sidecars, never touch course videos/receipts or other directories.
            var total = 0L
            root.listFiles()?.filter { it.isFile && it.name.endsWith(".srt") }?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, entry ->
                total += entry.length(); if (index >= 300 || total > 32L * 1024 * 1024) entry.delete()
            }
            track
        }
    }
    companion object { private val lock = Mutex() }
}
