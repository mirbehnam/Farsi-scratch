package ir.behnamapps.fascratch.inappbilling.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.CoursePolicy
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** Public UUID-addressed artwork only. Shared by native cards and the isolated WebView. */
class CourseImageCache(context: Context) {
    data class Image(val mime: String, val bytes: ByteArray)
    private val root = File(context.applicationContext.cacheDir, "scratch-covers")

    fun load(url: String, connectTimeout: Int = 1500, readTimeout: Int = 1500): Image? {
        if (!allowed(url)) return null
        val key = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = AtomicFile(File(root, "$key.img"))
        synchronized(lock) {
            runCatching {
                DataInputStream(file.openRead()).use { stream ->
                    val image = Image(stream.readUTF(), stream.readBytesBounded(MAX_BYTES))
                    require(valid(image))
                    file.baseFile.setLastModified(System.currentTimeMillis())
                    return image
                }
            }
        }
        val image = runCatching {
            val connection = URL(url).openConnection() as HttpsURLConnection
            try {
                connection.connectTimeout = connectTimeout; connection.readTimeout = readTimeout
                connection.instanceFollowRedirects = false; connection.useCaches = false
                if (connection.responseCode != 200) return@runCatching null
                val mime = connection.contentType?.substringBefore(';') ?: return@runCatching null
                if (mime !in MIME_TYPES) return@runCatching null
                Image(mime, connection.inputStream.use { it.readBytesBounded(MAX_BYTES) }).takeIf(::valid)
            } finally { connection.disconnect() }
        }.getOrNull() ?: return null
        synchronized(lock) {
            runCatching {
                check(root.isDirectory || root.mkdirs())
                val output = file.startWrite()
                try {
                    val stream = DataOutputStream(output)
                    stream.writeUTF(image.mime); stream.write(image.bytes); stream.flush()
                    file.finishWrite(output)
                } catch (error: Exception) { file.failWrite(output); throw error }
                var total = 0L
                root.listFiles()?.filter { it.name.endsWith(".img") }?.sortedByDescending { it.lastModified() }?.forEachIndexed { index, entry ->
                    total += entry.length()
                    if (index >= 100 || total > 64L * 1024 * 1024) entry.delete()
                }
            }
        }
        return image
    }

    private fun valid(image: Image): Boolean {
        if (image.mime !in MIME_TYPES || image.bytes.isEmpty() || image.bytes.size > MAX_BYTES) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, bounds)
        return bounds.outWidth > 0 && bounds.outHeight > 0 && bounds.outWidth.toLong() * bounds.outHeight <= 24_000_000
    }

    private fun allowed(url: String): Boolean {
        val base = BuildConfig.COURSE_API_BASE.trimEnd('/')
        if (!url.startsWith("https://") || !CoursePolicy.sameOrigin(base, url)) return false
        val uri = Uri.parse(url)
        val prefix = Uri.parse(base).path.orEmpty() + "/media/"
        return uri.query == null && uri.fragment == null && uri.path.orEmpty().startsWith(prefix) &&
            runCatching { CoursePolicy.uuid(uri.path!!.removePrefix(prefix)) }.isSuccess
    }

    companion object {
        private val lock = Any()
        private const val MAX_BYTES = 10 * 1024 * 1024
        private val MIME_TYPES = setOf("image/webp", "image/png", "image/jpeg", "image/gif")
    }
}
