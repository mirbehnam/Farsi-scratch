package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.drawscope.clipRect
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
    val syncStatus by repository.status.collectAsState()
    val height = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp - 32).coerceAtLeast(180).dp
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 940.dp).fillMaxWidth(.94f).heightIn(max = height),
            shape = RoundedCornerShape(28.dp), color = Color(0xFFFAF8FF), tonalElevation = 6.dp) {
            Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LearnerLevelBadge(profile.level, Modifier.size(92.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (profile.name == "هنرجو") "برنامه‌نویس" else profile.name,
                                modifier = Modifier.weight(1f, fill = false), maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
                            if (profile.nameBlocked) Text(" ⛔", color = Color(0xFFBA2525), modifier = Modifier.semantics { contentDescription = "نام نمایشی مسدود است" })
                            if (profile.nameSet) {
                                IconButton(onClick = { editing = true }, modifier = Modifier.semantics { contentDescription = "ویرایش نام نمایشی" }) {
                                    Text("✎", fontSize = 26.sp, color = Color(0xFF855CD6))
                                }
                            } else {
                                FilledTonalButton(onClick = { editing = true }, shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                                    Text("ثبت نام", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text("برنامه‌نویس", color = Color(0xFF746584), style = MaterialTheme.typography.labelLarge)
                        profile.fullName?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                    FilledTonalButton(onClick = { showPointsGuide = true }, shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
                        Text("✨ راهنمای کسب امتیاز", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                    }
                    IconButton(onClick = onClose, modifier = Modifier.semantics { contentDescription = "بستن پروفایل" }) {
                        Text("×", fontSize = 30.sp)
                    }
                }
                ScratchLevelProgress(profile)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (profile.registered) "🔐 حساب قابل بازیابی" else "حساب مهمان", style = MaterialTheme.typography.labelMedium)
                    TextButton(onClick = { showAccount = true }) { Text(if (profile.registered) "مدیریت ورود" else "ذخیره حساب / ورود") }
                }
                if (profile.nameBlocked) NameRestrictionNotice(profile)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileStat("زمان مشاهده آموزش", persianDisplay(learningDuration(profile.watchMs)), Modifier.weight(1f))
                    ProfileStat("زمان برنامه‌نویسی", persianDisplay(learningDuration(profile.codingMs)), Modifier.weight(1f))
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color(0xFFEEE5FF)) {
                    Row(Modifier.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🏆", fontSize = 28.sp)
                        Text("رتبه شما", Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.Bold)
                        Text(profile.rank?.let { persianDisplay(it.toString()) } ?: "—",
                            fontWeight = FontWeight.Black, fontSize = 26.sp, color = Color(0xFF7044BC))
                    }
                }
                if (!profile.nameSet && !profile.nameBlocked) Text("برای حضور در رتبه‌بندی، نام نمایشی ثبت کنید.", style = MaterialTheme.typography.bodySmall)
                val purchased = profile.courses.filter { it.purchased }
                if (purchased.isNotEmpty()) {
                    Text("دوره‌های خریداری‌شده", fontWeight = FontWeight.Bold)
                    purchased.forEach { item ->
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFFE8F3EB)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("✓ " + item.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color(0xFF21634F))
                                Text(persianDisplay(learningDuration(item.watchMs)) + " مشاهده" +
                                    if (item.enabled) "" else " · دسترسی غیرفعال", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                syncStatus?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (!profile.enabled) Text("امتیازدهی این حساب توسط مدیر متوقف شده است.", color = MaterialTheme.colorScheme.error)
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

@Composable private fun ScratchLevelProgress(profile: LearnerProfile) {
    val target = (profile.xpIntoLevel / profile.xpForNext.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }
    val animated by androidx.compose.animation.core.animateFloatAsState(if (started) target else 0f,
        animationSpec = androidx.compose.animation.core.tween(900), label = "scratch-level-progress")
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "progress-glow")
    val glow by transition.animateFloat(0f, 1f, animationSpec = androidx.compose.animation.core.infiniteRepeatable(
        androidx.compose.animation.core.tween(2400), androidx.compose.animation.core.RepeatMode.Restart), label = "glow-position")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(Modifier.size(44.dp), shape = CircleShape, color = Color(0xFFFFAB19)) {
            Box(contentAlignment = Alignment.Center) { Text(persianDisplay(profile.level.toString()), fontWeight = FontWeight.Black, color = Color(0xFF513508)) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                drawRoundRect(Color(0xFFE9DFF7), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                if (animated > 0) {
                    drawRoundRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF855CD6), Color(0xFFFFAB19))),
                        size = Size(size.width * animated, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                    clipRect(right = size.width * animated) {
                        drawRect(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = .35f), Color.Transparent)),
                            topLeft = Offset(size.width * glow - 60.dp.toPx(), 0f), size = Size(60.dp.toPx(), size.height))
                    }
                }
            }
            Text(persianDisplay((target * 100).toInt().toString()) + "٪ - " +
                persianDisplay(kotlin.math.ceil((profile.xpForNext - profile.xpIntoLevel).coerceAtLeast(0.0)).toLong().toString()) +
                " امتیاز تا سطح بعد", style = MaterialTheme.typography.labelMedium, color = Color(0xFF7044BC))
        }
    }
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

@Composable private fun ProfileStat(title: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(12.dp)) { Text(title, style = MaterialTheme.typography.labelSmall); Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) }
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
