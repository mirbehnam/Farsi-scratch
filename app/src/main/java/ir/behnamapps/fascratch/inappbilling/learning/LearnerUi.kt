package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import ir.behnamapps.fascratch.inappbilling.domain.Course
import ir.behnamapps.fascratch.R
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.delay

/** Native vector artwork stays sharp on tablets without downloading any avatar asset. */
@Composable private fun CatAvatar(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawCircle(Color(0xFFFFE5BC))
        val ears = Path().apply {
            moveTo(w*.17f,h*.48f); lineTo(w*.2f,h*.13f); lineTo(w*.44f,h*.32f); close()
            moveTo(w*.58f,h*.32f); lineTo(w*.83f,h*.13f); lineTo(w*.86f,h*.5f); close()
        }
        drawPath(ears, Color(0xFFE9A657))
        drawOval(Color(0xFFE9A657), Offset(w*.13f,h*.27f), Size(w*.75f,h*.6f))
        drawCircle(Color(0xFF3D3024), w*.035f, Offset(w*.34f,h*.53f))
        drawCircle(Color(0xFF3D3024), w*.035f, Offset(w*.67f,h*.53f))
        drawCircle(Color(0xFFC66970), w*.035f, Offset(w*.5f,h*.65f))
        drawLine(Color(0xFF725436), Offset(w*.24f,h*.66f), Offset(w*.04f,h*.62f), w*.018f)
        drawLine(Color(0xFF725436), Offset(w*.74f,h*.66f), Offset(w*.95f,h*.62f), w*.018f)
    }
}

@Composable fun LearnerChip(profile: LearnerProfile, modifier: Modifier = Modifier, badgeSize: Dp = 76.dp, onClick: () -> Unit) {
    // Material TextButton clips content to its capsule shape. The artwork is not
    // capsule-shaped: use a rectangular, unclipped hit target with a safety inset.
    Box(modifier.size(badgeSize + 8.dp)
        .semantics { contentDescription = "پروفایل برنامه‌نویس، سطح ${persianDisplay(profile.level.toString())}" }
        .clickable(role = Role.Button, onClick = onClick).padding(4.dp), contentAlignment = Alignment.Center) {
        LearnerLevelBadge(profile.level, Modifier.fillMaxSize())
        if (profile.nameBlocked) Surface(Modifier.align(Alignment.TopEnd).size(16.dp), shape = CircleShape, color = Color(0xFFBA2525)) {
            Box(contentAlignment = Alignment.Center) { Text("!", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp) }
        }
    }
}

/** Reserve the slot before revealing: delayed appearance never moves neighbouring controls. */
@Composable fun DelayedLearnerChip(profile: LearnerProfile?, screenKey: String, badgeSize: Dp = 76.dp, onClick: () -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var delayFinished by remember(screenKey) { mutableStateOf(false) }
    DisposableEffect(lifecycle, screenKey) {
        val observer = LifecycleEventObserver { _, _ ->
            resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (!resumed) delayFinished = false
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(screenKey, resumed) {
        delayFinished = false
        if (resumed) { delay(2_000); delayFinished = true }
    }
    val visible = resumed && delayFinished && !profile?.uuid.isNullOrBlank()
    val opacity by androidx.compose.animation.core.animateFloatAsState(
        if (visible) 1f else 0f, animationSpec = androidx.compose.animation.core.tween(450), label = "learner-badge-fade")
    Box(Modifier.size(badgeSize + 8.dp), contentAlignment = Alignment.Center) {
        // No invisible focus target or click handler during the two-second delay.
        if (visible) LearnerChip(profile!!, Modifier.graphicsLayer { alpha = opacity }, badgeSize, onClick)
    }
}

@Composable fun HomeLearnerBadge() {
    val context = LocalContext.current
    val repository = remember(context) { LearningRepository.get(context) }
    val profiles by repository.profiles.collectAsState()
    val profile = profiles[LearningIdentity.KEY]
    var showProfile by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(repository, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            repository.visit()
            // Retry initial registration after a network interruption, only while
            // home is visible and no authoritative account has been saved yet.
            while (repository.profiles.value[LearningIdentity.KEY] == null) {
                delay(15_000)
                if (repository.profiles.value[LearningIdentity.KEY] == null) repository.visit()
            }
        }
    }
    DelayedLearnerChip(profile, "home") { repository.refreshProfile(); showProfile = true }
    if (showProfile && profile != null) LearnerDialog(null, profile, repository) { showProfile = false }
}

/** Artwork contains no baked-in text: every level, including zero, stays dynamic and Persian. */
@Composable internal fun LearnerLevelBadge(level: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier, contentAlignment = Alignment.TopCenter) {
        // The plaque has fixed artwork dimensions. Do not inherit the button's much
        // taller line-height/font padding, which clips the glyphs in this small slot.
        val badgeFontSize = (maxWidth.value * (if (level < 1000) .20f else .16f) / LocalDensity.current.fontScale).sp
        Image(painterResource(R.drawable.learner_level_badge), contentDescription = null,
            contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        // Updated artwork has a taller plaque spanning roughly 60–90% of the badge.
        Box(Modifier.offset(y = maxHeight * .60f).width(maxWidth * .80f).height(maxHeight * .30f),
            contentAlignment = Alignment.Center) {
            Text("سطح ${persianDisplay(level.toString())}", color = Color(0xFFFFD44F), fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = badgeFontSize,
                    lineHeight = badgeFontSize * 1.15f, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                textAlign = TextAlign.Center, maxLines = 1)
        }
    }
}

@Composable fun LearnerDialog(course: Course?, profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    LearnerDialogTheme { LearnerProfileDialog(course, profile, repository, onClose) }
}

@Composable private fun LearnerProfileDialog(course: Course?, profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    var editing by remember(profile.uuid) { mutableStateOf(false) }
    var showPointsGuide by remember(profile.uuid) { mutableStateOf(false) }
    var showAccount by remember(profile.uuid) { mutableStateOf(false) }
    var showCourses by remember(profile.uuid) { mutableStateOf(false) }
    val syncStatus by repository.status.collectAsState()
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.learnerSafeDialogBounds()) {
        Surface(Modifier.fillMaxSize(),
            shape = RoundedCornerShape(27.dp), color = Color.White,
            border = BorderStroke(3.dp, Color.White), shadowElevation = 16.dp) {
            Box(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color.White, Color(0xFFFFFDFA), Color(0xFFF9F6FF))))) {
                Canvas(Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 10.dp).size(110.dp, 76.dp)) {
                    for (x in 0..3) for (y in 0..2) drawCircle(Color(0xFFF5CC65).copy(alpha = .3f),
                        3.dp.toPx(), Offset((13 + x * 27).dp.toPx(), (13 + y * 27).dp.toPx()))
                }
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
                    LearnerProfilePanel(profile, onAccount = { showAccount = true }, onEditName = { if (profile.registered) editing = true else showAccount = true },
                        onGuide = { showPointsGuide = true }, onClose = onClose)
                    if (profile.nameBlocked) {
                        Spacer(Modifier.height(12.dp))
                        NameRestrictionNotice(profile)
                    }
                    val purchased = profile.courses.filter { it.purchased }
                    if (purchased.isNotEmpty()) {
                        TextButton(onClick = { showCourses = !showCourses }) {
                            Text("دوره‌های خریداری‌شده " + if (showCourses) "⌃" else "⌄", color = Color(0xFF505DC0))
                        }
                        if (showCourses) purchased.forEach { item ->
                            Surface(Modifier.fillMaxWidth().padding(bottom = 6.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFFEAF7E7)) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text("✓ " + item.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color(0xFF407D48))
                                    Text(persianDisplay(learningDuration(item.watchMs)) + " مشاهده" +
                                        if (item.enabled) "" else " · دسترسی غیرفعال", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    syncStatus?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)) }
                    if (!profile.enabled) Text("امتیازدهی این حساب توسط مدیر متوقف شده است.", color = MaterialTheme.colorScheme.error)
                }
                // Physical left-to-right order matches the HTML stripe, regardless of RTL.
                Canvas(Modifier.fillMaxWidth().height(7.dp).align(Alignment.TopCenter)) {
                    listOf(0xFFFFBD44, 0xFFFF829A, 0xFF9C81FC, 0xFF4C97FF, 0xFF46CDA7).forEachIndexed { index, color ->
                        drawRect(Color(color), Offset(size.width * index / 5f, 0f), Size(size.width / 5f, size.height))
                    }
                }
            }
        }
        }
    }
    if (editing) LearnerNameEditorDialog(course, profile, repository, onClose = { editing = false }, onSaved = {
        editing = false
        if (!profile.registered) showAccount = true
    })
    if (showAccount) LearnerAccountDialog(profile, repository) { showAccount = false }
    if (showPointsGuide) LearnerPointsGuideDialog { showPointsGuide = false }
}


@Composable internal fun NameRestrictionNotice(profile: LearnerProfile) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFFFFE9E7), border = BorderStroke(1.dp, Color(0xFFE5AAA4))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text("⛔ نام نمایشی شما مسدود است", fontWeight = FontWeight.Bold, color = Color(0xFFAC2924))
            Text(persianDisplay(profile.nameBlockedDays.toString()) + " روز تا رفع محدودیت", color = Color(0xFFAC2924))
            profile.nameBlockReason?.let { Text("دلیل: " + it, style = MaterialTheme.typography.bodySmall, color = Color(0xFF823530)) }
            Text("این محدودیت بر امتیاز، پیشرفت و خریدهای شما تأثیری ندارد.", style = MaterialTheme.typography.labelSmall, color = Color(0xFF823530))
        }
    }
}

@Composable fun LevelCelebration(course: String, profile: LearnerProfile, repository: LearningRepository) {
    var shown by remember(course) { mutableStateOf(0) }
    LaunchedEffect(course, profile.level) {
        shown = repository.celebration(course)
        if (shown > 0) { delay(3200); repository.clearCelebration(course); shown = 0 }
    }
    AnimatedVisibility(visible = shown > 0, enter = fadeIn() + slideInVertically() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF21634F), shadowElevation = 4.dp) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CatAvatar(Modifier.size(42.dp)); Column(Modifier.weight(1f)) {
                    Text("یک قدم جلوتر!", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("لول ${persianDisplay(shown.toString())}", color = Color(0xFFFFDCA1), style = MaterialTheme.typography.titleMedium)
                }
                Text("✨ 🎉 ✨", fontSize = 24.sp)
            }
        }
    }
}
