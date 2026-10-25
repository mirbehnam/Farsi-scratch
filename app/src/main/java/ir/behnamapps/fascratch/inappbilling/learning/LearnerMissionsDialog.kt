package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import ir.behnamapps.fascratch.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

@Composable
internal fun LearnerMissionsDialog(profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    val state by repository.missions.collectAsState()
    val profiles by repository.profiles.collectAsState()
    val board = state?.takeIf { it.uuid == profile.uuid }
    val current = profiles[LearningIdentity.KEY]
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var celebration by remember { mutableStateOf<String?>(null) }
    var help by remember(profile.uuid) { mutableStateOf<MissionItem?>(null) }
    var adTicket by remember(profile.uuid) { mutableStateOf<RewardedAdTicket?>(null) }
    val adState by RewardedAdManager.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val adOffer = board?.rewardedAd
    suspend fun refresh() {
        loading = true; error = null
        try { repository.refreshMissions(profile.uuid) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "اتصال برقرار نشد؛ برای تازه‌سازی و دریافت جایزه آنلاین شو." }
        finally { loading = false }
    }
    LaunchedEffect(profile.uuid) { refresh() }
    LaunchedEffect(adOffer?.retryAtMs, adOffer?.remaining, adOffer?.serverTimeMs) {
        val offer = adOffer
        if (offer != null && offer.remaining == 0 && offer.retryAtMs != null) {
            delay((offer.retryAtMs - offer.serverTimeMs).coerceIn(1_000, 604_800_000))
            refresh()
        }
    }
    LaunchedEffect(profile.uuid) {
        repository.missionRewards.collect { (uuid, reward) ->
            if (uuid == profile.uuid && repository.profiles.value[LearningIdentity.KEY]?.uuid == profile.uuid) {
                celebration = "🪙 " + persianDisplay(reward.toString()) + " سکه گرفتی؛ ممنون از همراهی‌ات!"
                val process = android.app.ActivityManager.RunningAppProcessInfo()
                android.app.ActivityManager.getMyMemoryState(process)
                if (process.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) MissionRewardSound.play()
            }
        }
    }
    LaunchedEffect(celebration) { if (celebration != null) { delay(3000); celebration = null } }
    LaunchedEffect(current?.uuid) { if (current == null || current.uuid != profile.uuid) onClose() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.learnerDialogPanelBounds(), shape = RoundedCornerShape(25.dp), color = Color(0xFFFFF9EC),
                border = BorderStroke(2.dp, Color(0xFFE9D5A4)), shadowElevation = 12.dp) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏆", fontSize = 30.sp, modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("مأموریت‌های تو", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
                            Text(current?.coins?.let { "🪙 " + persianDisplay(coinBalanceText(it)) + " سکه داری" } ?: "یاد بگیر، بساز و جایزه‌ات را بگیر!", style = MaterialTheme.typography.bodySmall, color = Color(0xFF786647))
                        }
                        TextButton(enabled = !loading && busy == null, onClick = { scope.launch { refresh() } }) { Text("تازه‌سازی") }
                        TextButton(onClick = onClose) { Text("بستن ×") }
                    }
                    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    AnimatedVisibility(celebration != null) {
                        Surface(color = Color(0xFFFFE9A3), shape = RoundedCornerShape(12.dp)) {
                            Text(celebration.orEmpty(), Modifier.fillMaxWidth().padding(10.dp), color = Color(0xFF775300), fontWeight = FontWeight.Bold)
                        }
                    }
                    when {
                        board == null -> if (!loading) Text("فهرست مأموریت‌ها هنوز دریافت نشده است.")
                        !board.available -> Text("مأموریت‌ها هنوز در سرور آماده نشده‌اند.")
                        else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 8.dp)) {
                            items(board.gridEntries.chunked(2), key = { pair -> pair.joinToString { it.key } }) { pair ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    pair.forEach { entry ->
                                        if (entry is MissionGridEntry.Ad) {
                                            RewardedMissionCard(entry.offer, adState,
                                                enabled = busy == null && !loading && current?.enabled == true,
                                                modifier = Modifier.weight(1f)) {
                                                busy = "rewarded_ad"; error = null
                                                scope.launch {
                                                    var reserved: RewardedAdTicket? = null
                                                    var handedOff = false
                                                    try {
                                                        reserved = repository.reserveRewardedAd(profile.uuid, java.util.UUID.randomUUID().toString())
                                                        RewardedAdManager.prepareTicket(context, entry.offer, reserved)
                                                        adTicket = reserved; handedOff = true
                                                    } catch (_: kotlinx.coroutines.TimeoutCancellationException) { error = "فعلاً ویدیویی آماده نیست؛ کمی بعد تلاش کن." }
                                                    catch (e: CancellationException) { throw e }
                                                    catch (_: Exception) { error = "شروع تبلیغ تأیید نشد؛ فهرست را تازه کن و سهمیه یا اینترنت را بررسی کن." }
                                                    finally {
                                                        if (!handedOff) reserved?.let { ticket ->
                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                                                                runCatching { repository.finishRewardedAd(profile.uuid, ticket.uuid, false) }
                                                            }
                                                        }
                                                        busy = null
                                                    }
                                                }
                                            }
                                        } else if (entry is MissionGridEntry.Task) {
                                            val item = entry.mission
                                            MissionCard(item, busy == item.id, busy == null && !loading && current?.enabled == true,
                                                Modifier.weight(1f), onInfo = { help = item }, onReview = {
                                                    busy = item.id; error = null
                                                    if (!MissionActions.openStore(context, BuildConfig.FLAVOR)) {
                                                        error = "فروشگاه باز نشد؛ نصب بودن فروشگاه یا مرورگر را بررسی کن."; busy = null
                                                    } else scope.launch {
                                                        try { repository.startStoreReview(profile.uuid) }
                                                        catch (e: CancellationException) { throw e }
                                                        catch (_: Exception) { error = "شروع مأموریت ثبت نشد؛ با اینترنت متصل دوباره تلاش کن." }
                                                        finally { busy = null }
                                                    }
                                                }) {
                                                busy = item.id; error = null; celebration = null
                                                scope.launch {
                                                    try {
                                                        val awarded = repository.claimMission(profile.uuid, item)
                                                        if (awarded) {
                                                            celebration = "🪙 " + persianDisplay(item.rewardCoins.toString()) + " سکه گرفتی؛ آفرین برنامه‌نویس!"
                                                            MissionRewardSound.play()
                                                        }
                                                    } catch (e: CancellationException) { throw e }
                                                    catch (_: Exception) { error = "دریافت تأیید نشد؛ اینترنت را بررسی و فهرست را تازه کن. جایزهٔ دریافت‌نشده محفوظ است." }
                                                    finally { busy = null }
                                                }
                                            }
                                        }
                                    }
                                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    help?.let { item -> MissionHelpDialog(item) { help = null } }
    adTicket?.let { ticket ->
        RewardedAdConfirmation(ticket, onCancel = {
            adTicket = null
            scope.launch {
                try { repository.finishRewardedAd(profile.uuid, ticket.uuid, false) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { }
            }
        }, onWatch = {
            adTicket = null
            RewardedAdManager.show(profile.uuid, ticket, repository)
        })
    }
}

@Composable private fun RewardedMissionCard(offer: RewardedAdOffer, state: RewardedAdManager.State, enabled: Boolean,
    modifier: Modifier = Modifier, onWatch: () -> Unit) {
    val waitMs by produceState(initialValue = rewardedAdWaitMs(offer), key1 = offer) {
        val initial = rewardedAdWaitMs(offer)
        val start = android.os.SystemClock.elapsedRealtime()
        value = initial
        while (value > 0 && offer.remaining == 0) {
            delay(1_000)
            value = (initial - (android.os.SystemClock.elapsedRealtime() - start)).coerceAtLeast(0)
        }
    }
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color(0xFFF0E8FF), border = BorderStroke(1.dp, Color(0xFFD9C7F7))) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🎬", fontSize = 24.sp)
                Text("تماشای تبلیغ", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Color(0xFF5D3D8C), style = MaterialTheme.typography.titleSmall)
                Surface(color = Color(0xFFFFEDAF), shape = RoundedCornerShape(9.dp)) {
                    Text("🪙 " + persianDisplay(offer.rewardCoins.toString()), Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        color = Color(0xFF775300), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (offer.remaining == 0) Text(persianDisplay(
                if (offer.retryAtMs == null) "زمان نوبت بعد پس از اتصال مشخص می‌شود" else rewardedAdWaitText(waitMs)),
                color = Color(0xFF684B93), style = MaterialTheme.typography.labelMedium)
            Button(onClick = onWatch, enabled = enabled && !state.busy && offer.remaining > 0, modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8050D7)), shape = RoundedCornerShape(11.dp)) {
                Text(if (state.busy) "در حال نمایش…" else if (!enabled && offer.remaining > 0) "لطفاً صبر کن…" else if (offer.remaining == 0) "بعداً" else "مشاهده ویدیو", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable private fun RewardedAdConfirmation(ticket: RewardedAdTicket, onCancel: () -> Unit, onWatch: () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 300.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFFFFFBEE), border = BorderStroke(2.dp, Color(0xFFE9D5A4))) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎁 جایزهٔ تماشای کامل", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onCancel) { Text("×") }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(persianDisplay("🪙 ${ticket.coins} سکه"), fontWeight = FontWeight.Black, color = Color(0xFF8050D7), fontSize = 22.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onWatch, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8050D7))) { Text("مشاهده") }
                        OutlinedButton(onClick = onCancel) { Text("الان نه") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissionCard(item: MissionItem, busy: Boolean, enabled: Boolean, modifier: Modifier = Modifier,
    onInfo: () -> Unit = {}, onReview: () -> Unit = {}, onClaim: () -> Unit) {
    val claimed = item.status == "claimed"
    val icon = when (item.id) { "tour_complete" -> "🎞️"; "showcase_10" -> "🏛️"; "avatars_8" -> "🛠️"; "daily" -> "☀️"; "first_code" -> "🚀"; "coding_30", "coding_5h", "coding_15h" -> "🧩"; "coding_week", "coding_month" -> "🔥"; "store_review" -> "⭐"; "saved_project" -> "💾"; "details" -> "🎒"; else -> "🎬" }
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = if (claimed) Color(0xFFF0F6EE) else Color.White,
        border = BorderStroke(if (item.ready) 2.dp else 1.dp, if (item.ready) Color(0xFFE7B442) else Color(0xFFE5DFD2)), shadowElevation = 2.dp) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(icon, fontSize = 24.sp)
                Text(item.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                IconButton(onClick = onInfo, modifier = Modifier.semantics { contentDescription = "توضیح مأموریت ${item.title}" }) {
                    Surface(shape = androidx.compose.foundation.shape.CircleShape, color = Color(0xFFF1EAD7), border = BorderStroke(1.dp, Color(0xFFD7C495))) {
                        Box(Modifier.size(25.dp), contentAlignment = Alignment.Center) { Text("؟", fontWeight = FontWeight.Bold, color = Color(0xFF806025)) }
                    }
                }
                Surface(color = Color(0xFFFFEDAF), shape = RoundedCornerShape(9.dp)) {
                    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text("🪙 " + persianDisplay(item.rewardCoins.toString()), color = Color(0xFF775300), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            GameMissionProgress(item.fraction)
            if (item.repeatable) Text(persianDisplay(
                (if (item.achievementUnlocked) "🏆 دستاورد ثبت شده · ${item.completedCount} بار تکمیل" else "🔁 چالش تکرارشونده") +
                if (item.pendingRewards > 0) " · ${item.pendingRewards} جایزهٔ آماده" else ""), style = MaterialTheme.typography.labelSmall, color = Color(0xFF956308))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val caption = when (item.metric) {
                        "watch_ms", "coding_ms" -> "${item.progress / 60000} از ${item.target / 60000} دقیقه"
                        "details" -> "${item.progress} از ${item.target} گزینه"
                        "coding_days" -> item.dayCaption
                        "lessons" -> "${item.progress} از ${item.target} قسمت"
                        "avatars" -> "${item.progress} از ${item.target} آیکون خریداری‌شده"
                        "showcase_visits" -> "${item.progress} از ${item.target} تالار متفاوت"
                        "tour_videos" -> "${item.progress} از ${item.target} ویدیوی راهنما"
                        "review_ms" -> if (item.action == "review") "یک نظر و امتیاز برای ما" else if (claimed) "ممنون از همراهی‌ات" else "جایزه پس از ۱۰ ثانیه"
                        else -> if (item.fraction >= 1f) "تکمیل شد" else "منتظر اولین قدم تو"
                    }
                    Text(persianDisplay(caption), style = MaterialTheme.typography.bodySmall)
                    Text(if (item.repeatable) "پیشرفت چرخهٔ جدید" else if (item.id == "daily") "روزانه · تا نیمه‌شب" else "یک‌بار برای این حساب", style = MaterialTheme.typography.labelSmall, color = Color(0xFF887A62))
                }
                val reviewAction = item.action == "review"
                val storeAvailable = BuildConfig.FLAVOR == "bazaar" || BuildConfig.FLAVOR == "myket"
                Button(onClick = if (reviewAction) onReview else onClaim, enabled = (item.ready || (reviewAction && storeAvailable)) && enabled && !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF40852C), disabledContainerColor = Color(0xFFEDF0E8), disabledContentColor = Color(0xFF607255)),
                    shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(if (busy) "لطفاً صبر کن…" else if (claimed) "دریافت شد ✓" else if (reviewAction) { if (storeAvailable) "ثبت نظر ⭐" else "نسخهٔ فروشگاهی" } else if (item.ready) "دریافت جایزه" else "در حال انجام", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun MissionHelpDialog(item: MissionItem, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 600.dp).fillMaxWidth(.90f), shape = RoundedCornerShape(24.dp), color = Color(0xFFFFFBEE),
                border = BorderStroke(2.dp, Color(0xFFE9D5A4)), shadowElevation = 12.dp) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("✨ " + item.title, Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = onClose) { Text("بستن ×") }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                        Text(persianDisplay(item.description.ifBlank { "برای تکمیل این مأموریت، هدف نوشته‌شده روی کارت را انجام بده و جایزه‌ات را دریافت کن." }), style = MaterialTheme.typography.bodyMedium)
                    }
                    Surface(color = Color(0xFFFFE7A0), shape = RoundedCornerShape(12.dp)) {
                        Text("🪙 جایزه: " + persianDisplay(item.rewardCoins.toString()) + " سکه", Modifier.fillMaxWidth().padding(12.dp), fontWeight = FontWeight.Bold, color = Color(0xFF775300))
                    }
                }
            }
        }
    }
}

@Composable
private fun GameMissionProgress(progress: Float) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(650), label = "missionProgress")
    Box(Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(7.dp))
        .background(Color(0xFF483E32)).padding(3.dp)
        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f) }) {
        if (animated > 0) Box(Modifier.fillMaxHeight().fillMaxWidth(animated).clip(RoundedCornerShape(4.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFFC6EE65), Color(0xFF81BF32), Color(0xFF4D8E22))))) {
            Box(Modifier.fillMaxWidth().fillMaxHeight(.35f).background(Color.White.copy(alpha = .25f)))
        }
    }
}

@Preview(name = "Missions Landscape", widthDp = 900, heightDp = 560, locale = "fa", showBackground = true)
@Composable
private fun MissionsPreview() {
    LearnerDialogTheme {
        Column(Modifier.background(Color(0xFFFFF9EC)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RewardedMissionCard(RewardedAdOffer("preview", "preview", 150, 150, 2, 2, 120, null, 0),
                    RewardedAdManager.State(loaded = true), true, Modifier.weight(1f)) { }
                MissionCard(MissionItem("daily", "سلام، برنامه‌نویس!", "visit", 1, 1, 10, "today", "ready", rewardCoins = 10),
                    false, true, Modifier.weight(1f)) { }
            }
            listOf("active" to 12L, "ready" to 30L, "claimed" to 30L).forEach { (status, minutes) ->
                MissionCard(MissionItem("coding_30", "نیم ساعت خلق کردن", "coding_ms", 1800000, minutes * 60000, 50, "once", status), false, true) { }
            }
        }
    }
}
