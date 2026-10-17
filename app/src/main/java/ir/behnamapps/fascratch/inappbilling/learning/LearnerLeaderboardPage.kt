package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
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
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("🏆 رتبه‌های برنامه‌نویس‌ها", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Color(0xFF384885))
                    Text(board?.let { "${persianDisplay(it.total.toString())} برنامه‌نویس در رقابت" } ?: "یاد بگیر، بساز و بالاتر برو!", style = MaterialTheme.typography.bodySmall, color = Color(0xFF78819D))
                }
                TextButton(enabled = !loading, onClick = { scope.launch { refresh() } }) { Text("تازه‌سازی") }
                IconButton(onClick = onClose) { Text("×", fontSize = 26.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(enabled = board != null, onClick = { scope.launch { list.animateScrollToItem(0) } }) { Text("۵۰ نفر برتر") }
                FilledTonalButton(enabled = board?.nearby?.isNotEmpty() == true, onClick = {
                    board?.let { data -> scope.launch { list.animateScrollToItem(nearbyScrollIndex(data.top.size, data.nearby.size, data.nearby.indexOfFirst { it.uuid == viewer })) } }
                }) { Text("اطراف رتبهٔ من") }
                board?.viewerRank?.let { Text("رتبهٔ تو: ${persianDisplay(it.toString())}", Modifier.align(Alignment.CenterVertically), color = Color(0xFF4A7E62), fontWeight = FontWeight.Bold) }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            board?.let { data ->
                LazyColumn(state = list, modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp), contentPadding = PaddingValues(bottom = 14.dp)) {
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
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            LearnerDialogWindowBounds()
            Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
                Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp), color = Color(0xFFF8F7FD), border = BorderStroke(2.dp, Color.White)) { content() }
            }
        }
    }
}

@Composable private fun LeaderboardSection(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 6.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF475588), fontSize = 17.sp)
        Text(subtitle, style = MaterialTheme.typography.labelSmall, color = Color(0xFF7D86A0))
    }
}

@Composable private fun RankedLearnerRow(learner: RankedLearner, own: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick), shape = RoundedCornerShape(16.dp),
        color = if (own) Color(0xFFE8F5EE) else Color.White, border = BorderStroke(if (own) 2.dp else 1.dp, if (own) Color(0xFF73B393) else Color(0xFFE2E5F1))) {
        Row(Modifier.padding(horizontal = 13.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Surface(shape = RoundedCornerShape(12.dp), color = if (learner.rank <= 3) Color(0xFFFFE8A6) else Color(0xFFF0EFFA)) {
                Box(Modifier.widthIn(min = 50.dp).padding(8.dp), contentAlignment = Alignment.Center) {
                    Text((when (learner.rank) { 1 -> "🥇 "; 2 -> "🥈 "; 3 -> "🥉 "; else -> "" }) + persianDisplay(learner.rank.toString()), fontWeight = FontWeight.Black, color = Color(0xFF82611B))
                }
            }
            LearnerLevelBadge(learner.level, Modifier.size(61.dp), learner.avatarId, learner.imageUrl, learner.imageHash)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(learner.name + if (own) " · تو" else "", fontWeight = FontWeight.Bold, color = Color(0xFF354772), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    LeaderboardWindow(onClose) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✨ ویترین افتخارات", Modifier.weight(1f), fontWeight = FontWeight.Black, fontSize = 21.sp, color = Color(0xFF534580))
                TextButton(enabled = !loading, onClick = { scope.launch { refresh() } }) { Text("تازه‌سازی") }
                TextButton(onClick = onClose) { Text("بازگشت ›") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            data?.let { ShowcaseContent(it, Modifier.weight(1f)) }
        }
    }
}

@Composable private fun ShowcaseContent(data: LearnerShowcase, modifier: Modifier = Modifier) {
    val person = data.learner
    LazyVerticalGrid(GridCells.Adaptive(125.dp), modifier = modifier, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(shape = RoundedCornerShape(20.dp), color = Color(0xFFEDE9FA), border = BorderStroke(1.dp, Color(0xFFD7CDEB))) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    LearnerLevelBadge(person.level, Modifier.size(98.dp), person.avatarId, person.imageUrl, person.imageHash)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(person.name, color = Color(0xFF4E3E78), fontSize = 22.sp, fontWeight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(persianDisplay(membershipDuration(data.memberDays)), color = Color(0xFF837099), style = MaterialTheme.typography.bodySmall)
                        Text("🏆 رتبه ${persianDisplay(person.rank.toString())}   ·   سطح ${persianDisplay(person.level.toString())}   ·   ${persianDisplay(coinBalanceText(person.xp))} XP", color = Color(0xFF5F588E), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { LeaderboardSection("🖼 قاب آیکون‌های افتخار", "آیکون‌های خریداری‌شدهٔ این برنامه‌نویس") }
        if (data.avatars.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("این قاب هنوز منتظر اولین آیکون است.", color = Color(0xFF8C839F), style = MaterialTheme.typography.bodySmall) }
        items(data.avatars, key = { "avatar-${it.id}" }) { item ->
            Surface(shape = RoundedCornerShape(17.dp), color = Color.White, border = BorderStroke(if (item.equipped) 2.dp else 1.dp, if (item.equipped) Color(0xFFD8AE44) else Color(0xFFE6DFEF))) {
                Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    LearnerLevelBadge(person.level, Modifier.size(87.dp), item.id, item.url, item.hash)
                    Text(item.title, fontWeight = FontWeight.Bold, color = Color(0xFF695287), maxLines = 2, style = MaterialTheme.typography.labelMedium)
                    if (item.equipped) Text("ظاهر فعال ✓", color = Color(0xFF9C7623), style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { LeaderboardSection("🏆 تالار دستاوردها", "جام‌هایی که جایزه‌شان دریافت شده است") }
        if (data.trophies.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { Text("هنوز جامی در این تالار قرار نگرفته است.", color = Color(0xFF8C839F), style = MaterialTheme.typography.bodySmall) }
        items(data.trophies, key = { "trophy-${it.id}" }) { trophy ->
            Surface(shape = RoundedCornerShape(17.dp), color = Color(0xFFFFF7DB), border = BorderStroke(1.dp, Color(0xFFE5D09A))) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(65.dp).background(Brush.radialGradient(listOf(Color(0xFFFFE39A), Color(0xFFFFF7DB)))), contentAlignment = Alignment.Center) { Text("🏆", fontSize = 41.sp) }
                    Text(trophy.title, color = Color(0xFF886320), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium, maxLines = 3)
                }
            }
        }
    }
}

@Preview(name = "ویترین افتخارات - افقی", widthDp = 850, heightDp = 430, showBackground = true, locale = "fa")
@Composable private fun LearnerShowcasePreview() {
    LearnerDialogTheme { ShowcaseContent(LearnerShowcase(RankedLearner("demo", "گربهٔ خلاق", 12, 15, 3200.0, "junior", null, null), 72,
        listOf(ShowcaseAvatar("junior", "جونیور", true, null, null), ShowcaseAvatar("wizard", "جادوگر کد", false, null, null)),
        listOf(ShowcaseTrophy("first_code", "اولین قدم برنامه‌نویسی"), ShowcaseTrophy("coding_week", "یک هفته برنامه‌نویسی")))) }
}
