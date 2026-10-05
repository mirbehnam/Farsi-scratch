package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import ir.behnamapps.fascratch.inappbilling.domain.Course
import ir.behnamapps.fascratch.inappbilling.domain.CoursePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Display only. Missing deadlines never manufacture scarcity or a countdown. */
internal fun discountCountdownLabel(course: Course, now: Long): String? {
    if (!CoursePolicy.discountVisible(course, now)) return null
    val end = course.discountEndsAtMillis ?: return null
    val remaining = end - now
    val days = remaining / 86_400_000L
    val hours = remaining / 3_600_000L % 24
    val text = when {
        days > 0 -> "$days روز" + if (hours > 0) " و $hours ساعت" else ""
        hours > 0 -> "$hours ساعت"
        remaining >= 60_000L -> "${remaining / 60_000L} دقیقه"
        else -> "کمتر از یک دقیقه"
    }
    return persianDisplay("$text تا پایان تخفیف")
}

/** One-minute ticks while visible, an exact final tick, and immediate refresh on resume. */
@Composable
internal fun rememberDiscountClock(course: Course): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var clock by remember(course.id, course.discountEndsAtMillis) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(course.id, course.discountEndsAtMillis, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            clock = System.currentTimeMillis()
            val end = course.discountEndsAtMillis
            while (isActive && end != null && end > clock) {
                delay((end - clock).coerceAtMost(60_000L))
                clock = System.currentTimeMillis()
            }
        }
    }
    return clock
}

@Composable
internal fun DiscountCountdown(course: Course, modifier: Modifier = Modifier) {
    val text = discountCountdownLabel(course, rememberDiscountClock(course)) ?: return
    val red = Color(0xFFB42332)
    Surface(modifier = modifier, color = Color(0xFFFFF0F1), contentColor = red, shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFF4CDD2))) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Canvas(Modifier.size(14.dp)) {
                val stroke = 1.4.dp.toPx()
                drawCircle(red, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
                drawLine(red, center, Offset(center.x, size.height * .28f), stroke, StrokeCap.Round)
                drawLine(red, center, Offset(size.width * .68f, center.y), stroke, StrokeCap.Round)
            }
            Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
        }
    }
}
