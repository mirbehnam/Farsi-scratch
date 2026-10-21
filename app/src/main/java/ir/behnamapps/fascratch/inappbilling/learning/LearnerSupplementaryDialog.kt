@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.presentation.persianDisplay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun LearnerSupplementaryDialog(profile: LearnerProfile, repository: LearningRepository, onClose: () -> Unit) {
    var age by remember(profile.uuid) { mutableStateOf(profile.supplementary.age?.let { persianDisplay(it.toString()) }.orEmpty()) }
    var province by remember(profile.uuid) { mutableStateOf(profile.supplementary.province) }
    var gender by remember(profile.uuid) { mutableStateOf(profile.supplementary.gender) }
    var declined by remember(profile.uuid) { mutableStateOf(profile.supplementary.declinedFields) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var publicSection by remember(profile.uuid) { mutableStateOf<PublicProfileSection?>(null) }
    val version = remember(profile.uuid) { profile.supplementary.version }
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    publicSection?.let { section ->
        LearnerPublicProfileDialog(profile, repository, section) { publicSection = null }
        return
    }
    Dialog(onDismissRequest = { if (!busy) onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LearnerDialogWindowBounds()
        Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
            Surface(Modifier.widthIn(max = 620.dp).fillMaxWidth(.90f), shape = RoundedCornerShape(25.dp),
                border = BorderStroke(1.dp, Color(0xFFE0E6F4)), shadowElevation = 10.dp) {
                Column(Modifier.background(Brush.linearGradient(listOf(Color(0xFFF2F4FF), Color(0xFFFFFBF1))))) {
                    Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎒", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("اطلاعات تکمیلی", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text("همهٔ گزینه‌ها اختیاری‌اند ✨", color = Color(0xFF71629D), style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = onClose, enabled = !busy, modifier = Modifier.semantics { contentDescription = "بستن اطلاعات تکمیلی" }) { Text("×", style = MaterialTheme.typography.titleLarge) }
                        }
                        if (profile.publicProfile.available) PublicProfileEntryButtons(profile, !busy) { publicSection = it }
                        Surface(color = Color(0xFFE9F2ED), shape = RoundedCornerShape(12.dp)) {
                            Text("🔒 سن، استان و جنسیت خصوصی‌اند. معرفی و لینک‌های اجتماعی فقط پس از تأیید مدیر عمومی می‌شوند.",
                                Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF345B48))
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                            OutlinedTextField(age, { value -> if (value.length <= 3) { age = value; declined = declined - "age"; error = null } }, Modifier.weight(.32f),
                                label = { Text("سن 🎂") }, placeholder = { Text("اختیاری") }, suffix = { Text("سال") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, enabled = !busy,
                                isError = !SupplementaryPolicy.validAge(age), shape = RoundedCornerShape(14.dp))
                            ProvinceSelector(province, "province" in declined, { province = it; declined = if (it == null) declined + "province" else declined - "province"; error = null }, !busy, Modifier.weight(.68f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = "age" in declined, enabled = !busy, onCheckedChange = {
                                declined = if (it) declined + "age" else declined - "age"; if (it) age = ""
                            })
                            Text("ترجیح می‌دهم سنم را نگویم", style = MaterialTheme.typography.bodySmall)
                        }
                        if (!SupplementaryPolicy.validAge(age)) Text("سن را از ۱ تا ۱۲۰ وارد کن، یا خالی بگذار.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Text("جنسیت", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SupplementaryPolicy.genders.forEach { (id, label) ->
                                FilterChip(selected = gender == id, onClick = { gender = id; declined = declined - "gender" }, enabled = !busy, label = { Text(label) })
                            }
                            FilterChip(selected = "gender" in declined, onClick = { gender = null; declined = declined + "gender" }, enabled = !busy, label = { Text("نمی‌خواهم بگویم") })
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    }
                    HorizontalDivider(color = Color(0xFFE0E6F4))
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        TextButton(enabled = !busy, onClick = { age = ""; province = null; gender = null; declined = emptySet(); error = null }) { Text("پاک‌کردن گزینه‌ها") }
                        Button(enabled = !busy && SupplementaryPolicy.validAge(age), shape = RoundedCornerShape(12.dp), onClick = {
                            busy = true; error = null; focus.clearFocus()
                            scope.launch {
                                try { repository.saveSupplementary(profile.uuid, SupplementaryDetails(true, version, SupplementaryPolicy.age(age), province, gender, declined)); onClose() }
                                catch (e: CancellationException) { throw e }
                                catch (_: Exception) { error = "ذخیره نشد؛ اینترنت را بررسی کن. اگر اطلاعات تغییر کرده، فرم را ببند و دوباره باز کن." }
                                finally { busy = false }
                            }
                        }) { Text(if (busy) "در حال ذخیره…" else "ذخیره اطلاعات ✓") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProvinceSelector(selected: String?, declined: Boolean, onSelect: (String?) -> Unit, enabled: Boolean, modifier: Modifier) {
    var expanded by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    Box(modifier) {
        OutlinedButton(onClick = { search = ""; expanded = true }, enabled = enabled,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp), shape = RoundedCornerShape(14.dp)) {
            Text("📍 " + (SupplementaryPolicy.provinces[selected] ?: if (declined) "نمی‌خواهم بگویم" else "استان محل زندگی") + " ⌄", maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 260.dp).widthIn(max = 300.dp)) {
            OutlinedTextField(search, { search = it.take(40) }, label = { Text("جستجوی استان") }, singleLine = true, modifier = Modifier.padding(8.dp))
            DropdownMenuItem(text = { Text("نمی‌خواهم بگویم") }, onClick = { onSelect(null); expanded = false })
            SupplementaryPolicy.provinces.filterValues { SupplementaryPolicy.matchesProvince(it, search) }.forEach { (id, label) ->
                DropdownMenuItem(text = { Text((if (selected == id) "✓ " else "") + label) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}
