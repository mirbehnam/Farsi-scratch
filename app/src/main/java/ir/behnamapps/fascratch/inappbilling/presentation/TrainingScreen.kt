package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import java.util.Locale

@Composable
internal fun TrainingScreen(state: TrainingState, controller: TrainingController, onBack: () -> Unit, onPlay: (Lesson) -> Unit) {
    var offlineOnly by rememberSaveable(state.course?.id) { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Lesson?>(null) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 14.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(state.course?.title ?: "آموزش", Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 18.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.busy && state.downloadingId == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            if (state.course != null) {
                FilterChip(selected = !offlineOnly, onClick = { offlineOnly = false }, label = { Text("همه") })
                FilterChip(selected = offlineOnly, onClick = { offlineOnly = true }, label = { Text("دانلودها") })
                if (!state.purchased && BuildConfig.BILLING_PROVIDER != "website") TextButton(onClick = { controller.purchase(false) }, enabled = !state.busy) { Text("خرید") }
            }
            TextButton(onClick = { controller.load() }, enabled = !state.busy) { Text("به‌روزرسانی") }
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        state.message?.let { Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(it, Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        } }
        if (state.course == null) {
            if (state.courses.isEmpty()) EmptyContent(if (state.busy) "در حال دریافت دوره‌ها…" else "دوره‌ای منتشر نشده است", Modifier.weight(1f))
            else LazyVerticalGrid(columns = GridCells.Fixed(if (state.courses.size == 1) 1 else 2), modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.courses, key = { it.id }) { course ->
                    CourseCard(course, state, controller, horizontal = state.courses.size == 1)
                }
            }
        } else {
            val lessons = state.lessons.filter { !offlineOnly || it.id in state.downloaded }
            if (lessons.isEmpty()) EmptyContent(if (state.busy) "در حال دریافت درس‌ها…" else if (offlineOnly) "هنوز درسی دانلود نشده است" else "درسی منتشر نشده است", Modifier.weight(1f))
            else LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                var previous: String? = null
                lessons.forEach { lesson ->
                    if (lesson.section.isNotBlank() && previous != lesson.section) {
                        item(key = "section-${lesson.id}", span = { GridItemSpan(maxLineSpan) }) {
                            Text(lesson.section, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    previous = lesson.section
                    item(key = lesson.id) { LessonCard(lesson, state, controller, { onPlay(lesson) }, { deleting = lesson }) }
                }
            }
        }
    }
    deleting?.let { lesson -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف فایل دانلودشده؟") },
        text = { Text(lesson.title) }, confirmButton = { TextButton(enabled = !state.busy, onClick = { controller.delete(lesson); deleting = null }) { Text("حذف فایل") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف") } }) }
}

@Composable
private fun EmptyContent(message: String, modifier: Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun CourseCard(course: Course, state: TrainingState, controller: TrainingController, horizontal: Boolean) {
    val purchased = course.id in state.purchasedIds
    Card(onClick = { if (!state.busy) controller.select(course) }, shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        if (horizontal) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            CourseArtwork(course.coverUrl, course.title, Modifier.size(148.dp, 102.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) { CourseText(course) }
            Column(Modifier.width(176.dp), horizontalAlignment = Alignment.CenterHorizontally) { CourseActions(course, state, controller, purchased) }
        } else Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CourseArtwork(course.coverUrl, course.title, Modifier.size(104.dp, 76.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) { CourseText(course) }
            }
            CourseActions(course, state, controller, purchased)
        }
    }
}

@Composable
private fun CourseText(course: Course) {
    Text(course.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (course.description.isNotBlank()) Text(course.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (course.instructor.isNotBlank()) Text(course.instructor, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    val stats = listOfNotNull(durationLabel(course.durationSeconds), course.confirmedPurchases?.let { "$it خرید" }).joinToString(" · ")
    if (stats.isNotEmpty()) Text(stats, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CourseActions(course: Course, state: TrainingState, controller: TrainingController, purchased: Boolean) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .97f else 1f, label = "courseButtonPress")
    val canBuy = BuildConfig.BILLING_PROVIDER != "website"
    Button(onClick = { if (purchased) controller.select(course) else controller.purchase(false, course) },
        enabled = !state.busy && (purchased || canBuy), interactionSource = source,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)) {
        Box(Modifier.fillMaxWidth().background(Brush.horizontalGradient(if (purchased) listOf(Color(0xFF18745C), Color(0xFF26937C)) else listOf(Color(0xFF245B88), Color(0xFF347DA8))))
            .padding(horizontal = 10.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            Text(if (purchased) "شروع یادگیری" else "خرید · ${state.prices[course.id] ?: "…"}", color = Color.White,
                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    if (purchased) Text("✓ قبلاً خریداری شده", Modifier.padding(top = 4.dp), color = Color(0xFF18745C), style = MaterialTheme.typography.labelSmall)
    else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { controller.select(course) }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("مشاهده درس‌ها", style = MaterialTheme.typography.labelSmall) }
        if (canBuy) TextButton(onClick = { controller.purchase(true, course) }, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 4.dp)) { Text("بازیابی خرید", style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, state: TrainingState, controller: TrainingController, onPlay: () -> Unit, onDelete: () -> Unit) {
    val downloaded = lesson.id in state.downloaded
    val downloading = state.downloadingId == lesson.id
    val accessible = CoursePolicy.canLearn(lesson, state.purchased)
    var expanded by rememberSaveable(lesson.id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(14.dp), color = if (downloaded) Color(0xFFEAF6F1) else Color.White) {
        Column(Modifier.clickable { expanded = !expanded }.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                CourseArtwork(lesson.coverUrl, lesson.title, Modifier.size(112.dp, 76.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(lesson.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    DifficultyBadge(lesson.difficulty)
                    if (lesson.isPreview) Badge("پیش‌نمایش رایگان", Color(0xFFE0F3ED), Color(0xFF17634D))
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(listOfNotNull(durationLabel(lesson.durationSeconds), "${String.format(Locale.US, "%.1f", lesson.bytes / (1024.0 * 1024))} MB").joinToString(" · "),
                    Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (downloaded) {
                    TextButton(onClick = onDelete, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("حذف", style = MaterialTheme.typography.labelSmall) }
                    Button(onClick = onPlay, enabled = accessible, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text("تماشا") }
                } else if (downloading) TextButton(onClick = controller::cancelDownload) { Text("توقف") }
                else OutlinedButton(onClick = { controller.download(lesson) }, enabled = !state.busy && accessible,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) { Text(if (accessible) "دانلود" else "قفل") }
            }
            if (expanded && lesson.description.isNotBlank()) Text(lesson.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (downloading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text("${(state.progress * 100).toInt()}٪", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color) {
    Surface(shape = RoundedCornerShape(7.dp), color = background) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 3.dp), color = foreground, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DifficultyBadge(level: Int) {
    when (level) {
        1 -> Badge("🌱 مقدماتی", Color(0xFFE0F3ED), Color(0xFF17634D))
        2 -> Badge("🙂 آسان", Color(0xFFE4EFFC), Color(0xFF245B88))
        3 -> Badge("💡 متوسط", Color(0xFFFFF1D6), Color(0xFF805300))
        4 -> Badge("🚀 پیشرفته", Color(0xFFEDE6FA), Color(0xFF69469B))
        5 -> Badge("🔥 حرفه‌ای", Color(0xFFFFE7E0), Color(0xFFA23E25))
    }
}

private fun durationLabel(seconds: Double): String? = if (seconds.isFinite() && seconds > 0) "${kotlin.math.ceil(seconds / 60).toInt()} دقیقه" else null
