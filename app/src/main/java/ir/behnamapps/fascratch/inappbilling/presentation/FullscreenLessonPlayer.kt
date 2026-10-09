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
import androidx.compose.ui.platform.LocalLifecycleOwner
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
    watchTracker: ir.behnamapps.fascratch.inappbilling.learning.WatchTracker? = null) {
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
            position = video?.position() ?: 0; duration = video?.duration() ?: 0; playing = video?.isPlaying() == true
            watchTracker?.sample(position, playing && seeking == null && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
            if (!playing) controls = true
            delay(300)
        }
    }
    LaunchedEffect(controls, playing, interaction, seeking) {
        if (controls && playing && seeking == null) { delay(4_000); controls = false }
    }
    val playerColors = darkColorScheme(primary = Color(0xFFFFBC72), onPrimary = Color(0xFF39220D), surface = Color(0xFF182235))
    MaterialTheme(colorScheme = playerColors, typography = MaterialTheme.typography) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                ZoomVideoView(context, file, position,
                    onReady = { prepared = true }, onError = { error = true; controls = true },
                    onTap = { controls = !controls; interaction++ }, onZoom = { zoom = it }).also {
                        video = it; it.setForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
                    }
            })
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
                    Text("زوم با دو انگشت · جابه‌جایی تصویر بزرگ‌شده · دو ضربه برای اندازه اصلی", color = Color(0xFFDBDBDB), style = MaterialTheme.typography.labelSmall)
                    // Time always runs from left to right, independently of Persian layout.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Slider(value = seeking ?: position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()),
                            onValueChange = { seeking = it; interaction++ }, onValueChangeFinished = {
                                watchTracker?.discontinuity(); seeking?.let { video?.seek(it.toInt()); position = it.toInt() }; seeking = null; interaction++
                            }, valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = prepared && duration > 0,
                            modifier = Modifier.fillMaxWidth().height(32.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                        TextButton(onClick = { watchTracker?.discontinuity(); video?.seek(position - 10_000); interaction++ }, enabled = prepared) { Text("۱۰ ثانیه عقب") }
                        FilledTonalButton(onClick = { video?.toggle(); playing = video?.isPlaying() == true; interaction++ }, enabled = prepared) { Text(if (playing) "توقف" else "پخش") }
                        TextButton(onClick = { watchTracker?.discontinuity(); video?.seek(position + 10_000); interaction++ }, enabled = prepared) { Text("۱۰ ثانیه جلو") }
                        Spacer(Modifier.width(16.dp))
                        Text(persianDisplay("${playbackTime(position)} / ${playbackTime(duration)}"), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

private fun playbackTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)
}
