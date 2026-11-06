package ir.behnamapps.fascratch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Solid arrows do not depend on the Persian font's arrow glyph size or baseline. */
@Composable
internal fun GamepadKeyLabel(key: String, color: Color = Color.White) {
    val direction = when (key) { "UP", "↑" -> 0; "RIGHT", "→" -> 1; "DOWN", "↓" -> 2; "LEFT", "←" -> 3; else -> null }
    if (direction == null) Text(GamepadLayout.label(key), color = color, fontWeight = FontWeight.Bold)
    else Canvas(Modifier.size(25.dp).semantics {
        contentDescription = listOf("بالا", "راست", "پایین", "چپ")[direction]
    }) {
        val w = size.width
        val h = size.height
        rotate(direction * 90f) {
            drawPath(Path().apply {
                moveTo(w * .5f, h * .08f)
                lineTo(w * .94f, h * .51f)
                lineTo(w * .65f, h * .51f)
                lineTo(w * .65f, h * .92f)
                lineTo(w * .35f, h * .92f)
                lineTo(w * .35f, h * .51f)
                lineTo(w * .06f, h * .51f)
                close()
            }, color)
        }
    }
}
