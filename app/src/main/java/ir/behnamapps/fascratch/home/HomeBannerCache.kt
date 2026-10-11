package ir.behnamapps.fascratch.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.CoursePolicy
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

internal data class HomeBanner(val id: String, val imageUrl: String, val hash: String, val bytes: Long,
    val videoUrl: String?, val caption: String, val title: String)
internal data class BannerSelection(val banner: HomeBanner, val bitmap: Bitmap)
internal data class BannerSnapshot(val initialized: Boolean = false, val banners: List<HomeBanner> = emptyList(),
    val revision: Long = 0)

/** Bounded public-media cache. No learner identity, billing token, or activity is retained. */
internal object HomeBannerCache {
    private const val MAX_IMAGE = 10 * 1024 * 1024
    private const val MAX_CACHE = 64L * 1024 * 1024
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val mutable = MutableStateFlow(BannerSnapshot())
    val snapshots = mutable.asStateFlow()
    private var started = false
    private var lastAttempt = 0L
    private val base get() = BuildConfig.COURSE_API_BASE.trimEnd('/')
    private fun root(context: Context) = File(context.applicationContext.cacheDir, "scratch-home-banners")
    private fun imageFile(context: Context, banner: HomeBanner) = File(root(context), "${banner.id}-${banner.hash}.img")

    @Synchronized fun warm(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        scope.launch {
            lock.withLock {
                val rows = runCatching { parse(AtomicFile(File(root(app), "manifest.json")).openRead().use { bounded(it, 512 * 1024) }) }
                    .getOrDefault(emptyList())
                mutable.value = BannerSnapshot(true, rows, 1)
            }
            refresh(app)
        }
    }

    suspend fun select(context: Context): BannerSelection? = withContext(Dispatchers.IO) { choose(context, mutable.value.banners) }

    private fun choose(context: Context, rows: List<HomeBanner>): BannerSelection? {
        for (banner in rows.shuffled()) {
            val file = imageFile(context, banner)
            if (!file.isFile) continue
            val bitmap = runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 24_000_000)
                var sample = 1
                while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1000) sample *= 2
                BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull() ?: continue
            return BannerSelection(banner, bitmap)
        }
        return null
    }

    suspend fun refresh(context: Context) = withContext(Dispatchers.IO) {
        lock.withLock {
            val now = android.os.SystemClock.elapsedRealtime()
            if (lastAttempt != 0L && now - lastAttempt < 60_000) return@withLock
            lastAttempt = now
            try {
                val bytes = get("$base/home-banners", 512 * 1024, "application/json") ?: return@withLock
                val rows = parse(bytes) // Only a complete valid snapshot may evict removed banners.
                val directory = root(context)
                check(directory.isDirectory || directory.mkdirs())
                write(File(directory, "manifest.json"), bytes)
                val keep = rows.map { imageFile(context, it).name }.toSet()
                directory.listFiles()?.filter { it.name.endsWith(".img") && it.name !in keep }?.forEach { it.delete() }
                // Apply deletion/video removal before any potentially slow image downloads.
                mutable.value = BannerSnapshot(true, rows, mutable.value.revision + 1)
                val downloadStarted = android.os.SystemClock.elapsedRealtime()
                var used = 0L
                for (banner in rows.shuffled().take(60)) {
                    used += banner.bytes
                    if (used > MAX_CACHE) break
                    if (android.os.SystemClock.elapsedRealtime() - downloadStarted > 45_000) break
                    val file = imageFile(context, banner)
                    if (file.isFile && file.length() == banner.bytes) continue
                    val image = get(banner.imageUrl, MAX_IMAGE, "image/") ?: continue
                    if (image.size.toLong() != banner.bytes || sha(image) != banner.hash || !validImage(image)) continue
                    write(file, image)
                }
                // Bitmap of the displayed image stays alive during the fade; eviction never recycles it.
                var total = 0L
                directory.listFiles()?.filter { it.name.endsWith(".img") }?.sortedByDescending { it.lastModified() }?.forEach {
                    total += it.length(); if (total > MAX_CACHE) it.delete()
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* Transient failure keeps the last verified cache. */ }
        }
    }

    private fun parse(bytes: ByteArray): List<HomeBanner> {
        val data = JSONObject(String(bytes, Charsets.UTF_8)).getJSONObject("data")
        require(data.getInt("schema") == 1)
        val rows = data.getJSONArray("banners")
        require(rows.length() <= 1000)
        val seen = HashSet<String>()
        return List(rows.length()) { index ->
            val row = rows.getJSONObject(index)
            val id = CoursePolicy.uuid(row.getString("id"))
            require(seen.add(id))
            val image = row.getJSONObject("image")
            val hash = image.getString("sha256")
            val size = image.getLong("bytes")
            val imageUrl = image.getString("url")
            require(hash.matches(Regex("[a-f0-9]{64}")) && size in 1..MAX_IMAGE.toLong() && allowedMedia(imageUrl))
            require(image.getString("mime") in setOf("image/png", "image/jpeg", "image/webp"))
            val video = row.optJSONObject("video")?.getString("url")
            require(video == null || allowedMedia(video))
            HomeBanner(id, imageUrl, hash, size, video, row.optString("video_caption").takeUnless { it == "null" }.orEmpty().take(80), row.optString("title").take(255))
        }
    }

    fun allowedMedia(url: String): Boolean = runCatching {
        require(CoursePolicy.sameOrigin(base, url))
        val uri = java.net.URI(url)
        val prefix = java.net.URI(base).path + "/media/"
        require(uri.rawQuery == null && uri.fragment == null && uri.path.startsWith(prefix))
        CoursePolicy.uuid(uri.path.removePrefix(prefix)); true
    }.getOrDefault(false)

    private fun get(url: String, limit: Int, mimePrefix: String): ByteArray? {
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 4000; connection.readTimeout = 8000
            connection.instanceFollowRedirects = false
            if (connection.responseCode != 200 || !connection.contentType.orEmpty().startsWith(mimePrefix)) return null
            if (connection.contentLengthLong > limit) return null
            return connection.inputStream.use { bounded(it, limit) }
        } finally { connection.disconnect() }
    }

    private fun bounded(input: java.io.InputStream, limit: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break; require(out.size() + count <= limit); out.write(buffer, 0, count) }
        return out.toByteArray()
    }
    private fun validImage(bytes: ByteArray): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 24_000_000
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun write(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val stream = atomic.startWrite()
        try { stream.write(bytes); atomic.finishWrite(stream) }
        catch (error: Exception) { atomic.failWrite(stream); throw error }
    }
}
