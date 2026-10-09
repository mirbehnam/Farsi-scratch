package ir.behnamapps.fascratch.inappbilling.learning

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.behnamapps.fascratch.inappbilling.domain.Course
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Native vector artwork stays sharp on tablets without downloading any avatar asset. */
@Composable private fun CatAvatar(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawCircle(Color(0xFFFFE5BC))
        val ears = Path().apply {
            moveTo(w*.17f,h*.48f); lineTo(w*.2f,h*.13f); lineTo(w*.44f,h*.32f); close()
            moveTo(w*.58f,h*.32f); lineTo(w*.83f,h*.13f); lineTo(w*.86f,h*.5f); close()
        }
        drawPath(ears, Color(0xFFE9A657))
        drawOval(Color(0xFFE9A657), Offset(w*.13f,h*.27f), Size(w*.75f,h*.6f))
        drawCircle(Color(0xFF3D3024), w*.035f, Offset(w*.34f,h*.53f))
        drawCircle(Color(0xFF3D3024), w*.035f, Offset(w*.67f,h*.53f))
        drawCircle(Color(0xFFC66970), w*.035f, Offset(w*.5f,h*.65f))
        drawLine(Color(0xFF725436), Offset(w*.24f,h*.66f), Offset(w*.04f,h*.62f), w*.018f)
        drawLine(Color(0xFF725436), Offset(w*.74f,h*.66f), Offset(w*.95f,h*.62f), w*.018f)
    }
}

@Composable fun LearnerChip(profile: LearnerProfile, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CatAvatar(Modifier.size(30.dp))
            Text(persianDisplay(profile.level.toString()), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable fun LearnerDialog(course: Course?, profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    var name by remember(profile.uuid) { mutableStateOf(profile.name) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val counts = repository.allCounts()
    val syncStatus by repository.status.collectAsState()
    var editingName by remember(profile.uuid) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, shape = RoundedCornerShape(24.dp),
        title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CatAvatar(Modifier.size(52.dp)); Column { Text("پروفایل یادگیری", fontWeight = FontWeight.Bold); Text("لول ${persianDisplay(profile.level.toString())}", style = MaterialTheme.typography.titleMedium) }
        } },
        text = { Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ProfileStat("مشاهده واقعی", "${persianDisplay((profile.watchMs / 3600000).toString())} ساعت و ${persianDisplay((profile.watchMs / 60000 % 60).toString())} دقیقه", Modifier.weight(1f))
                ProfileStat("آخرین رتبه ثبت‌شده", profile.rank?.let { persianDisplay(it.toString()) } ?: "—", Modifier.weight(1f))
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("پیشرفت لول", fontWeight = FontWeight.Bold)
                        Text(persianDisplay(profile.level.toString()) + " ← " + persianDisplay((profile.level + 1).toString()))
                    }
                    val target = (profile.xpIntoLevel / profile.xpForNext.coerceAtLeast(1.0)).toFloat().coerceIn(0f, 1f)
                    val animated by androidx.compose.animation.core.animateFloatAsState(target, label = "level-progress")
                    LinearProgressIndicator(progress = { animated }, modifier = Modifier.fillMaxWidth().height(10.dp),
                        color = Color(0xFF26795A), trackColor = Color(0xFFD1E3D7))
                    Text(persianDisplay((target * 100).toInt().toString()) + "٪", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("${persianDisplay(kotlin.math.ceil(profile.xpForNext - profile.xpIntoLevel).toInt().toString())} امتیاز تا لول بعد", style = MaterialTheme.typography.bodySmall)
            Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { editingName = true; error = null }, modifier = Modifier.fillMaxWidth()) {
                Text(if (profile.nameSet) "تغییر نام کاربری" else "ثبت نام کاربری")
            }
            if (editingName) {
                Text("ثبت اولیه رایگان است؛ در هر ۳۰ روز فقط سه بار می‌توانید نام را تغییر دهید. " +
                    "تغییرهای باقی‌مانده: " + persianDisplay(profile.nameChangesLeft.toString()),
                    color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = name, onValueChange = { if (it.length <= 30) name = it },
                    label = { Text("نام کاربری نمایشی") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("این نام برای پروفایل است؛ ثبت‌نام با رمز و بازیابی حساب هنوز فعال نیست.", style = MaterialTheme.typography.labelSmall)
            }
            val purchased = profile.courses.filter { it.purchased }
            if (purchased.isNotEmpty()) {
                Text("دوره‌های خریداری‌شده", fontWeight = FontWeight.Bold)
                purchased.forEach { item ->
                    Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFFE8F3EB)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("✓ " + item.title, fontWeight = FontWeight.Bold, color = Color(0xFF21634F))
                            Text(persianDisplay((item.watchMs / 60000).toString()) + " دقیقه مشاهده" +
                                if (item.enabled) "" else " · دسترسی غیرفعال", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            syncStatus?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = { repository.refreshProfile() }) { Text("همگام‌سازی پیشرفت") }
            if (counts.first > 0) Text("${persianDisplay(counts.first.toString())} گزارش در انتظار همگام‌سازی", style = MaterialTheme.typography.bodySmall)
            if (counts.second > 0) Text("${persianDisplay(counts.second.toString())} گزارش پذیرفته نشده است؛ برای بررسی با پشتیبانی تماس بگیرید.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            androidx.compose.foundation.text.selection.SelectionContainer { Text("شناسه حساب: ${profile.uuid}", style = MaterialTheme.typography.labelSmall) }
            if (!profile.enabled) Text("امتیازدهی این حساب توسط مدیر متوقف شده است.", color = MaterialTheme.colorScheme.error)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        } },
        confirmButton = { if (editingName) Button(enabled = !busy && name.trim().length in 2..30 && (!profile.nameSet || profile.nameChangesLeft > 0 || name.trim() == profile.name), onClick = {
            busy = true; error = null
            scope.launch {
                try { repository.name(course, name); onClose() }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { error = e.message ?: "ذخیره نشد؛ اتصال اینترنت را بررسی کنید." }
                finally { busy = false }
            }
        }) { Text(if (busy) "در حال ذخیره…" else "ذخیره نام") } },
        dismissButton = { TextButton(onClick = onClose) { Text("بستن") } })
}

@Composable private fun ProfileStat(title: String, value: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(12.dp)) { Text(title, style = MaterialTheme.typography.labelSmall); Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable fun LevelCelebration(course: String, profile: LearnerProfile, repository: LearningRepository) {
    var shown by remember(course) { mutableStateOf(0) }
    LaunchedEffect(course, profile.level) {
        shown = repository.celebration(course)
        if (shown > 0) { delay(3200); repository.clearCelebration(course); shown = 0 }
    }
    AnimatedVisibility(visible = shown > 0, enter = fadeIn() + slideInVertically() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
        Surface(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(16.dp), color = Color(0xFF21634F), shadowElevation = 4.dp) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CatAvatar(Modifier.size(42.dp)); Column(Modifier.weight(1f)) {
                    Text("یک قدم جلوتر!", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("لول ${persianDisplay(shown.toString())}", color = Color(0xFFFFDCA1), style = MaterialTheme.typography.titleMedium)
                }
                Text("✨ 🎉 ✨", fontSize = 24.sp)
            }
        }
    }
}
