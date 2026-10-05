@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import java.util.Locale

private val Ink = Color(0xFF183F38)
private val Muted = Color(0xFF5D6C65)
private val Paper = Color(0xFFF7F5EF)
private val Mint = Color(0xFFE8F3EB)
private val PreviewGreen = Color(0xFF176D46)
private val PreviewTint = Color(0xFFD4EDDD)
private val Line = Color(0xFFE0E5DC)
private enum class LessonFilter { ALL, PREVIEW, DOWNLOADED }

@Composable
internal fun TrainingScreen(state: TrainingState, controller: TrainingController, onBack: () -> Unit, onPlay: (Lesson) -> Unit) {
    val context = LocalContext.current
    var pendingPurchase by remember { mutableStateOf<Pair<Course, Boolean>?>(null) }
    TrainingContent(state, onBack, { controller.load() }, { controller.select(it) },
        { course, restore ->
            if (purchaseNetworkAvailable(context)) controller.purchase(restore, course)
            else pendingPurchase = course to restore
        }, { controller.download(it) },
        controller::cancelDownload, onPlay, controller::refreshPrices)
    pendingPurchase?.let { pending ->
        AlertDialog(onDismissRequest = { pendingPurchase = null }, shape = RoundedCornerShape(24.dp),
            containerColor = Color.White,
            icon = { Surface(shape = RoundedCornerShape(18.dp), color = Mint) { Text("🌐", Modifier.padding(14.dp), fontSize = 28.sp) } },
            title = { Text("اتصال اینترنت برقرار نیست", color = Ink, fontWeight = FontWeight.Bold) },
            text = { Text("برای خرید یا بازیابی دوره، اینترنت را روشن کنید و دوباره تلاش کنید. هیچ پرداختی شروع نشده است.", color = Muted) },
            confirmButton = { Button(onClick = {
                if (purchaseNetworkAvailable(context)) {
                    pendingPurchase = null
                    controller.purchase(pending.second, pending.first)
                }
            }, enabled = !state.busy, shape = RoundedCornerShape(12.dp)) { Text("تلاش دوباره") } },
            dismissButton = { TextButton(onClick = { pendingPurchase = null }) { Text("بستن") } })
    }
}

/** Callbacks keep layout previews independent of billing, downloads and Android storage. */
@Composable
internal fun TrainingContent(
    state: TrainingState, onBack: () -> Unit, onRefresh: () -> Unit, onSelect: (Course) -> Unit,
    onPurchase: (Course, Boolean) -> Unit, onDownload: (Lesson) -> Unit,
    onCancelDownload: () -> Unit, onPlay: (Lesson) -> Unit, onRefreshPrices: () -> Unit = onRefresh
) {
    var filter by rememberSaveable(state.course?.id) { mutableStateOf(LessonFilter.ALL) }
    Column(Modifier.fillMaxSize().background(Paper).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(if (state.course == null) "بازگشت" else "همهٔ دوره‌ها") }
            Text(persian(state.course?.title ?: "آکادمی اسکرچ فارسی"), Modifier.weight(1f), fontWeight = FontWeight.Bold,
                color = Ink, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.busy && state.downloadingId == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            TextButton(onClick = onRefresh, enabled = !state.busy) { Text("تازه‌سازی") }
        }
        // Bound long server messages so retry/details remain reachable on short displays.
        state.message?.let { message ->
            Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(persian(message), Modifier.heightIn(max = 88.dp).verticalScroll(rememberScrollState()).padding(10.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        val course = state.course
        if (course == null) {
            Catalog(state, onSelect, Modifier.weight(1f))
        } else BoxWithConstraints(Modifier.weight(1f).padding(bottom = 10.dp)) {
            // A single column remains usable with unusually large display scaling.
            val split = maxWidth >= 580.dp && maxHeight >= 240.dp * LocalDensity.current.fontScale
            val panelWidth = (maxWidth * .32f).coerceIn(228.dp, 280.dp)
            val curriculum: @Composable (Modifier) -> Unit = { modifier ->
                Curriculum(course, state, filter, { filter = it }, modifier,
                    onDownload, onCancelDownload, onPlay,
                    if (split) null else { { PurchasePanel(course, state, onPurchase, onRefreshPrices) } })
            }
            if (split) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PurchasePanel(course, state, onPurchase, onRefreshPrices,
                    Modifier.width(panelWidth).fillMaxHeight(), pinned = true)
                curriculum(Modifier.weight(1f).fillMaxHeight())
            } else curriculum(Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun Catalog(state: TrainingState, onSelect: (Course) -> Unit, modifier: Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 680.dp
        LazyVerticalGrid(columns = GridCells.Fixed(if (wide && state.courses.size > 1) 2 else 1),
            contentPadding = PaddingValues(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (state.courses.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyContent(if (state.busy) "در حال دریافت دوره‌ها…" else if (state.message != null) "دریافت دوره‌ها کامل نشد؛ دوباره تازه‌سازی کنید." else "دوره‌های تازه در راه‌اند", "", Modifier.fillMaxWidth())
            }
            items(state.courses, key = { it.id }) { course ->
                CourseCard(course, state, onSelect, wide && state.courses.size == 1)
            }
        }
    }
}

@Composable
private fun CourseCard(course: Course, state: TrainingState, onSelect: (Course) -> Unit, horizontal: Boolean) {
    val owned = course.id in state.purchasedIds
    Card(onClick = { onSelect(course) }, enabled = !state.busy, shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Line)) {
        val details: @Composable ColumnScope.() -> Unit = {
            Text(persian(course.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (course.instructor.isNotBlank()) Text(persian(course.instructor), style = MaterialTheme.typography.bodySmall, color = Muted)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    if (owned) Text("✓ خریداری شده", color = PreviewGreen, fontWeight = FontWeight.Bold)
                    else CourseOfferPrice(course, state.prices[course.id],
                        if (BuildConfig.BILLING_PROVIDER == "website") "نسخهٔ وب‌سایت" else if (course.id in state.priceErrors) "قیمت دریافت نشد" else "قیمت در حال دریافت")
                }
                Button(onClick = { onSelect(course) }, enabled = !state.busy, shape = RoundedCornerShape(12.dp)) {
                    Text(if (owned) "ورود به دوره" else "مشاهده دوره")
                }
            }
            if (!owned && state.prices[course.id] != null) DiscountCountdown(course)
        }
        if (horizontal) Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CourseArtwork(course.coverUrl, course.title, Modifier.size(152.dp, 108.dp).clip(RoundedCornerShape(12.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), content = details)
        } else Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CourseArtwork(course.coverUrl, course.title, Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(12.dp)))
            details()
        }
    }
}

@Composable
private fun CourseMetadata(course: Course) {
    if (course.instructor.isNotBlank()) Text(persian("مدرس: ${course.instructor}"), style = MaterialTheme.typography.bodySmall, color = Muted)
    val facts = listOfNotNull(durationLabel(course.durationSeconds), course.confirmedPurchases?.takeIf { it > 0 }?.let { "${persian(it)} خرید تأییدشده" })
    if (facts.isNotEmpty()) Text(facts.joinToString("  ·  "), style = MaterialTheme.typography.labelMedium, color = Muted)
}

@Composable
private fun PurchasePanel(course: Course, state: TrainingState, onPurchase: (Course, Boolean) -> Unit, onRefreshPrices: () -> Unit, modifier: Modifier = Modifier, pinned: Boolean = false) {
    val canBuy = BuildConfig.BILLING_PROVIDER != "website"
    val price = state.prices[course.id]
    val store = if (BuildConfig.BILLING_PROVIDER == "myket") "مایکت" else "کافه‌بازار"
    val panelScroll = rememberScrollState()
    LaunchedEffect(course.id) { panelScroll.scrollTo(0) }
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column {
            // Reserve only the primary CTA. All variable-height content shares one bounded
            // scroll viewport, so prices/restore controls can never squeeze the summary to zero.
            // Inline panels already belong to the curriculum's scrolling grid: do not nest
            // an unbounded vertical scroller there.
            val bodyModifier = if (pinned) Modifier.weight(1f).verticalScroll(panelScroll) else Modifier
            Column(bodyModifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(persian(course.title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
                    CourseMetadata(course)
                    if (course.description.isNotBlank()) {
                        var showDescription by rememberSaveable(course.id) { mutableStateOf(false) }
                        TextButton(onClick = { showDescription = !showDescription }, contentPadding = PaddingValues(0.dp)) { Text(if (showDescription) "بستن توضیحات" else "دربارهٔ دوره", style = MaterialTheme.typography.labelMedium) }
                        if (showDescription) Text(persian(course.description), style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
                HorizontalDivider(color = Line)
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.purchased) {
                        Text("✓ خریداری شده", color = Ink, fontWeight = FontWeight.Bold)
                    } else if (canBuy) {
                        CourseOfferPrice(course, price, if (course.id in state.loadingPrices) "دریافت قیمت…" else "قیمت در سرور ثبت نشده یا دریافت نشد", compact = true)
                        if (course.id !in state.loadingPrices) TextButton(onClick = onRefreshPrices, enabled = !state.busy, contentPadding = PaddingValues(0.dp)) { Text("تازه‌سازی قیمت", style = MaterialTheme.typography.labelMedium) }
                        Text("پرداخت از طریق $store", modifier = Modifier.align(Alignment.CenterHorizontally), style = MaterialTheme.typography.labelSmall, color = Muted)
                        TextButton(onClick = { onPurchase(course, true) }, enabled = !state.busy, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) { Text("بازیابی خرید", style = MaterialTheme.typography.labelMedium) }
                    } else {
                        Text("خرید در نسخهٔ فروشگاهی", color = Ink, fontWeight = FontWeight.Bold)
                        Text("برای خرید، نسخهٔ مایکت یا کافه‌بازار برنامه را نصب کنید.", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                }
            }
            if (!state.purchased && canBuy) {
                HorizontalDivider(color = Line)
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Button(onClick = { onPurchase(course, false) }, enabled = !state.busy && price != null,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp), shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 0.dp)) {
                        Text("خرید دوره", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Curriculum(course: Course, state: TrainingState, filter: LessonFilter, onFilter: (LessonFilter) -> Unit,
    modifier: Modifier, onDownload: (Lesson) -> Unit, onCancel: () -> Unit,
    onPlay: (Lesson) -> Unit, inlinePurchase: (@Composable () -> Unit)?
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(course.id, filter) { gridState.scrollToItem(0) }
    val lessons = state.lessons.filter { when (filter) { LessonFilter.ALL -> true; LessonFilter.PREVIEW -> it.isPreview; LessonFilter.DOWNLOADED -> it.id in state.downloaded } }
    BoxWithConstraints(modifier) {
        LazyVerticalGrid(columns = GridCells.Fixed(if (maxWidth >= 740.dp) 2 else 1), state = gridState,
            verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            item(key = "introduction", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (filter == LessonFilter.ALL) {
                        inlinePurchase?.invoke()
                    }
                    // Wrapping avoids squeezing Persian labels at increased system font sizes.
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = filter == LessonFilter.ALL, onClick = { onFilter(LessonFilter.ALL) }, label = { Text("همهٔ درس‌ها") })
                        if (state.lessons.any { it.isPreview }) FilterChip(selected = filter == LessonFilter.PREVIEW, onClick = { onFilter(LessonFilter.PREVIEW) }, label = { Text("رایگان") })
                        FilterChip(selected = filter == LessonFilter.DOWNLOADED, onClick = { onFilter(LessonFilter.DOWNLOADED) }, label = { Text("دانلودها · ${persian(state.downloaded.size)}") })
                    }
                }
            }
            if (lessons.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyContent(if (state.busy) "در حال دریافت درس‌ها…" else if (filter == LessonFilter.DOWNLOADED) "هنوز درسی دانلود نکرده‌ای" else if (state.message != null) "دریافت درس‌ها کامل نشد" else "درسی برای نمایش نیست",
                    if (filter == LessonFilter.DOWNLOADED) "از بخش همهٔ درس‌ها، یک درس را برای تماشای آفلاین دانلود کن." else "", Modifier.fillMaxWidth())
            }
            var previous: String? = null
            lessons.forEach { lesson ->
                if (lesson.section.isNotBlank() && previous != lesson.section) item(key = "section-${lesson.id}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(persian(lesson.section), Modifier.padding(top = 2.dp), color = Ink, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                previous = lesson.section
                item(key = lesson.id) { LessonCard(lesson, state, onDownload, onCancel, { onPlay(lesson) }) }
            }
        }
    }
}

@Composable
private fun LessonCard(lesson: Lesson, state: TrainingState, onDownload: (Lesson) -> Unit, onCancel: () -> Unit, onPlay: () -> Unit) {
    val downloaded = lesson.id in state.downloaded
    val downloading = state.downloadingId == lesson.id
    val accessible = CoursePolicy.canLearn(lesson, state.purchased)
    var expanded by rememberSaveable(lesson.id) { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = if (lesson.isPreview) PreviewTint else if (downloaded && accessible) Mint else Color.White,
        border = BorderStroke(1.dp, if (lesson.isPreview) PreviewGreen.copy(alpha = .45f) else Line)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CourseArtwork(lesson.coverUrl, lesson.title, Modifier.size(86.dp, 64.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(persian(lesson.title), fontWeight = FontWeight.Bold, color = Ink, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DifficultyBadge(lesson.difficulty)
                        if (lesson.isPreview) Badge("مشاهده رایگان", PreviewGreen, Color.White)
                    }
                    Text(listOfNotNull(durationLabel(lesson.durationSeconds), "${persian(String.format(Locale.US, "%.1f", lesson.bytes / (1024.0 * 1024))).replace('.', '٫')} مگابایت").joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = Muted)
                }
                if (downloaded) {
                    Button(onClick = onPlay, enabled = accessible, shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (lesson.isPreview) PreviewGreen else MaterialTheme.colorScheme.primary)) { Text("تماشا") }
                } else if (downloading) OutlinedButton(onClick = onCancel) { Text("توقف دانلود") }
                else if (accessible) OutlinedButton(onClick = { onDownload(lesson) }, enabled = !state.busy, shape = RoundedCornerShape(10.dp)) { Text(if (lesson.isPreview) "دانلود رایگان" else "دانلود درس") }
                else Text("نیاز به خرید", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelMedium, color = Muted)
            }
            if (lesson.description.isNotBlank()) TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) { Text(if (expanded) "بستن توضیحات" else "توضیحات درس", style = MaterialTheme.typography.labelMedium) }
            if (expanded) Text(persian(lesson.description), style = MaterialTheme.typography.bodySmall, color = Muted)
            if (downloading) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                Text("${persian((state.progress * 100).toInt())}٪ دانلود شده · برای ادامه، در این صفحه بمانید", style = MaterialTheme.typography.labelSmall, color = Muted)
            }
        }
    }
}

@Composable
private fun EmptyContent(title: String, subtitle: String, modifier: Modifier) {
    Column(modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Ink, style = MaterialTheme.typography.titleMedium)
        if (subtitle.isNotBlank()) Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Badge(text: String, background: Color, foreground: Color) {
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = foreground, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun DifficultyBadge(level: Int) {
    when (level) {
        1 -> Badge("🌱 مقدماتی", Mint, Ink)
        2 -> Badge("🙂 آسان", Color(0xFFE4EFFC), Color(0xFF245B88))
        3 -> Badge("💡 متوسط", Color(0xFFFFF1D6), Color(0xFF805300))
        4 -> Badge("🚀 پیشرفته", Color(0xFFEDE6FA), Color(0xFF69469B))
        5 -> Badge("🔥 حرفه‌ای", Color(0xFFFFE7E0), Color(0xFFA23E25))
    }
}

private fun persian(value: Int) = persian(value.toString())
private fun persian(value: String) = persianDisplay(value)
private fun durationLabel(seconds: Double): String? = if (seconds.isFinite() && seconds > 0) "${persian(kotlin.math.ceil(seconds / 60).toInt())} دقیقه" else null
