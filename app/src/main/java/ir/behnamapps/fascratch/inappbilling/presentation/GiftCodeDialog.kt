package ir.behnamapps.fascratch.inappbilling.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.learning.LearningRepository
import ir.behnamapps.fascratch.inappbilling.learning.LearnerDialogWindowBounds
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
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val submit: () -> Unit = {
        if (!busy && (code.isNotBlank() || result != null)) {
            keyboard?.hide()
            focus.clearFocus()
            if (result != null) onClose()
            else {
                busy = true
                error = null
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
                    } finally { busy = false }
                }
            }
        }
    }
    val redeemButton: @Composable () -> Unit = {
        Button(onClick = submit, enabled = !busy && (code.isNotBlank() || result != null),
            modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) {
            if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(if (result != null) "چه خوب!" else "دریافت هدیه", maxLines = 1)
        }
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        // Use the same centered, bounded window as profile dialogs, including RTL devices.
        LearnerDialogWindowBounds()
        val keyboardVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding().padding(horizontal = 16.dp, vertical = 8.dp).clipToBounds(), contentAlignment = Alignment.Center) {
            val compact = keyboardVisible || maxHeight < 280.dp
            Surface(Modifier.widthIn(max = if (compact) 760.dp else 620.dp).fillMaxWidth(.92f).heightIn(max = maxHeight), shape = RoundedCornerShape(24.dp), color = Color(0xFFF8FAF4)) {
                // No weighted middle section: it could collapse the field to zero height in landscape.
                Column(Modifier.verticalScroll(rememberScrollState()).padding(if (compact) 10.dp else 20.dp), verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 12.dp)) {
                    if (!compact || result != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎁  کد هدیه", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            IconButton(onClick = onClose, enabled = !busy) { Text("×", style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                    if (result == null) {
                        if (!compact) Text("کدت را وارد کن و هدیه‌ات را بگیر.", style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = code, onValueChange = { if (it.length <= 64) { code = it; error = null } },
                                enabled = !busy, singleLine = true, label = { Text("کد هدیه") }, modifier = Modifier.weight(1f).heightIn(min = 64.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submit() }),
                                trailingIcon = { TextButton(onClick = { code = clipboard.getText()?.text.orEmpty().trim().take(64) }, enabled = !busy) { Text("چسباندن") } },
                                shape = RoundedCornerShape(14.dp), isError = error != null)
                            if (compact) {
                                redeemButton()
                                IconButton(onClick = onClose, enabled = !busy) { Text("×", style = MaterialTheme.typography.headlineSmall) }
                            }
                        }
                        if (guest && !compact) Text("هدیه برای همین حساب ذخیره می‌شود. برای بازیابی پس از پاک‌شدن اطلاعات اپ، حسابت را ثبت کن.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D6C65))
                        error?.let { GiftFeedback(feedbackCode, it) }
                    } else GiftFeedback(feedbackCode, result!!)
                    if (!compact || result != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { redeemButton() }
                }
            }
        }
    }
}
