package ir.behnamapps.fascratch

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** Anchors to the physical right edge, independent of Persian layout direction. */
@Composable
internal fun FloatingGamepadActions(layout: List<PadKey>, onSettings: () -> Unit, onClose: () -> Unit, onKeyChange: (Int, Boolean) -> Unit) {
    var x by remember { mutableFloatStateOf(0f) }
    var y by remember { mutableFloatStateOf(0f) }
    var rootWidth by remember { mutableIntStateOf(0) }
    var rootHeight by remember { mutableIntStateOf(0) }
    var panelWidth by remember { mutableIntStateOf(0) }
    var panelHeight by remember { mutableIntStateOf(0) }
    val minX = -(rootWidth - panelWidth).coerceAtLeast(0).toFloat()
    val minY = -(rootHeight - panelHeight).coerceAtLeast(0).toFloat()
    Box(Modifier.fillMaxSize().safeDrawingPadding().padding(8.dp)
        .onSizeChanged { rootWidth = it.width; rootHeight = it.height }) {
        Surface(
            Modifier.align(AbsoluteAlignment.BottomRight)
                .absoluteOffset { IntOffset(x.coerceIn(minX, 0f).roundToInt(), y.coerceIn(minY, 0f).roundToInt()) }
                .width(146.dp).onSizeChanged { panelWidth = it.width; panelHeight = it.height },
            color = Color(0x88211B35), shape = RoundedCornerShape(11.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .22f))
        ) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column {
                    Row(Modifier.fillMaxWidth().height(25.dp).background(Color(0x88332A4E))
                        .pointerInput(minX, minY) {
                            detectDragGestures { change, amount ->
                                change.consume()
                                x = (x + amount.x).coerceIn(minX, 0f)
                                y = (y + amount.y).coerceIn(minY, 0f)
                            }
                        }.padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⠿", color = Color.White.copy(alpha = .62f), fontSize = 15.sp)
                        Box(Modifier.size(25.dp).semantics { contentDescription = "تنظیمات گیم‌پد" }
                            .clickable(onClick = onSettings), contentAlignment = Alignment.Center) {
                            Text("⚙", color = Color.White, fontSize = 19.sp)
                        }
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(25.dp).semantics { contentDescription = "بستن گیم‌پد" }
                            .clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                            Text("×", color = Color.White, fontSize = 17.sp)
                        }
                    }
                    GamepadKeyRows(layout, onKeyChange)
                }
            }
        }
    }
}
