@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class PublicProfileSection(val title: String, val minimumLevel: Int) {
    BIO("افزودن متن پروفایل", 16), SOCIALS("آدرس شبکه‌های اجتماعی من", 21)
}

@Composable internal fun PublicProfileEntryButtons(profile: LearnerProfile, enabled: Boolean, onOpen: (PublicProfileSection) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PublicProfileSection.entries.forEach { section ->
            val unlocked = profile.level >= section.minimumLevel && if (section == PublicProfileSection.BIO) profile.publicProfile.canEditBio else profile.publicProfile.canEditSocials
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(persianDisplay(if (unlocked) "این بخش از سطح ${section.minimumLevel} فعال است" else "برای فعال شدن این دکمه باید به سطح ${section.minimumLevel} برسی"),
                    color = Color(0xFF71629D), style = MaterialTheme.typography.labelSmall)
                FilledTonalButton(onClick = { onOpen(section) }, enabled = enabled && unlocked, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
                    Text((if (section == PublicProfileSection.BIO) "✦ " else "↗ ") + section.title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
                Text(profile.publicProfile.editCostCoins?.let { "هر ثبت یا ویرایش: " + persianDisplay(it.toString()) + " سکه" } ?: "برای ثبت، سرور باید به‌روز شود",
                    color = Color(0xFF8A6B35), style = MaterialTheme.typography.labelSmall)
                val hasExisting = if (section == PublicProfileSection.BIO) profile.publicProfile.bio.isNotEmpty() else profile.publicProfile.links.isNotEmpty()
                if (!unlocked && hasExisting) TextButton(onClick = { onOpen(section) }, enabled = enabled) { Text("مشاهده / حذف اطلاعات قبلی", style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable internal fun LearnerPublicProfileDialog(profile: LearnerProfile, repository: LearningRepository, section: PublicProfileSection, onClose: () -> Unit) {
    LearnerPublicProfileFormDialog(profile, section, onClose) { version, bio, links, cost -> repository.savePublicProfile(profile.uuid, version, bio, links, cost) }
}

@Composable private fun LearnerPublicProfileFormDialog(profile: LearnerProfile, section: PublicProfileSection, onClose: () -> Unit,
    onSave: suspend (Int, String, Map<String, String>, Int) -> Unit) {
    val details = remember(profile.uuid, section) { profile.publicProfile }
    var bio by remember(profile.uuid, section) { mutableStateOf(details.bio) }
    var links by remember(profile.uuid, section) { mutableStateOf(details.links) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmationCost by remember { mutableStateOf<Int?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val canBio = profile.publicProfile.canEditBio && profile.level > 15
    val canSocial = profile.publicProfile.canEditSocials && profile.level > 20
    val linksValid = links.all { (network, value) -> PublicProfilePolicy.normalizeInput(network, value) != null }
    val normalizedLinks = links.mapNotNull { (network, value) -> PublicProfilePolicy.normalizeInput(network, value)?.takeIf { it.isNotEmpty() }?.let { network to it } }.toMap()
    val changed = bio.trim() != details.bio || normalizedLinks != details.links
    val cost = PublicProfilePolicy.editCost(details.bio, details.links, bio, links, details.editCostCoins ?: 2000)
    val enoughCoins = cost == 0 || profile.coins?.let { it >= cost } == true
    fun submit(confirmedCost: Int) {
        if (busy) return
        focus.clearFocus(); busy = true; error = null
        scope.launch {
            try { onSave(details.version, bio, links, confirmedCost); onClose() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { error = failure.message ?: "ذخیره نشد؛ دوباره تلاش کن." }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 700.dp).learnerDialogPanelBounds(), shape = RoundedCornerShape(24.dp), color = Color(0xFFFFFCF6), border = BorderStroke(2.dp, Color.White)) {
                Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFFFF8E7), Color(0xFFF4F1FF)))).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(section.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color(0xFF414B80))
                            Text(persianDisplay("از سطح ${section.minimumLevel} · موجودی: ${profile.coins?.toInt() ?: 0} سکه"), style = MaterialTheme.typography.bodySmall, color = Color(0xFF7A7595))
                        }
                        IconButton(onClick = onClose, enabled = !busy) { Text("×", style = MaterialTheme.typography.headlineMedium) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = Color(0xFFEAE8FA), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(when (details.status) { "pending" -> "◷ در انتظار بررسی مدیر"; "approved" -> "✓ اطلاعات تأیید شده"; "rejected" -> "اطلاعات نیاز به اصلاح دارد"; else -> "پروفایل عمومی تو" },
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF5A4E91))
                                Text("پس از تأیید مدیر عمومی می‌شود؛ شماره یا اطلاعات خصوصی ننویس. حذف اطلاعات رایگان است.", style = MaterialTheme.typography.bodySmall)
                                details.reason?.let { Text(it, color = Color(0xFFAE415A), style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        if (section == PublicProfileSection.BIO) {
                            OutlinedTextField(value = bio, onValueChange = { if (PublicProfilePolicy.length(it) <= 200) bio = it },
                                modifier = Modifier.fillMaxWidth(), enabled = canBio && !busy, minLines = 3, maxLines = 4,
                                placeholder = { Text("مثلاً: بازی می‌سازم و عاشق حل معما هستم!") },
                                supportingText = { Text(if (canBio) persianDisplay("${PublicProfilePolicy.length(bio)} از ۲۰۰") else "از سطح ۱۶ فعال می‌شود") }, shape = RoundedCornerShape(14.dp))
                            if (bio.isNotEmpty()) TextButton(enabled = !busy, onClick = { bio = "" }) { Text("پاک کردن متن پروفایل") }
                        } else {
                            if (!canSocial) Text("🔒 از سطح ۲۱ فعال می‌شود", color = Color(0xFF88729D), style = MaterialTheme.typography.bodySmall)
                            PublicProfilePolicy.networks.entries.chunked(2).forEach { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    row.forEach { (key, label) ->
                                        Column(Modifier.weight(1f)) {
                                            OutlinedTextField(value = links[key].orEmpty(), onValueChange = { if (it.length <= 200) links = links + (key to it) },
                                                label = { Text(label) }, placeholder = { Text("@username") }, singleLine = true,
                                                isError = PublicProfilePolicy.normalizeInput(key, links[key].orEmpty()) == null,
                                                enabled = canSocial && !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                                            if (!links[key].isNullOrEmpty()) TextButton(enabled = !busy, onClick = { links = links - key }) { Text("حذف لینک") }
                                        }
                                    }
                                }
                            }
                            if (!linksValid) Text("آیدی یا لینک عمومی معتبر همان شبکه را وارد کن.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                        }
                        if (changed && cost > 0 && !enoughCoins) Text("برای ثبت این تغییرات سکهٔ کافی نداری.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        if (changed && cost > 0 && details.editCostCoins == null) Text("برای ثبت، سرور باید به‌روز شود.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(if (changed && cost == 0) "حذف اطلاعات بدون هزینه است" else "هزینهٔ ثبت: " + persianDisplay(cost.toString()) + " سکه", Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A6B35))
                        Button(enabled = !busy && changed && linksValid && enoughCoins && (cost == 0 || details.editCostCoins != null), shape = RoundedCornerShape(13.dp), onClick = {
                            if (cost > 0) confirmationCost = cost else submit(0)
                        }) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(if (cost > 0) "ثبت · " + persianDisplay(cost.toString()) + " سکه" else "ذخیره تغییرات")
                        }
                    }
                }
            }
        }
    }
    confirmationCost?.let { price ->
        AlertDialog(onDismissRequest = { confirmationCost = null }, title = { Text("تأیید ثبت اطلاعات") },
            text = { Text(persianDisplay("برای ثبت این تغییرات $price سکه از حسابت کم می‌شود. انتشار پس از تأیید مدیر است. ادامه می‌دهی؟")) },
            confirmButton = { TextButton(onClick = { confirmationCost = null; submit(price) }) { Text("تأیید و ثبت") } },
            dismissButton = { TextButton(onClick = { confirmationCost = null }) { Text("بی‌خیال") } })
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "متن پروفایل · افقی", widthDp = 900, heightDp = 420, locale = "fa")
@androidx.compose.ui.tooling.preview.Preview(name = "متن پروفایل · کوچک", widthDp = 640, heightDp = 320, locale = "fa")
@Composable private fun PublicProfileFormPreview() {
    LearnerDialogTheme { LearnerPublicProfileFormDialog(publicProfilePreview(), PublicProfileSection.BIO, {}) { _, _, _, _ -> } }
}

@androidx.compose.ui.tooling.preview.Preview(name = "شبکه‌های اجتماعی · افقی", widthDp = 900, heightDp = 420, locale = "fa")
@Composable private fun PublicProfileSocialPreview() {
    LearnerDialogTheme { LearnerPublicProfileFormDialog(publicProfilePreview(), PublicProfileSection.SOCIALS, {}) { _, _, _, _ -> } }
}

private fun publicProfilePreview() = LearnerProfile("preview", "برنامه‌نویس خلاق", 21, 0, 0.0, 100.0, 3, true, coins = 5000.0,
    publicProfile = PublicProfileDetails(true, 1, true, true, "pending", bio = "من بازی‌های ماجراجویی می‌سازم! 😺", editCostCoins = 2000))

@Composable internal fun ShowcasePublicProfile(data: LearnerShowcase) {
    val context = LocalContext.current
    var error by remember(data.learner.uuid) { mutableStateOf(false) }
    if (data.learner.bio.isBlank() && data.socialLinks.isEmpty()) return
    Surface(shape = RoundedCornerShape(16.dp), color = Color(0xD9252238), border = BorderStroke(1.dp, Color(0x507A6555))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (data.learner.bio.isNotBlank()) Text(data.learner.bio, style = MaterialTheme.typography.bodySmall, color = Color(0xFFF2EDFF))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                data.socialLinks.forEach { (network, url) ->
                    val safe = PublicProfilePolicy.safeUrl(network, url)
                    if (safe != null) OutlinedButton(onClick = {
                        error = false
                        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safe)).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        catch (_: Exception) { error = true }
                    }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp)) {
                        Text(PublicProfilePolicy.networks[network].orEmpty() + " ↗", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFD99A))
                    }
                }
            }
            if (error) Text("باز کردن این لینک ممکن نشد؛ برنامه یا مرورگر را بررسی کن.", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFBDB9))
        }
    }
}
