package ir.behnamapps.fascratch.inappbilling.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import ir.behnamapps.fascratch.inappbilling.data.CourseImageCache
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.data.readBytesBounded
import ir.behnamapps.fascratch.inappbilling.domain.CoursePolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import javax.net.ssl.HttpsURLConnection

private val artworkCache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
    override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
}

/** Only public artwork is fetched; never send purchase credentials. Bounded bytes and sampled decoding. */
@Composable
internal fun CourseArtwork(url: String?, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val imageCache = remember(context) { CourseImageCache(context) }
    val bitmap by produceState<Bitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            if (url == null || !CoursePolicy.sameOrigin(BuildConfig.COURSE_API_BASE, url)) return@withContext null
            artworkCache.get(url)?.let { return@withContext it }
            runCatching {
                val bytes = imageCache.load(url, 10_000, 15_000)?.bytes ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                var sample = 1
                while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })?.also { artworkCache.put(url, it) }
            }.getOrNull()
        }
    }
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFF243B55), Color(0xFF596D8B)))), contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            ?: Text("اسکرچ", color = Color.White)
    }
}
