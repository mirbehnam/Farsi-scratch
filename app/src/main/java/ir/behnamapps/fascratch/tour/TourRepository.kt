package ir.behnamapps.fascratch.tour

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import ir.behnamapps.fascratch.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection
import java.net.URL

/** Public media only. Completion is installation-local and never enters learning/account APIs. */
internal class TourRepository private constructor(context: Context) {
    private val root = File(context.cacheDir, "app-tour-${BuildConfig.FLAVOR}").apply { mkdirs() }
    private val local = File(context.noBackupFilesDir, "app-tour-${BuildConfig.FLAVOR}").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val refreshMutex = Mutex()
    private var loaded = false
    private var lastAttempt = 0L
    private var pin: String? = null
    private var progress = JSONObject()
    private val mutable = MutableStateFlow(TourState())
    val state = mutable.asStateFlow()

    suspend fun active() = withContext(Dispatchers.IO) {
        refreshMutex.withLock {
            mutex.withLock {
                if (!loaded) {
                    progress = runCatching { readJson(File(local, "progress.json")) }.getOrDefault(JSONObject())
                    val items = runCatching { TourModels.parse(readJson(File(local, "manifest.json"))) }.getOrDefault(emptyList())
                    mutable.value = TourState(true, items, completed(items)); loaded = true
                }
            }
            val now = android.os.SystemClock.elapsedRealtime()
            if (lastAttempt != 0L && now - lastAttempt < 300_000) return@withLock
            lastAttempt = now
            try {
                val connection = connection(BuildConfig.COURSE_API_BASE.trimEnd('/') + "/app-tour")
                val json = try {
                    check(connection.responseCode == 200)
                    require(connection.contentType.orEmpty().startsWith("application/json"))
                    connection.inputStream.use { JSONObject(String(bounded(it, 1024 * 1024), Charsets.UTF_8)).getJSONObject("data") }
                } finally { connection.disconnect() }
                val items = TourModels.parse(json)
                mutex.withLock {
                    write(File(local, "manifest.json"), json)
                    val keep = items.map { it.key }.toSet()
                    progress.keys().asSequence().toList().filter { it !in keep && it != pin }.forEach { progress.remove(it) }
                    write(File(local, "progress.json"), progress)
                    mutable.update { it.copy(items = items, completed = completed(items)) }
                    val hashes = items.flatMap { listOfNotNull(it.video.hash, it.cover?.hash) }.toSet()
                    root.listFiles()?.filter { it.name.substringBefore('.') !in hashes && !it.name.startsWith(pin?.substringAfterLast('-') ?: "!none") }?.forEach { it.delete() }
                }
                for (item in items) {
                    currentCoroutineContext().ensureActive()
                    // Release the media lock between covers so a selected video has priority
                    // over the rest of the background cover queue.
                    mutex.withLock {
                        item.cover?.let { media ->
                            if (!File(root, "${media.hash}.image").isFile) {
                                trim(media.bytes, null)
                                if (root.listFiles().orEmpty().sumOf { it.length() } + media.bytes <= TourModels.MAX_CACHE) {
                                    try { fetch(media, File(root, "${media.hash}.image"), false) }
                                    catch (e: CancellationException) { throw e } catch (_: Exception) { /* Retry cover on next refresh. */ }
                                }
                            }
                        }
                    }
                }
                mutex.withLock { trim(0, null) }
                mutable.update { it.copy(coverRevision = it.coverRevision + 1) }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Retain the last valid manifest when offline or server unavailable. */ }
        }
    }

    private fun completed(items: List<TourVideo>) = items.filter { progress.optJSONObject(it.key)?.optBoolean("complete") == true }.map { it.key }.toSet()

    suspend fun open(item: TourVideo): Pair<File, TourCoverage> = withContext(Dispatchers.IO) {
        mutable.update { it.copy(downloading = item.key, progress = 0f) }
        try {
            mutex.withLock {
                require(mutable.value.items.any { it.key == item.key })
                pin = item.key
                val file = File(root, "${item.video.hash}.mp4")
                try {
                    if (!file.isFile || file.length() != item.video.bytes || sha(file) != item.video.hash) {
                        file.delete(); trim(item.video.bytes, item.video.hash)
                        val part = File(root, "${item.video.hash}.part")
                        if (root.usableSpace < (item.video.bytes - part.length()).coerceAtLeast(0) + 16 * 1024 * 1024) throw IOException("فضای کافی برای دریافت ویدیو وجود ندارد.")
                        fetch(item.video, file, true) { value -> mutable.update { it.copy(progress = value) } }
                    }
                    file.setLastModified(System.currentTimeMillis())
                    val saved = progress.optJSONObject(item.key)
                    val ranges = saved?.optJSONArray("ranges")
                    val spans = if (ranges == null) emptyList() else List(ranges.length()) { i -> ranges.getJSONArray(i).let { it.getInt(0) to it.getInt(1) } }
                    file to TourCoverage(item.durationMs, spans, saved?.optBoolean("ended") == true)
                } catch (e: Exception) { pin = null; throw e }
            }
        } finally { mutable.update { it.copy(downloading = null, progress = 0f) } }
    }

    fun save(item: TourVideo, coverage: TourCoverage, release: Boolean = false) {
        val ranges = coverage.ranges(); val ended = coverage.ended; val complete = coverage.complete
        scope.launch {
            mutex.withLock {
                try {
                    if (mutable.value.items.any { it.key == item.key }) {
                        progress.put(item.key, JSONObject().put("ranges", JSONArray(ranges.map { listOf(it.first, it.second) })).put("ended", ended).put("complete", complete))
                        write(File(local, "progress.json"), progress)
                        mutable.update { it.copy(completed = completed(it.items)) }
                    }
                } catch (_: Exception) { /* Disk errors must not interrupt playback. */ }
                finally { if (release && pin == item.key) pin = null }
            }
        }
    }

    suspend fun cover(item: TourVideo): Bitmap? = withContext(Dispatchers.IO) {
        val path = item.cover?.let { File(root, "${it.hash}.image") } ?: return@withContext null
        if (!path.isFile) return@withContext null
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path.path, options)
            require(options.outWidth > 0 && options.outHeight > 0 && options.outWidth.toLong() * options.outHeight <= 24_000_000)
            var sample = 1
            while (options.outWidth / sample > 640 || options.outHeight / sample > 400) sample *= 2
            BitmapFactory.decodeFile(path.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    private fun trim(incoming: Long, protectedHash: String?) {
        val files = root.listFiles().orEmpty()
        var total = files.sumOf { it.length() }
        files.sortedBy { it.lastModified() }.forEach { file ->
            val hash = file.name.substringBefore('.')
            if (total + incoming > TourModels.MAX_CACHE && hash != protectedHash && hash != pin?.substringAfterLast('-')) {
                val size = file.length(); if (file.delete()) total -= size
            }
        }
    }

    private suspend fun fetch(media: TourMedia, final: File, resume: Boolean, onProgress: (Float) -> Unit = {}) {
        val part = File(root, "${media.hash}.part")
        if (!resume || part.length() > media.bytes) part.delete()
        var offset = part.length()
        if (offset < media.bytes) {
            val c = connection(media.url)
            if (!resume) { c.connectTimeout = 3000; c.readTimeout = 4000 }
            try {
                if (offset > 0) c.setRequestProperty("Range", "bytes=$offset-")
                val status = c.responseCode
                if (offset > 0 && status == 200) { part.delete(); offset = 0 }
                if (status != 200 && status != 206) throw IOException("دریافت ویدیو کامل نشد؛ دوباره تلاش کن.")
                if (status == 206) require(c.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true)
                c.inputStream.use { input ->
                    java.io.FileOutputStream(part, offset > 0).use { out ->
                        val buffer = ByteArray(64 * 1024); var bytes = offset; var lastNotify = 0L
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = input.read(buffer); if (count < 0) break
                            bytes += count; require(bytes <= media.bytes)
                            out.write(buffer, 0, count)
                            val now = android.os.SystemClock.elapsedRealtime()
                            if (now - lastNotify >= 100 || bytes == media.bytes) { onProgress(bytes.toFloat() / media.bytes); lastNotify = now }
                        }
                        out.fd.sync()
                    }
                }
            } finally { c.disconnect() }
        }
        if (part.length() != media.bytes || sha(part) != media.hash) { part.delete(); throw IOException("فایل کامل دریافت نشد؛ دوباره تلاش کن.") }
        check(part.renameTo(final))
    }

    private fun connection(url: String): HttpsURLConnection {
        require(TourModels.mediaUrl(url))
        return (URL(url).openConnection() as HttpsURLConnection).apply {
            connectTimeout = 5000; readTimeout = 12000; instanceFollowRedirects = false
            setRequestProperty("Accept-Encoding", "identity")
        }
    }
    private fun bounded(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val b = ByteArray(8192)
        while (true) { val n = input.read(b); if (n < 0) break; require(out.size() + n <= limit); out.write(b, 0, n) }
        return out.toByteArray()
    }
    private fun sha(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val b = ByteArray(64 * 1024); while (true) { val n = input.read(b); if (n < 0) break; digest.update(b, 0, n) } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun readJson(file: File) = AtomicFile(file).openRead().use { JSONObject(String(bounded(it, 1024 * 1024), Charsets.UTF_8)) }
    private fun write(file: File, json: JSONObject) {
        val atomic = AtomicFile(file); val stream = atomic.startWrite()
        try { stream.write(json.toString().toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
        catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
    companion object {
        @Volatile private var instance: TourRepository? = null
        fun get(context: Context) = instance ?: synchronized(this) { instance ?: TourRepository(context.applicationContext).also { instance = it } }
    }
}
