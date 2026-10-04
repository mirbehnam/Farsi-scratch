package ir.behnamapps.fascratch.inappbilling.presentation

import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.R
import ir.behnamapps.fascratch.inappbilling.StoreBillingFactory
import ir.behnamapps.fascratch.inappbilling.data.*
import ir.behnamapps.fascratch.inappbilling.domain.BillingGateway
import java.io.File

class TrainingActivity : ComponentActivity() {
    private lateinit var billing: BillingGateway
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        billing = StoreBillingFactory.create(this)
        val api = CourseApi()
        val controller = TrainingController(lifecycleScope, billing, api, PurchaseVault(this), CourseCache(this), LessonDownloads(this, api))
        setContent {
            val state by controller.state.collectAsState()
            var playing by remember { mutableStateOf<File?>(null) }
            val font = FontFamily(Font(R.font.shabnam))
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFBBA0FF), secondary = Color(0xFFFFBD59), background = Color(0xFF100D21))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = font)) {
                    BackHandler(playing != null) { playing = null }
                    if (playing != null) LocalVideo(playing!!, onClose = { playing = null })
                    else TrainingScreen(state, controller, onBack = { finish() }, onPlay = { lesson -> playing = controller.playable(lesson) })
                }
            }
        }
        controller.load()
    }
    override fun onDestroy() { if (::billing.isInitialized) billing.close(); super.onDestroy() }
}

@Composable
private fun TrainingScreen(state: TrainingState, controller: TrainingController, onBack: () -> Unit, onPlay: (ir.behnamapps.fascratch.inappbilling.domain.Lesson) -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text("آموزش اسکرچ فارسی", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF251D3C))) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(state.course?.title ?: "خرید دوره", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        state.course?.description?.takeIf { it.isNotBlank() }?.let { Text(it) }
                        Text(if (state.purchased) "دوره فعال است • ${state.downloaded.size} از ${state.lessons.size} درس دانلود شده" else "با خرید دوره، درس‌ها برای دانلود جداگانه و تماشای آفلاین آماده می‌شوند.")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { controller.purchase(restoreOnly = state.purchased) }, enabled = !state.busy && state.course != null && BuildConfig.BILLING_PROVIDER != "website") {
                                Text(if (state.purchased) "تأیید مجدد خرید" else "خرید دوره${state.price?.let { " • $it" }.orEmpty()}")
                            }
                            OutlinedButton(onClick = { controller.purchase(restoreOnly = true) }, enabled = !state.busy && state.course != null && BuildConfig.BILLING_PROVIDER != "website") { Text("بازیابی خرید") }
                            TextButton(onClick = controller::load, enabled = !state.busy) { Text("به‌روزرسانی") }
                            if (state.busy && state.downloadingId == null) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                        if (BuildConfig.BILLING_PROVIDER == "website") Text("خرید در این نسخه فعال نیست؛ نسخه بازار یا مایکت را نصب کنید.")
                    }
                }
            }
            state.message?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.padding(8.dp)) } }
            if (state.lessons.isEmpty() && !state.busy) item { Text("درسی دریافت نشده است؛ اتصال اینترنت و انتشار دوره در سرور را بررسی کنید.", color = Color.White) }
            items(state.lessons, key = { it.id }) { lesson ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(lesson.section, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(lesson.title, fontWeight = FontWeight.Bold)
                                Text("${lesson.bytes / (1024 * 1024)} مگابایت • ${if (lesson.id in state.downloaded) "آماده پخش" else "آماده دانلود"}", style = MaterialTheme.typography.bodySmall)
                            }
                            if (lesson.id in state.downloaded) {
                                Button(onClick = { onPlay(lesson) }, enabled = state.purchased) { Text("پخش") }
                                TextButton(onClick = { controller.delete(lesson) }, enabled = !state.busy) { Text("حذف فایل") }
                            } else Button(onClick = { controller.download(lesson) }, enabled = !state.busy && state.purchased) { Text(if (state.purchased) "دانلود درس" else "نیازمند خرید") }
                        }
                        if (state.downloadingId == lesson.id) {
                            LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${(state.progress * 100).toInt()}٪ • دانلود و بررسی فایل")
                                TextButton(onClick = controller::cancelDownload) { Text("توقف") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalVideo(file: File, onClose: () -> Unit) {
    var video by remember { mutableStateOf<VideoView?>(null) }
    var error by remember { mutableStateOf(false) }
    DisposableEffect(file) { onDispose { video?.stopPlayback() } }
    Column(Modifier.fillMaxSize().background(Color.Black).windowInsetsPadding(WindowInsets.safeDrawing)) {
        TextButton(onClick = onClose) { Text("بازگشت به درس‌ها") }
        if (error) Text("پخش ویدئو روی این دستگاه ممکن نشد؛ فایل را حذف و دوباره دانلود کنید.", color = Color.White)
        AndroidView(modifier = Modifier.weight(1f).fillMaxWidth(), factory = { context ->
            VideoView(context).apply {
                video = this
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setOnErrorListener { _, _, _ -> error = true; true }
                setOnPreparedListener { start() }
                setVideoPath(file.absolutePath)
            }
        })
    }
}
