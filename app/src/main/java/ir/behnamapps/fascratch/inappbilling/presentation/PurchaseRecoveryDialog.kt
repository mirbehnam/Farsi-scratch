package ir.behnamapps.fascratch.inappbilling.presentation

import android.content.Intent
import android.net.Uri
import android.os.SystemClock
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.learning.LearnerDialogWindowBounds
import ir.behnamapps.fascratch.inappbilling.learning.learnerSafeDialogBounds
import kotlinx.coroutines.delay

internal const val RECOVERY_HELP_AFTER_MS = 10_000L

@Composable internal fun PurchaseRecoveryDialog(recovery: PurchaseRecoveryState, canRetry: Boolean = true,
    onRetry: () -> Unit, onClose: () -> Unit) {
    var delayed by remember(recovery.startedMs) { mutableStateOf(SystemClock.elapsedRealtime() - recovery.startedMs >= RECOVERY_HELP_AFTER_MS) }
    var contact by remember { mutableStateOf(false) }
    LaunchedEffect(recovery.startedMs) {
        delay((RECOVERY_HELP_AFTER_MS - (SystemClock.elapsedRealtime() - recovery.startedMs)).coerceAtLeast(0))
        delayed = true
    }
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Dialog(onDismissRequest = { if (!recovery.running) onClose() }, properties = DialogProperties(
            usePlatformDefaultWidth = false, dismissOnBackPress = !recovery.running, dismissOnClickOutside = false)) {
            LearnerDialogWindowBounds()
            BoxWithConstraints(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
                Surface(Modifier.widthIn(max = 500.dp).fillMaxWidth().heightIn(max = maxHeight), shape = RoundedCornerShape(28.dp), color = Color(0xFFFFFBF2)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(48.dp).background(Color(0xFFE2F3E9), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                                if (recovery.running) CircularProgressIndicator(Modifier.size(26.dp), color = Color(0xFF176D46), strokeWidth = 3.dp)
                                else Text("↻", fontSize = 30.sp, color = Color(0xFF176D46))
                            }
                            Text(if (recovery.running) "در حال بازیابی خریدت…" else "خریدت پیدا شد؛ تأیید کامل نشد",
                                Modifier.weight(1f), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Color(0xFF183F38))
                        }
                        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("خرید قبلی «${recovery.course.title}» پیدا شده است.", color = Color(0xFF183F38))
                            Text(recovery.message ?: if (delayed) "پاسخ تأیید کمی طول کشیده. خریدت محفوظ است؛ لازم نیست دوباره پرداخت کنی."
                                else "داریم خریدت را تأیید و دسترسی دوره را آماده می‌کنیم. لطفاً چند لحظه صبر کن.",
                                color = Color(0xFF5D6C65), lineHeight = 22.sp)
                        }
                        if (delayed || !recovery.running) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onRetry, enabled = canRetry, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("تلاش مجدد") }
                                OutlinedButton(onClick = { contact = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text("تماس با ما") }
                            }
                        }
                        if (!recovery.running) TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("بستن") }
                    }
                }
            }
        }
        if (contact) PurchaseContactDialog { contact = false }
    }
}

@Composable internal fun PurchaseContactDialog(onClose: () -> Unit) {
    val context = LocalContext.current
    var failed by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, shape = RoundedCornerShape(24.dp), containerColor = Color.White,
        title = { Text("برای رفع مشکل کنارت هستیم", fontWeight = FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("می‌توانی برای رفع مشکل بازیابی خرید با ادمین در پیام‌رسان بله تماس بگیری. روی شناسهٔ زیر بزن:")
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                OutlinedButton(onClick = {
                    val link = Uri.parse("https://ble.ir/behnamtajadini")
                    val opened = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link).setPackage("ir.nasim")) }.isSuccess
                    failed = !opened && runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link)) }.isFailure
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text("@behnamtajadini", fontWeight = FontWeight.Bold) }
            }
            if (failed) Text("بله باز نشد؛ می‌توانی شناسهٔ بالا را در بله جست‌وجو کنی.", color = MaterialTheme.colorScheme.error)
        } },
        confirmButton = { TextButton(onClick = onClose) { Text("بازگشت") } })
}
