package ir.behnamapps.fascratch

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.graphics.BitmapFactory
import android.widget.Toast
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import kotlin.math.roundToInt
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.delay

val ScratchPurple = Color(0xFF855CD6)
val ScratchBlue = Color(0xFF4C97FF)
val ScratchOrange = Color(0xFFFFAB19)
val ScratchPink = Color(0xFFCF63CF)
val Ink = Color(0xFF17122E)

class MainActivity : ComponentActivity() {
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var fileChooserParams: WebChromeClient.FileChooserParams? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var activeScratchWebView: WebView? = null
    private var pendingProjectForScratch: SavedScratchProject? = null
    private var openPhonePickerForScratch = false
    private var projectLoadCommandSent = false
    private var pendingSpriteExport: File? = null
    lateinit var projectRepository: ProjectRepository
        private set
    private lateinit var projectSaveBridge: ScratchProjectSaveBridge
    var showProjectLibrary by mutableStateOf(false)
        private set
    var projectLibraryVersion by mutableIntStateOf(0)
        private set

    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val uris = if (result.resultCode == RESULT_OK) {
            when {
                data?.data != null -> arrayOf(data.data!!)
                data?.clipData != null -> Array(data.clipData!!.itemCount) { index ->
                    data.clipData!!.getItemAt(index).uri
                }
                else -> null
            }
        } else null
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
        fileChooserParams = null
        showProjectLibrary = false
    }

    private val spriteSaveLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/x.scratch.sprite3")
    ) { uri ->
        val spriteFile = pendingSpriteExport
        pendingSpriteExport = null
        if (uri != null && spriteFile != null) {
            runCatching {
                check(spriteFile.length() > 0L) { "Sprite source is empty" }
                val descriptor = contentResolver.openFileDescriptor(uri, "rwt")
                    ?: error("Could not open sprite destination")
                descriptor.use { parcelFileDescriptor ->
                    java.io.FileOutputStream(parcelFileDescriptor.fileDescriptor).use { output ->
                        val copiedBytes = spriteFile.inputStream().use { input -> input.copyTo(output) }
                        output.flush()
                        output.fd.sync()
                        check(copiedBytes == spriteFile.length()) { "Incomplete sprite export" }
                    }
                }
            }.onSuccess {
                Toast.makeText(this, "فایل کاراکتر ذخیره شد", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this, "ذخیره فایل کاراکتر انجام نشد", Toast.LENGTH_LONG).show()
            }
        }
        spriteFile?.delete()
    }

    private val webPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val request = pendingWebPermissionRequest ?: return@registerForActivityResult
        if (results.values.all { it }) request.grant(request.resources) else request.deny()
        pendingWebPermissionRequest = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        projectRepository = ProjectRepository(this)
        projectSaveBridge = ScratchProjectSaveBridge(this)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        makeFullScreen()
        setContent { FarsiScratchApp(this) }
    }

    fun openFileChooser(
        callback: ValueCallback<Array<Uri>>?,
        params: WebChromeClient.FileChooserParams?
    ): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = callback
        fileChooserParams = params
        val acceptedTypes = params?.acceptTypes.orEmpty().map(String::lowercase)
        val acceptsSprite = acceptedTypes.any { it.contains("sprite") }
        val acceptsScratchProject = !acceptsSprite && acceptedTypes.any {
            it.contains(".sb") || it.contains("scratch.sb")
        }
        if (acceptsScratchProject) {
            pendingProjectForScratch?.let { project ->
                val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", project.projectFile)
                fileChooserCallback?.onReceiveValue(arrayOf(uri))
                fileChooserCallback = null
                fileChooserParams = null
                pendingProjectForScratch = null
                return true
            }
            if (openPhonePickerForScratch) {
                openPhonePickerForScratch = false
                return launchPhoneFileChooser()
            }
            showProjectLibrary = true
            return true
        }
        if (acceptsSprite) return launchSpriteFileChooser()
        return launchPhoneFileChooser()
    }

    fun loadProjectFromPhone() {
        showProjectLibrary = false
        launchPhoneFileChooser()
    }

    fun loadSavedProject(project: SavedScratchProject) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", project.projectFile)
        fileChooserCallback?.onReceiveValue(arrayOf(uri))
        fileChooserCallback = null
        fileChooserParams = null
        showProjectLibrary = false
    }

    fun dismissProjectLibrary() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null
        fileChooserParams = null
        showProjectLibrary = false
    }

    fun queueProjectFromHome(project: SavedScratchProject) {
        pendingProjectForScratch = project
        openPhonePickerForScratch = false
        projectLoadCommandSent = false
    }

    fun queuePhonePickerFromHome() {
        pendingProjectForScratch = null
        openPhonePickerForScratch = true
        projectLoadCommandSent = false
    }

    fun triggerQueuedProjectLoad(webView: WebView) {
        if (projectLoadCommandSent) return
        val project = pendingProjectForScratch
        val script = if (project != null) {
            val url = "https://appassets.androidplatform.net/projects/${Uri.encode(project.projectFile.name)}"
            """
                (function waitForAndroidProjectLoader(attempt) {
                    if (window.AndroidScratchLoadProjectUrl) {
                        window.AndroidScratchLoadProjectUrl(${JSONObject.quote(url)}, ${JSONObject.quote(project.projectFile.name)});
                    } else if (attempt < 50) {
                        setTimeout(function () { waitForAndroidProjectLoader(attempt + 1); }, 200);
                    }
                })(0);
            """.trimIndent()
        } else if (openPhonePickerForScratch) {
            """
                (function waitForAndroidProjectLoader(attempt) {
                    if (window.AndroidScratchLoadProject) {
                        window.AndroidScratchLoadProject();
                    } else if (attempt < 50) {
                        setTimeout(function () { waitForAndroidProjectLoader(attempt + 1); }, 200);
                    }
                })(0);
            """.trimIndent()
        } else return
        projectLoadCommandSent = true
        webView.evaluateJavascript(script, null)
    }

    fun onScratchProjectLoaderReady() {
        activeScratchWebView?.let(::triggerQueuedProjectLoad)
    }

    fun onDirectProjectLoadStarted() {
        pendingProjectForScratch = null
        projectLoadCommandSent = false
    }

    fun shareProject(project: SavedScratchProject) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", project.projectFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/x.scratch.sb3"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "اشتراک‌گذاری پروژه"))
    }

    fun deleteProject(project: SavedScratchProject) {
        if (projectRepository.deleteProject(project)) {
            projectLibraryVersion++
            Toast.makeText(this, "پروژه حذف شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun launchPhoneFileChooser(): Boolean {
        val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        return runCatching { fileChooserLauncher.launch(intent) }
            .onFailure {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = null
            }.isSuccess
    }

    private fun launchSpriteFileChooser(): Boolean {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES,
                arrayOf(
                    "application/x.scratch.sprite3",
                    "application/octet-stream",
                    "application/zip",
                    "image/png",
                    "image/jpeg",
                    "image/svg+xml"
                )
            )
        }
        return runCatching { fileChooserLauncher.launch(intent) }
            .onFailure {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = null
                fileChooserParams = null
            }.isSuccess
    }

    fun attachScratchWebView(webView: WebView) {
        activeScratchWebView = webView
        webView.addJavascriptInterface(projectSaveBridge, "AndroidProjectSaver")
    }

    fun detachScratchWebView(webView: WebView) {
        webView.removeJavascriptInterface("AndroidProjectSaver")
        if (activeScratchWebView === webView) activeScratchWebView = null
    }

    fun finishProjectSave(incomingFile: File, name: String) {
        val webView = activeScratchWebView
        val saveProject: () -> Unit = {
            runCatching {
                projectRepository.commitProject(incomingFile, name)
            }.onSuccess { project ->
                projectRepository.captureProjectPreview(project) {
                    projectLibraryVersion++
                    Toast.makeText(this, "پروژه «${project.name}» ذخیره شد", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                incomingFile.delete()
                Toast.makeText(this, "ذخیره پروژه انجام نشد", Toast.LENGTH_LONG).show()
            }
        }
        // Wait for the WebView's next UI pass so View.draw captures its latest visible frame.
        if (webView != null) webView.post(saveProject) else saveProject()
    }

    fun finishSpriteSave(incomingFile: File, requestedName: String) {
        pendingSpriteExport?.delete()
        pendingSpriteExport = incomingFile
        val safeName = requestedName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(100)
            .ifBlank { "کاراکتر.sprite3" }
            .let { if (it.endsWith(".sprite3", true)) it else "$it.sprite3" }
        runCatching { spriteSaveLauncher.launch(safeName) }
            .onFailure {
                pendingSpriteExport?.delete()
                pendingSpriteExport = null
                Toast.makeText(this, "امکان انتخاب محل ذخیره وجود ندارد", Toast.LENGTH_LONG).show()
            }
    }

    fun failSpriteSave(incomingFile: File) {
        incomingFile.delete()
        Toast.makeText(this, "داده فایل کاراکتر کامل دریافت نشد", Toast.LENGTH_LONG).show()
    }

    fun requestWebPermission(request: PermissionRequest) {
        val permissions = buildList {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in request.resources) add(Manifest.permission.CAMERA)
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in request.resources) add(Manifest.permission.RECORD_AUDIO)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            request.grant(request.resources)
        } else {
            pendingWebPermissionRequest?.deny()
            pendingWebPermissionRequest = request
            webPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    fun openProfile(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun makeFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) makeFullScreen()
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        pendingWebPermissionRequest?.deny()
        pendingSpriteExport?.delete()
        projectSaveBridge.dispose()
        super.onDestroy()
    }
}

private enum class AppScreen { Home, Scratch }

@Composable
private fun FarsiScratchApp(activity: MainActivity) {
    var screen by rememberSaveable { mutableStateOf(AppScreen.Home) }
    var showSocials by rememberSaveable { mutableStateOf(false) }
    var showMyProjects by rememberSaveable { mutableStateOf(false) }
    var showExit by rememberSaveable { mutableStateOf(false) }
    var isExpired by remember { mutableStateOf(System.currentTimeMillis() >= BuildConfig.EXPIRATION_TIME_MILLIS) }

    LaunchedEffect(Unit) {
        while (!isExpired) {
            val remaining = BuildConfig.EXPIRATION_TIME_MILLIS - System.currentTimeMillis()
            if (remaining <= 0L) {
                isExpired = true
            } else {
                delay(minOf(remaining, 5_000L))
            }
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            if (isExpired) {
                ExpiredScreen(BuildConfig.UPDATE_SOURCE)
            } else {
                when (screen) {
                    AppScreen.Home -> HomeScreen(
                        onEnter = { screen = AppScreen.Scratch },
                        onFollow = { showSocials = true },
                        onMyProjects = { showMyProjects = true }
                    )
                    AppScreen.Scratch -> ScratchScreen(activity, onHome = { screen = AppScreen.Home })
                }
            }
        }

        if (!isExpired && showSocials) {
            SocialDialog(
                onDismiss = { showSocials = false },
                onOpen = { activity.openProfile(it) }
            )
        }
        if (!isExpired && showExit) {
            ExitDialog(onDismiss = { showExit = false }, onExit = activity::finish)
        }
        if (!isExpired && activity.showProjectLibrary && screen == AppScreen.Scratch) {
            val projects = remember(activity.projectLibraryVersion) {
                activity.projectRepository.listProjects()
            }
            ProjectLibraryDialog(
                projects = projects,
                onLoad = activity::loadSavedProject,
                onLoadFromPhone = activity::loadProjectFromPhone,
                onDismiss = activity::dismissProjectLibrary,
                showManagementActions = false
            )
        }
        if (!isExpired && showMyProjects && screen == AppScreen.Home) {
            val projects = remember(activity.projectLibraryVersion) {
                activity.projectRepository.listProjects()
            }
            ProjectLibraryDialog(
                projects = projects,
                onLoad = { project ->
                    activity.queueProjectFromHome(project)
                    showMyProjects = false
                    screen = AppScreen.Scratch
                },
                onLoadFromPhone = {
                    activity.queuePhonePickerFromHome()
                    showMyProjects = false
                    screen = AppScreen.Scratch
                },
                onDismiss = { showMyProjects = false },
                showManagementActions = true,
                onShare = activity::shareProject,
                onDelete = activity::deleteProject
            )
        }
    }

    BackHandler {
        if (isExpired) activity.finish()
        else if (showMyProjects) showMyProjects = false
        else if (activity.showProjectLibrary) activity.dismissProjectLibrary()
        else if (showSocials) showSocials = false
        else if (screen == AppScreen.Scratch) screen = AppScreen.Home
        else showExit = true
    }
}

@Composable
private fun ExpiredScreen(updateSource: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF34225F), Color(0xFF0F0B25)),
                    radius = 900f
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(.72f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = .09f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("نسخه منقضی شده است", color = ScratchOrange, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "این نسخه از نرم‌افزار منقضی شده است. لطفاً آن را از $updateSource به‌روزرسانی کنید.",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ProjectLibraryDialog(
    projects: List<SavedScratchProject>,
    onLoad: (SavedScratchProject) -> Unit,
    onLoadFromPhone: () -> Unit,
    onDismiss: () -> Unit,
    showManagementActions: Boolean,
    onShare: (SavedScratchProject) -> Unit = {},
    onDelete: (SavedScratchProject) -> Unit = {}
) {
    var projectPendingDelete by remember { mutableStateOf<SavedScratchProject?>(null) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(.92f)
                .fillMaxHeight(.86f)
                .border(1.dp, Color.White.copy(.14f), RoundedCornerShape(28.dp)),
            color = Color.Transparent,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2C1E50), Color(0xFF171A38)),
                            start = Offset.Zero,
                            end = Offset(1200f, 600f)
                        )
                    )
                    .padding(horizontal = 22.dp, vertical = 16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Button(
                        onClick = onLoadFromPhone,
                        colors = ButtonDefaults.buttonColors(containerColor = ScratchBlue),
                        shape = RoundedCornerShape(15.dp),
                        modifier = Modifier.height(44.dp)
                    ) {
                        Text("بارگذاری از حافظه گوشی", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Text("پروژه‌های ذخیره‌شده", color = Color.White, fontWeight = FontWeight.Black, fontSize = 23.sp)
                }
                Spacer(Modifier.height(12.dp))
                if (projects.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("هنوز پروژه‌ای ذخیره نشده است", color = Color.White.copy(.62f), fontSize = 15.sp)
                    }
                } else {
                    LazyRow(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        items(projects, key = { it.projectFile.absolutePath }) { project ->
                            SavedProjectCard(
                                project = project,
                                showManagementActions = showManagementActions,
                                onClick = { onLoad(project) },
                                onShare = { onShare(project) },
                                onDelete = { projectPendingDelete = project }
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, modifier = Modifier.height(36.dp)) {
                        Text("بستن", color = Color(0xFFFFD36A))
                    }
                }
            }
        }
    }

    projectPendingDelete?.let { project ->
        AlertDialog(
            onDismissRequest = { projectPendingDelete = null },
            containerColor = Color(0xFF241A42),
            shape = RoundedCornerShape(22.dp),
            title = { Text("حذف پروژه؟", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("پروژه «${project.name}» حذف شود؟", color = Color.White.copy(.72f)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDelete(project)
                        projectPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C5C))
                ) { Text("حذف") }
            },
            dismissButton = {
                TextButton(onClick = { projectPendingDelete = null }) {
                    Text("انصراف", color = Color.White)
                }
            }
        )
    }
}

@Composable
private fun SavedProjectCard(
    project: SavedScratchProject,
    showManagementActions: Boolean,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val preview: android.graphics.Bitmap? = remember(project.previewFile?.absolutePath, project.modifiedAt) {
        project.previewFile?.absolutePath?.let { path -> BitmapFactory.decodeFile(path) }
    }
    Card(
        modifier = Modifier.width(210.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(.09f))
    ) {
        Column(Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                    .background(Color(0xFF17142A)),
                contentAlignment = Alignment.Center
            ) {
                if (preview != null) {
                    Image(
                        bitmap = preview.asImageBitmap(),
                        contentDescription = project.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("S", color = ScratchOrange, fontSize = 42.sp, fontWeight = FontWeight.Black)
                }
            }
            Text(
                text = project.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 9.dp),
                textAlign = TextAlign.Right
            )
            if (showManagementActions) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDelete, modifier = Modifier.height(34.dp)) {
                        Text("حذف", color = Color(0xFFFF7777), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onShare, modifier = Modifier.height(34.dp)) {
                        Text("اشتراک‌گذاری", color = ScratchBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitDialog(onDismiss: () -> Unit, onExit: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF241A42),
        shape = RoundedCornerShape(26.dp),
        title = { Text("خروج از برنامه؟", color = Color.White, fontWeight = FontWeight.Bold) },
        text = { Text("مطمئنی می‌خواهی از اسکرچ فارسی خارج شوی؟", color = Color.White.copy(.72f)) },
        confirmButton = {
            Button(onClick = onExit, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5C5C))) {
                Text("بله، خروج")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف", color = Color.White) } }
    )
}

@Composable
private fun ScratchScreen(activity: MainActivity, onHome: () -> Unit) {
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var scrollFraction by remember { mutableFloatStateOf(0f) }

    Row(Modifier.fillMaxSize().background(Color(0xFF12101E))) {
        Column(
            modifier = Modifier.weight(.05f).fillMaxHeight().background(Color(0xFF1D1930)).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(.72f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(.10f))
                    .clickable(onClick = onHome),
                contentAlignment = Alignment.Center
            ) {
                Text("⌂", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(CircleShape).background(ScratchOrange))
            Spacer(Modifier.height(8.dp))
            Text("FA", color = Color.White.copy(.48f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }

        WindowsScrollBar(
            modifier = Modifier.weight(.05f).fillMaxHeight(),
            scrollFraction = scrollFraction,
            onScroll = { fraction ->
                scrollFraction = fraction
                webView?.let { view ->
                    val maxScroll = (view.contentHeight * view.scale - view.height).toInt().coerceAtLeast(0)
                    view.scrollTo(0, (maxScroll * fraction).roundToInt())
                }
            }
        )

        Box(Modifier.weight(.90f).fillMaxHeight()) {
            ScratchWebView(
                activity = activity,
                onProgress = { progress = it },
                onLoadingChanged = { isLoading = it },
                onWebViewReady = { webView = it },
                onScrollFractionChanged = { scrollFraction = it }
            )
            LoadingVisibility(isLoading = isLoading, progress = progress)
        }
    }
}

@Composable
private fun WindowsScrollBar(
    modifier: Modifier,
    scrollFraction: Float,
    onScroll: (Float) -> Unit
) {
    var trackHeightPx by remember { mutableIntStateOf(1) }
    val thumbHeight = 82.dp
    val density = androidx.compose.ui.platform.LocalDensity.current
    val thumbHeightPx = with(density) { thumbHeight.toPx() }

    Box(
        modifier = modifier
            .background(Color(0xFF151321))
            .onSizeChanged { trackHeightPx = it.height }
            .pointerInput(trackHeightPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun update(y: Float) {
                        val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                        onScroll(((y - thumbHeightPx / 2f) / travel).coerceIn(0f, 1f))
                    }
                    update(down.position.y)
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        update(change.position.y)
                        change.consume()
                    } while (event.changes.any { it.pressed })
                }
            }
            .padding(horizontal = 4.dp)
    ) {
        val travelPx = (trackHeightPx - thumbHeightPx).coerceAtLeast(0f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(thumbHeight)
                .offset { IntOffset(0, (travelPx * scrollFraction.coerceIn(0f, 1f)).roundToInt()) }
                .clip(RoundedCornerShape(5.dp))
                .background(Color(0xFF4C4A58)),
            contentAlignment = Alignment.Center
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(3) {
                    Box(Modifier.width(14.dp).height(2.dp).background(Color(0xFF9B99A7)))
                }
            }
        }
    }
}

@Composable
private fun LoadingVisibility(isLoading: Boolean, progress: Int) {
    AnimatedVisibility(
        visible = isLoading,
        enter = fadeIn() + scaleIn(initialScale = .96f),
        exit = fadeOut() + scaleOut(targetScale = 1.03f)
    ) {
        LoadingOverlay(progress)
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ScratchWebView(
    activity: MainActivity,
    onProgress: (Int) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onWebViewReady: (WebView?) -> Unit,
    onScrollFractionChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val webView = remember {
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .addPathHandler("/projects/") { path ->
                activity.projectRepository.openProjectForWeb(path)
            }
            .build()

        WebView(context).apply {
            setBackgroundColor(android.graphics.Color.WHITE)
            activity.attachScratchWebView(this)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
                mediaPlaybackRequiresUserGesture = false
                userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    assetLoader.shouldInterceptRequest(request.url)

                override fun onPageFinished(view: WebView?, url: String?) {
                    onProgress(100)
                    onLoadingChanged(false)
                    view?.let(activity::triggerQueuedProjectLoad)
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    onProgress(newProgress)
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    request?.let(activity::requestWebPermission)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean = activity.openFileChooser(filePathCallback, fileChooserParams)
            }
            setOnScrollChangeListener { view, _, scrollY, _, _ ->
                val web = view as WebView
                val maxScroll = (web.contentHeight * web.scale - web.height).coerceAtLeast(0f)
                onScrollFractionChanged(if (maxScroll == 0f) 0f else (scrollY / maxScroll).coerceIn(0f, 1f))
            }
            loadUrl("https://appassets.androidplatform.net/assets/build/index.html")
        }
    }

    DisposableEffect(webView) {
        onWebViewReady(webView)
        onDispose {
            onWebViewReady(null)
            activity.detachScratchWebView(webView)
            webView.stopLoading()
            webView.webChromeClient = null
            webView.destroy()
        }
    }
    AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun LoadingOverlay(progress: Int) {
    val transition = rememberInfiniteTransition(label = "scratch-loading")
    val rotation by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1350, easing = androidx.compose.animation.core.LinearEasing)),
        label = "loader-rotation"
    )
    val pulse by transition.animateFloat(
        .94f, 1.04f,
        infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "loader-pulse"
    )

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF34225F), Color(0xFF17122E)), radius = 850f)
        ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize().rotate(rotation)) {
                    val colors = listOf(ScratchBlue, ScratchPurple, ScratchPink, ScratchOrange)
                    colors.forEachIndexed { index, color ->
                        rotate(index * 90f) {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset(size.width / 2 - 10f, 2f),
                                size = Size(20f, 44f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(9f, 9f)
                            )
                        }
                    }
                }
                Box(
                    Modifier.size(55.dp).scale(pulse).clip(CircleShape).background(Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    Text("S", color = ScratchOrange, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
            }
            Spacer(Modifier.height(22.dp))
            Text("در حال آماده‌کردن اسکرچ...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(7.dp))
            Text("$progress٪", color = Color.White.copy(.55f), fontSize = 13.sp)
        }
    }
}
