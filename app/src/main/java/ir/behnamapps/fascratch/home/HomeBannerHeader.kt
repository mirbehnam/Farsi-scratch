package ir.behnamapps.fascratch.home

import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import ir.behnamapps.fascratch.R

@Composable
internal fun HomeBannerHeader(default: Painter, modifier: Modifier, onOpenCourses: () -> Unit) {
    if (LocalInspectionMode.current) { HomeBannerFrame(default, null, false, "", modifier, {}); return }
    val context = LocalContext.current.applicationContext
    val snapshot by HomeBannerCache.snapshots.collectAsState()
    var selection by remember { mutableStateOf<BannerSelection?>(null) }
    var resolved by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { HomeBannerCache.warm(context) }
    LaunchedEffect(snapshot.initialized) {
        if (snapshot.initialized && !resolved) {
            selection = HomeBannerCache.select(context)
            resolved = true
        }
    }
    LaunchedEffect(snapshot.initialized) { if (snapshot.initialized) HomeBannerCache.refresh(context) }
    LaunchedEffect(snapshot.revision, selection?.banner?.id, selection?.banner?.hash) {
        val selected = selection
        if (selected != null) {
            val current = snapshot.banners.firstOrNull { it.id == selected.banner.id && it.hash == selected.banner.hash }
            selection = current?.let { selected.copy(banner = it) }
            if (current?.videoUrl != video) video = null
        }
    }
    // asImageBitmap() creates a new wrapper. Home's decorative animations recompose
    // this header frequently: keep the wrapper stable or Crossfade restarts every frame.
    val bitmap = selection?.bitmap
    val displayImage = remember(bitmap) { bitmap?.asImageBitmap() }
    // Resolve the local snapshot before painting: never flash the default under a cached banner.
    if (!resolved) Box(modifier)
    else HomeBannerFrame(default, displayImage, selection?.banner?.videoUrl != null,
        selection?.banner?.caption.orEmpty(), modifier,
        onPlay = { video = selection?.banner?.videoUrl }, onOpenCourses = onOpenCourses)
    video?.let { url -> PublicIntroPlayer(url, selection?.banner?.title.orEmpty()) { video = null } }
}

/** Pure fixed-frame UI, also used by previews: remote dimensions never affect Home layout. */
@Composable
internal fun HomeBannerFrame(default: Painter, image: ImageBitmap?, hasVideo: Boolean, caption: String,
    modifier: Modifier, onPlay: () -> Unit, onOpenCourses: () -> Unit = {}) {
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF11173D))
        .border(1.dp, Color.White.copy(.18f), RoundedCornerShape(16.dp))) {
        Crossfade(targetState = image, modifier = Modifier.fillMaxSize()
            .clickable(onClickLabel = "مشاهده دوره‌ها", onClick = onOpenCourses),
            animationSpec = tween(300), label = "home-banner-fade") { bitmap ->
            if (bitmap == null) Image(default, "نمایی از اسکرچ فارسی", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Image(bitmap, "بنر معرفی دوره", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        if (hasVideo) {
            Row(Modifier.align(Alignment.BottomEnd).padding(12.dp).widthIn(max = 290.dp)
                .clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0xDD102331))))
                .semantics { contentDescription = "پخش ویدئوی معرفی دوره" }
                .clickable(onClick = onPlay).padding(start = if (caption.isBlank()) 5.dp else 20.dp, end = 5.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (caption.isNotBlank()) Text(caption, color = Color.White, fontFamily = FontFamily(Font(R.font.shabnam)),
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color.White.copy(.95f)), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
                        val triangle = Path().apply { moveTo(size.width * .18f, 0f); lineTo(size.width, size.height / 2); lineTo(size.width * .18f, size.height); close() }
                        drawPath(triangle, Color(0xFF135B58))
                    }
                }
            }
        }
    }
}

@Composable
private fun PublicIntroPlayer(url: String, title: String, dismiss: () -> Unit) {
    var player by remember(url) { mutableStateOf<VideoView?>(null) }
    var error by remember(url) { mutableStateOf(false) }
    var prepared by remember(url) { mutableStateOf(false) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(url, attempt) {
        kotlinx.coroutines.delay(20_000)
        if (!prepared) { error = true; runCatching { player?.stopPlayback() } }
    }
    DisposableEffect(lifecycle, url) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) player?.pause() }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            player?.apply { setOnPreparedListener(null); setOnErrorListener(null); stopPlayback() }
            player = null
        }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (!error && HomeBannerCache.allowedMedia(url)) key(attempt) { AndroidView(modifier = Modifier.fillMaxSize(), factory = { context ->
                VideoView(context).apply {
                    player = this
                    val controller = MediaController(context)
                    controller.setAnchorView(this); setMediaController(controller)
                    setOnPreparedListener { prepared = true; if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start() }
                    setOnErrorListener { _, _, _ -> error = true; runCatching { stopPlayback() }; true }
                    runCatching { setVideoURI(Uri.parse(url)) }.onFailure { error = true }
                }
            }) }
            if (!prepared && !error) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            if (error) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ویدئو فعلاً در دسترس نیست.", color = Color.White)
                TextButton(onClick = { prepared = false; error = false; attempt++ }) { Text("تلاش دوباره", color = Color.White) }
            }
            TextButton(onClick = dismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(.65f), CircleShape)) {
                Text("بستن ×", color = Color.White)
            }
            Text(title, color = Color.White, modifier = Modifier.align(Alignment.TopStart).padding(16.dp), maxLines = 1)
        }
    }
}

@Preview(name = "Banner + video caption", group = "Home / Banners", widthDp = 500, heightDp = 232, locale = "fa")
@Composable private fun HomeBannerVideoPreview() {
    HomeBannerFrame(painterResource(R.drawable.meo_header), ImageBitmap.imageResource(R.drawable.meo_header), true, "یک دقیقه با اسکرچ آشنا شو", Modifier.fillMaxSize(), {})
}
@Preview(name = "Banner + minimal play", group = "Home / Banners", widthDp = 500, heightDp = 232, locale = "fa")
@Composable private fun HomeBannerPlayPreview() {
    HomeBannerFrame(painterResource(R.drawable.meo_header), ImageBitmap.imageResource(R.drawable.meo_header), true, "", Modifier.fillMaxSize(), {})
}
@Preview(name = "Banner without video", group = "Home / Banners", widthDp = 500, heightDp = 232, locale = "fa")
@Composable private fun HomeBannerStillPreview() {
    HomeBannerFrame(painterResource(R.drawable.meo_header), null, false, "", Modifier.fillMaxSize(), {})
}
