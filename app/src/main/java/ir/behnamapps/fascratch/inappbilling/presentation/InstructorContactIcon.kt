package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp

@Composable
internal fun InstructorContactIcon(kind: InstructorContactKind) {
    val color = when (kind) {
        InstructorContactKind.INSTAGRAM -> Color(0xFFC33F85)
        InstructorContactKind.TELEGRAM -> Color(0xFF2388BB)
        InstructorContactKind.EMAIL -> Color(0xFFB67B25)
        InstructorContactKind.PHONE -> Color(0xFF287B5B)
        InstructorContactKind.WEBSITE -> Color(0xFF5967B1)
    }
    Box(Modifier.size(36.dp).background(color.copy(alpha = .09f), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(22.dp)) {
            val factor = size.width / 24f
            scale(factor, factor, pivot = Offset.Zero) {
                val stroke = Stroke(1.7f, cap = StrokeCap.Round)
                when (kind) {
                    InstructorContactKind.INSTAGRAM -> {
                        drawRoundRect(color, Offset(3f, 3f), Size(18f, 18f), CornerRadius(5f), style = stroke)
                        drawCircle(color, 4f, Offset(12f, 12f), style = stroke)
                        drawCircle(color, 1.1f, Offset(17.5f, 6.7f))
                    }
                    InstructorContactKind.TELEGRAM -> {
                        val plane = Path().apply { moveTo(2f, 10f); lineTo(21f, 3f); lineTo(17f, 21f); lineTo(11f, 16f); lineTo(7f, 19f); lineTo(7.5f, 13f); close() }
                        drawPath(plane, color, style = stroke)
                        drawLine(color, Offset(7.5f, 13f), Offset(17f, 7f), 1.7f, cap = StrokeCap.Round)
                        drawLine(color, Offset(11f, 16f), Offset(17f, 7f), 1.7f, cap = StrokeCap.Round)
                    }
                    InstructorContactKind.EMAIL -> {
                        drawRoundRect(color, Offset(2f, 5f), Size(20f, 14f), CornerRadius(3f), style = stroke)
                        val fold = Path().apply { moveTo(3f, 6f); lineTo(12f, 13f); lineTo(21f, 6f) }
                        drawPath(fold, color, style = stroke)
                    }
                    InstructorContactKind.PHONE -> {
                        val phone = Path().apply {
                            moveTo(7f, 3f); cubicTo(1f, 3f, 3f, 13f, 8f, 18f)
                            cubicTo(13f, 23f, 21f, 22f, 21f, 17f); lineTo(16f, 14f); lineTo(13f, 17f)
                            cubicTo(10f, 15f, 8f, 13f, 7f, 10f); lineTo(10f, 7f); close()
                        }
                        drawPath(phone, color, style = stroke)
                    }
                    InstructorContactKind.WEBSITE -> {
                        drawCircle(color, 9f, Offset(12f, 12f), style = stroke)
                        drawOval(color, Offset(8f, 3f), Size(8f, 18f), style = stroke)
                        drawLine(color, Offset(3f, 12f), Offset(21f, 12f), 1.7f)
                    }
                }
            }
        }
    }
}
