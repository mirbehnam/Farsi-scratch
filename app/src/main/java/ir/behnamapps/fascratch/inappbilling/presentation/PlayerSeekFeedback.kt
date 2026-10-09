package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/** Non-interactive overlay: it must never steal the next tap from TextureView. */
@Composable internal fun PlayerSeekFeedback(delta: Int, modifier: Modifier = Modifier) {
    val phase = remember { Animatable(0f) }
    LaunchedEffect(Unit) { phase.animateTo(1f, tween(1050)) }
    val opacity = if (phase.value < .12f) phase.value / .12f else ((1f - phase.value) / .35f).coerceIn(0f, 1f)
    Box(modifier.graphicsLayer { alpha = opacity }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val radius = size.maxDimension * (.38f + phase.value * .25f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = .15f), Color.Black.copy(alpha = .12f), Color.Transparent),
                center, radius), radius, center)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.width(90.dp).height(30.dp)) {
                val direction = if (delta > 0) 1f else -1f
                for (index in 0..2) {
                    val step = if (delta > 0) index else 2 - index
                    val x = size.width * (.2f + index * .3f)
                    val pulse = (1f - abs((phase.value * 3f - step * .22f) % 1f - .5f) * 2f).coerceIn(.3f, 1f)
                    val arrow = Path().apply {
                        moveTo(x - direction * 7.dp.toPx(), size.height * .12f)
                        lineTo(x + direction * 7.dp.toPx(), size.height * .5f)
                        lineTo(x - direction * 7.dp.toPx(), size.height * .88f)
                        close()
                    }
                    drawPath(arrow, Color.White.copy(alpha = pulse))
                }
            }
            Text(persianDisplay((abs(delta.toLong()) / 1000).toString()) + " ثانیه",
                color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        }
    }
}
