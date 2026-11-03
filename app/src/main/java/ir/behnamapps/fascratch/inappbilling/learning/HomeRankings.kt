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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
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
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
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

// ───────────────────────── Scratch-style ranking panel ─────────────────────────

// زمینهٔ شبانهٔ آبی هم‌رنگ صفحهٔ اصلی
private val NavyTop = Color(0xFF1B2A4A)
private val NavyMid = Color(0xFF14233F)
private val NavyBottom = Color(0xFF0E1A33)

private val PanelRow = Color(0xFF22355E)      // بلاک‌های معمولی (رتبه ۴ به بعد)

// رنگ دسته‌بندی بلاک‌های اسکرچ
private val ScratchYellow = Color(0xFFFFBF00) // رویدادها
private val ScratchOrange = Color(0xFFFFAB19) // کنترل
private val ScratchBlue = Color(0xFF4C97FF)   // حرکت
private val ScratchCyan = Color(0xFF5CB1D6)   // حس‌گر
private val ScratchPurple = Color(0xFF9966FF) // ظاهر
private val DeepInk = Color(0xFF3A2400)

private val Accent = ScratchOrange
private val AccentSoft = Color(0xFFFFE9A8)

private data class RankTone(val accent: Color, val numberColor: Color, val solid: Boolean)
private fun rankTone(rank: Int) = when (rank) {
    1 -> RankTone(ScratchYellow, Color(0xFF5B3205), true)
    2 -> RankTone(ScratchCyan, Color(0xFF22384D), true)
    3 -> RankTone(Color(0xFFFF8C1A), Color(0xFF5A2D1B), true)
    else -> RankTone(ScratchBlue, Color.White, false)
}

/** شکل بلاک اسکرچ: بریدگی بالا و برآمدگی پایین، مثل بلاک‌های واقعی که روی هم سوار می‌شوند. */
private class RankBlockShape(private val radius: Dp = 10.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val d = with(density) { 4.dp.toPx() }
        val nx = with(density) { 18.dp.toPx() }
        val nw = with(density) { 24.dp.toPx() }
        val w = size.width
        val h = size.height
        val bottom = h - d
        val p = Path().apply {
            moveTo(0f, r)
            quadraticBezierTo(0f, 0f, r, 0f)
            lineTo(nx, 0f); lineTo(nx + d, d); lineTo(nx + nw - d, d); lineTo(nx + nw, 0f)
            lineTo(w - r, 0f)
            quadraticBezierTo(w, 0f, w, r)
            lineTo(w, bottom - r)
            quadraticBezierTo(w, bottom, w - r, bottom)
            lineTo(nx + nw, bottom); lineTo(nx + nw - d, h); lineTo(nx + d, h); lineTo(nx, bottom)
            lineTo(r, bottom)
            quadraticBezierTo(0f, bottom, 0f, bottom - r)
            close()
        }
        return Outline.Generic(p)
    }
}

/** بلاک اسکرچ: رنگ دسته‌بندی با درخشش بالا و لبهٔ تیره‌تر. بلاک‌های غیرپرکرده زمینهٔ تیره و لبهٔ رنگی دارند. */
private fun Modifier.scratchBlock(shape: Shape, color: Color, solid: Boolean, highlighted: Boolean): Modifier {
    val dark = Color(color.red * .76f, color.green * .76f, color.blue * .76f)
    val base = if (solid) color else PanelRow
    val sheen = if (solid) .20f else .06f
    return this
        .clip(shape)
        .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = sheen).compositeOver(base), base)))
        .border(
            if (highlighted) 3.dp else 2.dp,
            when {
                highlighted -> Color.White
                solid -> dark
                else -> color.copy(alpha = .75f)
            },
            shape
        )
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
        // One entrance glint only; no permanent animation loop on Home.
        if (!preview && (android.os.Build.VERSION.SDK_INT < 26 || android.animation.ValueAnimator.areAnimatorsEnabled())) {
            shine.animateTo(1.4f, tween(1800))
        }
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Surface(modifier, shape = RoundedCornerShape(22.dp), color = NavyBottom,
            border = BorderStroke(2.5.dp, Brush.verticalGradient(listOf(ScratchBlue.copy(alpha = .95f), ScratchBlue.copy(alpha = .35f))))) {
            Column(Modifier
                .background(Brush.verticalGradient(listOf(NavyTop, NavyMid, NavyBottom)))
                .drawBehind {
                    // هالهٔ نرم آبی و بنفش پشت عنوان
                    drawRect(Brush.radialGradient(listOf(ScratchBlue.copy(alpha = .22f), Color.Transparent),
                        center = Offset(size.width / 2f, 0f), radius = size.width * .65f))
                    drawRect(Brush.radialGradient(listOf(ScratchPurple.copy(alpha = .14f), Color.Transparent),
                        center = Offset(size.width, size.height), radius = size.width * .6f))
                }
                .drawWithContent {
                    drawContent()
                    if (shine.value > -.25f && shine.value < 1.35f) {
                        val x = size.width * shine.value
                        drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = .07f), Color.Transparent),
                            start = Offset(x - size.width * .2f, 0f), end = Offset(x + size.width * .2f, size.height)))
                    }
                }
                .padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)) {
                RankingHeader()
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp), contentPadding = PaddingValues(bottom = if (registered) 2.dp else 52.dp)) {
                        items(rows, key = { it.uuid }) { row ->
                            HomeRankingRow(row, row.uuid == viewer, artRevision) { onPlayer(row.uuid) }
                        }
                        if (HomeRankingPolicy.showViewer(registered, viewer, rows.map { it.uuid }) && viewerProfile != null) {
                            item(key = "home-viewer-summary") {
                                HomeViewerRank(viewerProfile, artRevision) { onPlayer(viewerProfile.uuid) }
                            }
                        }
                    }
                    if (!registered) RegisterPill(onRegister)
                }
            }
        }
    }
}

@Composable private fun RankingHeader() {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        val compact = maxWidth < 360.dp
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.home_rank_header_crown_v2), null,
                Modifier.fillMaxWidth(if (compact) .8f else .6f).height(if (compact) 16.dp else 20.dp), contentScale = ContentScale.Fit)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Image(painterResource(R.drawable.home_rank_header_right_v2), null, Modifier.size(16.dp, 26.dp), contentScale = ContentScale.Fit)
                Text("برنامه‌نویس‌های برتر اسکرچ", Modifier.weight(1f, fill = false).padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center, color = AccentSoft, fontFamily = RankingFont, fontWeight = FontWeight.Black,
                    fontSize = if (compact) 14.sp else 16.sp, lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = LocalTextStyle.current.copy(shadow = Shadow(Color.Black.copy(alpha = .55f), Offset(0f, 2f), 6f)))
                Image(painterResource(R.drawable.home_rank_header_left_v2), null, Modifier.size(16.dp, 26.dp), contentScale = ContentScale.Fit)
            }
        }
    }
}

@Composable private fun BoxScope.RegisterPill(onRegister: () -> Unit) {
    // Cycle: hidden 1s at first, then visible 3s, hidden 2s, visible 3s, hidden 2s, ...
    // It floats over the list bottom (no reserved strip); the scrim fades together with the button.
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1000)
        while (true) {
            alpha.animateTo(1f, tween(300))
            kotlinx.coroutines.delay(3000)
            alpha.animateTo(0f, tween(300))
            kotlinx.coroutines.delay(2000)
        }
    }
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().graphicsLayer { this.alpha = alpha.value },
        contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, NavyBottom.copy(alpha = .92f)))))
        Box(Modifier.heightIn(min = 44.dp)
            .clickable(enabled = alpha.value > .5f, role = Role.Button, onClick = onRegister), contentAlignment = Alignment.Center) {
            Text("چرا اسم من در لیست نیست؟ 😢",
                Modifier.clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFC61A), Color(0xFFFFAB19))))
                    .border(2.dp, Color(0xFFCF8B17), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 5.dp),
                color = DeepInk, fontFamily = RankingFont, fontSize = 13.sp, lineHeight = 19.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable private fun HomeRankingRow(row: RankedLearner, highlighted: Boolean, artRevision: Int, onClick: () -> Unit) {
    val tone = rankTone(row.rank)
    // Solid podium blocks are bright; dark ink is much clearer than white text.
    val nameColor = if (tone.solid) tone.numberColor else Color.White
    val nameShadow = if (tone.solid) {
        Shadow(Color.White.copy(alpha = .28f), Offset(0f, 1f), 1.5f)
    } else {
        Shadow(Color.Black.copy(alpha = .35f), Offset(0f, 1f), 3f)
    }
    val shape = RankBlockShape(radius = 10.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).scratchBlock(shape, tone.accent, tone.solid, highlighted)
        .clickable(role = Role.Button, onClick = onClick).padding(start = 8.dp, end = 8.dp, top = 3.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        RankingMedal(row.rank)
        HomeRankingAvatar(row.avatarId, row.imageUrl, row.imageHash, artRevision)
        Text(row.name, Modifier.weight(1f), textAlign = TextAlign.Start, color = nameColor, maxLines = 1,
            overflow = TextOverflow.Ellipsis, fontFamily = RankingFont,
            fontSize = if (tone.solid) 15.sp else 14.sp,
            fontWeight = if (tone.solid) FontWeight.Black else FontWeight.Bold,
            style = LocalTextStyle.current.copy(shadow = nameShadow))
        if (highlighted) Text("شما", Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 7.dp, vertical = 1.dp),
            color = DeepInk, fontFamily = RankingFont, fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.Black, maxLines = 1)
        RankingLevel(row.level, Color.White)
    }
}

@Composable private fun RankingLevel(level: Int, accent: Color = Color.White) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF0E1A33).copy(alpha = .55f))
        .border(1.dp, accent.copy(alpha = .6f), RoundedCornerShape(50))
        .padding(horizontal = 8.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("سطح ${persianDisplay(level.toString())}", color = Color.White, fontFamily = RankingFont,
            fontWeight = FontWeight.Bold, fontSize = 10.sp, lineHeight = 14.sp, maxLines = 1)
        Canvas(Modifier.size(10.dp, 12.dp)) {
            repeat(3) { index ->
                val height = size.height * (.35f + index * .28f)
                drawRoundRect(accent, topLeft = Offset(index * size.width / 3f, size.height - height),
                    size = Size(size.width * .22f, height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()))
            }
        }
    }
}

@Composable private fun RankingMedal(rank: Int) {
    val tone = rankTone(rank)
    val resource = when (rank) {
        1 -> R.drawable.home_rank_badge_gold_v2
        2 -> R.drawable.home_rank_badge_silver_v2
        3 -> R.drawable.home_rank_badge_bronze_v2
        else -> null
    }
    Box(Modifier.size(42.dp, 40.dp), contentAlignment = Alignment.Center) {
        if (resource != null) {
            Image(painterResource(resource), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            Text(persianDisplay(rank.toString()), Modifier.padding(bottom = 7.dp), color = tone.numberColor,
                fontFamily = RankingFont, fontWeight = FontWeight.Black, fontSize = 16.sp, lineHeight = 18.sp, maxLines = 1)
        } else {
            // رتبهٔ ۴ به بعد: دایرهٔ ساده مثل مقدار عددی داخل بلاک‌های اسکرچ
            Box(Modifier.size(28.dp).clip(CircleShape).background(Color.White.copy(alpha = .14f))
                .border(1.5.dp, Color.White.copy(alpha = .55f), CircleShape), contentAlignment = Alignment.Center) {
                Text(persianDisplay(rank.toString()), color = tone.numberColor, fontFamily = RankingFont,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 1)
            }
        }
    }
}

@Composable private fun HomeViewerRank(profile: LearnerProfile, artRevision: Int, onClick: () -> Unit) {
    val shape = RankBlockShape(radius = 12.dp)
    Column(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("· · ·", color = Color.White.copy(alpha = .4f), fontFamily = RankingFont, fontSize = 12.sp, lineHeight = 14.sp)
        Row(Modifier.fillMaxWidth().scratchBlock(shape, Accent, true, true)
            .clickable(role = Role.Button, onClick = onClick).padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeRankingAvatar(profile.avatarId, profile.avatarImageUrl, profile.avatarImageHash, artRevision)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(profile.rank?.takeIf { it > 0 }?.let { "رتبهٔ شما: ${persianDisplay(it.toString())}" } ?: "رتبهٔ شما هنوز ثبت نشده",
                    color = DeepInk, fontFamily = RankingFont, fontWeight = FontWeight.Black, fontSize = 14.sp, lineHeight = 20.sp,
                    style = LocalTextStyle.current.copy(shadow = Shadow(Color.White.copy(alpha = .25f), Offset(0f, 1f), 1.5f)))
                Text(profile.name, color = DeepInk, fontFamily = RankingFont, fontWeight = FontWeight.Black, fontSize = 12.sp,
                    lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            RankingLevel(profile.level, Color.White)
        }
    }
}

@Composable private fun HomeRankingAvatar(id: String, url: String?, hash: String?, artRevision: Int) {
    val context = LocalContext.current
    var bitmap by remember(hash) { mutableStateOf(AvatarImages.peek(hash)?.asImageBitmap()) }
    LaunchedEffect(hash, url, artRevision) { bitmap = AvatarImages.load(context, url, hash, allowNetwork = false)?.asImageBitmap() }
    val modifier = Modifier.size(38.dp)
    if (bitmap != null) Image(bitmap!!, null, modifier, contentScale = ContentScale.Fit)
    else Image(painterResource(AvatarArtwork.resource(id)), null, modifier, contentScale = ContentScale.Fit)
}

@Composable private fun HomeRankingNotice(title: String, text: String, action: String, onAction: () -> Unit, close: () -> Unit) {
    LearnerDialogTheme {
        Dialog(close, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
            LearnerDialogWindowBounds()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
                    Surface(Modifier.fillMaxWidth(.88f).widthIn(max = 520.dp), shape = RoundedCornerShape(28.dp), color = NavyMid,
                        border = BorderStroke(2.dp, ScratchBlue.copy(alpha = .7f))) {
                        Column(Modifier.background(Brush.verticalGradient(listOf(NavyTop, NavyMid)))
                            .verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Image(painterResource(R.drawable.home_rank_header_crown_v2), null, Modifier.fillMaxWidth(.55f).height(20.dp), contentScale = ContentScale.Fit)
                            Text(title, color = AccentSoft, fontFamily = RankingFont, fontSize = 20.sp, lineHeight = 30.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
                            Text(text, color = Color.White.copy(alpha = .88f), fontFamily = RankingFont, fontSize = 14.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
                                Button(onClick = onAction, shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(2.dp, Color(0xFFCF8B17)),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB81F), contentColor = DeepInk)) {
                                    Text(action, fontFamily = RankingFont, fontWeight = FontWeight.Black)
                                }
                                TextButton(onClick = close) { Text("بستن", color = Color.White.copy(alpha = .75f), fontFamily = RankingFont) }
                            }
                        }
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
