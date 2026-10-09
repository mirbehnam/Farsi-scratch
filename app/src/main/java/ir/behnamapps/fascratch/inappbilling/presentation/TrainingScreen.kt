@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.domain.*
import java.util.Locale
import ir.behnamapps.fascratch.inappbilling.learning.*

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
    var showProfile by remember { mutableStateOf(false) }
    val learning = controller.learning
    val profiles by learning?.profiles?.collectAsState() ?: remember { mutableStateOf(emptyMap<String, LearnerProfile>()) }
    val selectedProfile = profiles[LearningIdentity.KEY]
    LaunchedEffect(state.course?.id, state.courses.map { it.id }) { learning?.refreshProfile() }
    TrainingContent(state, onBack, { controller.load() }, { controller.select(it) },
        { course, restore ->
            if (purchaseNetworkAvailable(context)) controller.purchase(restore, course)
            else pendingPurchase = course to restore
        }, { controller.download(it) },
        controller::cancelDownload, onPlay, controller::refreshPrices, controller::catalogReady, controller::catalogFailed,
        learnerHeader = { DelayedLearnerChip(selectedProfile, state.course?.id ?: "catalog") { learning?.refreshProfile(); showProfile = true } },
        learnerCelebration = { if (state.course != null && selectedProfile != null && learning != null) LevelCelebration(state.course.id, selectedProfile, learning) })
    if (showProfile && selectedProfile != null && learning != null)
        LearnerDialog(state.course, selectedProfile, learning) { showProfile = false }
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
    onCancelDownload: () -> Unit, onPlay: (Lesson) -> Unit, onRefreshPrices: () -> Unit = onRefresh,
    onCatalogReady: (CatalogLayout) -> Unit = {}, onCatalogFailed: (CatalogLayout) -> Unit = {},
    learnerHeader: @Composable () -> Unit = {}, learnerCelebration: @Composable () -> Unit = {}
) {
    var filter by rememberSaveable(state.course?.id) { mutableStateOf(LessonFilter.ALL) }
    var optionsExpanded by remember(state.course?.id) { mutableStateOf(false) }
    val canBuy = BuildConfig.BILLING_PROVIDER != "website"
    var failedLayout by remember { mutableStateOf<String?>(null) }
    val courseOptions: @Composable () -> Unit = {
        CourseOptions(state, optionsExpanded, { optionsExpanded = it }, onRefresh, onRefreshPrices, onPurchase)
    }
    Column(Modifier.fillMaxSize().background(Paper).windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onBack) { Text(if (state.course == null) "بازگشت" else "همهٔ دوره‌ها") }
            Text(persian(state.course?.title ?: "آکادمی اسکرچ فارسی"), Modifier.weight(1f), fontWeight = FontWeight.Bold,
                color = Ink, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.course != null) {
                Row(Modifier.widthIn(max = 340.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = filter == LessonFilter.ALL, onClick = { filter = LessonFilter.ALL }, label = { Text("همهٔ درس‌ها") })
                    FilterChip(selected = filter == LessonFilter.PREVIEW, onClick = { filter = LessonFilter.PREVIEW }, label = { Text("رایگان") })
                    FilterChip(selected = filter == LessonFilter.DOWNLOADED, onClick = { filter = LessonFilter.DOWNLOADED }, label = { Text("دانلودها · ${persian(state.downloaded.size)}") })
                }
            }
            if (state.busy && state.downloadingId == null) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            learnerHeader()
            if (state.course == null) courseOptions()
        }
        learnerCelebration()
        // Bound long server messages so retry/details remain reachable on short displays.
        state.message?.let { message ->
            Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                Text(persian(message), Modifier.heightIn(max = 88.dp).verticalScroll(rememberScrollState()).padding(10.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
        val course = state.course
        if (course == null) {
            val layout = state.catalogLayout
            if (!state.catalogCacheLoaded || (layout != null && state.courses.isEmpty() && state.busy)) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (layout != null && layout.sha256 != failedLayout && state.courses.isNotEmpty()) {
                key(layout.sha256) {
                    RemoteCatalog(layout, state, onSelect, { failedLayout = layout.sha256; onCatalogFailed(layout) },
                        { onCatalogReady(layout) }, Modifier.weight(1f)) {
                        Catalog(state, onSelect, Modifier.fillMaxSize())
                    }
                }
            } else Catalog(state, onSelect, Modifier.weight(1f))
        } else BoxWithConstraints(Modifier.weight(1f).padding(bottom = 10.dp)) {
            // A single column remains usable with unusually large display scaling.
            val split = maxWidth >= 580.dp && maxHeight >= 240.dp * LocalDensity.current.fontScale
            val panelWidth = (maxWidth * .32f).coerceIn(228.dp, 280.dp)
            val curriculum: @Composable (Modifier) -> Unit = { modifier ->
                Curriculum(course, state, filter, modifier,
                    onDownload, onCancelDownload, onPlay,
                    if (split) null else { { PurchasePanel(course, state, onPurchase, courseOptions = courseOptions) } })
            }
            if (split) Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PurchasePanel(course, state, onPurchase,
                    Modifier.width(panelWidth).fillMaxHeight(), pinned = true, courseOptions = courseOptions)
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
    durationLabel(course.durationSeconds)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Muted) }
}

@Composable
private fun CourseOptions(state: TrainingState, expanded: Boolean, onExpanded: (Boolean) -> Unit,
    onRefresh: () -> Unit, onRefreshPrices: () -> Unit, onPurchase: (Course, Boolean) -> Unit) {
    Box {
        IconButton(onClick = { onExpanded(true) }, modifier = Modifier.semantics { contentDescription = "گزینه‌های بیشتر" }) {
            Text("⋮", fontSize = 28.sp, color = Ink)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpanded(false) }) {
            DropdownMenuItem(text = { Text("تازه‌سازی") }, enabled = !state.busy, onClick = { onExpanded(false); onRefresh() })
            if (BuildConfig.BILLING_PROVIDER != "website") {
                DropdownMenuItem(text = { Text("تازه‌سازی قیمت") }, enabled = !state.busy, onClick = { onExpanded(false); onRefreshPrices() })
                state.course?.takeIf { !state.purchased }?.let { course ->
                    DropdownMenuItem(text = { Text("بازیابی خرید") }, enabled = !state.busy, onClick = { onExpanded(false); onPurchase(course, true) })
                }
                HorizontalDivider(color = Line)
                DropdownMenuItem(text = { Text("پرداخت از طریق " + if (BuildConfig.BILLING_PROVIDER == "myket") "مایکت" else "کافه‌بازار") }, enabled = false, onClick = {})
            }
        }
    }
}

@Composable
private fun PurchasePanel(course: Course, state: TrainingState, onPurchase: (Course, Boolean) -> Unit, modifier: Modifier = Modifier, pinned: Boolean = false,
    courseOptions: @Composable () -> Unit = {}) {
    val canBuy = BuildConfig.BILLING_PROVIDER != "website"
    val price = state.prices[course.id]
    val panelScroll = rememberScrollState()
    LaunchedEffect(course.id) { panelScroll.scrollTo(0) }
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Line)) {
        Column {
            // Reserve only the primary CTA. All variable-height content shares one bounded
            // scroll viewport, so variable-height prices never squeeze the summary to zero.
            // Inline panels already belong to the curriculum's scrolling grid: do not nest
            // an unbounded vertical scroller there.
            val bodyModifier = if (pinned) Modifier.weight(1f).verticalScroll(panelScroll) else Modifier
            Column(bodyModifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(persian(course.title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Ink)
                        courseOptions()
                    }
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
private fun Curriculum(course: Course, state: TrainingState, filter: LessonFilter,
    modifier: Modifier, onDownload: (Lesson) -> Unit, onCancel: () -> Unit,
    onPlay: (Lesson) -> Unit, inlinePurchase: (@Composable () -> Unit)?
) {
    val gridState = rememberLazyGridState()
    LaunchedEffect(course.id, filter) { gridState.scrollToItem(0) }
    val lessons = state.lessons.filter { when (filter) { LessonFilter.ALL -> true; LessonFilter.PREVIEW -> it.isPreview; LessonFilter.DOWNLOADED -> it.id in state.downloaded } }
    BoxWithConstraints(modifier) {
        LazyVerticalGrid(columns = GridCells.Fixed(if (maxWidth >= 740.dp) 2 else 1), state = gridState,
            verticalArrangement = Arrangement.spacedBy(6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            if (filter == LessonFilter.ALL && inlinePurchase != null) item(key = "introduction", span = { GridItemSpan(maxLineSpan) }) { inlinePurchase() }
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
                DifficultyBadge(lesson.difficulty, lesson.difficultyLabel)
                        if (lesson.isPreview) Badge("مشاهده رایگان", PreviewGreen, Color.White)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(listOfNotNull(durationLabel(lesson.durationSeconds), "${persian(String.format(Locale.US, "%.1f", lesson.bytes / (1024.0 * 1024))).replace('.', '٫')} مگابایت").joinToString(" · "), modifier = Modifier.weight(1f, fill = false), style = MaterialTheme.typography.labelSmall, color = Muted)
                        if (lesson.description.isNotBlank()) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 4.dp)) {
                            Text(if (expanded) "بستن توضیحات" else "توضیحات", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        }
                    }
                }
                if (downloaded) {
                    Button(onClick = onPlay, enabled = accessible, shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (lesson.isPreview) PreviewGreen else MaterialTheme.colorScheme.primary)) { Text("تماشا") }
                } else if (downloading) OutlinedButton(onClick = onCancel) { Text("توقف دانلود") }
                else if (accessible) OutlinedButton(onClick = { onDownload(lesson) }, enabled = !state.busy, shape = RoundedCornerShape(10.dp)) { Text(if (lesson.isPreview) "دانلود رایگان" else "دانلود درس") }
                else Text("نیاز به خرید", Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelMedium, color = Muted)
            }
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
private fun DifficultyBadge(level: Int, customTitle: String) {
    val title = DifficultyLevel.label(level, customTitle)
    if (title.isEmpty()) return
    val (background, foreground) = when (level) {
        1 -> Mint to Ink
        2 -> Color(0xFFE4EFFC) to Color(0xFF245B88)
        3 -> Color(0xFFFFF1D6) to Color(0xFF805300)
        4 -> Color(0xFFEDE6FA) to Color(0xFF69469B)
        5, 6 -> Color(0xFFFFE7E0) to Color(0xFFA23E25)
        7, 8 -> Color(0xFFFCE4EC) to Color(0xFF9C2850)
        else -> Color(0xFFE9E4FF) to Color(0xFF5335A3)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = background) {
        Text("${DifficultyLevel.emoji(level)} ${persian(title)}", Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = foreground, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun persian(value: Int) = persian(value.toString())
private fun persian(value: String) = persianDisplay(value)
private fun durationLabel(seconds: Double): String? = if (seconds.isFinite() && seconds > 0) "${persian(kotlin.math.ceil(seconds / 60).toInt())} دقیقه" else null
