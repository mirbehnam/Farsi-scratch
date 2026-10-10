package ir.behnamapps.fascratch.inappbilling.presentation

import android.view.Window
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
internal fun FullscreenLessonPlayer(file: File, title: String, window: Window, onClose: () -> Unit,
    watchTracker: ir.behnamapps.fascratch.inappbilling.learning.WatchTracker? = null,
    nextLessonTitle: String? = null, onPlayNext: () -> Boolean = { false }) {
    var video by remember(file) { mutableStateOf<ZoomVideoView?>(null) }
    var prepared by remember(file) { mutableStateOf(false) }
    var error by remember(file) { mutableStateOf(false) }
    var controls by remember(file) { mutableStateOf(true) }
    var zoom by remember(file) { mutableFloatStateOf(1f) }
    var position by rememberSaveable(file.absolutePath) { mutableIntStateOf(0) }
    var duration by remember(file) { mutableIntStateOf(0) }
    var playing by remember(file) { mutableStateOf(false) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var speed by rememberSaveable(file.absolutePath) { mutableFloatStateOf(1f) }
    var speedMenu by remember(file) { mutableStateOf(false) }
    var speedError by remember(file) { mutableStateOf(false) }
    var completed by remember(file) { mutableStateOf(false) }
    var nextPrompt by remember(file) { mutableStateOf(false) }
    var seekHint by remember(file) { mutableStateOf<Int?>(null) }
    var seekHintSequence by remember(file) { mutableIntStateOf(0) }
    val currentNextTitle by rememberUpdatedState(nextLessonTitle)
    val currentPlayNext by rememberUpdatedState(onPlayNext)
    fun seekBy(delta: Int): Boolean {
        if (!prepared) return false
        watchTracker?.discontinuity()
        val currentDuration = video?.duration()?.takeIf { it > 0 } ?: duration
        val base = video?.seekPosition() ?: position
        val target = LessonPlayerPolicy.seek(base, delta, currentDuration)
        if (target == base || video?.seek(target) != true) return false
        position = target; interaction++
        completed = false; nextPrompt = false
        return true
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(window, file) {
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        val oldBehavior = bars.systemBarsBehavior
        val barsWereVisible = ViewCompat.getRootWindowInsets(window.decorView)?.isVisible(WindowInsetsCompat.Type.systemBars()) == true
        val keptAwake = window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            watchTracker?.finish()
            video?.release()
            bars.systemBarsBehavior = oldBehavior
            if (barsWereVisible) bars.show(WindowInsetsCompat.Type.systemBars())
            else bars.hide(WindowInsetsCompat.Type.systemBars())
            if (!keptAwake) window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) { watchTracker?.discontinuity(); video?.setForeground(false); playing = false; controls = true }
            if (event == Lifecycle.Event.ON_RESUME) video?.setForeground(true)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(video, prepared) {
        while (prepared) {
            if (watchTracker?.accessRevoked() == true) {
                video?.setForeground(false); watchTracker.discontinuity(); onClose(); break
            }
            val previouslyPlaying = playing
            val actualPosition = video?.position() ?: 0
            position = video?.seekPosition() ?: actualPosition; duration = video?.duration() ?: 0; playing = video?.isPlaying() == true
            watchTracker?.sample(actualPosition, playing && video?.isSeeking() != true && seeking == null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            if (previouslyPlaying && !playing) controls = true
            delay(300)
        }
    }
    LaunchedEffect(controls, playing, interaction, seeking, speedMenu, nextPrompt) {
        if (controls && playing && seeking == null && !speedMenu && !nextPrompt) { delay(4_000); controls = false }
    }
    LaunchedEffect(seekHintSequence) {
        if (seekHint != null) { delay(1100); seekHint = null }
    }
    val playerColors = darkColorScheme(primary = Color(0xFFFFBC72), onPrimary = Color(0xFF39220D), surface = Color(0xFF182235))
    MaterialTheme(colorScheme = playerColors, typography = MaterialTheme.typography) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                ZoomVideoView(context, file, position,
                    onReady = { prepared = true; video?.setSpeed(speed) }, onError = { error = true; controls = true },
                    onTap = { controls = !controls; interaction++ }, onZoom = { zoom = it; controls = false; interaction++ },
                    onDoubleTapSeek = { delta ->
                        if (seekBy(delta)) {
                            seekHint = if (seekHint != null && (seekHint!! > 0) == (delta > 0))
                                (seekHint!!.toLong() + delta).coerceIn(-3600000, 3600000).toInt() else delta
                            seekHintSequence++
                        }
                    },
                    onCompleted = {
                        position = video?.duration() ?: duration
                        playing = false; completed = true; controls = true
                        watchTracker?.finish()
                        nextPrompt = currentNextTitle != null
                    }).also {
                        video = it; it.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    }
            })
            seekHint?.let { delta ->
                key(seekHintSequence) {
                    PlayerSeekFeedback(delta, Modifier.align(if (delta > 0) androidx.compose.ui.AbsoluteAlignment.CenterRight else androidx.compose.ui.AbsoluteAlignment.CenterLeft)
                        .fillMaxWidth(.5f).fillMaxHeight())
                }
            }
            if (!prepared && !error) CircularProgressIndicator(Modifier.align(Alignment.Center))
            if (error) Surface(Modifier.align(Alignment.Center).padding(32.dp), color = Color(0xEE182235)) {
                Text("پخش این فایل ممکن نشد. به درس‌ها برگردید و فایل را دوباره دانلود کنید.", Modifier.padding(20.dp), color = Color.White)
            }
            if (controls || error) {
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xD9000000), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("بازگشت به درس‌ها") }
                    Text(persianDisplay(title), Modifier.weight(1f).padding(horizontal = 12.dp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = { video?.resetZoom(); interaction++ }) { Text("${persianDisplay(String.format(Locale.US, "%.1f", zoom)).replace('.', '٫')}× · بازنشانی") }
                }
                if (!error) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xED000000))))
                    .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text("زوم با دو انگشت · دو ضربه: راست ۵ ثانیه جلو، چپ ۵ ثانیه عقب", color = Color(0xFFDBDBDB), style = MaterialTheme.typography.labelSmall)
                    // Time always runs from left to right, independently of Persian layout.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Slider(value = seeking ?: position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                            onValueChange = { seeking = it; interaction++ }, onValueChangeFinished = {
                                watchTracker?.discontinuity(); seeking?.let { video?.seek(it.toInt()); position = it.toInt() }; seeking = null; interaction++; completed = false; nextPrompt = false
                            }, valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = prepared && duration > 0,
                            modifier = Modifier.fillMaxWidth().height(32.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { seekBy(10_000) }, enabled = prepared) { Text("۱۰ ثانیه جلو") }
                        FilledTonalButton(onClick = {
                            if (completed) { watchTracker?.discontinuity(); video?.seek(0); position = 0; completed = false }
                            video?.toggle(); playing = video?.isPlaying() == true; interaction++
                        }, enabled = prepared) { Text(if (playing) "توقف" else "پخش") }
                        TextButton(onClick = { seekBy(-10_000) }, enabled = prepared) { Text("۱۰ ثانیه عقب") }
                        Box {
                            TextButton(onClick = { speedMenu = true; interaction++ }, enabled = prepared) {
                                Text("سرعت ${persianDisplay(speed.toString()).replace('.', '٫')}×")
                            }
                            DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                                LessonPlayerPolicy.speeds.forEach { option ->
                                    DropdownMenuItem(text = { Text((if (option == speed) "✓ " else "") + persianDisplay(option.toString()).replace('.', '٫') + "×") },
                                        onClick = {
                                            watchTracker?.discontinuity()
                                            if (video?.setSpeed(option) == true) { speed = option; speedError = false }
                                            else speedError = true
                                            speedMenu = false; interaction++
                                        })
                                }
                            }
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(persianDisplay("${playbackTime(position)} / ${playbackTime(duration)}"), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                    if (speedError) Text("این دستگاه نتوانست سرعت انتخاب‌شده را اعمال کند.", color = Color(0xFFFFBC72), style = MaterialTheme.typography.labelSmall)
                }
            }
            if (nextPrompt && currentNextTitle != null) AlertDialog(
                onDismissRequest = { nextPrompt = false }, title = { Text("قسمت بعد پخش شود؟") },
                text = { Text(persianDisplay(currentNextTitle!!)) },
                confirmButton = { Button(onClick = {
                    if (!currentPlayNext()) { nextPrompt = false; onClose() }
                }) { Text("پخش قسمت بعد") } },
                dismissButton = { TextButton(onClick = { nextPrompt = false }) { Text("فعلاً نه") } })
        }
    }
}

private fun playbackTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}
