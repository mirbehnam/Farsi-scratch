package ir.behnamapps.fascratch.tour

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import ir.behnamapps.fascratch.R
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay

@Composable internal fun tourPlayPulse(active: Boolean): State<Float> {
    val pulse = remember { Animatable(0f) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(active, lifecycle) {
        pulse.snapTo(0f)
        if (android.os.Build.VERSION.SDK_INT >= 26 && !android.animation.ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        if (active) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { pulse.animateTo(1f, tween(1800)); pulse.snapTo(0f); delay(100) }
        }
    }
    return pulse.asState()
}

@Composable internal fun TourHero(item: TourVideo, state: TourState, repository: TourRepository?, pulse: State<Float>,
    modifier: Modifier, cancel: () -> Unit, select: () -> Unit) {
    val done = item.key in state.completed
    val downloading = state.downloading == item.key
    BoxWithConstraints(modifier) {
        val compact = maxHeight < 190.dp
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(26.dp), color = Color(0xFFF0F5FC), border = BorderStroke(1.dp, Color(0xFFD9E5F1))) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(.43f).fillMaxHeight().padding(if (compact) 10.dp else 16.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)) {
                    if (!compact) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TourTag(if (done) "✓ تماشا شد" else "از اینجا شروع کن", if (done) Color(0xFF137C54) else Color(0xFF2865A1),
                            if (done) Color(0xFFD9F2E6) else Color(0xFFDCEBFC))
                        Text(tourTime(item), style = MaterialTheme.typography.labelMedium, color = Color(0xFF64788D))
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(item.title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge, color = Color(0xFF183B62))
                        if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5B7188))
                    }
                    if (downloading) TourDownloadProgress(state.progress, cancel)
                    else Button(onClick = select, enabled = state.downloading == null, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
                        shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA934), contentColor = Color(0xFF39290F))) {
                        Text(if (done) "دوباره ببین  ▶" else "شروع تماشا  ▶", fontWeight = FontWeight.Bold)
                    }
                }
                TourCover(item, state.coverRevision, repository, done, pulse, Modifier.weight(.57f).fillMaxHeight(), state.downloading == null, select)
            }
        }
    }
}

@Composable internal fun TourGridCard(item: TourVideo, index: Int, state: TourState, repository: TourRepository?, pulse: State<Float>,
    coverHeight: androidx.compose.ui.unit.Dp, cancel: () -> Unit, select: () -> Unit) {
    val done = item.key in state.completed
    Surface(shape = RoundedCornerShape(20.dp), color = if (done) Color(0xFFEAF7EF) else Color.White,
        border = BorderStroke(1.dp, if (done) Color(0xFFBCE2CC) else Color(0xFFDCE6F0))) {
        Column {
            TourCover(item, state.coverRevision, repository, done, pulse, Modifier.fillMaxWidth().height(coverHeight), state.downloading == null, select)
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TourTag(if (done) "✓" else persianDisplay(index.toString()), if (done) Color(0xFF137C54) else Color(0xFF2865A1),
                        if (done) Color(0xFFD9F2E6) else Color(0xFFE8F1FD))
                    Text(item.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall, color = Color(0xFF183B62))
                }
                if (item.description.isNotBlank()) Text(item.description, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF64788D))
                if (state.downloading == item.key) TourDownloadProgress(state.progress, cancel)
                else Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(tourTime(item), style = MaterialTheme.typography.labelSmall, color = Color(0xFF64788D))
                    TextButton(onClick = select, enabled = state.downloading == null, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)) {
                        Text(if (done) "دوباره ببین" else "تماشا کن", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable private fun TourCover(item: TourVideo, revision: Int, repository: TourRepository?, done: Boolean, pulse: State<Float>,
    modifier: Modifier, enabled: Boolean, select: () -> Unit) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, item.cover?.hash, revision) { value = repository?.cover(item) }
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF12477B), Color(0xFF19344E), Color(0xFF117E87))))
        .clickable(enabled = enabled, role = Role.Button, onClick = select).semantics { contentDescription = "پخش ${item.title}" }, contentAlignment = Alignment.Center) {
        bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
            ?: Image(painterResource(R.drawable.home_tour_video_icon), contentDescription = null, modifier = Modifier.fillMaxSize(.6f), contentScale = ContentScale.Fit)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x55091F38)))))
        Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
            if (!done && enabled) Canvas(Modifier.fillMaxSize()) {
                val phase = pulse.value
                drawCircle(Color(0xFFFFC667).copy(alpha = .5f * (1f - phase)), radius = size.minDimension * (.35f + phase * .13f), style = Stroke(2.dp.toPx()))
            }
            Surface(Modifier.size(50.dp), shape = CircleShape, color = Color(0xF5FFFFFF), shadowElevation = 5.dp) {
                Box(contentAlignment = Alignment.Center) { Text("▶", fontSize = 23.sp, color = Color(0xFF1B5883)) }
            }
        }
        if (done) Box(Modifier.align(Alignment.TopEnd).padding(10.dp)) { TourTag("✓", Color(0xFFFFFFFF), Color(0xFF168254)) }
    }
}

@Composable private fun TourDownloadProgress(value: Float, cancel: () -> Unit) {
    val smooth by animateFloatAsState(value.coerceIn(0f, 1f), tween(180), label = "tour-download")
    Column {
        LinearProgressIndicator(progress = { smooth }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = Color(0xFF1D9B82), trackColor = Color(0xFFD6E9E6))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(persianDisplay("دریافت ویدیو · ${(value * 100).toInt()}٪"), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color(0xFF45667C))
            TextButton(onClick = cancel, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) { Text("لغو") }
        }
    }
}

@Composable internal fun TourTag(text: String, foreground: Color, background: Color) {
    Surface(shape = RoundedCornerShape(10.dp), color = background) {
        Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = foreground, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

private fun tourTime(item: TourVideo) = persianDisplay("${item.durationMs / 60000}:${(item.durationMs / 1000 % 60).toString().padStart(2, '0')}")
