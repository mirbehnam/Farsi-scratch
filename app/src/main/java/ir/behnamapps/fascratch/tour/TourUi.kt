package ir.behnamapps.fascratch.tour

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ir.behnamapps.fascratch.inappbilling.learning.*
import ir.behnamapps.fascratch.inappbilling.presentation.FullscreenLessonPlayer
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.*
import java.io.File

@Composable internal fun HomeTourButton(modifier: Modifier = Modifier) {
    if (LocalInspectionMode.current) { TourButton(modifier, false) {}; return }
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { TourRepository.get(context) }
    val state by repository.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(repository, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { repository.active() }
    }
    if (state.items.isEmpty()) return
    val needsWatching = state.items.any { it.key !in state.completed }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(needsWatching, lifecycle) {
        rotation.snapTo(0f)
        if (needsWatching) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try {
                delay(2000)
                while (isActive) {
                    for (angle in listOf(-7f, 7f, -5f, 5f, 0f)) rotation.animateTo(angle, tween(75))
                    delay(6000)
                }
            } finally { rotation.snapTo(0f) }
        }
    }
    TourButton(modifier.graphicsLayer { rotationZ = rotation.value }, !needsWatching) { open = true }
    if (open) TourDialog(repository) { open = false }
}

@Composable private fun TourButton(modifier: Modifier, done: Boolean, click: () -> Unit) {
    Box(modifier.size(52.dp).semantics { contentDescription = "ویدیوهای راهنمای نرم‌افزار" }) {
        Canvas(Modifier.fillMaxSize().clickable(role = Role.Button, onClick = click).padding(10.dp)) {
            drawRoundRect(Color(0xFF88DAF7), Offset(0f, size.height * .13f), Size(size.width, size.height * .74f), CornerRadius(6.dp.toPx()))
            val play = Path().apply { moveTo(size.width * .4f, size.height * .31f); lineTo(size.width * .4f, size.height * .69f); lineTo(size.width * .7f, size.height * .5f); close() }
            drawPath(play, Color(0xFF164064))
        }
        if (done) Text("✓", Modifier.align(Alignment.TopEnd), color = Color(0xFF7CE2AB), fontWeight = FontWeight.Bold)
    }
}

private data class TourPlayback(val item: TourVideo, val file: File, val coverage: TourCoverage)

@Composable private fun TourDialog(repository: TourRepository, close: () -> Unit) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var playback by remember { mutableStateOf<TourPlayback?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    fun stopPlayer() { playback?.let { repository.save(it.item, it.coverage, true) }; playback = null }
    fun dismiss() { downloadJob?.cancel(); stopPlayer(); close() }
    BackHandler { if (playback != null) stopPlayer() else dismiss() }
    LaunchedEffect(state.items.map { it.key }) { if (playback != null && state.items.none { it.key == playback?.item?.key }) stopPlayer() }
    DisposableEffect(Unit) { onDispose { playback?.let { repository.save(it.item, it.coverage, true) } } }
    LearnerDialogTheme {
        Dialog(onDismissRequest = { if (playback != null) stopPlayer() else dismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
            LearnerDialogWindowBounds()
            val current = playback
            if (current != null) {
                val window = (LocalView.current.parent as DialogWindowProvider).window
                var lastSave by remember(current.item.key) { mutableLongStateOf(0L) }
                var wasPlaying by remember(current.item.key) { mutableStateOf(false) }
                FullscreenLessonPlayer(current.file, current.item.title, window, onClose = { stopPlayer() },
                    allowSpeedChange = false,
                    onPlaybackDiscontinuity = { current.coverage.discontinuity() },
                    onPlaybackSample = { position, _, playing, finished ->
                        val now = SystemClock.elapsedRealtime()
                        current.coverage.sample(position, playing, now, finished)
                        if (finished || (wasPlaying && !playing) || (playing && now - lastSave >= 5000)) { repository.save(current.item, current.coverage); lastSave = now }
                        wasPlaying = playing
                    })
            } else Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
                Surface(Modifier.learnerDialogPanelBounds(), shape = RoundedCornerShape(24.dp), color = Color(0xFFFFFCF6)) {
                    TourContent(state, repository, error, { dismiss() }, { downloadJob?.cancel() }) { item ->
                        if (downloadJob?.isActive != true) {
                            error = null
                            downloadJob = scope.launch {
                                try { val (file, coverage) = repository.open(item); playback = TourPlayback(item, file, coverage) }
                                catch (e: CancellationException) { throw e }
                                catch (e: Exception) { error = if (e is java.io.IOException) e.message ?: "اینترنت را بررسی کن و دوباره تلاش کن." else "دریافت ویدیو کامل نشد؛ دوباره تلاش کن." }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun TourContent(state: TourState, repository: TourRepository?, error: String?, close: () -> Unit, cancel: () -> Unit, select: (TourVideo) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("یک گشت کوتاه در اسکرچ", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF203E59))
                Text("یاد بگیر، امتحان کن، بساز!", style = MaterialTheme.typography.bodySmall, color = Color(0xFF657681))
            }
            IconButton(onClick = close, modifier = Modifier.semantics { contentDescription = "بستن راهنما" }) { Text("×", style = MaterialTheme.typography.headlineMedium) }
        }
        error?.let { Text(it, color = Color(0xFFB3261E), style = MaterialTheme.typography.bodySmall) }
        if (state.items.isEmpty()) Text("هنوز ویدیویی برای نمایش نیست.")
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            val count = minOf(3, state.items.size.coerceAtLeast(1))
            val coverHeight = (maxHeight * .48f).coerceIn(56.dp, 146.dp)
            LazyVerticalGrid(GridCells.Fixed(count), modifier = if (count == 1) Modifier.widthIn(max = 420.dp).fillMaxWidth() else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.items, key = { it.key }) { item ->
                    val bitmap by produceState<android.graphics.Bitmap?>(null, item.cover?.hash, state.coverRevision) { value = repository?.cover(item) }
                    val downloading = state.downloading == item.key
                    val done = item.key in state.completed
                    Card(onClick = { select(item) }, enabled = state.downloading == null, shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (done) Color(0xFFE6F6ED) else Color(0xFFEDF3FA))) {
                        Box(Modifier.fillMaxWidth().height(coverHeight).background(Color(0xFF203E59)), contentAlignment = Alignment.Center) {
                            bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                            Surface(color = Color(0xD9FFFFFF), shape = RoundedCornerShape(50), modifier = Modifier.size(40.dp)) { Box(contentAlignment = Alignment.Center) { Text("▶", color = Color(0xFF203E59)) } }
                        }
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                if (done) Text("✓", color = Color(0xFF148551), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { contentDescription = "کامل تماشا شده" })
                            }
                            Text(persianDisplay("${item.durationMs / 60000}:${(item.durationMs / 1000 % 60).toString().padStart(2, '0')}"), style = MaterialTheme.typography.labelSmall, color = Color(0xFF657681))
                            if (item.description.isNotBlank()) Text(item.description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            if (downloading) {
                                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(persianDisplay("دریافت ${(state.progress * 100).toInt()}٪"), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                    TextButton(onClick = cancel) { Text("لغو") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview(widthDp = 880, heightDp = 390, locale = "fa")
@Composable private fun TourThreeVideosPreview() { TourPreview(3) }

@Preview(widthDp = 740, heightDp = 340, locale = "fa")
@Composable private fun TourSingleVideoPreview() { TourPreview(1) }

@Preview(widthDp = 880, heightDp = 390, locale = "fa")
@Composable private fun TourSixVideosPreview() { TourPreview(6) }

@Composable private fun TourPreview(count: Int) {
    val media = TourMedia("https://example.com/video", "a".repeat(64), 100)
    val items = (1..count).map { TourVideo(it.toString(), listOf("اولین پروژهٔ من", "آشنایی با بلوک‌ها", "ذخیره و اجرای پروژه")[(it - 1) % 3], "", 90000, media, null, "") }
    LearnerDialogTheme { Surface { TourContent(TourState(true, items, setOf(items.first().key)), null, null, {}, {}) {} } }
}
