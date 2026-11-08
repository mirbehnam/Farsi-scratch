package ir.behnamapps.fascratch.inappbilling.learning

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.util.Base64
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.behnamapps.fascratch.inappbilling.data.CourseApi
import ir.behnamapps.fascratch.inappbilling.data.readBytesBounded
import kotlinx.coroutines.*
import java.io.File

@Composable internal fun ContentStoreDialog(onClose: () -> Unit, purchasesInitially: Boolean = false, category: String? = null) {
    val context = LocalContext.current
    val repo = remember { ContentStoreRepository.get(context) }
    val ownedByAccount by repo.ownedByAccount.collectAsState()
    val owner by repo.owner.collectAsState()
    val wallet by repo.wallet.collectAsState()
    val transfers by repo.transfers.collectAsState()
    var tab by remember { mutableStateOf(if (purchasesInitially) "owned" else "all") }
    var kind by remember(category) { mutableStateOf(category.orEmpty()) }
    var query by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ContentPackage>>(emptyList()) }
    var catalog by remember { mutableStateOf<ContentCatalog?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<ContentPackage?>(null) }
    var buy by remember { mutableStateOf<ContentPackage?>(null) }
    var remove by remember { mutableStateOf<Pair<String, String>?>(null) }
    var preview by remember { mutableStateOf<ContentPart?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(append: Boolean = false) {
        loading = true; error = null
        try {
            val next = repo.load(query, kind, tab == "owned", if (append) (catalog?.page ?: 0) + 1 else 1)
            catalog = next
            items = if (append) (items + next.items).distinctBy { it.uuid } else next.items
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "به اینترنت وصل شو و دوباره تلاش کن." }
        finally { loading = false }
    }
    LaunchedEffect(tab, kind, query, owner, revision) { selected = null; if (tab != "installed") { delay(300); load() } }

    LearnerDialogTheme {
        Dialog(onDismissRequest = { if (selected != null) selected = null else onClose() }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true)) {
            LearnerDialogWindowBounds()
            Box(Modifier.learnerSafeDialogBounds(), contentAlignment = Alignment.Center) {
                Surface(Modifier.learnerDialogPanelBounds(), shape = RoundedCornerShape(20.dp), color = Color(0xFFF5F4FC)) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

                        // ───── نوار بالا: تب‌ها + موجودی سکه + بستن (بدون عنوان و آیکون) ─────
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (category != null) Text(kindLabel(category),fontWeight=FontWeight.Bold,fontSize=13.sp)
                            if (selected == null) {
                                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("all" to "فروشگاه", "owned" to "خریدهای من", "installed" to "روی دستگاه").forEach { (id, label) ->
                                        FilterChip(selected = tab == id, onClick = { tab = id }, label = { Text(label, fontSize = 12.sp) })
                                    }
                                }
                            } else Spacer(Modifier.weight(1f))
                            Surface(shape = RoundedCornerShape(30.dp), color = Color(0xFFFFEBCB)) {
                                Text("● ${coinBalanceText(wallet)} سکه", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            TextButton(onClick = { if (selected != null) selected = null else onClose() }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                                Text(if (selected != null) "بازگشت" else if(category != null) "دسته‌ها ‹" else "بستن ×")
                            }
                        }

                        // ───── جستجو + فیلتر نوع (یک ردیف فشرده) ─────
                        if (selected == null && tab == "all") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Surface(Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE7E1F5))) {
                                    Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                                        BasicTextField(
                                            value = query, onValueChange = { query = it.take(120) }, singleLine = true,
                                            textStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF2B2B3A)), modifier = Modifier.fillMaxWidth()
                                        )
                                        if (query.isEmpty()) Text("🔍 چی لازم داری؟", fontSize = 13.sp, color = Color(0xFF9A9AAF))
                                    }
                                }
                                if(category == null) Row(Modifier.weight(1.2f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("" to "همه", "sprites" to "اسپرایت‌ها", "backdrops" to "پس‌زمینه‌ها", "sounds" to "صداها", "projects" to "پروژه‌ها").forEach { (id, label) ->
                                        FilterChip(selected = kind == id, onClick = { kind = id }, label = { Text(label, fontSize = 11.sp) })
                                    }
                                }
                            }
                        }

                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        error?.let { message ->
                            Row(Modifier.fillMaxWidth().background(Color(0xFFFFE4DA), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("☁ $message", Modifier.weight(1f), fontSize = 12.sp)
                                TextButton(onClick = { revision++ }) { Text("تلاش دوباره") }
                            }
                        }

                        val detail = selected?.let { if (it.uuid in (ownedByAccount[owner] ?: emptySet())) it.copy(owned = true) else it }
                        if (detail != null) {
                            // ───── جزئیات بسته ─────
                            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                    PackagePicture(repo, detail.cover, Modifier.width(200.dp).height(140.dp))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(detail.title, fontWeight = FontWeight.Black, fontSize = 20.sp)
                                        Text(detail.description, fontSize = 13.sp)
                                        Text("${detail.files.size} فایل · ${sizeText(detail.bytes)} · ${detail.downloads} دانلود", fontSize = 11.sp)
                                        Text(detail.types.joinToString(" + ") { kindLabel(it) }, fontSize = 12.sp, color = Color(0xFF7757B9))
                                        if (detail.owned) Text("این بسته برای حساب توست؛ دریافت دوباره سکه نمی‌خواهد.", fontSize = 12.sp, color = Color(0xFF287764))
                                    }
                                }
                                val transfer = transfers[detail.uuid]
                                if (transfer?.status in setOf("preparing", "downloading")) {
                                    Text(transfer?.message ?: "در حال دریافت…", fontSize = 12.sp)
                                    if ((transfer?.total ?: 0) > 0) {
                                        LinearProgressIndicator(progress = { transfer!!.downloaded.toFloat() / transfer.total }, modifier = Modifier.fillMaxWidth())
                                        Text("${sizeText(transfer!!.downloaded)} از ${sizeText(transfer.total)}", fontSize = 11.sp)
                                    }
                                }
                                transfer?.message?.let { Text(it, fontSize = 12.sp) }
                                if (repo.installed(detail.uuid)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if("projects" in detail.types) "✓ پروژه در «پروژه‌های من» آماده است" else "✓ در دستهٔ دانلودی‌ها آماده است", Modifier.weight(1f), color = Color(0xFF27715B))
                                        TextButton(onClick = { remove = detail.uuid to detail.title }) { Text("حذف از دستگاه") }
                                    }
                                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    val busy = transfer?.status in setOf("preparing", "downloading")
                                    when {
                                        detail.owned -> Button(enabled = !busy, onClick = { repo.acquire(detail, "coins", context) }) { Text("دانلود مجدد رایگان") }
                                        detail.mode == "free" -> Button(enabled = !busy, onClick = { repo.acquire(detail, "free", context) }) { Text("دانلود رایگان") }
                                        else -> {
                                            if (detail.mode in setOf("coins", "choice")) Button(enabled = !busy, onClick = { if (wallet < detail.coins) error = "سکهٔ کافی نداری؛ موجودی کیف و قیمت بسته را بررسی کن." else buy = detail }) { Text("خرید دائمی · ${detail.coins} سکه") }
                                            if (detail.mode in setOf("ad", "choice")) OutlinedButton(enabled = !busy, onClick = { repo.acquire(detail, "ad", context) }) { Text(if (transfer?.status == "error") "تلاش مجدد با تبلیغ" else "تماشای ویدیو و دریافت") }
                                        }
                                    }
                                }
                                if (!detail.owned && detail.mode in setOf("ad", "choice")) Text("دریافت با تبلیغ مالکیت دائمی ندارد؛ پس از حذف فایل‌ها یا نصب دوباره باید تبلیغ ببینی.", fontSize = 11.sp, color = Color(0xFF74758F))
                                Text("داخل این بسته", fontWeight = FontWeight.Bold)
                                detail.files.forEach { part ->
                                    Surface(shape = RoundedCornerShape(14.dp), color = Color.White, modifier = Modifier.fillMaxWidth()) {
                                        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (part.thumbnail != null) PackagePicture(repo, part.thumbnail, Modifier.size(54.dp).padding(4.dp)) else Text(kindIcon(part.kind), Modifier.padding(8.dp), fontSize = 22.sp)
                                            Column(Modifier.weight(1f)) { Text(part.name, fontWeight = FontWeight.Bold, fontSize = 13.sp); Text(kindLabel(part.kind), fontSize = 11.sp) }
                                            TextButton(onClick = { preview = part }) { Text(if (part.kind == "sounds") "پیش‌شنوایی" else "پیش‌نمایش") }
                                        }
                                    }
                                }
                                Text("دریافت همیشه شامل همهٔ فایل‌هاست؛ فیلتر فقط نمایش فروشگاه را تغییر می‌دهد.", fontSize = 11.sp, color = Color(0xFF74758F))
                            }
                        } else if (tab == "installed") {
                            val local = repo.installedPackages(kind)
                            LazyVerticalGrid(GridCells.Adaptive(170.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (local.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { StoreEmpty("جای ایده‌های تازه اینجاست! هنوز بسته‌ای دانلود نکرده‌ای.") }
                                items(local, key = { it.first }) { (uuid, title) ->
                                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White) {
                                        Column(Modifier.padding(12.dp)) {
                                            Text("✓ $title", fontWeight = FontWeight.Bold)
                                            TextButton(onClick = { remove = uuid to title }) { Text("حذف از دستگاه") }
                                        }
                                    }
                                }
                            }
                        } else {
                            // ───── فروشگاه / خریدهای من ─────
                            if (tab == "owned") Surface(shape = RoundedCornerShape(12.dp), color = Color(0xFFEAE2FB)) {
                                Text("کلکسیون تو: ${catalog?.count ?: 0} بسته · ${catalog?.spent ?: 0} سکه خرج‌شده در همین فروشگاه", Modifier.fillMaxWidth().padding(10.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            LazyVerticalGrid(GridCells.Adaptive(150.dp), Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!loading && items.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { StoreEmpty(if (tab == "owned") "کلکسیونت منتظر اولین خرید دائمی است ✦" else "فعلاً بسته‌ای اینجا نیست؛ کمی بعد سر بزن ✦") }
                                items(items, key = { it.uuid }) { item ->
                                    val isOwned = item.owned || item.uuid in (ownedByAccount[owner] ?: emptySet())
                                    Surface(onClick = { selected = item }, shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE7E1F5))) {
                                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            PackagePicture(repo, item.cover, Modifier.fillMaxWidth().aspectRatio(1.35f))
                                            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(item.types.joinToString(" · ") { kindLabel(it) }, fontSize = 10.sp, color = Color(0xFF77788F), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                if (repo.installed(item.uuid)) "✓ دانلودشده" else if (isOwned) "◆ خریداری‌شده" else when (item.mode) {
                                                    "free" -> "رایگان"; "ad" -> "با تماشای ویدیو"; "choice" -> "${item.coins} سکه یا ویدیو"; else -> "${item.coins} سکه"
                                                }, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94601E)
                                            )
                                            Text("${item.downloads} دانلود · ${sizeText(item.bytes)}", fontSize = 10.sp, color = Color(0xFF88889B))
                                            item.pricePaid?.let { Text("خرید تو: $it سکه · ${item.purchasedAt?.take(10).orEmpty()}", fontSize = 10.sp) }
                                        }
                                    }
                                }
                                if (catalog?.more == true) item(span = { GridItemSpan(maxLineSpan) }) {
                                    OutlinedButton(enabled = !loading, onClick = { scope.launch { load(true) } }) { Text("بسته‌های بیشتر") }
                                }
                            }
                        }
                    }
                }
            }
        }
        buy?.let { item -> AlertDialog(onDismissRequest = { buy = null }, title = { Text("خرید دائمی این بسته؟") }, text = { Text("${item.title}\n${item.coins} سکه از کیف تو کم می‌شود و دانلود خودکار شروع می‌شود.\n" + if (LearningIdentity.get(context).profile()?.registered != true) "برای بازیابی پس از نصب دوباره، حسابت را با گوگل یا نام کاربری ثبت کن." else "با ورود دوباره به همین حساب، خریدت قابل بازیابی است.") }, confirmButton = { Button(onClick = { buy = null; repo.acquire(item, "coins", context) }) { Text("تأیید خرید و دانلود") } }, dismissButton = { TextButton(onClick = { buy = null }) { Text("انصراف") } }) }
        remove?.let { (uuid, title) ->
            val projectNotice = if(repo.installedPackages("projects").any { it.first == uuid }) "\nنسخهٔ قابل‌ویرایش در «پروژه‌های من» حذف نمی‌شود." else ""
            AlertDialog(onDismissRequest = { remove = null }, title = { Text("حذف از حافظهٔ دستگاه؟") },
                text = { Text("$title\nخرید دائمی حفظ می‌شود؛ دریافت تبلیغاتی پس از حذف دوباره تبلیغ می‌خواهد.$projectNotice") },
                confirmButton = { TextButton(onClick = { scope.launch { repo.remove(uuid); remove = null; revision++ } }) { Text("حذف فایل‌های همین بسته") } },
                dismissButton = { TextButton(onClick = { remove = null }) { Text("انصراف") } })
        }
        preview?.let { part -> ContentPreview(part) { preview = null } }
    }
}

@Composable private fun PackagePicture(repo: ContentStoreRepository, url: String?, modifier: Modifier) {
    var picture by remember(url) { mutableStateOf<StorePicture?>(null) }
    LaunchedEffect(url) { picture = repo.picture(url) }
    Box(modifier.background(Brush.linearGradient(listOf(Color(0xFFFFE9CF), Color(0xFFEAE3FF))), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        picture?.gif?.let { AnimatedStorePicture(it, Modifier.fillMaxSize()) } ?: picture?.bitmap?.let { Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) } ?: Text("✦", fontSize = 36.sp, color = Color(0xFF9166C9))
    }
}
@Composable private fun StoreEmpty(text: String) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("✧", fontSize = 42.sp, color = Color(0xFFA37AC9)); Text(text, fontSize = 14.sp) } }
internal fun kindLabel(kind: String) = when (kind) { "sprites" -> "اسپرایت"; "backdrops" -> "پس‌زمینه"; "sounds" -> "آهنگ و صدا"; "projects" -> "پروژهٔ کامل"; else -> kind }
private fun kindIcon(kind: String) = when (kind) { "sprites" -> "🐱"; "backdrops" -> "🌄"; "projects" -> "🧩"; else -> "♫" }
private fun sizeText(bytes: Long) = "%.1f MB".format(java.util.Locale.US, bytes / 1048576.0)

/** Preview-only WebView: no JS, bridges, files, editor, or network access. */
@Composable private fun ContentPreview(part: ContentPart, close: () -> Unit) {
    val context = LocalContext.current; var image by remember { mutableStateOf<String?>(null) }; var sound by remember { mutableStateOf<File?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    val player = remember { MediaPlayer() }; var prepared by remember { mutableStateOf(false) }
    LaunchedEffect(part.uuid) {
        try {
            withContext(Dispatchers.IO) {
                val api = CourseApi(context); val c = api.contentDownload(part.preview, LearningIdentity.get(context).secret())
                try {
                    check(c.responseCode == 200) { "پیش‌نمایش فعلاً در دسترس نیست." }
                    val bytes = c.inputStream.use { it.readBytesBounded(16 * 1048576) }; val mime = c.contentType.orEmpty().substringBefore(';')
                    if (part.kind == "sounds") { val file = File(context.cacheDir, "store-preview-${part.uuid}.mp3"); file.writeBytes(bytes); sound = file }
                    else { require(mime in setOf("image/png", "image/jpeg", "image/svg+xml", "image/webp", "image/gif")); image = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP) }
                } finally { api.finishConnection(c) }
            }
        } catch (e: CancellationException) { throw e } catch (_: Exception) { error = "پیش‌نمایش آماده نشد؛ اینترنت را بررسی کن یا کمی بعد دوباره تلاش کن." }
    }
    DisposableEffect(Unit) { onDispose { player.release(); sound?.delete() } }
    AlertDialog(onDismissRequest = close, title = { Text(part.name) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 240.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            error?.let { Text(it) }
            if (part.kind == "sounds") {
                if (sound == null && error == null) CircularProgressIndicator()
                else Button(enabled = sound != null, onClick = { runCatching { if (!prepared) { player.setDataSource(sound!!.absolutePath); player.prepare(); prepared = true }; if (player.isPlaying) player.pause() else player.start() }.onFailure { error = "پخش پیش‌شنوایی ممکن نشد." } }) { Text("پخش / مکث پیش‌شنوایی") }
            } else image?.let { data -> AndroidView(modifier = Modifier.fillMaxWidth().height(200.dp), factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = false; settings.allowFileAccess = false; settings.allowContentAccess = false; settings.blockNetworkLoads = true; webViewClient = WebViewClient(); setBackgroundColor(android.graphics.Color.TRANSPARENT); loadDataWithBaseURL(null, "<html><meta name='viewport' content='width=device-width'><body style='margin:0;display:flex;justify-content:center'><img style='max-width:100%;max-height:190px' src='$data'></body></html>", "text/html", "UTF-8", null) } }, onRelease = { view -> (view.parent as? ViewGroup)?.removeView(view); view.stopLoading(); view.removeAllViews(); view.destroy() }) } ?: if (error == null) CircularProgressIndicator() else Unit
        }
    }, confirmButton = { TextButton(onClick = close) { Text("بستن") } })
}
