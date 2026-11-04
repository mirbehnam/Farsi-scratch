package ir.behnamapps.fascratch.editor

import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.SavedProjectCard
import ir.behnamapps.fascratch.SavedScratchProject

private val Blue = Color(0xFF6CABFF)
private val Gold = Color(0xFFFFD36A)

@Composable
internal fun ProjectLibraryDialog(
    projects: List<SavedScratchProject>,
    onLoad: (SavedScratchProject) -> Unit,
    onLoadFromPhone: () -> Unit,
    onDismiss: () -> Unit,
    showManagementActions: Boolean,
    onShare: (SavedScratchProject) -> Unit = {},
    onDelete: (SavedScratchProject) -> Unit = {},
    htmlProjects: List<SavedHtmlProject> = emptyList(),
    initialHtmlTab: Boolean = false,
    preparingHtml: Boolean = false,
    onPlayHtml: ((SavedHtmlProject) -> Unit)? = null,
    onShareHtml: (SavedHtmlProject) -> Unit = {}
) {
    var htmlTab by rememberSaveable { mutableStateOf(initialHtmlTab) }
    var pendingDelete by remember { mutableStateOf<SavedScratchProject?>(null) }
    val dismiss = { if (!preparingHtml) onDismiss() }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                modifier = Modifier.fillMaxWidth(.94f).fillMaxHeight(.9f)
                    .border(1.dp, Color.White.copy(.14f), RoundedCornerShape(24.dp)),
                color = Color(0xFF171A38), shape = RoundedCornerShape(24.dp)
            ) {
                Column(Modifier.fillMaxSize().background(Brush.linearGradient(
                    listOf(Color(0xFF2C1E50), Color(0xFF171A38)))).padding(horizontal = 18.dp, vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("پروژه‌های من", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                        if (!htmlTab) {
                            TextButton(onClick = onLoadFromPhone, enabled = !preparingHtml) {
                                Text("＋ بارگذاری از حافظه گوشی", color = Blue, fontSize = 12.sp)
                            }
                        } else Text("آمادهٔ اجرا و اشتراک‌گذاری", color = Color.White.copy(.6f), fontSize = 12.sp)
                    }
                    if (onPlayHtml != null) {
                        Row(Modifier.fillMaxWidth().height(38.dp).clip(RoundedCornerShape(13.dp))
                            .background(Color.White.copy(.06f)).padding(3.dp).selectableGroup(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            LibraryTab("پروژه‌های SB3", projects.size, !htmlTab, !preparingHtml, Modifier.weight(1f)) { htmlTab = false }
                            LibraryTab("پروژه‌های HTML", htmlProjects.size, htmlTab, !preparingHtml, Modifier.weight(1f)) { htmlTab = true }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val previewHeight = (maxHeight - 84.dp).coerceIn(20.dp, 96.dp)
                        if ((htmlTab && htmlProjects.isEmpty()) || (!htmlTab && projects.isEmpty())) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (htmlTab) "〈 HTML 〉" else "SB3", color = Blue.copy(.7f), fontSize = 28.sp,
                                    fontWeight = FontWeight.Black)
                                Spacer(Modifier.height(6.dp))
                                Text(if (htmlTab) "هنوز خروجی HTML ذخیره نکرده‌ای" else "هنوز پروژه‌ای ذخیره نشده است",
                                    color = Color.White.copy(.7f), fontSize = 14.sp)
                                if (htmlTab) Text("خروجی‌های ذخیره‌شدهٔ پکیجر توربو اینجا نمایش داده می‌شوند",
                                    color = Color.White.copy(.45f), fontSize = 11.sp)
                            }
                        } else {
                            LazyRow(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                if (htmlTab) items(htmlProjects, key = { it.file.absolutePath }) { project ->
                                    HtmlProjectCard(project, previewHeight, maxHeight, !preparingHtml,
                                        onPlay = { onPlayHtml?.invoke(project) }, onShare = { onShareHtml(project) })
                                } else items(projects, key = { it.projectFile.absolutePath }) { project ->
                                    SavedProjectCard(project, showManagementActions,
                                        onClick = { onLoad(project) }, onShare = { onShare(project) },
                                        onDelete = { pendingDelete = project }, previewHeight = previewHeight, cardHeight = maxHeight)
                                }
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(32.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        if (preparingHtml) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), color = Blue, strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("در حال آماده‌سازی اجرا…", color = Color.White.copy(.7f), fontSize = 11.sp)
                            }
                        } else Text(if (htmlTab) "HTML برای اجراست؛ برای ویرایش نسخهٔ SB3 را نگه دار" else if (showManagementActions) "پروژه‌ها را با اسکرچ یا توربو باز کن" else "یک پروژهٔ SB3 را برای ویرایش انتخاب کن",
                            color = Color.White.copy(.45f), fontSize = 10.sp)
                        TextButton(onClick = dismiss, enabled = !preparingHtml, contentPadding = PaddingValues(horizontal = 10.dp)) {
                            Text("بستن", color = Gold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { project ->
        AlertDialog(onDismissRequest = { pendingDelete = null }, containerColor = Color(0xFF241A42),
            title = { Text("حذف پروژه؟", color = Color.White) },
            text = { Text("پروژه «${project.name}» حذف شود؟", color = Color.White.copy(.7f)) },
            confirmButton = { TextButton(onClick = { onDelete(project); pendingDelete = null }) { Text("حذف", color = Color(0xFFFF7777)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("انصراف", color = Color.White) } })
    }
}

@Composable
private fun LibraryTab(label: String, count: Int, selected: Boolean, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.fillMaxHeight().clip(RoundedCornerShape(10.dp))
        .background(if (selected) Color(0xFF7252C4) else Color.Transparent)
        .selectable(selected, enabled = enabled, role = Role.Tab, onClick = onClick), contentAlignment = Alignment.Center) {
        Text("$label  ·  $count", color = if (selected) Color.White else Color.White.copy(.6f),
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun HtmlProjectCard(project: SavedHtmlProject, previewHeight: Dp, cardHeight: Dp, enabled: Boolean, onPlay: () -> Unit, onShare: () -> Unit) {
    Card(Modifier.width(220.dp).heightIn(max = cardHeight), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(.08f))) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(previewHeight).background(Brush.linearGradient(
                listOf(Color(0xFF254B72), Color(0xFF473B78)))), contentAlignment = Alignment.Center) {
                Text("〈 HTML 〉", color = Color(0xFFBBDFFF), fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
            Text(project.name, Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("HTML · ${maxOf(1, project.file.length() / 1024)} KB", Modifier.padding(horizontal = 10.dp),
                color = Color.White.copy(.5f), fontSize = 10.sp)
            Row(Modifier.fillMaxWidth().padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = onPlay, enabled = enabled, modifier = Modifier.weight(1f).height(32.dp),
                    contentPadding = PaddingValues(0.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF407BCE))) {
                    Text("▶ اجرا", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                TextButton(onClick = onShare, enabled = enabled, modifier = Modifier.weight(1.2f).height(32.dp),
                    contentPadding = PaddingValues(0.dp)) { Text("↗ اشتراک‌گذاری", color = Blue, fontSize = 11.sp) }
            }
        }
    }
}
