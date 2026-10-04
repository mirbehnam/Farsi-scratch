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
        val api = CourseApi()
        val controller = TrainingController(lifecycleScope, billing, api, PurchaseVault(this), CourseCache(this), LessonDownloads(this, api))
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
                primary = Color(0xFFB54708), onPrimary = Color.White,
                secondary = Color(0xFF21634C), background = Color(0xFFF4F5F8),
                surface = Color.White, onSurface = Color(0xFF182235),
                onSurfaceVariant = Color(0xFF596579))) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = font)) {
                    BackHandler(playing != null) { playing = null }
                    if (playing != null) FullscreenLessonPlayer(playing!!.file, playing!!.title, window, onClose = { playing = null })
                    else TrainingScreen(state, controller, onBack = { finish() }, onPlay = { lesson ->
                        controller.playable(lesson)?.let { playing = PlayingLesson(it, lesson.title) }
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

private data class PlayingLesson(val file: File, val title: String)
