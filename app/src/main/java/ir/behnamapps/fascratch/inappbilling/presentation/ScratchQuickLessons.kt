package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import ir.behnamapps.fascratch.R
import ir.behnamapps.fascratch.inappbilling.data.ClientCommands
import ir.behnamapps.fascratch.inappbilling.data.QuickLessonData
import ir.behnamapps.fascratch.inappbilling.domain.Lesson
import ir.behnamapps.fascratch.inappbilling.learning.LearningIdentity
import ir.behnamapps.fascratch.inappbilling.learning.LearningRepository

/** Only observes an already-existing in-memory account stream until the book is clicked. */
@Composable
internal fun ScratchQuickLessonsShortcut(activity: ComponentActivity, onVisibilityChanged: (Boolean) -> Unit) {
    val app = activity.applicationContext
    val learning = remember(app) { LearningRepository.get(app) }
    val profiles by learning.profiles.collectAsState()
    val profile = profiles[LearningIdentity.KEY]
    val owned = profile?.courses.orEmpty().filter { it.purchased && it.enabled }.map { it.uuid }.toSet()
    val eligible = QuickLessonPolicy.shortcutVisible(profile?.enabled == true, learning.requiresRecovery(), owned)
    var session by remember { mutableStateOf<QuickLessonsController?>(null) }
    var owner by remember { mutableStateOf<String?>(null) }
    var openedEpoch by remember { mutableLongStateOf(0) }
    val latestSession by rememberUpdatedState(session)
    val notify by rememberUpdatedState(onVisibilityChanged)
    fun closeSession() { session?.close(); session = null; owner = null; notify(false) }
    DisposableEffect(Unit) { onDispose { latestSession?.close(); notify(false) } }
    LaunchedEffect(profile?.uuid, owned, eligible) {
        if (session != null && (!eligible || owner != profile?.uuid || session?.state?.value?.course?.id?.let { it !in owned } == true)) closeSession()
    }
    if (eligible) {
        Spacer(Modifier.height(7.dp))
        val minimized = session?.state?.value?.visible == false
        Box(Modifier.fillMaxWidth(.72f).height(36.dp).clip(RoundedCornerShape(12.dp))
            .background(if (minimized) Color(0xFF8050CB) else Color.White.copy(.1f))
            .semantics { contentDescription = if (minimized) "ادامه آموزش" else "آموزش سریع" }
            .clickable {
                val current = session
                if (current != null) { current.show(); notify(true) }
                else {
                    val accountId = profile?.uuid ?: return@clickable
                    owner = accountId
                    openedEpoch = ClientCommands.state.value.cacheEpoch
                    val controller = QuickLessonsController({ QuickLessonData(app) }, owns = { courseId ->
                        val actual = learning.profiles.value[LearningIdentity.KEY]
                        actual?.uuid == accountId && actual.enabled && !learning.requiresRecovery() &&
                            actual.courses.any { it.uuid == courseId && it.purchased && it.enabled }
                    }, prepareWatch = { learning.prepare(it) })
                    session = controller; notify(true)
                    if (ClientCommands.state.value.blockedMessage == null) controller.load()
                }
            }, contentAlignment = Alignment.Center) {
            BookShortcutIcon()
            if (minimized) Box(Modifier.align(Alignment.TopEnd).padding(3.dp).size(5.dp).background(Color(0xFFFFB84D), RoundedCornerShape(5.dp)))
        }
    }
    session?.let { controller ->
        val state by controller.state.collectAsState()
        val commands by ClientCommands.state.collectAsState()
        DisposableEffect(controller) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) { controller.minimize(); notify(false) }
            }
            activity.lifecycle.addObserver(observer)
            onDispose { activity.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(commands.cacheEpoch) { if (commands.cacheEpoch != openedEpoch) closeSession() }
        LaunchedEffect(commands.blockedMessage) {
            if (commands.blockedMessage != null) { controller.cancelDownload(); controller.backToLessons() }
        }
        if (state.visible) QuickLessonPopup(activity, state, controller, learning, commands.blockedMessage,
            onClose = ::closeSession, onMinimize = { controller.minimize(); notify(false) })
    }
}

@Composable
private fun BookShortcutIcon() {
    Canvas(Modifier.size(23.dp)) {
        val u = size.width / 24
        val pages = Path().apply {
            moveTo(12*u, 6*u); quadraticTo(7*u, 3*u, 2*u, 5*u); lineTo(2*u, 20*u)
            quadraticTo(7*u, 18*u, 12*u, 21*u); quadraticTo(17*u, 18*u, 22*u, 20*u)
            lineTo(22*u, 5*u); quadraticTo(17*u, 3*u, 12*u, 6*u); lineTo(12*u, 21*u)
        }
        drawPath(pages, Color(0xFFFFD79C), style = Stroke(1.7f*u))
        drawLine(Color.White, Offset(5*u, 9*u), Offset(9*u, 10*u), u)
        drawLine(Color.White, Offset(15*u, 10*u), Offset(19*u, 9*u), u)
    }
}

@Composable
private fun QuickLessonPopup(activity: ComponentActivity, state: QuickLessonsState, controller: QuickLessonsController,
    learning: LearningRepository, blocked: String?, onClose: () -> Unit, onMinimize: () -> Unit) {
    Dialog(onDismissRequest = { if (state.playing != null) controller.backToLessons() else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window ?: activity.window
        val font = FontFamily(Font(R.font.shabnam))
        val typography = Typography().let { it.copy(headlineSmall = it.headlineSmall.copy(fontFamily = font),
            titleLarge = it.titleLarge.copy(fontFamily = font), titleMedium = it.titleMedium.copy(fontFamily = font), bodyLarge = it.bodyLarge.copy(fontFamily = font),
            bodyMedium = it.bodyMedium.copy(fontFamily = font), bodySmall = it.bodySmall.copy(fontFamily = font),
            labelLarge = it.labelLarge.copy(fontFamily = font), labelMedium = it.labelMedium.copy(fontFamily = font), labelSmall = it.labelSmall.copy(fontFamily = font)) }
        MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFFFC36E), surface = Color(0xFF201C32), onSurface = Color.White), typography = typography) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalTextStyle provides LocalTextStyle.current.copy(fontFamily = font)) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding().padding(12.dp), shape = RoundedCornerShape(20.dp), color = Color(0xFF201C32)) {
                    Column(Modifier.fillMaxSize()) {
                        Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            var chooseCourse by remember { mutableStateOf(false) }
                            Box(Modifier.weight(1f)) {
                                Text(persianDisplay((state.course?.title ?: "آموزش سریع") + if (state.courses.size > 1 && state.playing == null) " ▾" else ""),
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = state.courses.size > 1 && state.playing == null && state.downloadingId == null) { chooseCourse = true }.padding(8.dp),
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = Color.White)
                                DropdownMenu(expanded = chooseCourse, onDismissRequest = { chooseCourse = false }) {
                                    state.courses.forEach { course -> DropdownMenuItem(text = { Text(persianDisplay(course.title)) },
                                        onClick = { chooseCourse = false; controller.select(course) }) }
                                }
                            }
                            if (state.loading && state.downloadingId == null) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            IconButton(onClick = onMinimize, modifier = Modifier.semantics { contentDescription = "مخفی‌کردن آموزش و توقف پخش" }) { Text("−", fontSize = 28.sp) }
                            IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "بستن کامل آموزش" }) { Text("×", fontSize = 26.sp) }
                        }
                        HorizontalDivider(color = Color.White.copy(.08f))
                        val playing = state.playing
                        if (blocked != null) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { Text(blocked) }
                        else if (playing != null) key(playing.lesson.id, playing.file.absolutePath) {
                            val tracker = remember(playing.lesson.id) { state.course?.let { learning.tracker(it, playing.lesson) } }
                            FullscreenLessonPlayer(playing.file, playing.lesson.title, window, onClose = controller::backToLessons,
                                watchTracker = tracker, hasSubtitles = playing.lesson.subtitle != null,
                                loadSubtitles = { controller.subtitles(playing.lesson) },
                                initialPlayback = state.checkpoint,
                                onPlaybackCheckpoint = { controller.saveCheckpoint(playing.lesson.id, it) },
                                nextLessonTitle = controller.nextDownloaded()?.title,
                                onPlayNext = { controller.nextDownloaded()?.let { controller.play(it); true } ?: false })
                        } else {
                            state.message?.let { message ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = Color(0xFFFFD79C))
                                    if (!state.loading) TextButton(onClick = { controller.load() }) { Text("تلاش دوباره") }
                                }
                            }
                            if (state.lessons.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                if (state.loading) CircularProgressIndicator() else Text("هنوز قسمتی برای نمایش آماده نیست.", style = MaterialTheme.typography.bodyMedium)
                            } else LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                itemsIndexed(state.lessons, key = { _, lesson -> lesson.id }) { index, lesson ->
                                    QuickLessonCard(lesson, index, lesson.id in state.downloaded, state.downloadingId == lesson.id,
                                        state.progress, state.downloadingId == null, { controller.play(lesson) }, controller::cancelDownload)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickLessonCard(lesson: Lesson, index: Int, downloaded: Boolean, downloading: Boolean, progress: Float,
    enabled: Boolean, onClick: () -> Unit, cancel: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(14.dp), color = Color(0xFF302A47)) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF6543A2)), contentAlignment = Alignment.Center) {
                    Text(persianDisplay("${index + 1}"), fontWeight = FontWeight.Bold, color = Color.White, fontSize = 20.sp)
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(persianDisplay(lesson.title), maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    val minutes = kotlin.math.ceil(lesson.durationSeconds / 60).toInt().coerceAtLeast(1)
                    Text(persianDisplay("$minutes دقیقه · " + when { downloading -> "دانلود ${(progress * 100).toInt()}٪"; downloaded -> "آماده پخش"; else -> "دانلود و تماشا" }),
                        style = MaterialTheme.typography.bodySmall, color = if (downloaded) Color(0xFF9DE7C3) else Color(0xFFD0C5E6))
                }
                if (downloading) TextButton(onClick = cancel) { Text("لغو") }
                else Text(if (downloaded) "▶" else "↓", color = Color(0xFFFFC36E), fontSize = 22.sp)
            }
            if (downloading) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(top = 7.dp).height(4.dp))
        }
    }
}
