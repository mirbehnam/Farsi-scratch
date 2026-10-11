package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Menu, login and complete registration share a fixed footer in landscape. */
@Composable internal fun LearnerAccountDialog(profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    var page by remember(profile.uuid) { mutableStateOf(0) }
    val register = page == 2
    var username by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf(if (profile.nameSet) profile.name else "") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // The verified token stays in memory only while awaiting explicit confirmation.
    var pendingGoogle by remember(profile.uuid) { mutableStateOf<Triple<String, String, String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var googleEnabled by remember { mutableStateOf(false) }
    var passwordEnabled by remember { mutableStateOf(false) }
    var optionsLoaded by remember { mutableStateOf(false) }
    var optionsLoading by remember { mutableStateOf(false) }
    var googleUnavailableMessage by remember { mutableStateOf<String?>(null) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var checkedUsername by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var checkRetry by remember { mutableIntStateOf(0) }
    val sessionSecret = remember { AccountAuthPolicy.secret() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var googleSupported by remember(context) { mutableStateOf(NativeGoogleLogin.supported(context)) }
    val validName = NameEditorPolicy.validName(displayName)
    val canSave = passwordEnabled && !busy && AccountAuthPolicy.username(username) &&
        AccountAuthPolicy.password(password, register) && (!register || (!checking &&
            AccountAuthPolicy.registration(username, displayName, password, confirmation, available, checkedUsername)))
    suspend fun loadOptions() {
        if (optionsLoading) return
        optionsLoading = true
        try {
            val options = repository.authOptions()
            googleEnabled = options.optBoolean("google_native_enabled"); passwordEnabled = options.optBoolean("password_enabled")
            googleUnavailableMessage = AccountAuthPolicy.googleUnavailableMessage(options.has("google_native_enabled"), googleEnabled)
            optionsLoaded = true; error = null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "اتصال برقرار نشد؛ دوباره تلاش کن." }
        finally { optionsLoading = false }
    }
    LaunchedEffect(Unit) { loadOptions() }
    LaunchedEffect(username, page, passwordEnabled, checkRetry) {
        val candidate = username
        available = null; checkedUsername = null; checkError = null; checking = false
        if (!register || !passwordEnabled || !AccountAuthPolicy.username(username)) return@LaunchedEffect
        checking = true
        try {
            delay(800)
            val result = repository.usernameAvailable(candidate)
            if (username == candidate) { available = result; checkedUsername = candidate }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { checkError = "نام بررسی نشد؛ دوباره بررسی کن." }
        finally { checking = false }
    }
    fun choose(next: Int) { page = next; error = null; password = ""; confirmation = ""; reveal = false }
    fun google() {
        focus.clearFocus(); busy = true; error = null
        scope.launch {
            try {
                if (!NativeGoogleLogin.supported(context)) throw NativeGoogleUnavailable()
                val secret = AccountAuthPolicy.secret()
                val challenge = repository.googleChallenge(secret, profile.uuid)
                val token = NativeGoogleLogin.token(context, challenge.getString("server_client_id"), challenge.getString("nonce"))
                if (token != null) {
                    val flow = challenge.getString("flow_uuid")
                    if (repository.googleComplete(flow, secret, token, profile.uuid)) onClose()
                    else pendingGoogle = Triple(flow, secret, token)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: NativeGoogleUnavailable) { googleSupported = false; error = e.message }
            catch (_: Exception) { error = "ورود گوگل کامل نشد؛ دوباره تلاش کن یا با نام کاربری وارد شو." }
            finally { busy = false }
        }
    }
    pendingGoogle?.let { pending ->
        AlertDialog(onDismissRequest = { if (!busy) pendingGoogle = null },
            title = { Text("جیمیل به حساب دیگری متصل است") },
            text = { Text("با ادامه، وارد آن حساب می‌شوی. پیشرفت فعلی‌ات به آن منتقل نمی‌شود؛ حساب فعلی حذف نخواهد شد. ادامه می‌دهی؟") },
            confirmButton = { TextButton(enabled = !busy, onClick = {
                busy = true; error = null
                scope.launch {
                    try {
                        if (repository.googleComplete(pending.first, pending.second, pending.third, profile.uuid, confirmSwitch = true)) {
                            pendingGoogle = null; onClose()
                        }
                    } catch (e: CancellationException) { throw e }
                    catch (_: Exception) { pendingGoogle = null; error = "ورود کامل نشد؛ دوباره تلاش کن." }
                    finally { busy = false }
                }
            }) { Text(if (busy) "در حال ورود…" else "تأیید و ورود") } },
            dismissButton = { TextButton(enabled = !busy, onClick = { pendingGoogle = null }) { Text("ماندن در حساب فعلی") } })
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.learnerDialogPanelBounds(), color = Color(0xFFF7F5EF), shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, Color(0xFFDCE6DF)), shadowElevation = 10.dp) {
                BoxWithConstraints {
                val compact = maxHeight < 300.dp
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = if (compact) 0.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!compact) LearnerLevelBadge(profile.level, Modifier.size(48.dp))
                        Text(when (page) { 1 -> "خوش برگشتی!"; 2 -> "حسابت را بساز ✨"; else -> "ثبت‌نام و ورود" },
                            Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.Bold, color = Color(0xFF183F38), fontSize = if (compact) 17.sp else 20.sp)
                        if (page != 0) TextButton(enabled = !busy, onClick = { choose(0) }) { Text("بازگشت") }
                        IconButton(enabled = !busy, onClick = onClose) { Text("×", fontSize = 28.sp) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (page == 0) {
                            Text("ادامهٔ ماجراجویی، با حساب خودت", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF183F38))
                            Text("قبلاً حساب ساخته‌ای؟ وارد شو. برای نگه‌داشتن پیشرفت مهمان، حساب بساز.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D6C65))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AccountChoice("ورود به حساب", "با نام کاربری و رمز", "↪", Modifier.weight(1f), passwordEnabled && !busy, false) { choose(1) }
                                AccountChoice("ساخت حساب", "پیشرفتت را نگه دار", "+", Modifier.weight(1f), passwordEnabled && profile.username == null && !busy, true) { choose(2) }
                            }
                            if (googleSupported) {
                                OutlinedButton(onClick = { google() }, enabled = googleEnabled && !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                                    shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, Color(0xFFD3DDD6)),
                                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
                                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    else Text("G", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF4285F4))
                                    Spacer(Modifier.width(12.dp))
                                    Text(if (busy) "در حال ورود…" else "ادامه با گوگل", fontWeight = FontWeight.Bold)
                                }
                            }
                            if (profile.username != null) Text("نام کاربری شما: " + profile.username, color = Color(0xFF407D48))
                            if (!googleSupported) Text("ورود با نام کاربری و رمز در این گوشی در دسترس است.", style = MaterialTheme.typography.bodySmall)
                            else if (optionsLoaded && !googleEnabled) googleUnavailableMessage?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        } else {
                            AccountNotice(if (register) "امتیازها و خریدهای همین حساب حفظ می‌شوند." else "وارد حساب قبلی‌ات می‌شوی؛ امتیاز دو حساب با هم جمع نمی‌شود.")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AccountField(username, { username = it; error = null; available = null }, "نام کاربری", Modifier.weight(1f), !busy, false, false, 30,
                                    username.isNotEmpty() && (!AccountAuthPolicy.username(username) || (register && available == false)))
                                if (register) AccountField(displayName, { displayName = it; error = null }, "نام نمایشی", Modifier.weight(1f), !busy, false, true, 30, displayName.isNotEmpty() && !validName)
                                else AccountField(password, { password = it; error = null }, "رمز عبور", Modifier.weight(1f), !busy, !reveal, false, 64, false, true)
                            }
                            if (register) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(when { !AccountAuthPolicy.username(username) -> "نام کاربری: ۳ تا ۳۰ حرف انگلیسی، عدد یا _"; checking -> "در حال بررسی نام کاربری…"; available == true -> "✓ این نام کاربری آزاد است"; available == false -> "این نام کاربری قبلاً استفاده شده"; else -> checkError.orEmpty() },
                                        color = if (available == true) Color(0xFF21634F) else if (available == false) MaterialTheme.colorScheme.error else Color(0xFF6974A0),
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    if (checkError != null) TextButton(onClick = { checkRetry++ }) { Text("بررسی دوباره") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AccountField(password, { password = it; error = null }, "رمز عبور", Modifier.weight(1f), !busy, !reveal, false, 64,
                                        password.isNotEmpty() && !AccountAuthPolicy.password(password, true), true)
                                    AccountField(confirmation, { confirmation = it; error = null }, "تکرار رمز عبور", Modifier.weight(1f), !busy, !reveal, false, 64,
                                        confirmation.isNotEmpty() && password != confirmation, true)
                                }
                                Text("رمز حداقل ۱۵ کاراکتر باشد؛ می‌توانی یک عبارت بسازی. فعلاً بازیابی رمز نداریم؛ آن را نگه دار.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D6C65))
                                Text("نام نمایشی محترمانه انتخاب کن؛ نام نامناسب محدود می‌شود.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D6C65))
                            }
                        }
                        error?.let { AccountNotice(it, failure = true) }
                        if (!optionsLoaded || (googleSupported && !googleEnabled)) TextButton(enabled = !busy && !optionsLoading,
                            onClick = { scope.launch { loadOptions() } }) { Text(if (optionsLoading) "در حال بررسی…" else "بررسی مجدد اتصال") }
                    }
                    HorizontalDivider(color = Color(0xFFE0E7E1))
                    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = if (compact) 0.dp else 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !busy, onClick = onClose) { Text(if (page == 0) "فعلاً مهمان می‌مانم" else "بی‌خیال") }
                        Spacer(Modifier.weight(1f))
                        if (page != 0) {
                            TextButton(enabled = !busy, onClick = { reveal = !reveal }) { Text(if (reveal) "پنهان کردن رمز" else "نمایش رمز") }
                            Button(enabled = canSave, modifier = Modifier.heightIn(min = 48.dp), shape = RoundedCornerShape(14.dp), contentPadding = PaddingValues(horizontal = 24.dp), onClick = {
                                focus.clearFocus(); busy = true; error = null
                                scope.launch {
                                    try {
                                        repository.authenticate(register, username, password, confirmation, sessionSecret, profile.uuid, if (register) displayName else null)
                                        password = ""; confirmation = ""; onClose()
                                    } catch (e: CancellationException) { throw e }
                                    catch (e: Exception) { error = e.message ?: "ذخیره نشد؛ دوباره تلاش کن." }
                                    finally { busy = false }
                                }
                            }) {
                                if (busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                                Text(if (register) "تکمیل ثبت‌نام" else "ورود", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                }
            }
        }
    }
}

@Composable private fun AccountChoice(title: String, hint: String, icon: String, modifier: Modifier, enabled: Boolean, highlighted: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 90.dp), shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (highlighted) Color(0xFFC7DCCE) else Color(0xFFDEE5E0)),
        contentPadding = PaddingValues(14.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = if (highlighted) Color(0xFFE7F1E9) else Color.White)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = if (highlighted) Color(0xFF21634F) else Color(0xFFF5E8CE), shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) { Text(icon, fontSize = 24.sp, color = if (highlighted) Color.White else Color(0xFF855F27)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Start)
                Text(hint, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Start)
            }
        }
    }
}

@Composable private fun AccountNotice(text: String, failure: Boolean = false) {
    Surface(color = if (failure) Color(0xFFFFEDE9) else Color(0xFFEAF0EA), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, Modifier.padding(horizontal = 12.dp, vertical = 9.dp), color = if (failure) Color(0xFF9B3026) else Color(0xFF345E4D), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun AccountField(value: String, change: (String) -> Unit, label: String, modifier: Modifier, enabled: Boolean,
    hidden: Boolean, persian: Boolean, limit: Int, error: Boolean, password: Boolean = false) {
    CompositionLocalProvider(LocalLayoutDirection provides if (persian) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        OutlinedTextField(value, onValueChange = { if (it.length <= limit) change(it) }, modifier = modifier, enabled = enabled,
            label = { CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { Text(label) } }, singleLine = true, shape = RoundedCornerShape(14.dp), isError = error,
            colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
                unfocusedBorderColor = Color(0xFFCED9D0), focusedBorderColor = Color(0xFF21634F)),
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = if (password) KeyboardType.Password else if (persian) KeyboardType.Text else KeyboardType.Ascii))
    }
}
