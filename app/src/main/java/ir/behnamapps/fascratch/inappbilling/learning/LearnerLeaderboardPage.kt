package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.domain.CourseFailure
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun LearnerLeaderboardPage(viewer: String, repository: LearningRepository, onClose: () -> Unit) {
    val profiles by repository.profiles.collectAsState()
    var board by remember(viewer) { mutableStateOf<LeaderboardBoard?>(null) }
    var loading by remember(viewer) { mutableStateOf(true) }
    var error by remember(viewer) { mutableStateOf<String?>(null) }
    var selected by remember(viewer) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    var initiallyPositioned by remember(viewer) { mutableStateOf(false) }
    suspend fun refresh() {
        loading = true; error = null
        try { board = repository.leaderboard(viewer) }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "رتبه‌ها دریافت نشد؛ اینترنت را بررسی کن و دوباره تلاش کن." }
        finally { loading = false }
    }
    LaunchedEffect(viewer) { refresh() }
    LaunchedEffect(profiles[LearningIdentity.KEY]?.uuid) { if (profiles[LearningIdentity.KEY]?.uuid != viewer) onClose() }
    LaunchedEffect(board) {
        val data = board ?: return@LaunchedEffect
        if (!initiallyPositioned && data.nearby.isNotEmpty()) {
            initiallyPositioned = true
            // Top 50 are immediately above this section; scrolling upward reveals them naturally.
            val ownIndex = data.nearby.indexOfFirst { it.uuid == viewer }.coerceAtLeast(0)
            list.scrollToItem(nearbyScrollIndex(data.top.size, data.nearby.size, ownIndex))
        }
    }
    LeaderboardWindow(onClose) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("🏆 رتبه‌های برنامه‌نویس‌ها", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF384885), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(board?.let { "${persianDisplay(it.total.toString())} برنامه‌نویس در رقابت" + (it.viewerRank?.let { rank -> " · رتبهٔ تو ${persianDisplay(rank.toString())}" } ?: "") } ?: "یاد بگیر، بساز و بالاتر برو!", style = MaterialTheme.typography.labelSmall, color = Color(0xFF78819D), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                FilledTonalButton(enabled = board != null, onClick = { scope.launch { list.animateScrollToItem(0) } }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) { Text("۵۰ نفر برتر", style = MaterialTheme.typography.labelMedium) }
                FilledTonalButton(enabled = board?.nearby?.isNotEmpty() == true, onClick = {
                    board?.let { data -> scope.launch { list.animateScrollToItem(nearbyScrollIndex(data.top.size, data.nearby.size, data.nearby.indexOfFirst { it.uuid == viewer })) } }
                }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)) { Text("اطراف رتبهٔ من", style = MaterialTheme.typography.labelMedium) }
                IconButton(onClick = onClose) { Text("×", fontSize = 26.sp) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            board?.let { data ->
                LazyColumn(state = list, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
                    item(key = "top-header") { LeaderboardSection("🌟 پنجاه نفر برتر", "نام هر نفر را لمس کن و ویترین افتخاراتش را ببین") }
                    items(data.top, key = { "top-${it.uuid}" }) { learner -> RankedLearnerRow(learner, learner.uuid == viewer) { selected = learner.uuid } }
                    item(key = "near-header") { LeaderboardSection("📍 اطراف رتبهٔ تو", "ده نفر بالاتر، خودت و ده نفر پایین‌تر") }
                    if (data.nearby.isEmpty()) item(key = "no-rank") {
                        Text("حساب تو فعلاً در فهرست عمومی نیست؛ ثبت‌نام، نام نمایشی و وضعیت محدودیت نام را در پروفایلت بررسی کن. برترین‌ها را می‌توانی ببینی.", Modifier.padding(15.dp), color = Color(0xFF78819D))
                    }
                    items(data.nearby, key = { "near-${it.uuid}" }) { learner -> RankedLearnerRow(learner, learner.uuid == viewer) { selected = learner.uuid } }
                    if (data.top.isEmpty()) item(key = "empty") { Text("هنوز برنامه‌نویسی در فهرست رتبه‌ها نیست؛ اولین نفر باش!", Modifier.padding(16.dp)) }
                }
            }
        }
    }
    selected?.let { target -> LearnerShowcasePage(viewer, target, repository) { selected = null } }
}

@Composable private fun LeaderboardWindow(onClose: () -> Unit, content: @Composable () -> Unit) {
    LearnerDialogTheme {
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
            LearnerDialogWindowBounds()
            Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
                // Match the established profile/account dialogs: centered, 92% of safe width,
                // capped at 960dp, rather than filling the edge-to-edge Activity's width.
                Surface(Modifier.learnerDialogPanelBounds(), shape = RoundedCornerShape(24.dp), color = Color(0xFFF8F7FD), border = BorderStroke(2.dp, Color.White)) { content() }
            }
        }
    }
}

@Composable private fun LeaderboardSection(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 3.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF475588), fontSize = 15.sp)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF7D86A0))
    }
}

@Composable private fun RankedLearnerRow(learner: RankedLearner, own: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), shape = RoundedCornerShape(16.dp),
        color = if (own) Color(0xFFE8F5EE) else Color.White, border = BorderStroke(if (own) 2.dp else 1.dp, if (own) Color(0xFF73B393) else Color(0xFFE2E5F1))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (learner.rank <= 3) Color(0xFFFFE8A6) else Color(0xFFF0EFFA)) {
                Box(Modifier.widthIn(min = 42.dp).padding(6.dp), contentAlignment = Alignment.Center) {
                    Text((when (learner.rank) { 1 -> "🥇 "; 2 -> "🥈 "; 3 -> "🥉 "; else -> "" }) + persianDisplay(learner.rank.toString()), fontWeight = FontWeight.Black, color = Color(0xFF82611B))
                }
            }
            LearnerLevelBadge(learner.level, Modifier.size(48.dp), learner.avatarId, learner.imageUrl, learner.imageHash)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val availableWidth = maxWidth
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(learner.name + if (own) " · تو" else "", modifier = if (learner.bio.isNotBlank() && availableWidth > 220.dp) Modifier.widthIn(max = availableWidth * .55f) else Modifier.weight(1f),
                            fontWeight = FontWeight.Bold, color = Color(0xFF354772), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (learner.bio.isNotBlank() && availableWidth > 220.dp) Text("· " + learner.bio, Modifier.weight(1f), color = Color(0xFF77839B),
                            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text("سطح ${persianDisplay(learner.level.toString())} · ${persianDisplay(coinBalanceText(learner.xp))} XP", color = Color(0xFF7E89A4), style = MaterialTheme.typography.labelSmall)
            }
            Text("افتخارات ‹", color = Color(0xFF8770BC), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable private fun LearnerShowcasePage(viewer: String, target: String, repository: LearningRepository, onClose: () -> Unit) {
    var data by remember(target) { mutableStateOf<LearnerShowcase?>(null) }
    var loading by remember(target) { mutableStateOf(true) }
    var error by remember(target) { mutableStateOf<String?>(null) }
    val profiles by repository.profiles.collectAsState()
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true; error = null
        try { data = repository.showcase(viewer, target) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { data = null; error = if ((e as? CourseFailure)?.status == 404) "این پروفایل دیگر در رتبه‌بندی قابل نمایش نیست." else "افتخارات دریافت نشد؛ اتصال را بررسی کن." }
        finally { loading = false }
    }
    LaunchedEffect(target) { refresh() }
    LaunchedEffect(profiles[LearningIdentity.KEY]?.uuid) { if (profiles[LearningIdentity.KEY]?.uuid != viewer) onClose() }
    ShowcaseHallWindow(onClose) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("✦ تالار افتخارات", Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 20.sp, color = Color(0xFFFFD998), maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = onClose) { Text("بازگشت ›", color = Color(0xFFE4D7FF)) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFFE6B66A), trackColor = Color(0xFF2D2942))
            error?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message, Modifier.weight(1f), color = Color(0xFFFFBDB9))
                    TextButton(onClick = { scope.launch { refresh() } }) { Text("تلاش دوباره", color = Color(0xFFFFD998)) }
                }
            }
            data?.let { ShowcaseHallContent(it, Modifier.weight(1f)) }
        }
    }
}
