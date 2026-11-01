package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Context
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import ir.behnamapps.fascratch.R
import kotlinx.coroutines.launch
import kotlin.random.Random

private val RankingFont = FontFamily(Font(R.font.shabnam))

private data class RankingMetal(val light: Color, val middle: Color, val dark: Color, val edge: Color)
private fun rankingMetal(rank: Int) = when (rank) {
    1 -> RankingMetal(Color(0xFFFDBB26), Color(0xFFAB5905), Color(0xFF713707), Color(0xFFFFDE75))
    2 -> RankingMetal(Color(0xFF91B2CB), Color(0xFF425E78), Color(0xFF24394D), Color(0xFFC8E1F0))
    3 -> RankingMetal(Color(0xFFE49B57), Color(0xFF9A4E27), Color(0xFF62321F), Color(0xFFFFC388))
    else -> RankingMetal(Color(0xFF33495C), Color(0xFF243443), Color(0xFF182634), Color(0xFF62798E))
}

internal fun homeOnline(context: Context): Boolean = runCatching {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    manager.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
}.getOrDefault(false)

@Composable internal fun HomeRankingsSlot(modifier: Modifier, normal: @Composable () -> Unit) {
    if (androidx.compose.ui.platform.LocalInspectionMode.current) { Box(modifier) { normal() }; return }
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { LearningRepository.get(context) }
    // Never replace this entry's selection after disk/network completion or recomposition.
    val selected = remember { HomeRankingsCache.selection(Random.nextDouble()) }
    val profiles by repository.profiles.collectAsState()
    val profile = profiles[LearningIdentity.KEY]
    var online by remember { mutableStateOf(homeOnline(context)) }
    var resumed by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    DisposableEffect(context, owner) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { online = homeOnline(context) }
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) { online = homeOnline(context) }
            override fun onLost(network: Network) { online = homeOnline(context) }
        }
        val observer = LifecycleEventObserver { _, _ -> resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        owner.lifecycle.addObserver(observer)
        resumed = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val registered = runCatching { manager.registerDefaultNetworkCallback(callback) }.isSuccess
        onDispose { owner.lifecycle.removeObserver(observer); if (registered) runCatching { manager.unregisterNetworkCallback(callback) } }
    }
    LaunchedEffect(online, resumed, profile?.uuid) {
        if (online && resumed && profile != null) HomeRankingsCache.refresh(context, repository, profile.uuid)
    }
    var guide by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(false) }
    var target by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf<(() -> Unit)?>(null) }
    val commands by ir.behnamapps.fascratch.inappbilling.data.ClientCommands.state.collectAsState()
    fun ready(action: () -> Unit) {
        online = homeOnline(context)
        if (!online) { retry = action; notice = "برای ادامه به اینترنت وصل شو و دوباره امتحان کن." }
        else if (profile == null || repository.requiresRecovery()) {
            repository.refreshProfile(); retry = null; notice = "حسابت هنوز آماده نیست؛ چند لحظه دیگر دوباره امتحان کن."
        } else { action() }
    }
    LaunchedEffect(profile?.registered, profile?.uuid) { if (profile?.registered == true) account = false; target = null }
    Box(modifier) {
        if (selected.isEmpty()) normal()
        else HomeRankingsPanel(selected, profile?.uuid, profile?.registered == true, {
            ready { target = it; scope.launch { ir.behnamapps.fascratch.inappbilling.data.ClientCommands.refresh(context) } }
        }, { guide = true }, Modifier.fillMaxSize(), viewerProfile = profile)
    }
    if (guide) HomeRankingNotice("جای تو اینجاست!", "برای اینکه نامت بتواند در رتبه‌بندی نمایش داده شود، اول ثبت‌نام کن و امتیاز جمع کن.", "ثبت‌نام", {
        ready { guide = false; account = true; scope.launch { ir.behnamapps.fascratch.inappbilling.data.ClientCommands.refresh(context) } }
    }, { guide = false })
    notice?.let { message -> HomeRankingNotice("یه اتصال کوچولو لازم داریم", message, if (retry != null) "تلاش مجدد" else "باشه", {
        val pending = retry; notice = null; retry = null; if (pending != null) ready(pending)
    }, { notice = null; retry = null }) }
    if ((account || target != null) && commands.profileStop != null) {
        LearnerDialogTheme { ProfileStopNotice(commands.profileStop!!) { account = false; target = null } }
    } else {
        if (account && profile != null && !profile.registered) LearnerDialogTheme { LearnerAccountDialog(profile, repository) { account = false } }
        if (target != null && profile != null) LearnerShowcasePage(profile.uuid, target!!, repository) { target = null }
    }
}

@Composable internal fun HomeRankingsPanel(rows: List<RankedLearner>, viewer: String?, registered: Boolean,
    onPlayer: (String) -> Unit, onRegister: () -> Unit, modifier: Modifier = Modifier, viewerProfile: LearnerProfile? = null) {
    val artRevision by HomeRankingsCache.artworkRevision.collectAsState()
    val shine = remember { Animatable(-.3f) }
    val preview = LocalInspectionMode.current
    LaunchedEffect(Unit) {
        // A single entrance glint; no permanent animation loop on the Home screen.
        if (!preview && (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())) {
            shine.animateTo(1.4f, tween(2200))
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    Surface(modifier, shape = RoundedCornerShape(23.dp), color = Color(0xFF352019),
        border = BorderStroke(1.5.dp, Brush.verticalGradient(listOf(Color(0xFFFFD56A), Color(0xFFE58227), Color(0xFFFFC86A))))) {
        Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF783710), Color(0xFF40261D), Color(0xFF182633))))
            .drawBehind {
                // Vector corner ribbons keep the frame crisp at every screen size.
                val topRibbon = Path().apply {
                    moveTo(0f, 0f); lineTo(size.width * .24f, 0f)
                    quadraticTo(size.width * .13f, size.height * .07f, 0f, size.height * .15f); close()
                }
                drawPath(topRibbon, Brush.linearGradient(listOf(Color(0xFFFFA11D), Color(0xFFC85108))))
                val lowerRibbon = Path().apply {
                    moveTo(size.width, size.height); lineTo(size.width * .72f, size.height)
                    quadraticTo(size.width * .91f, size.height * .94f, size.width, size.height * .85f); close()
                }
                drawPath(lowerRibbon, Brush.linearGradient(listOf(Color(0xFFB44A0C), Color(0xFFFF9A1D))))
            }
            .drawWithContent {
                drawContent()
                if (shine.value > -.25f && shine.value < 1.35f) {
                    val x = size.width * shine.value
                    drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .08f), Color.Transparent),
                        start = Offset(x - size.width * .22f, 0f), end = Offset(x + size.width * .22f, size.height)))
                }
                // Small static sparkles stay at the frame edges, away from names and controls.
                listOf(Offset(size.width * .025f, size.height * .12f), Offset(size.width * .975f, size.height * .06f),
                    Offset(size.width * .975f, size.height * .88f)).forEach { point ->
                    val radius = 3.dp.toPx()
                    drawLine(Color(0xFFFFD783).copy(alpha = .5f), point - Offset(radius, 0f), point + Offset(radius, 0f), 1.dp.toPx())
                    drawLine(Color(0xFFFFD783).copy(alpha = .5f), point - Offset(0f, radius), point + Offset(0f, radius), 1.dp.toPx())
                }
            }.padding(start = 11.dp, end = 11.dp, top = 8.dp, bottom = 4.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 7.dp).height(56.dp), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.home_rank_header_v1), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
                Text("برنامه‌نویس‌های برتر اسکرچ", Modifier.fillMaxWidth().padding(start = 34.dp, end = 34.dp, top = 16.dp), textAlign = TextAlign.Center,
                    color = Color(0xFFFFF7E8), fontFamily = RankingFont, fontWeight = FontWeight.Black, fontSize = if (maxWidth < 360.dp) 14.sp else 16.sp,
                    lineHeight = 20.sp,
                    style = LocalTextStyle.current.copy(shadow = Shadow(Color(0xFF35170B), Offset(0f, 2f), 4f)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 2.dp)) {
                items(rows, key = { it.uuid }) { row ->
                    HomeRankingRow(row, row.uuid == viewer, artRevision) { onPlayer(row.uuid) }
                }
                if (HomeRankingPolicy.showViewer(registered, viewer, rows.map { it.uuid }) && viewerProfile != null) {
                    item(key = "home-viewer-summary") {
                        HomeViewerRank(viewerProfile, artRevision) { onPlayer(viewerProfile.uuid) }
                    }
                }
            }
            if (!registered) Box(Modifier.align(Alignment.CenterHorizontally).heightIn(min = 44.dp)
                .clickable(role = Role.Button, onClick = onRegister), contentAlignment = Alignment.BottomCenter) {
                // Compact visible pill inside a comfortable touch target; explicit app font.
                Surface(shape = RoundedCornerShape(50), color = Color.Transparent, shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, Color(0xFFFFA14C))) {
                    Text("چرا اسم من در لیست نیست؟ 😢", Modifier.background(Brush.verticalGradient(
                        listOf(Color(0xFFFF861C), Color(0xFFF4670B), Color(0xFFCC4005))))
                        .padding(horizontal = 10.dp, vertical = 3.dp), color = Color.White,
                        fontFamily = RankingFont, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    }
}

@Composable private fun HomeRankingRow(row: RankedLearner, highlighted: Boolean, artRevision: Int, onClick: () -> Unit) {
    val metal = rankingMetal(row.rank)
    val frame = when (row.rank) {
        1 -> R.drawable.home_rank_gold_v1
        2 -> R.drawable.home_rank_silver_v1
        3 -> R.drawable.home_rank_bronze_v1
        else -> R.drawable.home_rank_regular_v1
    }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), color = Color.Transparent,
        border = if (highlighted) BorderStroke(2.dp, Color(0xFFFFF1B4)) else null) {
        Box {
        Image(painterResource(frame), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Row(Modifier.clickable(role = Role.Button, onClick = onClick).padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RankingMedal(row.rank, metal)
            HomeRankingAvatar(row.avatarId, row.imageUrl, row.imageHash, artRevision)
            Text(row.name, Modifier.weight(1f), textAlign = TextAlign.Start, color = Color.White, maxLines = 1,
                overflow = TextOverflow.Ellipsis, fontFamily = RankingFont, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                style = LocalTextStyle.current.copy(shadow = Shadow(Color.Black.copy(alpha = .3f), Offset(0f, 1f), 2f)))
            RankingLevel(row.level)
        }
        }
    }
}

@Composable private fun RankingLevel(level: Int) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF101D2C).copy(alpha = .55f))
        .padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("سطح ${persianDisplay(level.toString())}", color = Color(0xFFFFF4E7), fontFamily = RankingFont,
            fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 1)
        Canvas(Modifier.size(10.dp, 12.dp)) {
            repeat(3) { index ->
                val height = size.height * (.35f + index * .28f)
                drawRoundRect(Color(0xFFDCEAF7), topLeft = Offset(index * size.width / 3f, size.height - height),
                    size = Size(size.width * .22f, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            }
        }
    }
}

@Composable private fun RankingMedal(rank: Int, metal: RankingMetal) {
    val winner = rank in 1..3
    Box(Modifier.size(46.dp, 42.dp), contentAlignment = Alignment.Center) {
        if (winner) Canvas(Modifier.fillMaxSize()) {
            val ribbon = Path().apply {
                moveTo(size.width * .38f, size.height * .53f); lineTo(size.width * .26f, size.height)
                lineTo(size.width * .43f, size.height * .89f); lineTo(size.width * .55f, size.height)
                lineTo(size.width * .63f, size.height * .58f); close()
            }
            drawPath(ribbon, if (rank == 1) Color(0xFFD94027) else if (rank == 2) Color(0xFF3C8AC7) else Color(0xFFD96E28))
            for (side in listOf(-1, 1)) repeat(5) { index ->
                val point = Offset(size.width * .5f + side * size.width * (.38f - index * .015f), size.height * (.18f + index * .11f))
                rotate(side * (28f + index * 12f), point) {
                    drawOval(metal.edge.copy(alpha = .9f), topLeft = point - Offset(size.width * .035f, size.height * .055f),
                        size = Size(size.width * .07f, size.height * .11f))
                }
            }
        }
        Box(Modifier.padding(bottom = if (winner) 5.dp else 0.dp).size(if (winner) 29.dp else 27.dp)
            .clip(CircleShape).background(if (winner) Brush.linearGradient(listOf(metal.edge, metal.light, metal.middle))
                else Brush.linearGradient(listOf(Color(0xFF2B4052), Color(0xFF1D2D3D))))
            .then(Modifier.drawBehind { drawCircle(metal.edge.copy(alpha = if (winner) .95f else .45f), style = Stroke(1.dp.toPx())) }),
            contentAlignment = Alignment.Center) {
            Text(persianDisplay(rank.toString()), color = if (winner) metal.dark else Color(0xFFDAE5EF),
                fontFamily = RankingFont, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
    }
}

@Composable private fun HomeViewerRank(profile: LearnerProfile, artRevision: Int, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(top = 5.dp), shape = RoundedCornerShape(14.dp),
        color = Color.Transparent, border = BorderStroke(1.5.dp, Color(0xFFFFD783))) {
        Box {
        Image(painterResource(R.drawable.home_rank_regular_v1), null, Modifier.matchParentSize(), contentScale = ContentScale.FillBounds)
        Row(Modifier.clickable(role = Role.Button, onClick = onClick).padding(10.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeRankingAvatar(profile.avatarId, profile.avatarImageUrl, profile.avatarImageHash, artRevision)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(profile.rank?.takeIf { it > 0 }?.let { "رتبهٔ شما: ${persianDisplay(it.toString())}" } ?: "رتبهٔ شما هنوز ثبت نشده",
                    color = Color(0xFFFFDF94), fontFamily = RankingFont, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Text(profile.name, color = Color.White, fontFamily = RankingFont, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RankingLevel(profile.level)
        }
        }
    }
}

@Composable private fun HomeRankingAvatar(id: String, url: String?, hash: String?, artRevision: Int) {
    val context = LocalContext.current
    var bitmap by remember(hash) { mutableStateOf(AvatarImages.peek(hash)?.asImageBitmap()) }
    LaunchedEffect(hash, url, artRevision) { bitmap = AvatarImages.load(context, url, hash, allowNetwork = false)?.asImageBitmap() }
    if (bitmap != null) Image(bitmap!!, null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
    else Image(painterResource(AvatarArtwork.resource(id)), null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
}

@Composable private fun HomeRankingNotice(title: String, text: String, action: String, onAction: () -> Unit, close: () -> Unit) {
    LearnerDialogTheme {
        Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
            LearnerDialogWindowBounds()
            Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
                Surface(Modifier.fillMaxWidth(.88f).widthIn(max = 520.dp), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("✦  $title", fontSize = 20.sp, fontWeight = FontWeight.Black)
                        Text(text, fontSize = 14.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Button(onClick = onAction) { Text(action) }; TextButton(onClick = close) { Text("بستن") } }
                    }
                }
            }
        }
    }
}

@Preview(name = "Home rankings / landscape", widthDp = 450, heightDp = 330, locale = "fa")
@Preview(name = "Home rankings / compact", widthDp = 350, heightDp = 250, locale = "fa")
@Composable private fun HomeRankingsPreview() {
    val names = listOf("علی‌اصغر", "Ghazal", "محمد", "AMIR")
    HomeRankingsPanel((1..15).map { RankedLearner("preview-$it", names.getOrNull(it - 1) ?: "برنامه‌نویس $it",
        it, (5-it).coerceAtLeast(2), 0.0, "default", null, null) }, null, false, {}, {}, Modifier.fillMaxSize())
}

@Preview(name = "Home rankings / own rank at end", widthDp = 450, heightDp = 330, locale = "fa")
@Composable private fun HomeViewerRankPreview() {
    val profile = LearnerProfile("viewer", "برنامه‌نویس خلاق", 8, 0, 0.0, 100.0, 842, true, registered = true)
    HomeRankingsPanel(listOf(RankedLearner("other", "قهرمان اسکرچ", 1, 25, 0.0, "default", null, null)),
        profile.uuid, true, {}, {}, Modifier.fillMaxSize(), profile)
}
