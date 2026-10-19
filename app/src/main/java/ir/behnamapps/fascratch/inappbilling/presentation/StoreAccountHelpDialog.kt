package ir.behnamapps.fascratch.inappbilling.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.domain.StoreAccountPolicy
import ir.behnamapps.fascratch.inappbilling.learning.LearnerDialogWindowBounds
import ir.behnamapps.fascratch.inappbilling.learning.learnerSafeDialogBounds

/** Never invoked by automatic inventory refresh or by the app's own account HTTP errors. */
@Composable internal fun StoreAccountHelpDialog(code: String, busy: Boolean, onDismiss: () -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    var opened by remember(code) { mutableStateOf(false) }
    var launchFailed by remember(code) { mutableStateOf(false) }
    var offline by remember(code) { mutableStateOf(false) }
    val missing = code == StoreAccountPolicy.NOT_INSTALLED
    val title = when (code) {
        StoreAccountPolicy.LOGIN_REQUIRED -> "اول وارد کافه‌بازارت شو"
        StoreAccountPolicy.UPDATE_REQUIRED -> "کافه‌بازار نیاز به بروزرسانی دارد"
        else -> "کافه‌بازار پیدا نشد"
    }
    val explanation = when (code) {
        StoreAccountPolicy.LOGIN_REQUIRED -> "برای خرید یا بازیابی دوره، باید وارد حساب کافه‌بازارت باشی. بازار را باز کن، وارد حسابت شو و بعد به این صفحه برگرد."
        StoreAccountPolicy.UPDATE_REQUIRED -> "نسخهٔ نصب‌شدهٔ بازار از پرداخت پشتیبانی نمی‌کند. ابتدا بازار را بروزرسانی کن و بعد دوباره تلاش کن."
        else -> "برای خرید از این نسخهٔ برنامه، کافه‌بازار باید نصب و فعال باشد."
    }
    fun openMarket() {
        offline = false
        val success = if (missing || code == StoreAccountPolicy.UPDATE_REQUIRED) openBazaarDownload(context) else openBazaarApplication(context)
        launchFailed = !success
        if (success) opened = true
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(max = 330.dp), shape = RoundedCornerShape(26.dp), color = Color.White) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.background(Color(0xFFE1F4EA), RoundedCornerShape(16.dp)).padding(13.dp), contentAlignment = Alignment.Center) {
                            Text(if (missing) "↓" else "↗", fontSize = 27.sp, color = Color(0xFF16754C))
                        }
                        Text(title, Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF183F38))
                    }
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(explanation, fontSize = 14.sp, lineHeight = 23.sp, color = Color(0xFF5D6C65))
                        if (opened) Text("بعد از برگشت، «تلاش دوباره» را بزن؛ پرداخت خودکار شروع نمی‌شود.", fontSize = 13.sp, color = Color(0xFF16754C))
                        if (launchFailed) Text("بازار باز نشد؛ نصب و فعال‌بودن آن را بررسی کن.", fontSize = 13.sp, color = Color(0xFFB3261E))
                        if (offline) Text("اینترنت وصل نیست؛ پس از اتصال دوباره تلاش کن.", fontSize = 13.sp, color = Color(0xFFB3261E))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            if (!opened) openMarket()
                            else if (!purchaseNetworkAvailable(context)) offline = true
                            else onRetry()
                        }, enabled = !busy, shape = RoundedCornerShape(14.dp)) {
                            Text(if (opened) "تلاش دوباره" else if (missing) "دریافت بازار" else if (code == StoreAccountPolicy.UPDATE_REQUIRED) "بروزرسانی بازار" else "ورود به بازار")
                        }
                        if (opened) TextButton(onClick = { openMarket() }, enabled = !busy) { Text("باز کردن بازار") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) { Text("فعلاً نه") }
                    }
                }
            }
        }
    }
}

private fun openBazaarApplication(context: Context): Boolean = runCatching {
    val intent = context.packageManager.getLaunchIntentForPackage("com.farsitel.bazaar") ?: return false
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)

private fun openBazaarDownload(context: Context): Boolean = runCatching {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://cafebazaar.ir/install")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
}.getOrDefault(false)
