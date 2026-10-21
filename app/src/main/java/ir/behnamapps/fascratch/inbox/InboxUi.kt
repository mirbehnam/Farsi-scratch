package ir.behnamapps.fascratch.inbox

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import ir.behnamapps.fascratch.inappbilling.learning.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

@Composable internal fun HomeInboxButton(modifier: Modifier = Modifier) {
    if(LocalInspectionMode.current) { InboxEnvelope(2,modifier,{ }); return }
    val app = LocalContext.current.applicationContext
    val repository = remember(app) { InboxRepository.get(app) }
    val state by repository.state.collectAsState()
    var opened by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(repository,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            try { repository.active() } catch(cancelled: CancellationException) { throw cancelled } catch(_: Exception) { /* Inbox must never interrupt Home. */ }
        }
    }
    InboxEnvelope(state.unread,modifier) { opened = true }
    if(opened) InboxDialog(repository) { opened = false }
}

@Composable private fun InboxEnvelope(unread: Int, modifier: Modifier, onOpen: () -> Unit) {
    Box(modifier.size(52.dp).semantics { contentDescription = "پیام‌های مدیر" + if(unread > 0) "، ${InboxPolicy.badge(unread)} پیام نخوانده" else "" }) {
        Box(Modifier.fillMaxSize().clickable(role = Role.Button,onClick = onOpen).padding(9.dp)) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 2.dp.toPx()
                drawRoundRect(Color(0xFFFFD46D),topLeft = Offset(0f,size.height*.1f),size = androidx.compose.ui.geometry.Size(size.width,size.height*.8f),
                    cornerRadius = CornerRadius(3.dp.toPx()),style = Stroke(stroke))
                drawLine(Color(0xFFFFD46D),Offset(stroke,size.height*.2f),Offset(size.width*.5f,size.height*.57f),stroke)
                drawLine(Color(0xFFFFD46D),Offset(size.width-stroke,size.height*.2f),Offset(size.width*.5f,size.height*.57f),stroke)
            }
        }
        if(unread > 0) Box(Modifier.align(Alignment.TopEnd).offset(x = 5.dp,y = (-5).dp).size(24.dp).background(Color(0xFFEE6570),CircleShape).border(2.dp,Color(0xFF142C3F),CircleShape),contentAlignment = Alignment.Center) {
            Text(InboxPolicy.badge(unread),color = Color.White,fontWeight = FontWeight.Black,fontSize = if(unread > 99) 9.sp else 11.sp)
        }
    }
}

@Composable private fun InboxDialog(repository: InboxRepository, onClose: () -> Unit) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(repository) { repository.refresh() }
    LearnerDialogTheme {
        Dialog(onDismissRequest = onClose,properties = DialogProperties(usePlatformDefaultWidth = false,decorFitsSystemWindows = true)) {
            LearnerDialogWindowBounds()
            Box(Modifier.learnerSafeDialogBounds(),contentAlignment = Alignment.Center) {
                Surface(Modifier.learnerDialogPanelBounds(),shape = RoundedCornerShape(24.dp),color = Color(0xFFFFFCF6)) {
                    InboxContent(state,repository,onClose,{ scope.launch { repository.refresh() } },{ repository.readAll(state.sessionKey) })
                }
            }
        }
    }
}

@Composable private fun InboxContent(state: InboxState, repository: InboxRepository?, onClose: () -> Unit, onRefresh: () -> Unit, onReadAll: () -> Unit) {
    var selectedKey by remember(state.sessionKey) { mutableStateOf<String?>(null) }
    val selected = state.messages.find { it.readKey == selectedKey }
    val listState = rememberLazyListState()
    BackHandler(enabled = selected != null) { selectedKey = null }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(selected?.readKey,selected?.notificationVersion,state.sessionKey,lifecycle) {
        if(selected != null && repository != null) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            repository.read(selected,state.sessionKey); awaitCancellation()
        }
    }
    Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFFFFAEC),Color(0xFFEDF8FA)))).padding(14.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f)) {
                Text(if(selected == null) "صندوق پیام‌های تو" else "پیام برای تو",fontSize = 19.sp,fontWeight = FontWeight.Black,color = Color(0xFF285773),maxLines = 1,overflow = TextOverflow.Ellipsis)
                if(selected == null) Text(if(state.unread > 0) "${InboxPolicy.badge(state.unread)} پیام تازه داری ✨" else "خبرهای تازه از تیم اسکرچ",style = MaterialTheme.typography.labelSmall,color = Color(0xFF6B8593))
            }
            if(state.refreshing) CircularProgressIndicator(Modifier.size(16.dp),strokeWidth = 2.dp)
            if(selected == null) {
                if(state.unread > 0) TextButton(onClick = onReadAll) { Text("همه را خواندم",style = MaterialTheme.typography.labelSmall) }
                IconButton(onClick = onRefresh,enabled = !state.refreshing,modifier = Modifier.semantics { contentDescription = "بررسی پیام‌های تازه" }) { Text("↻",fontSize = 23.sp,color = Color(0xFF2A7B89)) }
            } else TextButton(onClick = { selectedKey = null }) { Text("بازگشت به پیام‌ها",style = MaterialTheme.typography.labelMedium) }
            IconButton(onClick = onClose,modifier = Modifier.semantics { contentDescription = "بستن پیام‌ها" }) { Text("×",fontSize = 27.sp,color = Color(0xFF996475)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if(state.messages.isEmpty()) {
                Column(Modifier.align(Alignment.Center).padding(20.dp),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✉",fontSize = 44.sp,color = Color(0xFF48A8B6))
                    Text(if(!state.initialized || state.refreshing) "داریم صندوقت را بررسی می‌کنیم…" else if(state.failed) "دریافت پیام‌ها فعلاً ممکن نیست؛ بعداً دوباره سر بزن." else "هنوز پیامی نداری؛ خبرهای خوب اینجا می‌آیند!",color = Color(0xFF507586))
                    if(state.failed) OutlinedButton(onClick = onRefresh,enabled = !state.refreshing) { Text("تلاش دوباره") }
                }
            } else if(selected == null) LazyColumn(Modifier.fillMaxSize(),state = listState,verticalArrangement = Arrangement.spacedBy(8.dp),contentPadding = PaddingValues(bottom = 8.dp)) {
                items(state.messages,key = { it.readKey }) { message -> InboxRow(message,InboxPolicy.unread(message.notificationVersion,state.read[message.readKey]),false) { selectedKey = message.readKey } }
            } else Surface(Modifier.fillMaxSize(),shape = RoundedCornerShape(18.dp),color = Color.White,border = BorderStroke(1.dp,Color(0xFFD9EAEC))) {
                key(selected.readKey) { InboxDetails(selected,state.sessionKey,repository,Modifier.fillMaxSize()) }
            }
        }
    }
}

private fun symbol(kind: String) = when(kind) { "celebration" -> "🎉"; "lesson" -> "💡"; "warning" -> "⚠"; else -> "💬" }
@Composable private fun InboxRow(message: InboxMessage, unread: Boolean, selected: Boolean, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(role = Role.Button,onClick = onClick),shape = RoundedCornerShape(16.dp),
        color = if(selected) Color(0xFFE1F4F2) else Color.White,border = BorderStroke(if(unread) 2.dp else 1.dp,if(unread) Color(0xFFE6B74C) else Color(0xFFE0E8E9))) {
        Column(Modifier.padding(12.dp),verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(symbol(message.kind),fontSize = 20.sp)
                Text(message.title,Modifier.weight(1f),fontWeight = if(unread) FontWeight.Black else FontWeight.Bold,color = Color(0xFF2B566A),maxLines = 2,overflow = TextOverflow.Ellipsis)
                if(unread) Box(Modifier.size(7.dp).background(Color(0xFFEAA935),CircleShape))
            }
            if(message.body.isNotEmpty()) Text(message.body,style = MaterialTheme.typography.bodySmall,color = Color(0xFF718991),maxLines = 2,overflow = TextOverflow.Ellipsis)
            Text((if(message.pinned) "📌 " else "") + (if(message.scope == "personal") "🔒 برای تو · " else "") + message.dateLabel,style = MaterialTheme.typography.labelSmall,color = Color(0xFF8A9A9F))
        }
    }
}

@Composable private fun InboxDetails(message: InboxMessage, session: String?, repository: InboxRepository?, modifier: Modifier) {
    // Physical placement stays consistent even when opened from an LTR host.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Row(modifier.fillMaxSize().padding(14.dp),horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            InboxMessageCopy(message,Modifier.weight(if(message.image != null) .6f else 1f).fillMaxHeight())
            if(message.image != null) Box(Modifier.weight(.4f).fillMaxHeight(),contentAlignment = Alignment.Center) {
                InboxPicture(message,session,repository,Modifier.fillMaxSize())
            }
        }
    }
}

@Composable private fun InboxMessageCopy(message: InboxMessage, modifier: Modifier) {
    val context = LocalContext.current
    var linkFailed by remember(message.readKey) { mutableStateOf(false) }
    Column(modifier.verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(symbol(message.kind) + " " + message.title,fontSize = 21.sp,fontWeight = FontWeight.Black,color = Color(0xFF2B566A))
        Text(message.dateLabel + if(message.scope == "personal") " · پیام شخصی" else "",style = MaterialTheme.typography.labelSmall,color = Color(0xFF8A9A9F))
        if(message.body.isNotEmpty()) Text(message.body,color = Color(0xFF425D6C),style = MaterialTheme.typography.bodyMedium)
        message.buttons.forEach { button ->
            Button(onClick = {
                linkFailed = false
                if(InboxPolicy.safeLink(button.url)) try { context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(button.url)).addCategory(Intent.CATEGORY_BROWSABLE)) }
                catch(_: Exception) { linkFailed = true }
            },modifier = Modifier.fillMaxWidth(),shape = RoundedCornerShape(14.dp),colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC95B),contentColor = Color(0xFF5F410B))) {
                Text(button.label + " ↗",fontWeight = FontWeight.Bold,modifier = Modifier.padding(vertical = 5.dp))
            }
        }
        if(linkFailed) Text("باز کردن لینک ممکن نشد؛ مرورگر یا برنامهٔ مربوط را بررسی کن.",color = Color(0xFFB35C68),style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun InboxPicture(message: InboxMessage, session: String?, repository: InboxRepository?, modifier: Modifier) {
    var bitmap by remember(message.readKey,message.image?.hash,session) { mutableStateOf<Bitmap?>(null) }
    var retry by remember(message.readKey) { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    LaunchedEffect(message.readKey,message.image?.hash,session,retry) {
        loading = true; bitmap = repository?.bitmap(message,session); loading = false
    }
    Surface(modifier,shape = RoundedCornerShape(14.dp),color = Color(0xFFF0F6F7)) {
        val image = bitmap
        if(image != null) Image(image.asImageBitmap(),"تصویر پیام",Modifier.fillMaxSize(),contentScale = ContentScale.Fit)
        else Column(Modifier.fillMaxSize(),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.Center) {
            Text(if(loading) "تصویر پیام…" else "تصویر فعلاً در دسترس نیست",style = MaterialTheme.typography.labelSmall,color = Color(0xFF78909D))
            if(!loading && repository != null) TextButton(onClick = { retry++ }) { Text("دوباره") }
        }
    }
}

@Preview(name = "صندوق پیام · لنداسکیپ",widthDp = 900,heightDp = 420,locale = "fa",showBackground = true)
@Preview(name = "صندوق پیام · کوچک",widthDp = 640,heightDp = 320,locale = "fa",showBackground = true)
@Composable private fun InboxPreview() {
    LearnerDialogTheme {
        val message = InboxMessage("a",1,1,"یک خبر خوب برای برنامه‌نویس‌ها!","یک پروژهٔ تازه بساز و ایده‌هایت را به زندگی بیاور ✨","celebration",true,1,null,"۱۴۰۵/۰۸/۰۱",emptyList(),null,"public")
        InboxContent(InboxState(listOf(message,message.copy(id = "b",title = "پیام مخصوص تو",scope = "personal")),initialized = true),null,{},{},{})
    }
}

@Preview(name = "جزئیات پیام · لنداسکیپ",widthDp = 900,heightDp = 420,locale = "fa",showBackground = true)
@Preview(name = "جزئیات پیام · کوچک",widthDp = 640,heightDp = 320,locale = "fa",showBackground = true)
@Composable private fun InboxDetailsPreview() {
    LearnerDialogTheme {
        val message = InboxMessage("preview",1,1,"یک خبر خوب برای برنامه‌نویس‌ها!","سلام! یک پروژهٔ تازه بساز و ایده‌هایت را به زندگی بیاور ✨\nاین پیام از طرف تیم اسکرچ برای تو فرستاده شده است.","celebration",true,1,null,"۱۴۰۵/۰۸/۰۱",
            listOf(InboxButton("بیشتر بدان","https://behnamapp.ir")),InboxImage("","a".repeat(64),1,800,600),"public")
        Surface(Modifier.fillMaxSize(),color = Color.White) { InboxDetails(message,null,null,Modifier.fillMaxSize()) }
    }
}
