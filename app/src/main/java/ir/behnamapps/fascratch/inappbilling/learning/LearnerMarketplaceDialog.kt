package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import java.util.UUID

@Composable internal fun LearnerMarketplaceDialog(profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    val state by repository.marketplace.collectAsState()
    val profiles by repository.profiles.collectAsState()
    val current = profiles[LearningIdentity.KEY]?.takeIf { it.uuid == profile.uuid }
    val catalog = state?.takeIf { it.uuid == profile.uuid }
    val scope = rememberCoroutineScope()
    var loading by remember(profile.uuid) { mutableStateOf(true) }
    var busy by remember(profile.uuid) { mutableStateOf(false) }
    var error by remember(profile.uuid) { mutableStateOf<String?>(null) }
    var notice by remember(profile.uuid) { mutableStateOf<String?>(null) }
    var confirmation by remember(profile.uuid) { mutableStateOf<AvatarItem?>(null) }
    // Reuse one operation ID on a network retry; ownership is independently idempotent server-side.
    val operations = remember(profile.uuid) { mutableMapOf<String, String>() }
    fun failure(e: Exception) {
        error = when ((e as? CourseFailure)?.status) {
            401 -> "نشست حساب تغییر کرده؛ بازارچه را ببند و دوباره وارد شو."
            409 -> "خرید تأیید نشد؛ موجودی را تازه کن و دوباره بررسی کن."
            else -> "ارتباط برقرار نشد؛ دوباره تلاش کن. نتیجهٔ خرید قبلی با تازه‌سازی مشخص می‌شود."
        }
    }
    suspend fun refresh() {
        loading = true; error = null
        try { repository.refreshMarketplace(profile.uuid) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { failure(e) }
        finally { loading = false }
    }
    fun equip(id: String) {
        if (busy || loading) return
        busy = true; error = null; notice = null
        scope.launch {
            try { repository.equipAvatar(profile.uuid, id); notice = "ظاهر جدیدت فعال شد ✨" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure(e) }
            finally { busy = false }
        }
    }
    LaunchedEffect(profile.uuid) { refresh() }
    LaunchedEffect(current?.uuid) { if (current == null) onClose() }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.learnerDialogPanelBounds(), shape = RoundedCornerShape(25.dp), color = Color(0xFFF6F2FF),
                border = BorderStroke(2.dp, Color(0xFFDDD0F6))) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("🛍 بازارچهٔ گربه‌ها", color = Color(0xFF4B347E), fontWeight = FontWeight.Black, fontSize = 20.sp)
                            Text("ظاهر دلخواهت را انتخاب کن", color = Color(0xFF7D6B98), style = MaterialTheme.typography.bodySmall)
                        }
                        Surface(color = Color(0xFFFFE7A0), shape = RoundedCornerShape(12.dp)) {
                            Text("🪙 " + persianDisplay(coinBalanceText(current?.coins ?: 0.0)), Modifier.padding(10.dp), fontWeight = FontWeight.Bold, color = Color(0xFF795500))
                        }
                        TextButton(enabled = !loading && !busy, onClick = { scope.launch { refresh() } }) { Text("تازه‌سازی") }
                        IconButton(enabled = !busy, onClick = onClose) { Text("×", fontSize = 25.sp) }
                    }
                    if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Color(0xFF9164DD))
                    error?.let { Text(it, color = Color(0xFFB83445), style = MaterialTheme.typography.bodySmall) }
                    notice?.let { Text(it, color = Color(0xFF267250), style = MaterialTheme.typography.bodySmall) }
                    when {
                        catalog == null -> if (!loading) Text("برای دیدن بازارچه به اینترنت متصل شو.")
                        !catalog.available -> Text("بازارچه هنوز در سرور آماده نشده است.")
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("آیکون‌های خریداری‌شده همیشه برای این حساب می‌مانند.", Modifier.weight(1f), color = Color(0xFF7D6B98), style = MaterialTheme.typography.labelSmall)
                                TextButton(enabled = !busy && !loading && current?.avatarId != "default", onClick = { equip("default") }) { Text("گربهٔ پیش‌فرض") }
                            }
                            LazyVerticalGrid(columns = GridCells.Adaptive(155.dp), modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 6.dp)) {
                                items(catalog.items, key = { it.id }) { item ->
                                    AvatarCard(item, current?.level ?: profile.level,
                                        enabled = !busy && !loading && current?.enabled == true && current.coins != null,
                                        affordable = (current?.coins ?: 0.0) >= item.price) {
                                        if (item.owned) equip(item.id) else confirmation = item
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    confirmation?.let { item ->
        AvatarBuyConfirmation(item, current?.level ?: profile.level, busy, onClose = { confirmation = null }) {
            if (busy || current?.uuid != profile.uuid) return@AvatarBuyConfirmation
            busy = true; error = null; notice = null
            val operation = operations.getOrPut(item.id) { UUID.randomUUID().toString() }
            scope.launch {
                try {
                    val bought = repository.buyAvatar(profile.uuid, item.id, operation)
                    notice = if (bought) "${item.title} مال تو شد! ✨" else "این آیکون قبلاً برای تو ثبت شده است."
                    confirmation = null
                    if (bought) launch { MissionRewardSound.play() }
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { confirmation = null; failure(e) }
                finally { busy = false }
            }
        }
    }
}

@Preview(name = "بازارچه - همه آیکون‌ها", widthDp = 900, heightDp = 450, showBackground = true, locale = "fa")
@Composable private fun AvatarMarketplacePreview() {
    val ids = listOf("junior", "explorer", "builder", "debugger", "designer", "roboticist", "architect", "wizard", "senior", "full_stack")
    val titles = listOf("جونیور", "کاوشگر", "سازنده", "باگ‌یاب", "طراح خلاق", "ربات‌ساز", "معمار کد", "جادوگر کد", "سینیور", "فول‌استک")
    val prices = listOf(100, 200, 350, 550, 800, 1100, 1500, 2100, 3000, 4000)
    LearnerDialogTheme {
        LazyVerticalGrid(GridCells.Fixed(5), contentPadding = PaddingValues(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(ids.indices.toList()) { i -> AvatarCard(AvatarItem(ids[i], titles[i], prices[i], "یک ظاهر تازه برای برنامه‌نویس خلاق", i == 0, i == 0), 12, true, i < 4) {} }
        }
    }
}

@Preview(name = "پروفایل - سکه و بازارچه", widthDp = 780, heightDp = 340, showBackground = true, locale = "fa")
@Composable private fun AvatarProfilePreview() {
    LearnerDialogTheme {
        Column(Modifier.padding(20.dp)) {
            LearnerProfilePanel(LearnerProfile("preview", "برنامه‌نویس خلاق", 12, 1_200_000, 150.0, 400.0, 7, true,
                nameSet = true, registered = true, coins = 1250.0, avatarId = "junior"), {}, {}, {}, {}, {})
        }
    }
}

@Preview(name = "تأیید خرید - افقی", widthDp = 740, heightDp = 360, showBackground = true, locale = "fa")
@Composable private fun AvatarBuyPreview() {
    LearnerDialogTheme { AvatarBuyConfirmation(AvatarItem("full_stack", "فول‌استک", 4000, "", false, false), 12, false, {}) {} }
}

@Composable private fun AvatarCard(item: AvatarItem, level: Int, enabled: Boolean, affordable: Boolean, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(19.dp), color = if (item.equipped) Color(0xFFEBF8EE) else Color.White,
        border = BorderStroke(if (item.equipped) 2.dp else 1.dp, if (item.equipped) Color(0xFF53A974) else Color(0xFFE0D7F0)), shadowElevation = 2.dp) {
        Column(Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            LearnerLevelBadge(level, Modifier.size(100.dp), item.id)
            Text(item.title, fontWeight = FontWeight.Bold, color = Color(0xFF4B347E), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(item.description, style = MaterialTheme.typography.labelSmall, color = Color(0xFF81748E), maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
            Text(if (item.owned) "✓ خریداری‌شده" else "🪙 ${persianDisplay(item.price.toString())} سکه", color = if (item.owned) Color(0xFF267250) else Color(0xFF916116), style = MaterialTheme.typography.labelMedium)
            Button(onClick = onClick, enabled = enabled && !item.equipped && (item.owned || affordable), modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8050D7)), shape = RoundedCornerShape(11.dp), contentPadding = PaddingValues(8.dp)) {
                Text(if (item.equipped) "فعال ✓" else if (item.owned) "انتخاب" else if (!affordable) "سکه کافی نیست" else "خرید", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable private fun AvatarBuyConfirmation(item: AvatarItem, level: Int, busy: Boolean, onClose: () -> Unit, onBuy: () -> Unit) {
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 490.dp).fillMaxWidth().heightIn(max = 310.dp), shape = RoundedCornerShape(24.dp), color = Color(0xFFFFFBF2)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LearnerLevelBadge(level, Modifier.size(110.dp), item.id)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${item.title} را می‌خوای؟", fontWeight = FontWeight.Bold, fontSize = 19.sp, color = Color(0xFF4B347E))
                            Text("با خرید، ${persianDisplay(item.price.toString())} سکه از موجودی‌ات کم می‌شود. امتیاز و سطحت تغییری نمی‌کند.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(enabled = !busy, onClick = onBuy, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8050D7))) { Text(if (busy) "در حال بررسی…" else "🪙 خرید آیکون") }
                        OutlinedButton(enabled = !busy, onClick = onClose) { Text("بی‌خیال") }
                    }
                }
            }
        }
    }
}
