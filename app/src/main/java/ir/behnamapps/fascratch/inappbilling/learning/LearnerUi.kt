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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.graphics.Shadow
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
import androidx.compose.ui.tooling.preview.Preview
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
        LearnerLevelBadge(profile.level, Modifier.fillMaxSize(), profile.avatarId)
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
@Composable internal fun LearnerLevelBadge(level: Int, modifier: Modifier = Modifier, avatarId: String? = null,
    imageUrl: String? = null, imageHash: String? = null) {
    val context = LocalContext.current
    val current = if (androidx.compose.ui.platform.LocalInspectionMode.current) null else {
        val profiles by LearningRepository.get(androidx.compose.ui.platform.LocalContext.current).profiles.collectAsState()
        profiles[LearningIdentity.KEY]
    }
    val selected = avatarId ?: current?.avatarId ?: "default"
    val selectedProfile = current?.takeIf { it.avatarId == selected }
    val url = imageUrl ?: selectedProfile?.avatarImageUrl
    val hash = imageHash ?: selectedProfile?.avatarImageHash
    val preview = androidx.compose.ui.platform.LocalInspectionMode.current
    var artwork by remember(selected) { mutableStateOf(AvatarImages.peek(hash)?.asImageBitmap()) }
    LaunchedEffect(selected, url, hash) {
        if (preview || !AvatarImages.validHash(hash)) { artwork = null; return@LaunchedEffect }
        AvatarImages.peek(hash)?.let { artwork = it.asImageBitmap() }
        val loaded = AvatarImages.load(context, url, hash)
        if (loaded != null) artwork = loaded.asImageBitmap()
    }
    val badgeFont = remember { FontFamily(Font(R.font.shabnam)) }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val side = AvatarBadgeLayout.side(maxWidth.value, maxHeight.value).dp
        val density = LocalDensity.current
        val badgeFontSize = with(density) { (side * AvatarBadgeLayout.fontRatio(level)).toSp() }
        val shadowSize = with(density) { (side * AvatarBadgeLayout.SHADOW).toPx() }
        // Fit both layers to the identical square, not the possibly rectangular caller slot.
        Box(Modifier.size(side), contentAlignment = Alignment.TopCenter) {
            if (artwork != null) Image(artwork!!, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            else Image(painterResource(AvatarArtwork.resource(selected)), contentDescription = null,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            Box(Modifier.offset(y = side * AvatarBadgeLayout.TEXT_TOP).width(side * AvatarBadgeLayout.TEXT_WIDTH).height(side * AvatarBadgeLayout.TEXT_HEIGHT),
                contentAlignment = Alignment.Center) {
                Text("سطح ${persianDisplay(level.toString())}", color = Color(0xFFFFD44F), fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = badgeFontSize,
                        fontFamily = badgeFont, letterSpacing = 0.sp,
                        shadow = Shadow(Color(0xFF18356F), Offset(0f, shadowSize), shadowSize),
                        lineHeight = badgeFontSize * AvatarBadgeLayout.LINE_HEIGHT, platformStyle = PlatformTextStyle(includeFontPadding = false)),
                    textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable fun LearnerDialog(course: Course?, profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    val commands by ir.behnamapps.fascratch.inappbilling.data.ClientCommands.state.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { ir.behnamapps.fascratch.inappbilling.data.ClientCommands.refresh(context) }
    if (commands.profileStop != null) {
        LearnerDialogTheme { ProfileStopNotice(commands.profileStop!!, onClose) }
        return
    }
    val profiles by repository.profiles.collectAsState()
    val current = profiles[LearningIdentity.KEY] ?: profile
    LearnerDialogTheme { LearnerProfileDialog(course, current, repository, onClose) }
}

@Composable private fun LearnerProfileDialog(course: Course?, profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    var editing by remember(profile.uuid) { mutableStateOf(false) }
    var showPointsGuide by remember(profile.uuid) { mutableStateOf(false) }
    var showAccount by remember(profile.uuid) { mutableStateOf(false) }
    var showCourses by remember(profile.uuid) { mutableStateOf(false) }
    var showSupplementary by remember(profile.uuid) { mutableStateOf(false) }
    var showMissions by remember(profile.uuid) { mutableStateOf(false) }
    var showMarketplace by remember(profile.uuid) { mutableStateOf(false) }
    var showLeaderboard by remember(profile.uuid) { mutableStateOf(false) }
    val syncStatus by repository.status.collectAsState()
    LaunchedEffect(profile.registered) { if (profile.registered) showAccount = false }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
        Surface(Modifier.learnerDialogPanelBounds(),
            shape = RoundedCornerShape(27.dp), color = Color.White,
            border = BorderStroke(3.dp, Color.White), shadowElevation = 16.dp) {
            Box(Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(
                listOf(Color.White, Color(0xFFFFFDFA), Color(0xFFF9F6FF))))) {
                Canvas(Modifier.align(Alignment.TopEnd).padding(top = 11.dp, end = 10.dp).size(110.dp, 76.dp)) {
                    for (x in 0..3) for (y in 0..2) drawCircle(Color(0xFFF5CC65).copy(alpha = .3f),
                        3.dp.toPx(), Offset((13 + x * 27).dp.toPx(), (13 + y * 27).dp.toPx()))
                }
                Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
                    LearnerProfilePanel(profile, onAccount = { if (profile.registered) editing = true else showAccount = true }, onEditName = { if (profile.registered) editing = true else showAccount = true },
                        onGuide = { showPointsGuide = true }, onMarketplace = { showMarketplace = true }, onClose = onClose,
                        onLeaderboard = { showLeaderboard = true })
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showMissions = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF936411))) { Text("🏆 مأموریت‌ها و جایزه‌ها", fontWeight = FontWeight.Bold) }
                        if (profile.supplementary.available) Button(onClick = { showSupplementary = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5756B9)),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp, pressedElevation = 1.dp)) {
                            Text("🎒 " + if (profile.supplementary.version > 0) "ویرایش اطلاعات تکمیلی" else "ثبت اطلاعات تکمیلی", fontWeight = FontWeight.Bold)
                        }
                    }
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
    if (showAccount && !profile.registered) LearnerAccountDialog(profile, repository) { showAccount = false }
    if (showPointsGuide) LearnerPointsGuideDialog { showPointsGuide = false }
    if (showSupplementary) LearnerSupplementaryDialog(profile, repository) { showSupplementary = false }
    if (showMissions) LearnerMissionsDialog(profile, repository) { showMissions = false }
    if (showMarketplace) LearnerMarketplaceDialog(profile, repository) { showMarketplace = false }
    if (showLeaderboard) LearnerLeaderboardPage(profile.uuid, repository) { showLeaderboard = false }
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
                LearnerLevelBadge(shown, Modifier.size(42.dp), profile.avatarId); Column(Modifier.weight(1f)) {
                    Text("یک قدم جلوتر!", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("لول ${persianDisplay(shown.toString())}", color = Color(0xFFFFDCA1), style = MaterialTheme.typography.titleMedium)
                }
                Text("✨ 🎉 ✨", fontSize = 24.sp)
            }
        }
    }
}


@Preview(
    name = "Learner Profile - Landscape",
    showBackground = true,
    widthDp = 900,
    heightDp = 500,
    locale = "fa"
)
@Composable
private fun LearnerProfileDialogPreview() {
    // پیش‌نمایش ظاهری مستقل از Repository و اطلاعات سرور
    LearnerDialogTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF252A40)),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.90f)
                    .fillMaxHeight(0.92f),
                shape = RoundedCornerShape(27.dp),
                color = Color.White,
                border = BorderStroke(3.dp, Color.White),
                shadowElevation = 16.dp
            ) {
                Box(
                    modifier = Modifier.background(
                        Brush.linearGradient(
                            listOf(
                                Color.White,
                                Color(0xFFFFFDFA),
                                Color(0xFFF9F6FF)
                            )
                        )
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            LearnerLevelBadge(
                                level = 12,
                                modifier = Modifier.size(105.dp)
                            )

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "برنامه‌نویس اسکرچ",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF293454)
                                )

                                Text(
                                    text = "سطح ۱۲ · برنامه‌نویس خلاق",
                                    fontSize = 14.sp,
                                    color = Color(0xFF666B85)
                                )

                                LinearProgressIndicator(
                                    progress = { 0.65f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp),
                                    color = Color(0xFF7359DD),
                                    trackColor = Color(0xFFEAE5FF)
                                )

                                Text(
                                    text = "۶۵۰ از ۱۰۰۰ امتیاز تا سطح بعدی",
                                    fontSize = 12.sp,
                                    color = Color(0xFF666B85)
                                )
                            }

                            Text(
                                text = "✕",
                                fontSize = 22.sp,
                                color = Color(0xFF888888)
                            )
                        }

                        HorizontalDivider(
                            color = Color(0xFFEDEAF4)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            listOf(
                                "⭐" to "۱۲ سطح",
                                "🏆" to "۲۴۵۰ امتیاز",
                                "⏱" to "۸ ساعت آموزش"
                            ).forEach { (icon, title) ->
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(16.dp),
                                    color = Color(0xFFF4F0FF)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(icon, fontSize = 24.sp)
                                        Text(
                                            text = title,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF47406B)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "حساب کاربری",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF293454)
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFF3F7FF)
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "اطلاعات حساب و نام نمایشی",
                                    modifier = Modifier.weight(1f),
                                    color = Color(0xFF34476C)
                                )
                                Text(
                                    text = "ویرایش",
                                    color = Color(0xFF505DC0),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Text(
                            text = "دوره‌های خریداری‌شده",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF505DC0)
                        )

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0xFFEAF7E7)
                        ) {
                            Text(
                                text = "✓ آموزش برنامه‌نویسی اسکرچ · ۸ ساعت مشاهده",
                                modifier = Modifier.padding(14.dp),
                                color = Color(0xFF407D48),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .align(Alignment.TopCenter)
                    ) {
                        listOf(
                            0xFFFFBD44,
                            0xFFFF829A,
                            0xFF9C81FC,
                            0xFF4C97FF,
                            0xFF46CDA7
                        ).forEachIndexed { index, color ->
                            drawRect(
                                color = Color(color),
                                topLeft = Offset(
                                    size.width * index / 5f,
                                    0f
                                ),
                                size = Size(
                                    size.width / 5f,
                                    size.height
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(
    name = "Level Badges",
    showBackground = true,
    backgroundColor = 0xFFF2F2F8,
    widthDp = 400,
    heightDp = 160,
    locale = "fa"
)
@Composable
private fun LearnerLevelBadgesPreview() {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LearnerLevelBadge(0, Modifier.size(76.dp))
        LearnerLevelBadge(12, Modifier.size(76.dp))
        LearnerLevelBadge(125, Modifier.size(76.dp))
        LearnerLevelBadge(1000, Modifier.size(76.dp))
    }
}
