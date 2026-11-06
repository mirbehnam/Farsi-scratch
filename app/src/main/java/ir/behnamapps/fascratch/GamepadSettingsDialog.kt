@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ir.behnamapps.fascratch

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

internal fun gamepadKeyCode(key: String): Int = when (key) {
    "UP" -> KeyEvent.KEYCODE_DPAD_UP; "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
    "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT; "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
    "SPACE" -> KeyEvent.KEYCODE_SPACE
    else -> if (key.single() in 'A'..'Z') KeyEvent.KEYCODE_A + (key.single() - 'A') else KeyEvent.KEYCODE_0 + (key.single() - '0')
}

@Composable
internal fun GamepadKeyRows(layout: List<PadKey>, onKeyChange: (Int, Boolean) -> Unit) {
    Column(Modifier.heightIn(max = 230.dp).verticalScroll(rememberScrollState()).padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val lastRow = (layout.maxOfOrNull { it.cell } ?: 0) / 3
        for (row in 0..lastRow) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val space = layout.firstOrNull { it.key == "SPACE" && it.cell / 3 == row }
                if (space != null) key(space.key) { ScratchKeyButton("SPACE", gamepadKeyCode(space.key), onKeyChange, Modifier.fillMaxWidth().height(31.dp), glass = true) }
                else for (col in 0..2) {
                    val item = layout.firstOrNull { it.cell == row * 3 + col }
                    if (item == null) Spacer(Modifier.size(38.dp))
                    else key(item.key) { ScratchKeyButton(GamepadLayout.label(item.key), gamepadKeyCode(item.key), onKeyChange, glass = true) }
                }
            }
        }
    }
}

@Composable
internal fun GamepadSettingsDialog(initial: List<PadKey>, onDismiss: () -> Unit, onSave: (List<PadKey>) -> Unit) {
    var draft by remember { mutableStateOf(initial) }
    var moving by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val paletteBounds = remember { mutableMapOf<String, Rect>() }
    val cellBounds = remember { mutableMapOf<Int, Rect>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var gridBounds by remember { mutableStateOf(Rect.Zero) }
    var gridViewport by remember { mutableStateOf(Rect.Zero) }
    var dragPosition by remember { mutableStateOf<Offset?>(null) }
    val gridScroll = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val lift = with(density) { 58.dp.toPx() }
    LaunchedEffect(moving, dragPosition) {
        val position = dragPosition ?: return@LaunchedEffect
        while (isActive && moving != null && position.x in gridViewport.left..gridViewport.right) {
            val margin = with(density) { 36.dp.toPx() }
            val step = when {
                position.y < gridViewport.top + margin && position.y >= gridViewport.top -> -8f
                position.y > gridViewport.bottom - margin && position.y <= gridViewport.bottom -> 8f
                else -> 0f
            }
            if (step == 0f) break
            gridScroll.scrollBy(step)
            delay(16)
        }
    }
    fun targetAt(position: Offset?): Int? {
        if (position == null || !gridBounds.contains(position) || !gridViewport.contains(position)) return null
        return cellBounds.entries.firstOrNull { it.value.contains(position) }?.key
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MaterialTheme(colorScheme = darkColorScheme()) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = FontFamily(Font(R.font.shabnam)), color = Color.White)) {
                Surface(Modifier.padding(16.dp).widthIn(max = 640.dp).fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = Color(0xFF211B35), contentColor = Color(0xFFF2EDF8)) {
                  Box {
                    Column(Modifier.onGloballyPositioned { rootOrigin = it.positionInRoot() }
                        .pointerInput(draft) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { point ->
                                    val position = point + rootOrigin
                                    moving = targetAt(position)?.let { cell -> draft.firstOrNull { cell in GamepadLayout.cells(it) }?.key }
                                        ?: paletteBounds.entries.firstOrNull { it.value.contains(position) }?.key
                                    dragPosition = if (moving != null) position else null
                                    if (moving != null) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onDrag = { change, _ ->
                                    if (moving != null) { change.consume(); dragPosition = change.position + rootOrigin }
                                },
                                onDragCancel = { moving = null; dragPosition = null },
                                onDragEnd = {
                                    val name = moving
                                    val cell = targetAt(dragPosition)
                                    if (name != null && cell != null) {
                                        val next = GamepadLayout.drop(draft, name, cell)
                                        message = if (next == draft && draft.none { it.key == name } && draft.size >= 10) "حداکثر ۱۰ کلید مجاز است." else null
                                        draft = next
                                    }
                                    moving = null; dragPosition = null
                                }
                            )
                        }.padding(16.dp)) {
                        Text("تنظیمات گیم‌پد · ${draft.size} از ۱۰ کلید", style = MaterialTheme.typography.titleMedium)
                        // Reset sits immediately below the settings heading.
                        TextButton(onClick = { draft = GamepadLayout.defaults; moving = null }) { Text("↺  بازگشت به حالت اولیه") }
                        Row(Modifier.weight(1f, fill = false), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                                Text("برای افزودن یا حذف لمس کن؛ برای چیدن، نگه دار و بکش.", style = MaterialTheme.typography.bodySmall)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    GamepadLayout.allowed.forEach { name ->
                                        val selected = draft.any { it.key == name }
                                        FilterChip(selected = selected, enabled = selected || draft.size < 10,
                                            modifier = Modifier.onGloballyPositioned { paletteBounds[name] = it.boundsInRoot() }
                                                .alpha(if (moving == name && dragPosition != null) .3f else 1f),
                                            onClick = {
                                                val next = if (selected) draft.filter { it.key != name } else GamepadLayout.add(draft, name)
                                                message = if (!selected && next == draft) "برای Space یک ردیف خالی کن." else null
                                                draft = next
                                                moving = null
                                            }, label = { GamepadKeyLabel(name) })
                                    }
                                }
                                message?.let { Text(it, color = Color(0xFFFFD59C), style = MaterialTheme.typography.bodySmall) }
                            }
                            Column(Modifier.width(170.dp).onGloballyPositioned { gridViewport = it.boundsInRoot() }
                                .verticalScroll(gridScroll), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("کلید را روی خانهٔ خالی یا پُر بکش. Space یک ردیف کامل می‌گیرد.", style = MaterialTheme.typography.bodySmall)
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                  Column(Modifier.onGloballyPositioned {
                                      gridBounds = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                                  }, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    for (row in 0 until GamepadLayout.rows) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        for (col in 0..2) {
                                            val cell = row * 3 + col
                                            val item = draft.firstOrNull { cell in GamepadLayout.cells(it) }
                                            Surface(Modifier.size(50.dp, 38.dp).onGloballyPositioned {
                                                cellBounds[cell] = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat()))
                                            }.semantics { contentDescription = "خانهٔ $cell" }.clickable {
                                                val selected = moving
                                                if (selected == null) moving = item?.key
                                                else {
                                                    val target = if (selected == "SPACE") row * 3 else cell
                                                    val next = GamepadLayout.move(draft, selected, target)
                                                    if (next != draft || item?.key == selected) moving = null
                                                    draft = next
                                                }
                                            }, shape = RoundedCornerShape(8.dp),
                                                color = if (cell == targetAt(dragPosition) || (moving == "SPACE" && targetAt(dragPosition)?.div(3) == row)) Color(0xFF387967)
                                                    else if (item?.key == moving && dragPosition != null) Color(0xFF211B35) else Color(0xFF39304F),
                                                border = BorderStroke(1.dp, if (cell == targetAt(dragPosition)) Color(0xFF9EF0D6) else Color.White.copy(alpha = .2f))) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    if (item != null && !(item.key == moving && dragPosition != null)) GamepadKeyLabel(item.key)
                                                    else Text("·", color = Color.White.copy(alpha = .35f))
                                                }
                                            }
                                        }
                                    }
                                  }
                                }
                                if (moving != null) Text("در حال جابه‌جایی: ${GamepadLayout.label(moving!!)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDismiss) { Text("انصراف") }
                            Button(onClick = { onSave(draft) }) { Text("ذخیره") }
                        }
                    }
                    val floatingKey = moving
                    val position = dragPosition
                    if (floatingKey != null && position != null) {
                        val ghostWidth = if (floatingKey == "SPACE") 158.dp else 56.dp
                        val halfWidth = with(density) { ghostWidth.toPx() / 2 }
                        Surface(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset {
                            IntOffset((position.x - rootOrigin.x - halfWidth).roundToInt(),
                                (position.y - rootOrigin.y - lift).roundToInt())
                        }.size(ghostWidth, 44.dp).semantics { contentDescription = "کلید در حال حرکت" }, color = Color(0xFF387967),
                            shape = RoundedCornerShape(10.dp), shadowElevation = 12.dp,
                            border = BorderStroke(2.dp, Color(0xFF9EF0D6))) {
                            Box(contentAlignment = Alignment.Center) { GamepadKeyLabel(floatingKey) }
                        }
                    }
                  }
                }
            }
        }
    }
}
