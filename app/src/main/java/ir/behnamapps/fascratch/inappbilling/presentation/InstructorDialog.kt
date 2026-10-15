package ir.behnamapps.fascratch.inappbilling.presentation

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.domain.*
import ir.behnamapps.fascratch.inappbilling.data.CourseImageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory

@Composable
internal fun InstructorName(course: Course) {
    var show by remember(course.id) { mutableStateOf(false) }
    val profile = course.instructorProfile
    if (profile != null) {
        TextButton(onClick = { show = true }, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 2.dp)) {
            Text("مدرس: ${profile.name}  ‹", style = MaterialTheme.typography.bodySmall)
        }
        if (show) InstructorDialog(profile) { show = false }
    } else if (course.instructor.isNotBlank()) Text("مدرس: ${course.instructor}", style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D6C65))
}

/** Landscape-first: fixed reachable close button; only biography and contacts scroll. */
@Composable
internal fun InstructorDialog(profile: InstructorProfile, onClose: () -> Unit) {
    val context = LocalContext.current
    var linkMessage by remember(profile.uuid) { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp), contentAlignment = Alignment.Center) {
            val screenLimit = (LocalConfiguration.current.screenWidthDp - 32).coerceAtLeast(0).toFloat()
            val dialogWidth = InstructorDialogPolicy.width(minOf(maxWidth.value, screenLimit)).dp
            val compact = maxHeight < 320.dp
            Surface(Modifier.width(dialogWidth).heightIn(max = maxHeight), shape = RoundedCornerShape(24.dp), color = Color(0xFFFAFCF9), tonalElevation = 4.dp,
                border = BorderStroke(1.dp, Color(0xFFDDE8DF))) {
                Column(Modifier.padding(if (compact) 12.dp else 18.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("دربارهٔ مدرس", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onClose, modifier = Modifier.size(36.dp).background(Color(0xFFEDF2ED), CircleShape)) {
                            Text("×", style = MaterialTheme.typography.headlineSmall,
                                modifier = Modifier.semantics { contentDescription = "بستن دربارهٔ مدرس" })
                        }
                    }
                    HorizontalDivider(color = Color(0xFFE0E9E2))
                    Row(Modifier.weight(1f, fill = false).fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Column(Modifier.weight(.32f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            InstructorPortrait(profile, if (compact) 72.dp else 88.dp)
                            Text(profile.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            if (profile.headline.isNotBlank()) Text(profile.headline, style = MaterialTheme.typography.bodySmall, color = Color(0xFF5D6C65), textAlign = TextAlign.Center)
                        }
                        Column(Modifier.weight(.68f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (profile.biography.isNotBlank()) Text(profile.biography, style = MaterialTheme.typography.bodyMedium)
                            if (profile.contacts.isNotEmpty()) {
                                Text("راه‌های ارتباطی", style = MaterialTheme.typography.labelLarge, color = Color(0xFF21634F))
                                profile.contacts.forEach { contact ->
                                    Surface(onClick = {
                                        if (InstructorPolicy.contactAllowed(contact.url)) {
                                            val result = runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(contact.url))) }
                                            if (result.isFailure) {
                                                runCatching { (context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(contact.label, contact.value)) }
                                                linkMessage = "برنامهٔ مناسب پیدا نشد؛ آدرس را از اینجا کپی کنید: ${contact.value}"
                                            }
                                        }
                                    }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White,
                                        border = BorderStroke(1.dp, Color(0xFFE1E8E2))) {
                                        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            InstructorContactIcon(InstructorDialogPolicy.contactKind(contact.url))
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(contact.label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(contact.value, style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                                                    color = Color(0xFF6B7970), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Text("‹", color = Color(0xFF849288), style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                            linkMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            if (profile.biography.isBlank() && profile.contacts.isEmpty()) Text("معرفی بیشتری به‌زودی اضافه می‌شود.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstructorPortrait(profile: InstructorProfile, size: androidx.compose.ui.unit.Dp = 92.dp) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    val bitmap by produceState<Bitmap?>(null, profile.photoUrl) {
        if (!preview && profile.photoUrl != null) value = withContext(Dispatchers.IO) {
            runCatching {
                val image = CourseImageCache(context).load(profile.photoUrl, 2500, 3500) ?: return@runCatching null
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
                BitmapFactory.decodeByteArray(image.bytes, 0, image.bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            }.getOrNull()
        }
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFFE2EFE7)), contentAlignment = Alignment.Center) {
        if (image != null) Image(image, "عکس ${profile.name}", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(profile.name.take(1), style = MaterialTheme.typography.headlineMedium, color = Color(0xFF21634F))
    }
}

@Preview(name = "Instructor / Landscape", widthDp = 840, heightDp = 380)
@Preview(name = "Instructor / Compact landscape", widthDp = 640, heightDp = 300)
@Composable
private fun InstructorDialogPreview() {
    MaterialTheme {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
            InstructorDialog(InstructorProfile("preview", "بهنام تاج‌الدینی", "مدرس برنامه‌نویسی و بازی‌سازی", "با پروژه‌های کوچک و جذاب، قدم‌به‌قدم اسکرچ را یاد می‌گیریم.\nهدف من این است که هر هنرجو ایده‌های خودش را بسازد.", null,
                listOf(InstructorContact("اینستاگرام", "instagram.com/scratch.teacher", "https://instagram.com/scratch.teacher"),
                    InstructorContact("تلگرام", "t.me/scratch_teacher", "https://t.me/scratch_teacher"),
                    InstructorContact("ایمیل مدرس", "teacher@example.com", "mailto:teacher@example.com")))) {}
        }
    }
}
