package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.domain.Course
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable internal fun LearnerNameEditorDialog(course: Course?, profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit, onSaved: () -> Unit = onClose) {
    var name by remember(profile.uuid) { mutableStateOf(if (profile.nameSet) profile.name else "") }
    var fullName by remember(profile.uuid) { mutableStateOf(profile.fullName.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val canSave = !busy && NameEditorPolicy.canSave(name, profile.name, profile.nameSet, profile.nameChangesLeft, profile.nameBlocked) &&
        NameEditorPolicy.validFullName(fullName)
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
        Surface(Modifier.learnerDialogPanelBounds(),
            shape = RoundedCornerShape(26.dp), border = BorderStroke(1.dp, Color(0xFFDCC8FF)), shadowElevation = 10.dp) {
            Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFAF5FF), Color(0xFFFFF8EB))))) {
                // Keep the action bar outside the scrolling content, especially on landscape phones.
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LearnerLevelBadge(profile.level, Modifier.size(68.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(if (profile.nameSet) "نام نمایشی‌ات را تغییر بده" else "یک اسم برای خودت انتخاب کن ✨",
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, color = Color(0xFF7044BC))
                        Text("این اسم در پروفایل و رتبه‌بندی دیده می‌شود.", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(enabled = !busy, onClick = onClose, modifier = Modifier.semantics { contentDescription = "بستن ویرایش نام" }) {
                        Text("×", fontSize = 28.sp, color = Color(0xFF7044BC))
                    }
                }
                Text("می‌توانی یک لقب انتخاب کنی؛ لازم نیست اسم واقعی‌ات باشد.", style = MaterialTheme.typography.bodyMedium)
                if (profile.nameBlocked) NameRestrictionNotice(profile)
                BoxWithConstraints {
                    val nameField: @Composable (Modifier) -> Unit = { modifier ->
                        OutlinedTextField(name, { if (it.length <= 30) { name = it; error = null } },
                            modifier = modifier, enabled = !busy && !profile.nameBlocked, singleLine = true,
                            shape = RoundedCornerShape(14.dp), label = { Text("نام نمایشی") }, placeholder = { Text("مثلاً: کدنویس خلاق") },
                            isError = name.isNotEmpty() && !NameEditorPolicy.validName(name),
                            supportingText = {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(if (name.isNotEmpty() && !NameEditorPolicy.validName(name)) "یک نام ساده با ۲ تا ۳۰ حرف بنویس." else "اسمی کوتاه و محترمانه انتخاب کن.", Modifier.weight(1f))
                                    Text(persianDisplay(name.length.toString()) + " / ۳۰")
                                }
                            }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Next) }))
                    }
                    val fullNameField: @Composable (Modifier) -> Unit = { modifier ->
                        OutlinedTextField(fullName, { if (it.length <= 100) { fullName = it; error = null } },
                            modifier = modifier, enabled = !busy, singleLine = true, shape = RoundedCornerShape(14.dp),
                            label = { Text("نام و نام خانوادگی (اختیاری)") },
                            isError = !NameEditorPolicy.validFullName(fullName), supportingText = { Text("اگر دوست نداری، خالی بگذار.") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
                    }
                    if (maxWidth >= 580.dp) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        nameField(Modifier.weight(1f)); fullNameField(Modifier.weight(1f))
                    } else Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        nameField(Modifier.fillMaxWidth()); fullNameField(Modifier.fillMaxWidth())
                    }
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFFEEE5FF)) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🔄", fontSize = 22.sp)
                        Text("نامت را هر ۳۰ روز تا ۳ بار می‌توانی تغییر بدهی.", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(persianDisplay(profile.nameChangesLeft.toString()) + " تغییر باقی‌مانده", color = Color(0xFF7044BC),
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
                Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFFFFEFDA)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("با یک اسم محترمانه وارد رقابت شو 🌟", fontWeight = FontWeight.Bold, color = Color(0xFF795322), style = MaterialTheme.typography.labelLarge)
                        Text("نام نامناسب، بی‌ادبانه، توهین‌آمیز یا سیاسی باعث مسدود شدن نام می‌شود؛ نه پیشرفت و خریدهای تو.",
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF795322))
                    }
                }
                error?.let {
                    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color(0xFFFFE9E7)) {
                        Text(it, Modifier.padding(12.dp), color = Color(0xFFAC2924), style = MaterialTheme.typography.bodySmall)
                    }
                }
                }
                HorizontalDivider(color = Color(0xFFDCC8FF))
                Row(Modifier.fillMaxWidth().background(Color(0xFFFAF5FF)).padding(horizontal = 18.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = !busy, onClick = onClose) { Text("بعداً") }
                    Spacer(Modifier.width(10.dp))
                    Button(enabled = canSave, shape = RoundedCornerShape(14.dp), onClick = {
                        focus.clearFocus(); busy = true; error = null
                        scope.launch {
                            try { repository.name(course, name, fullName); onSaved() }
                            catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "ذخیره نشد؛ اتصال اینترنت را بررسی کن." }
                            finally { busy = false }
                        }
                    }) {
                        if (busy) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                        Text(if (busy) "در حال ذخیره…" else if (profile.nameSet) "ذخیره تغییرات" else "ثبت نام نمایشی", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    }
}
