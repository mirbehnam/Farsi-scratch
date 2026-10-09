package ir.behnamapps.fascratch.inappbilling.learning

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.platform.LocalConfiguration
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

/** Optional account protection, never a paywall. Passwords stay in volatile form state only. */
@Composable internal fun LearnerAccountDialog(profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    var register by remember { mutableStateOf(true) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmation by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var googleEnabled by remember { mutableStateOf(false) }
    var passwordEnabled by remember { mutableStateOf(false) }
    var optionsLoaded by remember { mutableStateOf(false) }
    var googlePending by remember { mutableStateOf(repository.pendingGoogle() != null) }
    val sessionSecret = remember { AccountAuthPolicy.secret() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val height = (LocalConfiguration.current.screenHeightDp - 24).coerceAtLeast(160).dp
    val alreadyPassword = register && profile.username != null
    val canSave = passwordEnabled && !busy && !googlePending && !alreadyPassword && AccountAuthPolicy.username(username) &&
        AccountAuthPolicy.password(password, register) && (!register || password == confirmation)
    suspend fun loadOptions() {
        try {
            val options = repository.authOptions()
            googleEnabled = options.optBoolean("google_enabled"); passwordEnabled = options.optBoolean("password_enabled")
            optionsLoaded = true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "اتصال برقرار نشد؛ برای بررسی دوباره تلاش کن." }
    }
    LaunchedEffect(Unit) { loadOptions() }
    LaunchedEffect(googlePending, lifecycle) {
        if (!googlePending) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (googlePending) {
                try {
                    when (repository.pollGoogle()) {
                        "ready" -> { googlePending = false; onClose() }
                        "failed", "expired" -> { googlePending = false; error = "ورود گوگل کامل نشد؛ دوباره تلاش کن یا با رمز وارد شو." }
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { error = "منتظر اتصال اینترنت هستیم؛ ورود گوگل را دوباره بررسی می‌کنیم." }
                delay(5_000)
            }
        }
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 880.dp).fillMaxWidth(.95f).heightIn(max = height), shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color(0xFFDCC8FF)), shadowElevation = 10.dp) {
            Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFFAF5FF), Color(0xFFFFF8EB))))) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LearnerLevelBadge(profile.level, Modifier.size(54.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (profile.registered) "کلید ورود به حسابت 🔐" else "پیشرفتت را همراهت نگه دار ✨",
                            fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF7044BC))
                        Text("با گوگل یا نام کاربری و رمز، بعداً همین حساب را باز کن.", style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(enabled = !busy, onClick = onClose) { Text("×", fontSize = 28.sp) }
                }
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = register, enabled = !busy && !googlePending, onClick = { register = true; error = null; password = ""; confirmation = "" },
                            label = { Text(if (profile.registered) "روش‌های ورود من" else "ساخت حساب") })
                        FilterChip(selected = !register, enabled = !busy && !googlePending, onClick = { register = false; error = null; password = ""; confirmation = "" },
                            label = { Text("ورود به حساب موجود") })
                        if (register && profile.googleLinked) Text("✓ گوگل متصل است", style = MaterialTheme.typography.labelMedium, color = Color(0xFF21634F))
                    }
                    if (!register) Text("با ورود، حساب فعلی عوض می‌شود؛ امتیاز دو حساب با هم جمع نمی‌شود.", style = MaterialTheme.typography.bodySmall)
                    if (alreadyPassword) {
                        Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color(0xFFE0EFE4)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("✓ نام کاربری شما", Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Text(profile.username.orEmpty(), fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        BoxWithConstraints {
                            val fields: @Composable (Modifier, Int) -> Unit = { modifier, index ->
                                val value = when (index) { 0 -> username; 1 -> password; else -> confirmation }
                                // English credentials read naturally without changing the shared Persian dialog direction.
                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                    OutlinedTextField(value = value, onValueChange = {
                                        if (it.length <= if (index == 0) 30 else 64) {
                                            when (index) { 0 -> username = it; 1 -> password = it; else -> confirmation = it }; error = null
                                        }
                                    }, modifier = modifier, enabled = !busy && !googlePending, singleLine = true, shape = RoundedCornerShape(12.dp),
                                        label = { Text(when (index) { 0 -> "نام کاربری"; 1 -> "رمز عبور"; else -> "تکرار رمز" }) },
                                        placeholder = { Text(if (index == 0) "scratch_star" else "") },
                                        visualTransformation = if (index == 0 || reveal) VisualTransformation.None else PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = if (index == 0) KeyboardType.Ascii else KeyboardType.Password),
                                        isError = value.isNotEmpty() && when (index) { 0 -> !AccountAuthPolicy.username(value); 1 -> !AccountAuthPolicy.password(value, register); else -> value != password })
                                }
                            }
                            if (maxWidth >= 620.dp) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                fields(Modifier.weight(1f), 0); fields(Modifier.weight(1f), 1)
                                if (register) fields(Modifier.weight(1f), 2)
                            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                fields(Modifier.fillMaxWidth(), 0); fields(Modifier.fillMaxWidth(), 1)
                                if (register) fields(Modifier.fillMaxWidth(), 2)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (register) Text("نام کاربری: ۳ تا ۳۰ حرف انگلیسی، عدد یا _ · رمز: حداقل ۱۵ حرف", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { reveal = !reveal }) { Text(if (reveal) "پنهان کردن رمز" else "نمایش رمز", style = MaterialTheme.typography.labelSmall) }
                        }
                    }
                    if (register) Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = Color(0xFFFFEFDA)) {
                        Text("رمزت را نگه دار و به دیگران نده. فعلاً بازیابی رمز نداریم؛ نام واقعی لازم نیست.", Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall, color = Color(0xFF795322))
                    }
                    if (googlePending) Text("ورود را در مرورگر کامل کن و به برنامه برگرد؛ نتیجه خودکار بررسی می‌شود.", color = Color(0xFF7044BC), style = MaterialTheme.typography.bodySmall)
                    if (optionsLoaded && !googleEnabled) Text("ورود گوگل هنوز توسط مدیر فعال نشده است.", style = MaterialTheme.typography.bodySmall)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (!optionsLoaded) TextButton(onClick = { scope.launch { loadOptions() } }) { Text("بررسی اتصال") }
                }
                HorizontalDivider(color = Color(0xFFDCC8FF))
                Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled = !busy, onClick = onClose) { Text("بی‌خیال") }
                    OutlinedButton(enabled = googleEnabled && !busy && !googlePending && !(register && profile.googleLinked),
                        modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), onClick = {
                            focus.clearFocus(); busy = true; error = null
                            scope.launch {
                                try {
                                    val result = repository.startGoogle(if (register && profile.registered) "link" else "login", profile.uuid)
                                    val url = Uri.parse(result.getString("authorization_url"))
                                    require(url.scheme == "https" && url.host == "accounts.google.com")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    googlePending = true
                                } catch (e: CancellationException) { throw e }
                                catch (_: Exception) { error = "مرورگر یا ورود گوگل در دسترس نیست؛ با نام کاربری وارد شو." }
                                finally { busy = false }
                            }
                        }) { Text(if (register && profile.registered) "اتصال گوگل" else "ورود با گوگل", maxLines = 1) }
                    if (!alreadyPassword) Button(enabled = canSave, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp), onClick = {
                        focus.clearFocus(); busy = true; error = null
                        scope.launch {
                            try {
                                repository.authenticate(register, username, password, confirmation, sessionSecret, profile.uuid)
                                password = ""; confirmation = ""; onClose()
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { error = e.message ?: "ذخیره نشد؛ دوباره تلاش کن." }
                            finally { busy = false }
                        }
                    }) {
                        if (busy) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(6.dp)) }
                        Text(if (register) "ساخت حساب" else "ورود", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
