package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Intent
import android.net.Uri
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
    var error by remember { mutableStateOf<String?>(null) }
    var googleEnabled by remember { mutableStateOf(false) }
    var passwordEnabled by remember { mutableStateOf(false) }
    var optionsLoaded by remember { mutableStateOf(false) }
    var googlePending by remember { mutableStateOf(repository.pendingGoogle() != null) }
    var available by remember { mutableStateOf<Boolean?>(null) }
    var checkedUsername by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var checkRetry by remember { mutableIntStateOf(0) }
    val sessionSecret = remember { AccountAuthPolicy.secret() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val validName = NameEditorPolicy.validName(displayName)
    val canSave = passwordEnabled && !busy && !googlePending && AccountAuthPolicy.username(username) &&
        AccountAuthPolicy.password(password, register) && (!register || (!checking &&
            AccountAuthPolicy.registration(username, displayName, password, confirmation, available, checkedUsername)))
    suspend fun loadOptions() {
        try {
            val options = repository.authOptions()
            googleEnabled = options.optBoolean("google_enabled"); passwordEnabled = options.optBoolean("password_enabled")
            optionsLoaded = true; error = null
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "اتصال برقرار نشد؛ دوباره تلاش کن." }
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
    LaunchedEffect(googlePending, lifecycle) {
        if (!googlePending) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (googlePending) {
                try {
                    when (repository.pollGoogle()) {
                        "ready" -> { googlePending = false; onClose() }
                        "failed", "expired" -> { googlePending = false; error = "ورود گوگل کامل نشد؛ دوباره تلاش کن." }
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "منتظر اتصال اینترنت هستیم؛ نتیجه ورود دوباره بررسی می‌شود." }
                delay(5_000)
            }
        }
    }
    fun choose(next: Int) { page = next; error = null; password = ""; confirmation = ""; reveal = false }
    fun google() {
        focus.clearFocus(); busy = true; error = null
        scope.launch {
            try {
                val result = repository.startGoogle("login", profile.uuid)
                val url = Uri.parse(result.getString("authorization_url"))
                require(url.scheme == "https" && url.host == "accounts.google.com")
                context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                googlePending = true
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { error = "ورود گوگل در دسترس نیست؛ با نام کاربری وارد شو." }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().padding(16.dp)) {
            Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(24.dp), border = BorderStroke(2.dp, Color.White), shadowElevation = 10.dp) {
                Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFFAF5FF), Color(0xFFFFF8EB))))) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LearnerLevelBadge(profile.level, Modifier.size(58.dp))
                        Text(when (page) { 1 -> "خوش برگشتی!"; 2 -> "حسابت را بساز ✨"; else -> "ثبت‌نام و ورود" },
                            Modifier.weight(1f).padding(horizontal = 12.dp), fontWeight = FontWeight.Bold, color = Color(0xFF344F83), fontSize = 20.sp)
                        if (page != 0) TextButton(enabled = !busy && !googlePending, onClick = { choose(0) }) { Text("بازگشت") }
                        IconButton(enabled = !busy, onClick = onClose) { Text("×", fontSize = 28.sp) }
                    }
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (page == 0) {
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AccountChoice("ورود", "به حسابی که قبلاً ساختی", "🔑", Modifier.weight(1f), passwordEnabled && !busy && !googlePending) { choose(1) }
                                AccountChoice("ثبت‌نام با یوزرنیم", "نام کاربری و رمز عبور", "✨", Modifier.weight(1f), passwordEnabled && profile.username == null && !busy && !googlePending) { choose(2) }
                                AccountChoice("ورود با گوگل", "با حساب گوگلت وارد شو", "G", Modifier.weight(1f), googleEnabled && !busy && !googlePending) { google() }
                            }
                            if (profile.username != null) Text("نام کاربری شما: " + profile.username, color = Color(0xFF407D48))
                            if (optionsLoaded && !googleEnabled) Text("ورود گوگل فعلاً فعال نیست.", style = MaterialTheme.typography.bodySmall)
                        } else {
                            if (!register) Text("با ورود، حساب فعلی عوض می‌شود؛ امتیاز حساب‌ها با هم جمع نمی‌شود.", style = MaterialTheme.typography.bodySmall)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AccountField(username, { username = it; error = null; available = null }, "نام کاربری", Modifier.weight(1f), !busy && !googlePending, false, false, 30,
                                    username.isNotEmpty() && (!AccountAuthPolicy.username(username) || (register && available == false)))
                                if (register) AccountField(displayName, { displayName = it; error = null }, "نام نمایشی", Modifier.weight(1f), !busy && !googlePending, false, true, 30, displayName.isNotEmpty() && !validName)
                                else AccountField(password, { password = it; error = null }, "رمز عبور", Modifier.weight(1f), !busy && !googlePending, !reveal, false, 64, false, true)
                            }
                            if (register) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(when { !AccountAuthPolicy.username(username) -> "نام کاربری: ۳ تا ۳۰ حرف انگلیسی، عدد یا _"; checking -> "در حال بررسی نام کاربری…"; available == true -> "✓ این نام کاربری آزاد است"; available == false -> "این نام کاربری قبلاً استفاده شده"; else -> checkError.orEmpty() },
                                        color = if (available == true) Color(0xFF21634F) else if (available == false) MaterialTheme.colorScheme.error else Color(0xFF6974A0),
                                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    if (checkError != null) TextButton(onClick = { checkRetry++ }) { Text("بررسی دوباره") }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    AccountField(password, { password = it; error = null }, "رمز عبور", Modifier.weight(1f), !busy && !googlePending, !reveal, false, 64,
                                        password.isNotEmpty() && !AccountAuthPolicy.password(password, true), true)
                                    AccountField(confirmation, { confirmation = it; error = null }, "تکرار رمز عبور", Modifier.weight(1f), !busy && !googlePending, !reveal, false, 64,
                                        confirmation.isNotEmpty() && password != confirmation, true)
                                }
                                Text("رمز: حداقل ۱۵ حرف؛ نام نمایشی محترمانه باشد تا محدود نشود. فعلاً بازیابی رمز نداریم.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (googlePending) Text("ورود را در مرورگر کامل کن و به برنامه برگرد؛ نتیجه خودکار بررسی می‌شود.", color = Color(0xFF7044BC), style = MaterialTheme.typography.bodySmall)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                        if (!optionsLoaded) TextButton(onClick = { scope.launch { loadOptions() } }) { Text("بررسی اتصال") }
                    }
                    HorizontalDivider(color = Color(0xFFDCC8FF))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !busy, onClick = onClose) { Text("بی‌خیال") }
                        Spacer(Modifier.weight(1f))
                        if (page != 0) {
                            TextButton(enabled = !busy, onClick = { reveal = !reveal }) { Text(if (reveal) "پنهان کردن رمز" else "نمایش رمز") }
                            Button(enabled = canSave, shape = RoundedCornerShape(12.dp), onClick = {
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

@Composable private fun AccountChoice(title: String, hint: String, icon: String, modifier: Modifier, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 110.dp), shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(12.dp), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(icon, fontSize = 26.sp)
            Text(title, fontWeight = FontWeight.Bold)
            Text(hint, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun AccountField(value: String, change: (String) -> Unit, label: String, modifier: Modifier, enabled: Boolean,
    hidden: Boolean, persian: Boolean, limit: Int, error: Boolean, password: Boolean = false) {
    CompositionLocalProvider(LocalLayoutDirection provides if (persian) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        OutlinedTextField(value, onValueChange = { if (it.length <= limit) change(it) }, modifier = modifier, enabled = enabled,
            label = { Text(label) }, singleLine = true, shape = RoundedCornerShape(12.dp), isError = error,
            visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = if (password) KeyboardType.Password else if (persian) KeyboardType.Text else KeyboardType.Ascii))
    }
}
