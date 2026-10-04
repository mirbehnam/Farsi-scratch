package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.Lesson
import java.util.Locale

/** Two independently scrollable panes keep purchase actions and lessons usable on short landscape phones. */
@Composable
internal fun TrainingScreen(state: TrainingState, controller: TrainingController, onBack: () -> Unit, onPlay: (Lesson) -> Unit) {
    var offlineOnly by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Lesson?>(null) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(12.dp)) {
                Text("S", Modifier.padding(horizontal = 12.dp, vertical = 5.dp), color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("آکادمی اسکرچ فارسی", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("یاد بگیر، تمرین کن، بساز", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.busy && state.downloadingId == null) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            TextButton(onClick = controller::load, enabled = !state.busy) { Text("به‌روزرسانی") }
            TextButton(onClick = onBack) { Text("بازگشت") }
        }
        Row(Modifier.weight(1f).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LazyColumn(Modifier.weight(.28f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { CourseSummary(state, controller) }
                state.message?.let { message -> item {
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFFFF0DB)) {
                        Text(message, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF743B12))
                    }
                } }
            }
            Surface(Modifier.weight(.72f).fillMaxHeight(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(horizontal = 14.dp)) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("محتوای دوره", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("${state.lessons.size} درس · ${state.lessons.map { it.section }.distinct().size} فصل", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        FilterChip(selected = !offlineOnly, onClick = { offlineOnly = false }, label = { Text("همه") })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = offlineOnly, onClick = { offlineOnly = true }, label = { Text("دانلودها") })
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color(0xFFEDF0F4))
                    val lessons = state.lessons.filter { !offlineOnly || it.id in state.downloaded }
                    if (lessons.isEmpty()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                if (state.busy) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
                                Text(when { state.busy -> "در حال دریافت اطلاعات…"; offlineOnly -> "هنوز درسی دانلود نکرده‌اید"; else -> "درسی برای نمایش موجود نیست" }, fontWeight = FontWeight.Bold)
                                Text(if (offlineOnly) "از بخش «همه» درس موردنظر را دانلود کنید." else "وضعیت دریافت اطلاعات در کنار صفحه نمایش داده می‌شود.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        var previousSection: String? = null
                        lessons.forEach { lesson ->
                            if (previousSection != lesson.section) {
                                item(key = "section-${lesson.id}") { Text(lesson.section, Modifier.padding(top = 8.dp, bottom = 4.dp),
                                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge) }
                            }
                            previousSection = lesson.section
                            item(key = lesson.id) {
                                LessonRow(lesson, state, state.lessons.indexOf(lesson) + 1, controller,
                                    onPlay = { onPlay(lesson) }, onDelete = { deleting = lesson })
                            }
                        }
                    }
                }
            }
        }
    }
    deleting?.let { lesson -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("حذف فایل دانلودشده؟") },
        text = { Text("${lesson.title}\nخرید شما باقی می‌ماند و می‌توانید این درس را دوباره دانلود کنید.") },
        confirmButton = { TextButton(enabled = !state.busy, onClick = { controller.delete(lesson); deleting = null }) { Text("حذف فایل") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("انصراف") } }) }
}

@Composable
private fun CourseSummary(state: TrainingState, controller: TrainingController) {
    val canBuy = !state.busy && state.course != null && BuildConfig.BILLING_PROVIDER != "website"
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        CourseArtwork(state.course?.coverUrl, "کاور دوره", Modifier.fillMaxWidth().aspectRatio(2.8f))
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF153F55), Color(0xFF246B74)))).padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("آموزش پروژه‌محور", color = Color(0xFFB8F1E4), style = MaterialTheme.typography.labelSmall)
            Text(state.course?.title ?: "دوره آموزش اسکرچ", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(state.course?.description?.takeIf { it.isNotBlank() } ?: "قدم‌به‌قدم یاد بگیرید؛ هر درس را جداگانه دانلود کنید و بدون اینترنت تماشا کنید.",
                color = Color(0xFFE0E8F2), style = MaterialTheme.typography.bodySmall)
            Text("${state.lessons.size} درس آموزشی  •  تماشای آفلاین", color = Color(0xFFB8F1E4), style = MaterialTheme.typography.labelSmall)
            state.course?.let { course ->
                if (course.instructor.isNotBlank()) Text("مدرس: ${course.instructor}", color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text("${difficultyLabel(course.difficulty)} · ${durationLabel(course.durationSeconds)}", color = Color(0xFFE0E8F2), style = MaterialTheme.typography.labelSmall)
                course.confirmedPurchases?.let { count -> Text("$count خرید تأییدشده", color = Color(0xFFE0E8F2), style = MaterialTheme.typography.labelSmall) }
            }
        }
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.purchased) {
                Text("✓ دوره شما فعال است", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                Text("${state.downloaded.size} از ${state.lessons.size} درس دانلود شده", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(progress = { if (state.lessons.isEmpty()) 0f else state.downloaded.size.toFloat() / state.lessons.size },
                    modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondary, trackColor = Color(0xFFE7EFEA))
            } else {
                Text(state.price ?: "قیمت از فروشگاه دریافت می‌شود", fontWeight = FontWeight.Bold)
                Text("یک‌بار خرید · دسترسی به درس‌های این دوره", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { controller.purchase(false) }, enabled = canBuy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text("خرید دوره", fontWeight = FontWeight.Bold) }
                TextButton(onClick = { controller.purchase(true) }, enabled = canBuy, modifier = Modifier.fillMaxWidth()) { Text("قبلاً خریده‌ام؛ بازیابی خرید") }
            }
            if (BuildConfig.BILLING_PROVIDER == "website") Text("خرید در نسخه بازار و مایکت فعال است.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, state: TrainingState, index: Int, controller: TrainingController, onPlay: () -> Unit, onDelete: () -> Unit) {
    val downloaded = lesson.id in state.downloaded
    val downloading = state.downloadingId == lesson.id
    var expanded by rememberSaveable(lesson.id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(14.dp), color = if (downloaded) Color(0xFFEAF6F1) else Color(0xFFF0F4F8)) {
        Column(Modifier.clickable { expanded = !expanded }.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    CourseArtwork(lesson.coverUrl, "کاور ${lesson.title}", Modifier.size(width = 112.dp, height = 76.dp).clip(RoundedCornerShape(10.dp)))
                    Surface(Modifier.align(Alignment.BottomEnd), shape = RoundedCornerShape(topStart = 6.dp), color = Color(0xDD182235)) {
                        Text(index.toString().padStart(2, '0'), Modifier.padding(horizontal = 5.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.White)
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(lesson.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                    Text("${difficultyLabel(lesson.difficulty)} · ${durationLabel(lesson.durationSeconds)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${String.format(Locale.US, "%.1f", lesson.bytes / (1024.0 * 1024))} مگابایت · ${when { downloading -> "در حال دانلود"; downloaded -> "آماده تماشا"; !state.purchased -> "قفل؛ نیازمند خرید"; else -> "آماده دانلود" }}",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (downloaded) {
                        Button(onClick = onPlay, enabled = state.purchased, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) { Text("تماشا") }
                        TextButton(onClick = onDelete, enabled = !state.busy, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) { Text("حذف فایل", style = MaterialTheme.typography.labelSmall) }
                    } else if (downloading) TextButton(onClick = controller::cancelDownload) { Text("توقف") }
                    else OutlinedButton(onClick = { controller.download(lesson) }, enabled = !state.busy && state.purchased,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) { Text(if (state.purchased) "دانلود" else "قفل") }
                }
            }
            if (expanded) Text(lesson.description.takeIf { it.isNotBlank() } ?: "این درس پس از دانلود در همین اپ قابل تماشاست.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (downloading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text("${(state.progress * 100).toInt()}٪ · ${if (state.progress >= .99f) "بررسی سلامت فایل…" else "دانلود درس…"}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun difficultyLabel(level: Int) = when (level) { 1 -> "مقدماتی"; 2 -> "آسان"; 3 -> "متوسط"; 4 -> "پیشرفته"; 5 -> "حرفه‌ای"; else -> "سطح تعیین نشده" }
private fun durationLabel(seconds: Double): String = if (seconds.isFinite() && seconds > 0) "${kotlin.math.ceil(seconds / 60).toInt()} دقیقه" else "مدت تعیین نشده"
