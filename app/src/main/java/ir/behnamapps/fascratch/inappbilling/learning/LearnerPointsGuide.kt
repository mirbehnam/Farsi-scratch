package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/** Short, local help: no network work and no continuous animations in the profile. */
@Composable internal fun LearnerPointsGuideDialog(onClose: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    var tipsVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        entered = true
        delay(140)
        tipsVisible = true
    }
    val alpha by animateFloatAsState(if (entered) 1f else 0f, tween(280), label = "guide-fade")
    val scale by animateFloatAsState(if (entered) 1f else .92f, tween(360), label = "guide-pop")
    val height = (LocalConfiguration.current.screenHeightDp - 24).coerceAtLeast(180).dp
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 820.dp).fillMaxWidth(.92f).heightIn(max = height)
            .graphicsLayer { this.alpha = alpha; scaleX = scale; scaleY = scale },
            shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, Color(0xFFDCC8FF)), shadowElevation = 12.dp) {
            Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFFAF5FF), Color(0xFFFFF6E5))))
                .verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("✨", fontSize = 28.sp)
                    Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                        Text("چطور سطح خود را بالا ببریم؟", fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge, color = Color(0xFF7044BC))
                        Text("هر روز یک قدم، هر قدم یک پیشرفت!", style = MaterialTheme.typography.bodySmall, color = Color(0xFF80668E))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "بستن راهنمای کسب امتیاز" }) {
                        Text("×", fontSize = 28.sp, color = Color(0xFF7044BC))
                    }
                }
                BoxWithConstraints {
                    if (maxWidth >= 520.dp) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            GuideTip("۱", "🎬", "ببین و یاد بگیر!", "هرچه فیلم آموزشی بیشتری ببینی، امتیاز می‌گیری و سطحت بالاتر می‌رود.", Color(0xFF855CD6), Color(0xFFF0E7FF), tipsVisible, 0, Modifier.weight(1f))
                            GuideTip("۲", "🐱", "بساز و پیشرفت کن!", "هرچه بیشتر در محیط اسکرچ برنامه‌نویسی کنی، امتیاز بیشتری می‌گیری و سطحت بالاتر می‌رود.", Color(0xFFB06B0C), Color(0xFFFFEDC8), tipsVisible, 1, Modifier.weight(1f))
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            GuideTip("۱", "🎬", "ببین و یاد بگیر!", "هرچه فیلم آموزشی بیشتری ببینی، امتیاز می‌گیری و سطحت بالاتر می‌رود.", Color(0xFF855CD6), Color(0xFFF0E7FF), tipsVisible, 0)
                            GuideTip("۲", "🐱", "بساز و پیشرفت کن!", "هرچه بیشتر در محیط اسکرچ برنامه‌نویسی کنی، امتیاز بیشتری می‌گیری و سطحت بالاتر می‌رود.", Color(0xFFB06B0C), Color(0xFFFFEDC8), tipsVisible, 1)
                        }
                    }
                }
                FilledTonalButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterHorizontally), shape = RoundedCornerShape(14.dp)) {
                    Text("فهمیدم، بزن بریم! 🚀", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable private fun GuideTip(number: String, icon: String, title: String, body: String, accent: Color, background: Color,
    visible: Boolean, index: Int, modifier: Modifier = Modifier) {
    val reveal by animateFloatAsState(if (visible) 1f else 0f, tween(350, delayMillis = index * 80), label = "guide-tip-$index")
    val offset = with(androidx.compose.ui.platform.LocalDensity.current) { 12.dp.toPx() }
    // Keep the final layout measured from the first frame: cards animate, not the dialog's size.
    Surface(modifier.fillMaxWidth().graphicsLayer { alpha = reveal; translationY = (1f - reveal) * offset }, shape = RoundedCornerShape(18.dp), color = background) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(icon, fontSize = 26.sp)
                Text("$number. $title", fontWeight = FontWeight.Bold, color = accent, style = MaterialTheme.typography.titleSmall)
            }
            Text(body, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF514659))
        }
    }
}
