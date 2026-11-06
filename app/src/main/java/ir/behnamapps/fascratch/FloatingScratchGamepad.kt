package ir.behnamapps.fascratch

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable
internal fun FloatingScratchGamepad(modifier: Modifier, onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit, onKeyChange: (Int, Boolean) -> Unit) {
    val send by rememberUpdatedState(onKeyChange)
    val drag by rememberUpdatedState(onDrag)
    var sector by remember { mutableStateOf<Int?>(null) }
    val held = remember { mutableSetOf<Int>() }
    var foreground by remember { mutableStateOf(true) }
    fun select(next: Int?) {
        val vector = next?.let { GamepadDirection.vectors[it] }
        val keys = buildSet {
            if (vector?.first == -1) add(KeyEvent.KEYCODE_DPAD_LEFT)
            if (vector?.first == 1) add(KeyEvent.KEYCODE_DPAD_RIGHT)
            if (vector?.second == -1) add(KeyEvent.KEYCODE_DPAD_UP)
            if (vector?.second == 1) add(KeyEvent.KEYCODE_DPAD_DOWN)
        }
        (held - keys).forEach { send(it, false) }
        (keys - held).forEach { send(it, true) }
        held.clear(); held.addAll(keys); sector = next
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                foreground = false; select(null)
            } else if (event == Lifecycle.Event.ON_RESUME) foreground = true
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); select(null) }
    }
    LaunchedEffect(sector) {
        if (sector != null) {
            delay(400)
            while (isActive) { held.toList().forEach { send(it, true) }; delay(50) }
        }
    }
    Surface(modifier.width(140.dp), color = Color(0x88211B35), shape = RoundedCornerShape(11.dp), shadowElevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .22f))) {
        Column {
            Row(Modifier.fillMaxWidth().height(25.dp).background(Color(0x88332A4E))
                .pointerInput(Unit) { detectDragGestures { change, amount -> change.consume(); drag(amount.x, amount.y) } }
                .padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("⠿", color = Color.White.copy(.62f), fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(25.dp).clickable { select(null); onClose() }.semantics { contentDescription = "بستن گیم‌پد" }, contentAlignment = Alignment.Center) {
                    Text("×", color = Color.White, fontSize = 17.sp)
                }
            }
            Canvas(Modifier.padding(8.dp).size(124.dp).semantics { contentDescription = "آنالوگ هشت‌جهته" }
                .pointerInput(foreground) {
                    if (!foreground) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = minOf(size.width, size.height) / 2f
                        fun update(position: Offset) {
                            val delta = position - center
                            select(GamepadDirection.sector(delta.x, delta.y, radius * .19f))
                        }
                        down.consume()
                        try {
                            update(down.position)
                            // Track the original pointer until release, even beyond panel bounds.
                            while (true) {
                                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) break
                                update(change.position)
                            }
                        } finally { select(null) }
                    }
                }) {
                val radius = size.minDimension / 2
                val center = center
                drawCircle(Color(0x5539304F), radius)
                for (i in 0..7) {
                    drawArc(if (sector == i) Color(0xDD70DCC2) else Color(0x554C4262),
                        i * 45f - 21.5f, 43f, true, Offset.Zero, Size(size.width, size.height))
                    val angle = i * PI / 4
                    val point = center + Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius * .79f
                    drawCircle(if (sector == i) Color(0xFF153D36) else Color(0xFFBDB1D6), radius * .035f, point)
                }
                drawCircle(Color(0x66211B35), radius * .57f)
                drawCircle(Color.White.copy(.18f), radius * .57f, style = Stroke(1.dp.toPx()))
                val angle = (sector ?: 0) * PI / 4
                val shift = if (sector == null) Offset.Zero else Offset(cos(angle).toFloat(), sin(angle).toFloat()) * radius * .32f
                drawCircle(if (sector == null) Color(0xFFE6DFF2) else Color(0xFF70DCC2), radius * .25f, center + shift)
            }
        }
    }
}
