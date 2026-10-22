package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.learning.LearningRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal fun giftExpiryLabel(time: Long): String = java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT, java.util.Locale("fa", "IR")).format(java.util.Date(time))

@Composable
internal fun GiftCodeDialog(repository: LearningRepository, guest: Boolean, onRedeemed: () -> Unit, onClose: () -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var feedbackCode by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(16.dp), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 620.dp).fillMaxWidth().heightIn(max = maxHeight), shape = RoundedCornerShape(24.dp), color = Color(0xFFF8FAF4)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🎁  کد هدیه", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onClose, enabled = !busy) { Text("×", style = MaterialTheme.typography.headlineSmall) }
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (result == null) {
                            Text("کدت را وارد کن و هدیه‌ات را بگیر.", style = MaterialTheme.typography.bodyMedium)
                            OutlinedTextField(value = code, onValueChange = { if (it.length <= 64) { code = it; error = null } },
                                enabled = !busy, singleLine = true, label = { Text("کد هدیه") }, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                                trailingIcon = { TextButton(onClick = { code = clipboard.getText()?.text.orEmpty().trim().take(64) }, enabled = !busy) { Text("چسباندن") } },
                                shape = RoundedCornerShape(14.dp), isError = error != null)
                            if (guest) Text("هدیه برای همین حساب ذخیره می‌شود. برای بازیابی پس از پاک‌شدن اطلاعات اپ، حسابت را ثبت کن.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D6C65))
                            error?.let { GiftFeedback(feedbackCode, it) }
                        } else GiftFeedback(feedbackCode, result!!)
                    }
                    Button(onClick = {
                        if (result != null) { onClose(); return@Button }
                        busy = true; error = null
                        scope.launch {
                            try {
                                val response = repository.redeemGift(code)
                                feedbackCode = if (response.optBoolean("awarded")) "success" else "already_redeemed"
                                result = if (!response.optBoolean("awarded")) "این هدیه قبلاً برای حسابت ثبت شده است."
                                else when (response.optString("type")) {
                                    "coins" -> "${persianDisplay(response.optInt("amount").toString())} سکه به حسابت اضافه شد!"
                                    "xp" -> "${persianDisplay(response.optInt("amount").toString())} XP به حسابت اضافه شد!"
                                    else -> "دورهٔ هدیه برای حسابت فعال شد!"
                                }
                                onRedeemed()
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) {
                                feedbackCode = (e as? ir.behnamapps.fascratch.inappbilling.domain.CourseFailure)?.code.orEmpty()
                                error = if (e is java.io.IOException) "اینترنت را بررسی کن؛ می‌توانی همین کد را دوباره بفرستی." else e.message ?: "دریافت هدیه کامل نشد؛ دوباره تلاش کن."
                            }
                            finally { busy = false }
                        }
                    }, enabled = !busy && (code.isNotBlank() || result != null), modifier = Modifier.align(Alignment.End), shape = RoundedCornerShape(12.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(if (result != null) "چه خوب!" else "دریافت هدیه")
                    }
                }
            }
        }
    }
}
