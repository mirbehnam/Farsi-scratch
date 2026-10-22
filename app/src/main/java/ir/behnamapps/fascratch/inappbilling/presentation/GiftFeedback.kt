package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class GiftFeedbackStyle(val title: String, val symbol: String, val color: Long)
internal fun giftFeedbackStyle(code: String) = when (code) {
    "gift_expired" -> GiftFeedbackStyle("زمان این هدیه تمام شده", "clock", 0xFFB56C14)
    "gift_exhausted" -> GiftFeedbackStyle("هدیه‌ها تمام شدند", "box", 0xFF8C58B6)
    "gift_deleted" -> GiftFeedbackStyle("این کد دیگر وجود ندارد", "removed", 0xFFB95759)
    "gift_disabled" -> GiftFeedbackStyle("این هدیه فعلاً متوقف است", "pause", 0xFF64748B)
    "gift_not_started" -> GiftFeedbackStyle("هنوز وقتش نرسیده!", "clock", 0xFF397BB0)
    "gift_course_owned", "gift_not_better" -> GiftFeedbackStyle("دوره‌ات همین حالا فعال است", "check", 0xFF218064)
    "gift_course_unavailable" -> GiftFeedbackStyle("دوره فعلاً در دسترس نیست", "pause", 0xFF64748B)
    "gift_not_found", "validation_failed" -> GiftFeedbackStyle("کد را دوباره بررسی کن", "search", 0xFFC1762C)
    "success" -> GiftFeedbackStyle("هدیه‌ات رسید!", "gift", 0xFF218064)
    "already_redeemed" -> GiftFeedbackStyle("قبلاً این هدیه را گرفته‌ای", "check", 0xFF397BB0)
    else -> GiftFeedbackStyle("دوباره امتحان کنیم؟", "retry", 0xFF397BB0)
}

@Composable
internal fun GiftFeedback(code: String, message: String) {
    val style = giftFeedbackStyle(code)
    val accent = Color(style.color)
    var visible by remember(code, message) { mutableStateOf(false) }
    LaunchedEffect(code, message) { visible = true }
    AnimatedVisibility(visible, enter = fadeIn() + scaleIn(initialScale = .97f)) {
        Surface(color = accent.copy(alpha = .09f), shape = RoundedCornerShape(18.dp)) {
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Canvas(Modifier.size(62.dp)) {
                    val unit = size.minDimension / 64f
                    fun point(x: Float, y: Float) = Offset(x * unit, y * unit)
                    fun line(x: Float, y: Float, a: Float, b: Float) = drawLine(accent, point(x,y), point(a,b), 3f * unit, StrokeCap.Round)
                    drawCircle(accent.copy(alpha = .12f))
                    drawCircle(accent.copy(alpha = .28f), 2 * unit, point(10f,12f))
                    drawCircle(accent.copy(alpha = .3f), 3 * unit, point(54f,50f))
                    when (style.symbol) {
                        "clock" -> {
                            drawCircle(accent, 17 * unit, point(32f,32f), style = Stroke(3 * unit))
                            line(32f,21f,32f,32f); line(32f,32f,40f,37f)
                        }
                        "pause" -> { line(25f,21f,25f,43f); line(39f,21f,39f,43f) }
                        "check" -> { line(18f,33f,28f,42f); line(28f,42f,46f,22f) }
                        "removed" -> {
                            drawCircle(accent, 18 * unit, point(32f,32f), style = Stroke(3 * unit))
                            line(20f,20f,44f,44f)
                        }
                        "search" -> {
                            drawCircle(accent, 12 * unit, point(28f,27f), style = Stroke(3 * unit)); line(37f,36f,48f,47f)
                        }
                        "box", "gift" -> {
                            drawRoundRect(accent, point(17f,29f), Size(30*unit,20*unit), CornerRadius(3*unit), style = Stroke(3*unit))
                            if (style.symbol == "gift") {
                                drawRoundRect(accent, point(14f,22f), Size(36*unit,8*unit), CornerRadius(2*unit), style = Stroke(3*unit))
                                line(32f,22f,32f,49f); line(23f,15f,32f,22f); line(41f,15f,32f,22f)
                            } else { line(17f,29f,11f,21f); line(11f,21f,25f,17f); line(47f,29f,53f,21f); line(53f,21f,39f,17f) }
                        }
                        else -> { drawArc(accent, 35f, 285f, false, point(16f,16f), Size(32*unit,32*unit), style=Stroke(3*unit, cap=StrokeCap.Round)); line(48f,24f,48f,34f); line(48f,34f,38f,31f) }
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(style.title, color = accent, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(message, color = Color(0xFF48564F), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
