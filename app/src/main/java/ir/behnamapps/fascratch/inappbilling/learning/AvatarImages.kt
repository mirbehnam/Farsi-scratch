package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.URL
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.HttpsURLConnection

/** Public, content-addressed artwork only. Never send account, store or purchase credentials. */
internal object AvatarImages {
    private const val MAX_FILE = 1024 * 1024
    private val memory = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    private val locks = ConcurrentHashMap<String, Mutex>()
    fun validHash(value: String?) = value != null && value.matches(Regex("[a-f0-9]{64}"))
    fun peek(hash: String?): Bitmap? = hash?.let(memory::get)

    suspend fun load(context: Context, url: String?, hash: String?): Bitmap? = withContext(Dispatchers.IO) {
        if (!validHash(hash) || url == null) return@withContext null
        val key = hash!!
        locks.getOrPut(key) { Mutex() }.withLock {
            memory.get(key)?.let { return@withLock it }
            try {
                val folder = File(context.applicationContext.filesDir, "learner-avatar-images").apply { mkdirs() }
                val target = File(folder, "$key.webp")
                if (target.isFile && target.length() in 1..MAX_FILE.toLong()) {
                    decode(target.readBytes(), key)?.let { target.setLastModified(System.currentTimeMillis()); memory.put(key, it); return@withLock it }
                }
                // Replace corrupt/truncated cache entries rather than repeatedly redownloading them.
                if (target.exists()) target.delete()
                val address = URL(url)
                require(address.protocol == "https" && address.host.isNotBlank() && address.userInfo == null && address.port in listOf(-1, 443))
                val connection = (address.openConnection() as HttpsURLConnection).apply {
                    connectTimeout = 4500; readTimeout = 6000; instanceFollowRedirects = false
                    setRequestProperty("Accept", "image/webp,image/png")
                }
                val bytes = try {
                    require(connection.responseCode == 200)
                    require(connection.contentLengthLong <= MAX_FILE)
                    connection.inputStream.use { input ->
                        val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                        while (true) { val count = input.read(buffer); if (count < 0) break; require(output.size() + count <= MAX_FILE); output.write(buffer, 0, count) }
                        output.toByteArray()
                    }
                } finally { connection.disconnect() }
                val bitmap = decode(bytes, key) ?: return@withLock null
                val temp = File(folder, "$key-${UUID.randomUUID()}.tmp")
                try { temp.writeBytes(bytes); if (!target.exists()) require(temp.renameTo(target)) }
                finally { temp.delete() }
                memory.put(key, bitmap)
                // A bounded persistent cache, not Android's opportunistically deleted cacheDir.
                val files = folder.listFiles { f -> f.extension == "webp" }?.sortedByDescending { it.lastModified() }.orEmpty()
                var retained = 0L
                files.forEach { f -> retained += f.length(); if (retained > 64L * MAX_FILE && f.name != target.name) f.delete() }
                bitmap
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { null } // A failed picture can never block profile, courses or purchases.
        }
    }

    private fun decode(bytes: ByteArray, hash: String): Bitmap? {
        if (bytes.isEmpty() || bytes.size > MAX_FILE) return null
        val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        if (actual != hash) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..1024 || bounds.outHeight !in 1..1024 || bounds.outWidth.toLong() * bounds.outHeight > 1_048_576) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}
