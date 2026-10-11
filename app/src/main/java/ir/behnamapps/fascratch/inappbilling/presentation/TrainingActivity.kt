package ir.behnamapps.fascratch.inappbilling.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
        hideSystemBars()
        billing = StoreBillingFactory.create(this)
        val api = CourseApi(this)
        ir.behnamapps.fascratch.inappbilling.learning.LearningRepository.get(this).visit()
        val controller = TrainingController(lifecycleScope, billing, api, PurchaseVault(this), CourseCache(this), LessonDownloads(this, api),
            ir.behnamapps.fascratch.inappbilling.learning.LearningRepository.get(this))
        setContent {
            val state by controller.state.collectAsState()
            var playing by remember { mutableStateOf<PlayingLesson?>(null) }
            val font = FontFamily(Font(R.font.shabnam))
            val typography = Typography().let { defaults -> defaults.copy(
                headlineSmall = defaults.headlineSmall.copy(fontFamily = font),
                titleLarge = defaults.titleLarge.copy(fontFamily = font),
                titleMedium = defaults.titleMedium.copy(fontFamily = font),
                bodyLarge = defaults.bodyLarge.copy(fontFamily = font),
                bodyMedium = defaults.bodyMedium.copy(fontFamily = font),
                bodySmall = defaults.bodySmall.copy(fontFamily = font),
                labelLarge = defaults.labelLarge.copy(fontFamily = font),
                labelMedium = defaults.labelMedium.copy(fontFamily = font),
                labelSmall = defaults.labelSmall.copy(fontFamily = font)) }
            MaterialTheme(typography = typography, colorScheme = lightColorScheme(
                primary = Color(0xFF21634F), onPrimary = Color.White,
                primaryContainer = Color(0xFFE0EFE4), onPrimaryContainer = Color(0xFF183F38),
                secondary = Color(0xFF855F27), secondaryContainer = Color(0xFFF5E8CE),
                onSecondaryContainer = Color(0xFF553C18), background = Color(0xFFF7F5EF),
                surface = Color.White, onSurface = Color(0xFF183F38),
                onBackground = Color(0xFF183F38), outline = Color(0xFF77877D),
                onSurfaceVariant = Color(0xFF5D6C65))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = font)) {
                    BackHandler(playing != null) { playing = null }
                    BackHandler(playing == null && state.course != null) { controller.backToCatalog() }
                    if (playing != null) key(playing!!.lessonId) {
                        val current = playing!!
                        FullscreenLessonPlayer(current.file, current.title, window, onClose = { playing = null }, watchTracker = current.tracker,
                            nextLessonTitle = controller.nextDownloadedLesson(current.lessonId)?.title,
                            onPlayNext = {
                                val next = controller.nextDownloadedLesson(current.lessonId)
                                val nextFile = next?.let(controller::playable)
                                if (next != null && nextFile != null) {
                                    playing = PlayingLesson(nextFile, next.title, controller.watchTracker(next), next.id)
                                    true
                                } else false
                            })
                    }
                    else TrainingScreen(state, controller, onBack = { if (!controller.backToCatalog()) finish() }, onPlay = { lesson ->
                        controller.playable(lesson)?.let { playing = PlayingLesson(it, lesson.title, controller.watchTracker(lesson), lesson.id) }
                    })
                }
            }
        }
        controller.load()
    }
    override fun onDestroy() { if (::billing.isInitialized) runCatching { billing.close() }; super.onDestroy() }
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

private data class PlayingLesson(val file: File, val title: String, val tracker: ir.behnamapps.fascratch.inappbilling.learning.WatchTracker?, val lessonId: String)
