package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.material3.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import ir.behnamapps.fascratch.R

/** Explicitly center a bounded window; do not inherit an Activity's no-limits layout. */
@Composable internal fun LearnerDialogWindowBounds() {
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.SideEffect {
        (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window?.apply {
            clearFlags(android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            setGravity(android.view.Gravity.CENTER)
            attributes = attributes.apply { x = 0; y = 0 }
            setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
        }
    }
}

/** Use the dialog window's bounds, including landscape side bars/cutouts and the keyboard. */
internal fun Modifier.learnerSafeDialogBounds(): Modifier = fillMaxSize()
    .safeDrawingPadding().imePadding().padding(16.dp).clipToBounds()

/** Leave extra room on both sides, and avoid excessively wide panels on tablets. */
internal fun Modifier.learnerDialogPanelBounds(): Modifier = fillMaxHeight()
    .widthIn(max = 960.dp).fillMaxWidth(.92f)

/** Home and training use different outer themes; learner dialogs must not inherit either. */
@Composable internal fun LearnerDialogTheme(content: @Composable () -> Unit) {
    val typography = remember {
        val font = FontFamily(Font(R.font.shabnam))
        Typography().let { t -> t.copy(
            displayLarge = t.displayLarge.copy(fontFamily = font), displayMedium = t.displayMedium.copy(fontFamily = font),
            displaySmall = t.displaySmall.copy(fontFamily = font), headlineLarge = t.headlineLarge.copy(fontFamily = font),
            headlineMedium = t.headlineMedium.copy(fontFamily = font), headlineSmall = t.headlineSmall.copy(fontFamily = font),
            titleLarge = t.titleLarge.copy(fontFamily = font), titleMedium = t.titleMedium.copy(fontFamily = font),
            titleSmall = t.titleSmall.copy(fontFamily = font), bodyLarge = t.bodyLarge.copy(fontFamily = font),
            bodyMedium = t.bodyMedium.copy(fontFamily = font), bodySmall = t.bodySmall.copy(fontFamily = font),
            labelLarge = t.labelLarge.copy(fontFamily = font), labelMedium = t.labelMedium.copy(fontFamily = font),
            labelSmall = t.labelSmall.copy(fontFamily = font)) }
    }
    val colors = remember { lightColorScheme(
        primary = Color(0xFF21634F), onPrimary = Color.White,
        primaryContainer = Color(0xFFE0EFE4), onPrimaryContainer = Color(0xFF183F38),
        secondary = Color(0xFF855F27), onSecondary = Color.White,
        secondaryContainer = Color(0xFFF5E8CE), onSecondaryContainer = Color(0xFF553C18),
        background = Color(0xFFF7F5EF), onBackground = Color(0xFF183F38),
        surface = Color.White, onSurface = Color(0xFF183F38), onSurfaceVariant = Color(0xFF5D6C65),
        outline = Color(0xFF77877D)) }
    MaterialTheme(colorScheme = colors, typography = typography) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
            LocalTextStyle provides typography.bodyLarge, LocalContentColor provides colors.onSurface) { content() }
    }
}
