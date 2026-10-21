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

@Composable internal fun LearnerPublicProfileDialog(profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    LearnerPublicProfileFormDialog(profile, onClose) { version, bio, links -> repository.savePublicProfile(profile.uuid, version, bio, links) }
}

@Composable private fun LearnerPublicProfileFormDialog(profile: LearnerProfile, onClose: () -> Unit,
    onSave: suspend (Int, String, Map<String, String>) -> Unit) {
    val details = remember(profile.uuid) { profile.publicProfile }
    var bio by remember(profile.uuid) { mutableStateOf(details.bio) }
    var links by remember(profile.uuid) { mutableStateOf(details.links) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    // Re-evaluate server-supplied gates if an admin reduces the level while this dialog is open.
    val canBio = profile.publicProfile.canEditBio && profile.level > 15
    val canSocial = profile.publicProfile.canEditSocials && profile.level > 20
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 800.dp).learnerDialogPanelBounds(), shape = RoundedCornerShape(24.dp),
                color = Color(0xFFFFFCF6), border = BorderStroke(2.dp, Color.White)) {
                Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFFFF8E7), Color(0xFFF4F1FF)))).padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("✦ معرفی من", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = Color(0xFF414B80))
                            Text("یک معرفی کوتاه از دنیای خودت بنویس", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7A7595))
                        }
                        IconButton(onClick = onClose, enabled = !busy) { Text("×", style = MaterialTheme.typography.headlineMedium) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(color = Color(0xFFEAE8FA), shape = RoundedCornerShape(12.dp)) {
                            Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(when (details.status) { "pending" -> "◷ در انتظار بررسی مدیر"; "approved" -> "✓ معرفی تأیید شده"; "rejected" -> "معرفی نیاز به اصلاح دارد"; else -> "معرفی عمومی تو" },
                                    style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color(0xFF5A4E91))
                                Text("بعد از تأیید مدیر، دیگران معرفی و لینک‌هایت را می‌بینند. شماره، آدرس خانه یا اطلاعات خصوصی ننویس.", style = MaterialTheme.typography.bodySmall)
                                details.reason?.let { Text(it, color = Color(0xFFAE415A), style = MaterialTheme.typography.bodySmall) }
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("دربارهٔ من", fontWeight = FontWeight.Bold, color = Color(0xFF414B80))
                                OutlinedTextField(value = bio, onValueChange = { if (PublicProfilePolicy.length(it) <= 200) bio = it },
                                    modifier = Modifier.fillMaxWidth(), enabled = canBio && !busy, minLines = 3, maxLines = 4,
                                    placeholder = { Text("مثلاً: بازی می‌سازم و عاشق حل معما هستم!") },
                                    supportingText = { Text(if (canBio) persianDisplay("${PublicProfilePolicy.length(bio)} از ۲۰۰") else "از سطح ۱۶ فعال می‌شود") },
                                    shape = RoundedCornerShape(14.dp))
                                if (bio.isNotEmpty()) TextButton(enabled = !busy, onClick = { bio = "" }) { Text("پاک کردن معرفی") }
                            }
                            Column(Modifier.weight(1.1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("شبکه‌های اجتماعی", fontWeight = FontWeight.Bold, color = Color(0xFF414B80))
                                if (!canSocial) Text("🔒 از سطح ۲۱ فعال می‌شود", color = Color(0xFF88729D), style = MaterialTheme.typography.bodySmall)
                                PublicProfilePolicy.networks.entries.chunked(2).forEach { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        row.forEach { (key, label) ->
                                            Column(Modifier.weight(1f)) {
                                                OutlinedTextField(value = links[key].orEmpty(), onValueChange = { if (it.length <= 200) links = links + (key to it) },
                                                    label = { Text(label) }, placeholder = { Text("@username") }, singleLine = true,
                                                    enabled = canSocial && !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                                                if (!links[key].isNullOrEmpty()) TextButton(enabled = !busy, onClick = { links = links - key }) { Text("حذف لینک") }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("می‌توانی هر زمان معرفی یا لینک‌ها را پاک کنی.", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = Color(0xFF7A7595))
                        Button(enabled = !busy && (bio != details.bio || links != details.links), shape = RoundedCornerShape(13.dp), onClick = {
                            focus.clearFocus(); busy = true; error = null
                            scope.launch {
                                try { onSave(details.version, bio, links); onClose() }
                                catch (cancelled: CancellationException) { throw cancelled }
                                catch (failure: Exception) { error = failure.message ?: "ذخیره نشد؛ دوباره تلاش کن." }
                                finally { busy = false }
                            }
                        }) {
                            if (busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text("ذخیره تغییرات")
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "معرفی من · افقی", widthDp = 900, heightDp = 420, locale = "fa")
@androidx.compose.ui.tooling.preview.Preview(name = "معرفی من · صفحه کوچک", widthDp = 640, heightDp = 320, locale = "fa")
@Composable private fun PublicProfileFormPreview() {
    LearnerDialogTheme {
        val profile = LearnerProfile("preview", "برنامه‌نویس خلاق", 21, 0, 0.0, 100.0, 3, true,
            publicProfile = PublicProfileDetails(true, 1, true, true, "pending", bio = "من بازی‌های ماجراجویی می‌سازم! 😺"))
        LearnerPublicProfileFormDialog(profile, {}) { _, _, _ -> }
    }
}

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
