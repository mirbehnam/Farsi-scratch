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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ir.behnamapps.fascratch.inappbilling.learning.*
import ir.behnamapps.fascratch.R
import ir.behnamapps.fascratch.inappbilling.presentation.FullscreenLessonPlayer
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
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
    TourButton(modifier.graphicsLayer { rotationZ = rotation.value }, state.allWatched) { open = true }
    if (open) TourDialog(repository) { open = false }
}

@Composable private fun TourButton(modifier: Modifier, done: Boolean, click: () -> Unit) {
    Box(modifier.size(52.dp).semantics { contentDescription = "ویدیوهای راهنمای نرم‌افزار" }.clickable(role = Role.Button, onClick = click)) {
        Image(painterResource(R.drawable.home_tour_video_icon), contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(3.dp), contentScale = ContentScale.Fit)
        if (done) Text("✓", Modifier.align(Alignment.TopEnd), color = Color(0xFF7CE2AB), fontWeight = FontWeight.Bold)
    }
}

private data class TourPlayback(val item: TourVideo, val file: File, val coverage: TourCoverage)

@Composable private fun TourDialog(repository: TourRepository, close: () -> Unit) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var downloadJob by remember { mutableStateOf<Job?>(null) }
    var playback by remember { mutableStateOf<TourPlayback?>(null) }
    var error by remember { mutableStateOf<TourNotice?>(null) }
    var failedItem by remember { mutableStateOf<TourVideo?>(null) }
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(repository, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            tourConnections(context).collectLatest { connected -> if (connected) repository.active(force = true) }
        }
    }
    fun download(item: TourVideo) {
        if (downloadJob?.isActive == true) return
        error = null; failedItem = item
        downloadJob = scope.launch {
            try { val (file, coverage) = repository.open(item); playback = TourPlayback(item, file, coverage); failedItem = null }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = TourFeedback.download(e) }
        }
    }
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
                    TourContent(state, repository, error, { dismiss() }, { downloadJob?.cancel() },
                        refresh = { error = null; scope.launch { repository.active(force = true) } },
                        retry = { failedItem?.let { download(it) } }, select = { download(it) })
                }
            }
        }
    }
}

@Composable private fun TourContent(state: TourState, repository: TourRepository?, error: TourNotice?, close: () -> Unit, cancel: () -> Unit,
    refresh: () -> Unit = {}, retry: () -> Unit = {}, select: (TourVideo) -> Unit) {
    val pulse = tourPlayPulse(state.downloading == null && state.items.any { it.key !in state.completed })
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFFFFAF0), Color(0xFFF1F6FD)))).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.home_tour_video_icon), contentDescription = null, modifier = Modifier.size(34.dp))
            Spacer(Modifier.width(10.dp))
            Text("تور نرم‌افزار", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color(0xFF203E59))
            if (state.items.size > 1) {
                TourTag(persianDisplay("${state.items.count { it.key in state.completed }} از ${state.items.size} دیده شده"), Color(0xFF1C7A5C), Color(0xFFE0F2E8))
                Spacer(Modifier.width(8.dp))
            }
            if (state.items.isNotEmpty()) TextButton(onClick = refresh, enabled = !state.refreshing && state.downloading == null) {
                Text(if (state.refreshing) "در حال دریافت…" else "تازه‌سازی")
            }
            IconButton(onClick = close, modifier = Modifier.semantics { contentDescription = "بستن راهنما" }) { Text("×", style = MaterialTheme.typography.headlineMedium) }
        }
        error?.let { notice ->
            Surface(color = Color(0xFFFFEEDC), shape = RoundedCornerShape(16.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(notice.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, color = Color(0xFF864617))
                        Text(notice.message, style = MaterialTheme.typography.bodySmall, color = Color(0xFF72563E))
                    }
                    FilledTonalButton(onClick = retry, enabled = state.downloading == null) { Text("دوباره دریافت کن") }
                }
            }
        }
        if (state.items.isEmpty()) TourEmptyState(state, Modifier.weight(1f).fillMaxWidth(), refresh)
        else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.items.size == 1) {
                val item = state.items.single()
                TourHero(item, state, repository, pulse, Modifier.fillMaxSize(), cancel) { select(item) }
            } else LazyVerticalGrid(GridCells.Fixed(minOf(3, state.items.size)), modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(state.items, key = { _, item -> item.key }) { index, item ->
                    TourGridCard(item, index + 1, state, repository, pulse, (maxHeight * .5f).coerceIn(56.dp, 150.dp), cancel) { select(item) }
                }
            }
        }
    }
}

@Composable private fun TourEmptyState(state: TourState, modifier: Modifier, refresh: () -> Unit) {
    val bob = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        if (android.os.Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { bob.animateTo(-6f, tween(1100)); bob.animateTo(0f, tween(1100)); delay(100) }
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val compact = maxHeight < 210.dp
        val loading = state.refreshing || !state.initialized
        val onlineEmpty = state.hasManifest && !state.catalogFailed
        Row(Modifier.widthIn(max = 690.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 32.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp)) {
                Text(when { loading -> "راهنماها را پیدا می‌کنیم…"; onlineEmpty -> "ویدیوهای تازه در راه‌اند!"; else -> "برای شروع تور، آنلاین شو!" },
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF203E59))
                Text(when { loading -> "فقط یک لحظه؛ داریم ویدیوهای راهنما را می‌گیریم."; onlineEmpty -> "هنوز ویدیویی منتشر نشده. کمی بعد دوباره به اینجا سر بزن."; else -> "برای دریافت ویدیوهای راهنما به اینترنت وصل شو. بعد از دانلود، آفلاین هم می‌توانی تماشایشان کنی." },
                    style = MaterialTheme.typography.bodyMedium, color = Color(0xFF657681))
                Button(onClick = refresh, enabled = !loading, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF206EB8))) {
                    if (loading) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(if (loading) "در حال دریافت" else "دریافت راهنماها")
                }
            }
            Box(Modifier.size(if (compact) 104.dp else 136.dp).background(Color(0xFFFFE9C7), RoundedCornerShape(32.dp)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.home_tour_video_icon), contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(10.dp).graphicsLayer { translationY = bob.value }, contentScale = ContentScale.Fit)
            }
        }
    }
}

@Preview(widthDp = 880, heightDp = 390, locale = "fa")
@Composable private fun TourThreeVideosPreview() { TourPreview(3) }

@Preview(widthDp = 740, heightDp = 340, locale = "fa")
@Composable private fun TourSingleVideoPreview() { TourPreview(1) }

@Preview(widthDp = 740, heightDp = 340, locale = "fa")
@Composable private fun TourSingleDownloadingPreview() { TourPreview(1, progress = .42f) }

@Preview(widthDp = 880, heightDp = 390, locale = "fa")
@Composable private fun TourSingleCompletedPreview() { TourPreview(1, done = true) }

@Preview(widthDp = 880, heightDp = 390, locale = "fa")
@Composable private fun TourSixVideosPreview() { TourPreview(6) }

@Preview(widthDp = 740, heightDp = 340, locale = "fa")
@Composable private fun TourOfflinePreview() {
    LearnerDialogTheme { Surface { TourContent(TourState(initialized = true, catalogFailed = true), null, null, {}, {}) {} } }
}

@Composable private fun TourPreview(count: Int, done: Boolean = false, progress: Float? = null) {
    val media = TourMedia("https://example.com/video", "a".repeat(64), 100)
    val items = (1..count).map { TourVideo(it.toString(), listOf("اولین پروژهٔ من", "آشنایی با بلوک‌ها", "ذخیره و اجرای پروژه")[(it - 1) % 3], "با محیط اسکرچ آشنا شو و اولین پروژه‌ات را بساز.", 90000, media, null, "") }
    val state = TourState(true, items, if (done) setOf(items.first().key) else emptySet(),
        downloading = items.first().key.takeIf { progress != null }, progress = progress ?: 0f)
    LearnerDialogTheme { Surface { TourContent(state, null, null, {}, {}) {} } }
}
