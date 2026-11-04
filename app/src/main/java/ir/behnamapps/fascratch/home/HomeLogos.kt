package ir.behnamapps.fascratch.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.AtomicFile
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.R
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

internal data class HomeLogo(val id: Int, val hash: String, val url: String, val bytes: Int)
private data class LogosState(val initialized: Boolean = false, val synced: Boolean = false, val active: List<HomeLogo> = emptyList(), val available: List<HomeLogo> = emptyList())
internal object HomeLogoPolicy {
    fun removed(selectedId: Int?, activeIds: List<Int>) = selectedId != null && selectedId !in activeIds
    const val LIMIT = 1024 * 1024
}
private object LogoCache {
    val state = MutableStateFlow(LogosState())
    private val mutex = Mutex()
    private var warmed = false
    private var attempted = -60_000L
    private var etag: String? = null
    private val endpoint get() = BuildConfig.COURSE_API_BASE.trimEnd('/') + "/home-logos"
    private fun directory(c: Context) = File(c.filesDir, "home-logos-v1").apply { mkdirs() }
    private fun file(c: Context, row: HomeLogo) = File(directory(c), row.hash + ".webp")
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun read(input: java.io.InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream(); val buffer = ByteArray(8192)
        while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= max); out.write(buffer, 0, n) }
        return out.toByteArray()
    }
    private fun parse(json: JSONObject): List<HomeLogo> {
        require(json.getInt("schema") == 1)
        val rows = json.getJSONArray("logos"); require(rows.length() <= 4)
        val result = List(rows.length()) { i ->
            val row = rows.getJSONObject(i); val image = row.getJSONObject("image")
            val id = row.getInt("id"); val hash = image.getString("sha256"); val url = image.getString("url"); val bytes = image.getInt("bytes")
            val uri = java.net.URI(url)
            require(id in 1..4 && hash.matches(Regex("[a-f0-9]{64}")) && bytes in 1..HomeLogoPolicy.LIMIT)
            require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null)
            require(image.getString("mime") == "image/webp" && image.getInt("width") in 1..1024 && image.getInt("height") in 1..1024)
            HomeLogo(id, hash, url, bytes)
        }
        require(result.map { it.id }.distinct().size == result.size)
        return result
    }
    private fun valid(bytes: ByteArray, row: HomeLogo): Boolean {
        if (bytes.size != row.bytes || sha(bytes) != row.hash) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        return bounds.outWidth in 1..1024 && bounds.outHeight in 1..1024
    }
    private fun cached(c: Context, row: HomeLogo): ByteArray? = runCatching {
        file(c, row).inputStream().use { read(it, HomeLogoPolicy.LIMIT) }.takeIf { valid(it, row) }
    }.getOrNull()
    private fun write(target: File, bytes: ByteArray) {
        val atomic = AtomicFile(target); val out = atomic.startWrite()
        try { out.write(bytes); atomic.finishWrite(out) } catch (e: Exception) { atomic.failWrite(out); throw e }
    }
    suspend fun warm(c: Context) = withContext(Dispatchers.IO) { mutex.withLock {
        if (warmed) return@withLock
        warmed = true
        val rows = runCatching { AtomicFile(File(directory(c), "manifest.json")).openRead().use { parse(JSONObject(String(read(it, 32768), Charsets.UTF_8))) } }.getOrDefault(emptyList())
        state.value = LogosState(initialized = true, active = rows, available = rows.filter { cached(c, it) != null })
    } }
    suspend fun refresh(c: Context) = withContext(Dispatchers.IO) { mutex.withLock {
        val now = SystemClock.elapsedRealtime(); if (now - attempted < 60_000) return@withLock; attempted = now
        try {
            val connection = URL(endpoint).openConnection() as HttpsURLConnection
            val manifest = try {
                connection.connectTimeout = 4000; connection.readTimeout = 8000; connection.instanceFollowRedirects = false
                etag?.let { connection.setRequestProperty("If-None-Match", it) }
                when (connection.responseCode) {
                    304 -> null
                    200 -> {
                        require(connection.contentType.orEmpty().startsWith("application/json"))
                        val data = connection.inputStream.use { JSONObject(String(read(it, 32768), Charsets.UTF_8)).getJSONObject("data") }
                        val rows = parse(data)
                        write(File(directory(c), "manifest.json"), data.toString().toByteArray(Charsets.UTF_8))
                        etag = connection.getHeaderField("ETag")
                        state.value = LogosState(true, false, rows, rows.filter { cached(c, it) != null })
                        val hashes = rows.map { it.hash }.toSet()
                        directory(c).listFiles()?.filter { it.name.matches(Regex("[a-f0-9]{64}\\.webp")) && it.name.removeSuffix(".webp") !in hashes }?.forEach { it.delete() }
                        rows
                    }
                    else -> error("Manifest unavailable")
                }
            } finally { connection.disconnect() }
            val rows = manifest ?: state.value.active
            for (row in rows) {
                ensureActive()
                if (cached(c, row) != null) continue
                runCatching {
                    val image = URL(row.url).openConnection() as HttpsURLConnection
                    val bytes = try {
                        image.connectTimeout = 4000; image.readTimeout = 8000; image.instanceFollowRedirects = false
                        require(image.responseCode == 200 && image.contentType.orEmpty().startsWith("image/") && image.contentLengthLong <= HomeLogoPolicy.LIMIT)
                        image.inputStream.use { read(it, HomeLogoPolicy.LIMIT) }
                    } finally { image.disconnect() }
                    require(valid(bytes, row)); write(file(c, row), bytes)
                }
            }
            state.value = LogosState(true, true, rows, rows.filter { cached(c, it) != null })
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* A failed request is never evidence of deletion. */ }
    } }
    suspend fun bitmap(c: Context, row: HomeLogo): Bitmap? = withContext(Dispatchers.IO) {
        cached(c, row)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
    }
}

@Composable internal fun HomeLogoImage(modifier: Modifier) {
    if (LocalInspectionMode.current) { Image(painterResource(R.drawable.scratch_home_logo), "اسکرچ فارسی", modifier, contentScale = ContentScale.Fit); return }
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val snapshot by LogoCache.state.collectAsState()
    var selected by remember { mutableStateOf<HomeLogo?>(null) }
    var resolved by remember { mutableStateOf(false) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(owner, context) {
        LogoCache.warm(context)
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            // Connectivity transitions are checked while Home is visible; no work continues after leaving.
            var wasOnline = false
            while (isActive) {
                val online = ir.behnamapps.fascratch.inappbilling.learning.homeOnline(context)
                if (online && (!wasOnline || snapshot.initialized)) LogoCache.refresh(context)
                wasOnline = online
                delay(5000)
            }
        }
    }
    LaunchedEffect(snapshot) {
        if (HomeLogoPolicy.removed(selected?.id, snapshot.active.map { it.id })) {
            selected = null; resolved = true; bitmap = null
        }
        selected?.let { old -> snapshot.available.firstOrNull { it.id == old.id && it.hash != old.hash }?.let { selected = it } }
        if (!resolved && snapshot.initialized && (snapshot.available.isNotEmpty() || snapshot.synced)) {
            selected = snapshot.available.randomOrNull(); resolved = true
        }
    }
    LaunchedEffect(selected) {
        val logo = selected
        if (logo == null) bitmap = null
        else LogoCache.bitmap(context, logo)?.let { bitmap = it }
    }
    Crossfade(bitmap, modifier = modifier, animationSpec = tween(200), label = "home-logo") { image ->
        if (image == null) Image(painterResource(R.drawable.scratch_home_logo), "اسکرچ فارسی", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Image(image.asImageBitmap(), "اسکرچ فارسی", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}
