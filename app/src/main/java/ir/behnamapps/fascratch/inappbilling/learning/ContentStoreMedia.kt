package ir.behnamapps.fascratch.inappbilling.learning

import android.media.MediaPlayer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.behnamapps.fascratch.inappbilling.data.CourseApi
import ir.behnamapps.fascratch.inappbilling.data.readBytesBounded
import kotlinx.coroutines.*
import java.io.File

@Composable internal fun OwnedDownloadButton(enabled: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "owned-download")
    val pulse by transition.animateFloat(1f, 1.045f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "download-pulse")
    Button(enabled = enabled, onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21865C)),
        modifier = Modifier.graphicsLayer { scaleX = if (enabled) pulse else 1f; scaleY = if (enabled) pulse else 1f }) { Text("دانلود رایگان") }
}

@Composable internal fun StoreImagePreview(part: ContentPart, repo: ContentStoreRepository,
    load: suspend (String?) -> StorePicture? = { repo.picture(it) }, close: () -> Unit) {
    var picture by remember(part.uuid) { mutableStateOf<StorePicture?>(null) }
    var loading by remember(part.uuid) { mutableStateOf(true) }
    LaunchedEffect(part.uuid) { picture = load(part.preview) ?: load(part.thumbnail); loading = false }
    AlertDialog(onDismissRequest = close, title = { Text(part.name) }, text = {
        Box(Modifier.fillMaxWidth().height(220.dp)) {
            val current = picture
            when {
                loading -> CircularProgressIndicator()
                current?.bitmap != null -> Image(current.bitmap.asImageBitmap(), part.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                current?.gif != null -> AnimatedStorePicture(current.gif, Modifier.fillMaxSize())
                else -> Text("پیش‌نمایش آماده نشد؛ اینترنت را بررسی کن و دوباره تلاش کن.")
            }
        }
    }, confirmButton = { TextButton(onClick = close) { Text("بستن") } })
}

/** One selected row plays; leaving the package or selecting another row releases playback. */
@Composable internal fun InlineSoundPreview(part: ContentPart, selected: Boolean,
    load: (suspend () -> File)? = null, select: (Boolean) -> Unit) {
    val context = LocalContext.current
    var loading by remember(part.uuid) { mutableStateOf(false) }
    var error by remember(part.uuid) { mutableStateOf<String?>(null) }
    LaunchedEffect(part.uuid, selected) {
        if (!selected) return@LaunchedEffect
        var player: MediaPlayer? = null
        var local: File? = null
        loading = true; error = null
        try {
            local = if (load != null) load() else withContext(Dispatchers.IO) {
                val api = CourseApi(context); val c = api.contentDownload(part.preview, LearningIdentity.get(context).secret())
                try {
                    check(c.responseCode == 200)
                    val bytes = c.inputStream.use { it.readBytesBounded(16 * 1048576) }
                    File.createTempFile("store-sound-", ".mp3", context.cacheDir).also { it.writeBytes(bytes) }
                } finally { api.finishConnection(c) }
            }
            ensureActive()
            player = MediaPlayer().also { media ->
                media.setDataSource(local!!.absolutePath)
                media.setOnCompletionListener { select(false) }
                media.setOnErrorListener { _, _, _ -> error = "پخش صدا ممکن نشد."; select(false); true }
                media.setOnPreparedListener { loading = false; it.start() }
                media.prepareAsync()
            }
            awaitCancellation()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "پخش صدا آماده نشد؛ اینترنت را بررسی کن."; select(false) }
        finally { player?.release(); local?.delete(); loading = false }
    }
    Column {
        TextButton(onClick = { select(!selected) }) { Text(if (loading && selected) "توقف آماده‌سازی" else if (selected) "■ توقف" else "▶ پخش") }
        error?.let { Text(it, color = Color(0xFFB43737), style = MaterialTheme.typography.labelSmall) }
    }
}
