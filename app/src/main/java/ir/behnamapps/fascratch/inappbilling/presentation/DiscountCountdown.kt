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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import ir.behnamapps.fascratch.inappbilling.domain.Course
import ir.behnamapps.fascratch.inappbilling.domain.CoursePolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

internal data class DiscountTimeParts(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)

internal fun discountTimeParts(course: Course, now: Long): DiscountTimeParts? {
    if (!CoursePolicy.discountVisible(course, now)) return null
    val remaining = (course.discountEndsAtMillis ?: return null) - now
    // Round up the last partial second: never show 00:00:00 while still active.
    val seconds = remaining / 1000 + if (remaining % 1000 > 0) 1 else 0
    return DiscountTimeParts(seconds / 86400, seconds / 3600 % 24, seconds / 60 % 60, seconds % 60)
}

internal fun discountTickMillis(course: Course, now: Long): Long? {
    val remaining = (course.discountEndsAtMillis ?: return null) - now
    if (remaining <= 0) return null
    return if (remaining <= 86_400_000L) minOf(remaining, 1000L)
        else minOf(60_000L, remaining - 86_400_000L)
}

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

/** Minute ticks for distant deadlines; second ticks in the final day, only while visible. */
@Composable
internal fun rememberDiscountClock(course: Course): Long {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var clock by remember(course.id, course.discountEndsAtMillis) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(course.id, course.discountEndsAtMillis, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            clock = System.currentTimeMillis()
            val end = course.discountEndsAtMillis
            while (isActive && end != null && end > clock) {
                delay(discountTickMillis(course, clock) ?: break)
                clock = System.currentTimeMillis()
            }
        }
    }
    return clock
}

@Composable
internal fun DiscountCountdown(course: Course, modifier: Modifier = Modifier) {
    val clock = rememberDiscountClock(course)
    val parts = discountTimeParts(course, clock) ?: return
    val text = discountCountdownLabel(course, clock) ?: return
    val red = Color(0xFFB42332)
    val urgent = course.discountEndsAtMillis!! - clock <= 21_600_000L
    Surface(modifier = modifier.widthIn(max = 240.dp).fillMaxWidth().clearAndSetSemantics { contentDescription = text },
        color = if (urgent) Color(0xFFFFE6EA) else Color(0xFFFFF3F5), contentColor = red,
        shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFF0C2CB))) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Canvas(Modifier.size(14.dp)) {
                    val stroke = 1.4.dp.toPx()
                    drawCircle(red, radius = size.minDimension / 2 - stroke, style = Stroke(stroke))
                    drawLine(red, center, Offset(center.x, size.height * .28f), stroke, StrokeCap.Round)
                    drawLine(red, center, Offset(size.width * .68f, center.y), stroke, StrokeCap.Round)
                }
                Text("پایان تخفیف",
                    style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
            }
            val units = if (parts.days > 0) listOf(parts.days to "روز", parts.hours to "ساعت", parts.minutes to "دقیقه")
                else listOf(parts.hours to "ساعت", parts.minutes to "دقیقه", parts.seconds to "ثانیه")
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    units.forEach { (value, unit) ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Surface(Modifier.fillMaxWidth(), color = if (urgent) red else Color(0xFF84223B),
                                contentColor = Color.White, shape = RoundedCornerShape(9.dp)) {
                                Box(Modifier.padding(horizontal = 3.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                                    Text(persianDisplay(value.toString().padStart(2, '0')), fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp)
                                }
                            }
                            Text(unit, color = red, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
