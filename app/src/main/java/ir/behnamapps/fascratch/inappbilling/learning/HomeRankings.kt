package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Context
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.launch
import kotlin.random.Random

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
        }, { guide = true }, Modifier.fillMaxSize())
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
    onPlayer: (String) -> Unit, onRegister: () -> Unit, modifier: Modifier = Modifier) {
    val artRevision by HomeRankingsCache.artworkRevision.collectAsState()
    Surface(modifier, shape = RoundedCornerShape(22.dp), color = Color(0xFF172C49), border = BorderStroke(1.dp, Color(0xFF42718A))) {
        Column(Modifier.background(Brush.verticalGradient(listOf(Color(0xFF223958), Color(0xFF10203B)))).padding(12.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("برنامه‌نویس‌های برتر", Modifier.weight(1f), color = Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("✦ TOP ${rows.size}", color = Color(0xFFFFD783), fontSize = 11.sp)
            }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                items(rows, key = { it.uuid }) { row ->
                    val context = LocalContext.current
                    var image by remember(row.imageHash) { mutableStateOf(AvatarImages.peek(row.imageHash)?.asImageBitmap()) }
                    LaunchedEffect(row.imageHash, artRevision) { image = AvatarImages.load(context, row.imageUrl, row.imageHash, allowNetwork = false)?.asImageBitmap() }
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(if (row.uuid == viewer) Color(0xFF315563) else Color.White.copy(alpha = .045f))
                        .clickable(role = Role.Button) { onPlayer(row.uuid) }.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(persianDisplay(row.rank.toString()), Modifier.width(25.dp), color = if (row.rank <= 3) Color(0xFFFFD783) else Color(0xFFB5C9E8), fontWeight = FontWeight.Bold)
                        if (image != null) Image(image!!, null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
                        else Image(painterResource(AvatarArtwork.resource(row.avatarId)), null, Modifier.size(36.dp), contentScale = ContentScale.Fit)
                        Text(row.name, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp)
                        Text("سطح ${persianDisplay(row.level.toString())}", color = Color(0xFF9BE1CB), fontSize = 10.sp)
                    }
                }
            }
            if (!registered) TextButton(onClick = onRegister, modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)) {
                Text("چرا اسم من در لیست نیست؟", color = Color(0xFFFFD783), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
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
@Composable private fun HomeRankingsPreview() {
    HomeRankingsPanel((1..15).map { RankedLearner("preview-$it", if (it == 2) "برنامه‌نویس با نام نمایشی طولانی" else "برنامه‌نویس $it", it, 20-it, 0.0, "default", null, null) }, null, false, {}, {}, Modifier.fillMaxSize())
}
