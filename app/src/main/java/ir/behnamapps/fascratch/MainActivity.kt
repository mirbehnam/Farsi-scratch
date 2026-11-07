package ir.behnamapps.fascratch
import androidx.compose.runtime.collectAsState

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.graphics.BitmapFactory
import android.view.View
import android.view.KeyEvent
import android.view.ViewConfiguration
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import ir.behnamapps.fascratch.editor.EditorAssetsConfig
import ir.behnamapps.fascratch.editor.EditorEngine
import ir.behnamapps.fascratch.editor.EditorEngineDialog
import ir.behnamapps.fascratch.editor.EditorAssetsPathHandler
import ir.behnamapps.fascratch.editor.PackagedHtmlPlayer
import ir.behnamapps.fascratch.editor.PackagedPlayback
import ir.behnamapps.fascratch.editor.PackagedHtmlRepository
import ir.behnamapps.fascratch.editor.SavedHtmlProject
import ir.behnamapps.fascratch.editor.ProjectLibraryDialog
import ir.behnamapps.fascratch.editor.PackagedExportBridge
import com.google.firebase.analytics.FirebaseAnalytics
import kotlin.math.roundToInt
import java.io.File
import org.json.JSONObject
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

val ScratchPurple = Color(0xFF855CD6)
val ScratchBlue = Color(0xFF4C97FF)
val ScratchOrange = Color(0xFFFFAB19)
val ScratchPink = Color(0xFFCF63CF)
val Ink = Color(0xFF17122E)

class MainActivity : ComponentActivity() {
    internal val appLaunchPolicy by lazy { androidx.lifecycle.ViewModelProvider(this)[ir.behnamapps.fascratch.inappbilling.data.AppLaunchPolicy::class.java] }

    // Main-thread callback only; no Compose state changes or extra WebView listeners
    // for each touch/move. The coding effect owns and removes this callback.
    internal var editorInteractionListener: (() -> Unit)? = null

    override fun onUserInteraction() {
        super.onUserInteraction()
        editorInteractionListener?.invoke()
    }

    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE ||
            event.actionMasked == android.view.MotionEvent.ACTION_POINTER_DOWN) editorInteractionListener?.invoke()
        return super.dispatchTouchEvent(event)
    }

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var fileChooserParams: WebChromeClient.FileChooserParams? = null
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private var activeScratchWebView: WebView? = null
    private var activePackagedPlayer: PackagedHtmlPlayer? = null
    internal var packagedPlayback by mutableStateOf<PackagedPlayback?>(null)
        private set
    private val scratchKeyDownTimes = mutableMapOf<Int, Long>()
    private val scratchKeyRepeatCounts = mutableMapOf<Int, Int>()
    private val scratchTextInputBridge = ScratchTextInputBridge(this)
    private var pendingProjectForScratch: SavedScratchProject? = null
    private var homePhonePickerCallback: ((SavedScratchProject?) -> Unit)? = null
    private var projectLoadCommandSent = false
    private var notificationPermissionResult: ((Boolean) -> Unit)? = null
    lateinit var projectRepository: ProjectRepository
        private set
    private lateinit var projectSaveBridge: ScratchProjectSaveBridge
    lateinit var htmlProjectRepository: PackagedHtmlRepository
        private set
    var htmlLibraryVersion by mutableIntStateOf(0)
        private set
    var preparingSavedHtml by mutableStateOf(false)
        private set
    private lateinit var packagedExportBridge: PackagedExportBridge
    var showProjectLibrary by mutableStateOf(false)
        private set
    var projectLibraryVersion by mutableIntStateOf(0)
        private set
    internal var scratchTextInputSession by mutableStateOf<ScratchTextInputSession?>(null)
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
        val homeCallback = homePhonePickerCallback
        if (homeCallback != null) {
            homePhonePickerCallback = null
            val uri = uris?.firstOrNull()
            if (uri == null) homeCallback(null) else lifecycleScope.launch {
                val project = try {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val folder = File(cacheDir, "phone_imports/${java.util.UUID.randomUUID()}").apply { mkdirs() }
                        try {
                            val displayName = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Project.sb3"
                            val filename = displayName.map { if (it == '/' || it == '\\' || it.isISOControl()) '_' else it }.joinToString("").take(180)
                                .takeUnless { it.isBlank() || it == "." || it == ".." } ?: "Project.sb3"
                            val file = File(folder, filename)
                            requireNotNull(contentResolver.openInputStream(uri)) { "Cannot read selected project" }.use { input ->
                                file.outputStream().use { input.copyTo(it) }
                            }
                            val saved = projectRepository.commitProject(file, file.name)
                            folder.delete()
                            saved
                        } catch (error: Exception) {
                            folder.deleteRecursively()
                            throw error
                        }
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (error: Exception) {
                    android.util.Log.e("PhoneProject", "Cannot import selected phone project", error)
                    Toast.makeText(this@MainActivity, "بازکردن فایل پروژه انجام نشد", Toast.LENGTH_SHORT).show()
                    null
                }
                if (project != null) projectLibraryVersion++
                homeCallback(project)
            }
            return@registerForActivityResult
        }
        fileChooserCallback?.onReceiveValue(uris)
        fileChooserCallback = null
        fileChooserParams = null
        showProjectLibrary = false
    }

    private val webPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val request = pendingWebPermissionRequest ?: return@registerForActivityResult
        if (results.values.all { it }) request.grant(request.resources) else request.deny()
        pendingWebPermissionRequest = null
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        completeNotificationPermissionIntro()
        notificationPermissionResult?.invoke(granted)
        notificationPermissionResult = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ir.behnamapps.fascratch.inappbilling.data.PurchaseConfirmationJob.schedule(applicationContext)
        appLaunchPolicy.start(applicationContext)
        ir.behnamapps.fascratch.inappbilling.data.ClientCommands.launchPolicy = appLaunchPolicy
        runCatching { ir.behnamapps.fascratch.inappbilling.learning.ContentPackageInstaller.get(filesDir).recover() }
        NotificationChannels.create(this)
        FirebaseAnalytics.getInstance(this)
            .setUserProperty("distribution", BuildConfig.FLAVOR)
        File(filesDir, "downloaded_sprites").mkdirs()
        File(filesDir, "downloaded_backdrops").mkdirs()
        File(filesDir, "downloaded_sounds").mkdirs()
        projectRepository = ProjectRepository(this)
        htmlProjectRepository = PackagedHtmlRepository(File(filesDir, "packaged_projects"))
        projectSaveBridge = ScratchProjectSaveBridge(this)
        packagedExportBridge = PackagedExportBridge(this)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        makeFullScreen()
        ir.behnamapps.fascratch.home.HomeBannerCache.warm(applicationContext)
        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                while (true) {
                    ir.behnamapps.fascratch.inappbilling.data.ClientCommands.refresh(applicationContext)
                    delay(30_000)
                }
            }
        }
        setContent { FarsiScratchApp(this) }
    }

    override fun onStart() {
        super.onStart()
        ir.behnamapps.fascratch.inappbilling.learning.LearningRepository.get(this).visit()
    }

    fun requestNotificationPermission(onResult: (Boolean) -> Unit) {
        val needsPermission =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        if (!needsPermission) {
            completeNotificationPermissionIntro()
            onResult(true)
            return
        }

        notificationPermissionResult = onResult
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun shouldShowNotificationPermissionIntro(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        ) return false

        return !getSharedPreferences("notification_preferences", MODE_PRIVATE)
            .getBoolean("permission_intro_completed", false)
    }

    fun completeNotificationPermissionIntro() {
        getSharedPreferences("notification_preferences", MODE_PRIVATE)
            .edit()
            .putBoolean("permission_intro_completed", true)
            .apply()
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
        projectLoadCommandSent = false
    }

    fun pickProjectFromPhone(onPicked: (SavedScratchProject?) -> Unit) {
        homePhonePickerCallback = onPicked
        try {
            fileChooserLauncher.launch(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            })
        } catch (error: Exception) {
            homePhonePickerCallback = null
            android.util.Log.e("PhoneProject", "Cannot open system project picker", error)
            Toast.makeText(this, "بازکردن حافظهٔ گوشی انجام نشد", Toast.LENGTH_SHORT).show()
            onPicked(null)
        }
    }

    fun triggerQueuedProjectLoad(webView: WebView) {
        if (projectLoadCommandSent) return
        val project = pendingProjectForScratch
        val script = if (project != null) {
            val importRoot = File(cacheDir, "phone_imports").canonicalFile
            val imported = project.projectFile.canonicalFile.startsWith(importRoot)
            val path = if (imported) "imports/${project.projectFile.relativeTo(importRoot).invariantSeparatorsPath}"
                else "projects/${project.projectFile.name}"
            val url = "https://appassets.androidplatform.net/${Uri.encode(path, "/")}"
            """
                (function waitForAndroidProjectLoader(attempt) {
                    if (window.AndroidScratchLoadProjectUrl) {
                        window.AndroidScratchLoadProjectUrl(${JSONObject.quote(url)}, ${JSONObject.quote(project.projectFile.name)});
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
        packagedExportBridge.attach(webView)
        activeScratchWebView = webView
        webView.addJavascriptInterface(projectSaveBridge, "AndroidProjectSaver")
        webView.addJavascriptInterface(scratchTextInputBridge, "AndroidScratchTextInput")
    }

    fun detachScratchWebView(webView: WebView) {
        packagedExportBridge.detach(webView)
        webView.removeJavascriptInterface("AndroidProjectSaver")
        webView.removeJavascriptInterface("AndroidScratchTextInput")
        if (activeScratchWebView === webView) {
            releaseScratchKeys()
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            fileChooserParams = null
            showProjectLibrary = false
            pendingProjectForScratch = null
            projectLoadCommandSent = false
            activeScratchWebView = null
            scratchTextInputSession = null
        }
    }

    fun installScratchTextInputSupport(webView: WebView) {
        webView.evaluateJavascript(SCRATCH_TEXT_INPUT_SCRIPT, null)
    }

    internal fun openScratchTextInput(
        id: String,
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        multiline: Boolean
    ) {
        val safeStart = selectionStart.coerceIn(0, text.length)
        val safeEnd = selectionEnd.coerceIn(safeStart, text.length)
        scratchTextInputSession = ScratchTextInputSession(
            id = id,
            text = text,
            selectionStart = safeStart,
            selectionEnd = safeEnd,
            multiline = multiline
        )
    }

    internal fun updateScratchTextInputFromWeb(
        id: String,
        text: String,
        selectionStart: Int,
        selectionEnd: Int
    ) {
        val current = scratchTextInputSession ?: return
        if (current.id != id) return
        val safeStart = selectionStart.coerceIn(0, text.length)
        val safeEnd = selectionEnd.coerceIn(safeStart, text.length)
        scratchTextInputSession = current.copy(
            text = text,
            selectionStart = safeStart,
            selectionEnd = safeEnd
        )
    }

    fun updateScratchTextInput(id: String, value: TextFieldValue) {
        val current = scratchTextInputSession ?: return
        if (current.id != id) return
        scratchTextInputSession = current.copy(
            text = value.text,
            selectionStart = value.selection.start,
            selectionEnd = value.selection.end
        )
        val script = """
            window.__farsiScratchNativeInput && window.__farsiScratchNativeInput.setValue(
                ${JSONObject.quote(id)},
                ${JSONObject.quote(value.text)},
                ${value.selection.start},
                ${value.selection.end}
            );
        """.trimIndent()
        activeScratchWebView?.evaluateJavascript(script, null)
    }

    fun finishScratchTextInput() {
        val session = scratchTextInputSession ?: return
        scratchTextInputSession = null
        val script = """
            window.__farsiScratchNativeInput &&
                window.__farsiScratchNativeInput.finish(${JSONObject.quote(session.id)});
        """.trimIndent()
        activeScratchWebView?.evaluateJavascript(script, null)
    }

    fun onHtmlProjectSaved() {
        htmlLibraryVersion++
        ir.behnamapps.fascratch.inappbilling.learning.LearningRepository.get(this).recordEditorMission("html_export")
    }

    fun playSavedHtml(project: SavedHtmlProject, onOpened: () -> Unit) {
        if (preparingSavedHtml) return
        preparingSavedHtml = true
        lifecycleScope.launch {
            try {
                val file = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    htmlProjectRepository.preparePlayback(project, File(cacheDir, "packaged_html"))
                }
                openPackagedPlayback(file, "fa")
                onOpened()
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Exception) {
                android.util.Log.e("HtmlLibrary", "Cannot play saved HTML: ${project.file.name}", error)
                Toast.makeText(this@MainActivity, "اجرای فایل انجام نشد", Toast.LENGTH_SHORT).show()
            } finally { preparingSavedHtml = false }
        }
    }

    fun shareSavedHtml(project: SavedHtmlProject) {
        try {
            htmlProjectRepository.requireOwned(project)
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", project.file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/html"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newRawUri(project.file.name, uri)
            }
            startActivity(Intent.createChooser(intent, "اشتراک‌گذاری فایل HTML"))
        } catch (error: Exception) {
            android.util.Log.e("HtmlLibrary", "Cannot share saved HTML: ${project.file.name}", error)
            Toast.makeText(this, "اشتراک‌گذاری فایل انجام نشد", Toast.LENGTH_SHORT).show()
        }
    }

    fun openPackagedPlayback(file: File, locale: String, qualityMission: Boolean = false) {
        releaseScratchKeys()
        packagedPlayback = PackagedPlayback(file, locale, qualityMission)
    }

    fun attachPackagedPlayer(player: PackagedHtmlPlayer) {
        activePackagedPlayer = player
    }

    fun detachPackagedPlayer(player: PackagedHtmlPlayer) {
        if (activePackagedPlayer === player) {
            releaseScratchKeys()
            activePackagedPlayer = null
        }
        player.dispose()
    }

    fun refreshPackagedPlayback() {
        releaseScratchKeys()
        activePackagedPlayer?.refresh()
    }

    fun closePackagedPlayback() {
        releaseScratchKeys()
        packagedPlayback = null
    }

    private fun releaseScratchKeys() {
        scratchKeyDownTimes.keys.toList().forEach { dispatchScratchKey(it, false) }
    }

    fun dispatchScratchKey(keyCode: Int, pressed: Boolean) {
        val webView = activePackagedPlayer?.inputView ?: activeScratchWebView ?: return
        val eventTime = SystemClock.uptimeMillis()
        if (pressed) {
            val wasAlreadyPressed = scratchKeyDownTimes.containsKey(keyCode)
            val downTime = scratchKeyDownTimes.getOrPut(keyCode) { eventTime }
            val repeatCount = if (wasAlreadyPressed) {
                (scratchKeyRepeatCounts[keyCode] ?: 0) + 1
            } else {
                0
            }
            scratchKeyRepeatCounts[keyCode] = repeatCount
            webView.dispatchKeyEvent(
                KeyEvent(
                    downTime,
                    eventTime,
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    repeatCount
                )
            )
        } else {
            val downTime = scratchKeyDownTimes.remove(keyCode) ?: eventTime
            scratchKeyRepeatCounts.remove(keyCode)
            webView.dispatchKeyEvent(
                KeyEvent(
                    downTime,
                    eventTime,
                    KeyEvent.ACTION_UP,
                    keyCode,
                    0
                )
            )
        }
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
        val safeName = requestedName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .trim()
            .take(100)
            .ifBlank { "کاراکتر.sprite3" }
            .let { if (it.endsWith(".sprite3", true)) it else "$it.sprite3" }
        var sharedFile: File? = null
        runCatching {
            check(incomingFile.length() > 0L) { "Sprite source is empty" }
            val shareDirectory = File(cacheDir, "shared_sprites").apply { mkdirs() }
            val expiryTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            shareDirectory.listFiles().orEmpty()
                .filter { it.isFile && it.lastModified() < expiryTime }
                .forEach(File::delete)

            var candidate = File(shareDirectory, safeName)
            var suffix = 2
            while (candidate.exists()) {
                candidate = File(
                    shareDirectory,
                    "${safeName.removeSuffix(".sprite3")} ($suffix).sprite3"
                )
                suffix++
            }
            sharedFile = candidate
            if (!incomingFile.renameTo(candidate)) {
                incomingFile.copyTo(candidate)
                incomingFile.delete()
            }

            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", candidate)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/x.scratch.sprite3"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newUri(contentResolver, candidate.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "اشتراک‌گذاری کاراکتر"))
        }
            .onFailure {
                sharedFile?.delete()
                incomingFile.delete()
                Toast.makeText(this, "اشتراک‌گذاری فایل کاراکتر انجام نشد", Toast.LENGTH_LONG).show()
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

    fun openOtherApp(packageName: String) {
        val storeUrl = if (BuildConfig.FLAVOR == "myket") {
            "https://myket.ir/app/$packageName"
        } else {
            "https://cafebazaar.ir/app/$packageName"
        }
        openProfile(storeUrl)
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
        notificationPermissionResult = null
        homePhonePickerCallback = null
        fileChooserCallback?.onReceiveValue(null)
        pendingWebPermissionRequest?.deny()
        projectSaveBridge.dispose()
        packagedExportBridge.dispose()
        activePackagedPlayer?.let(::detachPackagedPlayer)
        super.onDestroy()
    }
}

private enum class AppScreen { Home, Scratch, HtmlPlayback }

@Composable
private fun FarsiScratchApp(activity: MainActivity) {
    val stop by activity.appLaunchPolicy.state.collectAsState()
    if (stop != null) { ir.behnamapps.fascratch.inappbilling.presentation.AppStopScreen(stop!!); return }
    var screen by rememberSaveable { mutableStateOf(AppScreen.Home) }
    var htmlTabRequested by remember { mutableStateOf(false) }
    val editorConfig = remember(activity) { EditorAssetsConfig.load(activity.assets) }
    var selectedEngine by rememberSaveable { mutableStateOf(editorConfig.defaultEngine) }
    var showEnginePicker by rememberSaveable { mutableStateOf(false) }
    var showSocials by rememberSaveable { mutableStateOf(false) }
    var showMyProjects by rememberSaveable { mutableStateOf(false) }
    var projectAwaitingEngine by rememberSaveable { mutableStateOf<String?>(null) }
    var showExit by rememberSaveable { mutableStateOf(false) }
    var showNotificationPermission by rememberSaveable { mutableStateOf(false) }
    var permissionRequestInProgress by remember { mutableStateOf(false) }
    var permissionResult by remember { mutableStateOf<Boolean?>(null) }
    var isExpired by remember { mutableStateOf(System.currentTimeMillis() >= BuildConfig.EXPIRATION_TIME_MILLIS) }
    var quickLessonsVisible by remember { mutableStateOf(false) }
    ir.behnamapps.fascratch.inappbilling.learning.CodingTimeEffect(activity, screen == AppScreen.Scratch && !isExpired && !showNotificationPermission && !quickLessonsVisible)

    LaunchedEffect(permissionResult) {
        val granted = permissionResult ?: return@LaunchedEffect
        delay(if (granted) 850L else 300L)
        showNotificationPermission = false
        permissionResult = null
        screen = AppScreen.Scratch
    }

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

    LaunchedEffect(screen, activity.packagedPlayback) {
        if (screen == AppScreen.HtmlPlayback && activity.packagedPlayback == null) {
            screen = AppScreen.Home
            showMyProjects = true
        }
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = Ink) {
            if (isExpired) {
                ExpiredScreen(BuildConfig.UPDATE_SOURCE)
            } else {
                if (showNotificationPermission) {
                    NotificationPermissionScreen(
                        granted = permissionResult == true,
                        requestInProgress = permissionRequestInProgress,
                        onRequestPermission = {
                            if (!permissionRequestInProgress && permissionResult == null) {
                                permissionRequestInProgress = true
                                activity.requestNotificationPermission { granted ->
                                    permissionRequestInProgress = false
                                    permissionResult = granted
                                }
                            }
                        },
                        onSkip = {
                            if (!permissionRequestInProgress && permissionResult == null) {
                                activity.completeNotificationPermissionIntro()
                                permissionResult = false
                            }
                        }
                    )
                } else when (screen) {
                    AppScreen.Home -> HomeScreen(
                        onEnter = {
                            showEnginePicker = true
                        },
                        onFollow = { showSocials = true },
                        onMyProjects = { htmlTabRequested = false; showMyProjects = true },
                        onTraining = { activity.startActivity(Intent(activity, ir.behnamapps.fascratch.inappbilling.presentation.TrainingActivity::class.java)) },
                        onLunaPlayer = { activity.openOtherApp("ir.behnamapps.lunamusic") },
                        onMafiaApp = { activity.openOtherApp("ir.behnamapp.mafia") }
                    )
                    AppScreen.HtmlPlayback -> ScratchScreen(
                        activity = activity, engine = selectedEngine,
                        onQuickLessonsVisibility = { quickLessonsVisible = it }, onHome = {}, playbackOnly = true
                    )
                    AppScreen.Scratch -> ScratchScreen(
                        activity = activity,
                        engine = selectedEngine,
                        onQuickLessonsVisibility = { quickLessonsVisible = it },
                        onHome = { showExit = true }
                    )
                }
            }
        }

        if (!isExpired && showEnginePicker && screen == AppScreen.Home) {
            EditorEngineDialog(
                availableEngines = editorConfig.availableEngines,
                onDismiss = { showEnginePicker = false },
                onSelect = { engine ->
                    selectedEngine = engine
                    showEnginePicker = false
                    if (activity.shouldShowNotificationPermissionIntro()) {
                        showNotificationPermission = true
                    } else {
                        screen = AppScreen.Scratch
                    }
                }
            )
        }
        if (!isExpired && showSocials) {
            SocialDialog(
                onDismiss = { showSocials = false },
                onOpen = { activity.openProfile(it) }
            )
        }
        if (!isExpired && showExit && screen == AppScreen.Scratch) {
            ExitDialog(
                onDismiss = { showExit = false },
                onExit = {
                    showExit = false
                    activity.finishScratchTextInput()
                    screen = AppScreen.Home
                }
            )
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
        if (!isExpired && showMyProjects && projectAwaitingEngine == null && screen == AppScreen.Home) {
            val projects = remember(activity.projectLibraryVersion) {
                activity.projectRepository.listProjects()
            }
            androidx.compose.runtime.key(activity.projectLibraryVersion) {
                ProjectLibraryDialog(
                    projects = projects,
                    onLoad = { project ->
                        projectAwaitingEngine = project.projectFile.name
                    },
                    onLoadFromPhone = {
                        activity.pickProjectFromPhone { project ->
                            if (project != null) {
                                Toast.makeText(activity, "پروژه به فهرست پروژه‌های من اضافه شد", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    onDismiss = { showMyProjects = false },
                    showManagementActions = true,
                    htmlProjects = remember(activity.htmlLibraryVersion) { activity.htmlProjectRepository.listProjects() },
                    initialHtmlTab = htmlTabRequested,
                    preparingHtml = activity.preparingSavedHtml,
                    onPlayHtml = { project ->
                        activity.playSavedHtml(project) {
                            htmlTabRequested = true
                            showMyProjects = false
                            screen = AppScreen.HtmlPlayback
                        }
                    },
                    onShareHtml = activity::shareSavedHtml,
                    onShare = activity::shareProject,
                    onDelete = activity::deleteProject
                )
            }
        }
        if (!isExpired && showMyProjects && projectAwaitingEngine != null && screen == AppScreen.Home) {
            EditorEngineDialog(
                availableEngines = editorConfig.availableEngines,
                onDismiss = { projectAwaitingEngine = null },
                onSelect = { engine ->
                    val project = activity.projectRepository.listProjects()
                        .firstOrNull { it.projectFile.name == projectAwaitingEngine }
                    projectAwaitingEngine = null
                    if (project != null) {
                        selectedEngine = engine
                        activity.queueProjectFromHome(project)
                        showMyProjects = false
                        screen = AppScreen.Scratch
                    }
                }
            )
        }
    }

    BackHandler {
        if (isExpired) activity.finish()
        else if (activity.preparingSavedHtml) Unit
        else if (activity.packagedPlayback != null) activity.closePackagedPlayback()
        else if (showExit) showExit = false
        else if (projectAwaitingEngine != null) projectAwaitingEngine = null
        else if (showMyProjects) showMyProjects = false
        else if (activity.showProjectLibrary) activity.dismissProjectLibrary()
        else if (showSocials) showSocials = false
        else if (screen == AppScreen.Scratch) showExit = true
        else activity.finish()
    }
}

@Composable
private fun NotificationPermissionScreen(
    granted: Boolean,
    requestInProgress: Boolean,
    onRequestPermission: () -> Unit,
    onSkip: () -> Unit
) {
    val accent = if (granted) Color(0xFF35C86F) else ScratchBlue

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF253B73), Color(0xFF0D0A1C)),
                    radius = 1100f
                )
            )
            .padding(horizontal = 20.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(.86f),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF191631).copy(alpha = .96f))
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(accent.copy(alpha = .18f))
                        .border(2.dp, accent.copy(alpha = .72f), RoundedCornerShape(22.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (granted) "✓" else "🔔",
                        color = accent,
                        fontSize = if (granted) 42.sp else 34.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(Modifier.width(28.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                Text(
                    text = if (granted) "دسترسی با موفقیت تأیید شد" else "پیام‌های اسکرچ فارسی",
                    color = if (granted) accent else Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "برای دریافت پیام‌های نرم‌افزار اسکرچ و اطلاع از به‌روزرسانی‌های جدید، دسترسی اعلان‌ها را تأیید کنید.",
                    color = Color.White.copy(alpha = .72f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onRequestPermission,
                    enabled = !requestInProgress && !granted,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        disabledContainerColor = if (granted) Color(0xFF35C86F) else accent.copy(.55f),
                        disabledContentColor = Color.White
                    )
                ) {
                    if (requestInProgress) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Color.White,
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(25.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .border(2.dp, Color.White, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (granted) Text("✓", color = Color.White, fontWeight = FontWeight.Black)
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (granted) "تأیید شد" else "تأیید دسترسی اعلان‌ها",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (!granted) {
                    TextButton(onClick = onSkip, enabled = !requestInProgress) {
                        Text("فعلاً نه؛ ورود به اسکرچ", color = Color.White.copy(alpha = .62f))
                    }
                }
                }
            }
        }
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
internal fun SavedProjectCard(
    project: SavedScratchProject,
    showManagementActions: Boolean,
    onClick: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    previewHeight: androidx.compose.ui.unit.Dp = 96.dp,
    cardHeight: androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp.Infinity
) {
    val preview: android.graphics.Bitmap? = remember(project.previewFile?.absolutePath, project.modifiedAt) {
        project.previewFile?.absolutePath?.let { path -> BitmapFactory.decodeFile(path) }
    }
    Card(
        modifier = Modifier.width(210.dp).heightIn(max = cardHeight).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(.09f))
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Box(
                modifier = Modifier.fillMaxWidth().height(previewHeight)
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
internal fun ScratchScreen(activity: MainActivity, engine: EditorEngine, onQuickLessonsVisibility: (Boolean) -> Unit, onHome: () -> Unit, playbackOnly: Boolean = false) {
    var isLoading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val playback = activity.packagedPlayback
    var scrollFraction by remember { mutableFloatStateOf(0f) }
    var showFloatingKeyboard by rememberSaveable { mutableStateOf(false) }
    var showGamepad by rememberSaveable { mutableStateOf(false) }
    var showGamepadSettings by rememberSaveable { mutableStateOf(false) }
    val gamepadPreferences = remember { activity.getSharedPreferences("gamepad_settings", android.content.Context.MODE_PRIVATE) }
    var gamepadLayout by remember { mutableStateOf(GamepadLayout.decode(gamepadPreferences.getString("layout", null))) }
    val gamepadDispatch = remember(activity) { GamepadKeyDispatch(activity::dispatchScratchKey) }
    val stickKeyChange: (Int, Boolean) -> Unit = remember(gamepadDispatch) { { code, down -> gamepadDispatch.change(0, code, down) } }
    val actionKeyChange: (Int, Boolean) -> Unit = remember(gamepadDispatch) { { code, down -> gamepadDispatch.change(1, code, down) } }
    var keyboardX by remember { mutableFloatStateOf(0f) }
    var keyboardY by remember { mutableFloatStateOf(0f) }
    var rootWidth by remember { mutableIntStateOf(0) }
    var rootHeight by remember { mutableIntStateOf(0) }
    var keyboardWidth by remember { mutableIntStateOf(0) }
    var keyboardHeight by remember { mutableIntStateOf(0) }
    var keyboardPositioned by remember { mutableStateOf(false) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val keyboardMarginPx = with(density) { 8.dp.toPx() }

    LaunchedEffect(rootWidth, rootHeight, keyboardWidth, keyboardHeight) {
        if (rootWidth <= 0 || rootHeight <= 0 || keyboardWidth <= 0 || keyboardHeight <= 0) {
            return@LaunchedEffect
        }
        val maxX = (rootWidth - keyboardWidth - keyboardMarginPx).coerceAtLeast(keyboardMarginPx)
        val maxY = (rootHeight - keyboardHeight - keyboardMarginPx).coerceAtLeast(keyboardMarginPx)
        if (!keyboardPositioned) {
            keyboardX = keyboardMarginPx
            keyboardY = maxY
            keyboardPositioned = true
        } else {
            keyboardX = keyboardX.coerceIn(keyboardMarginPx, maxX)
            keyboardY = keyboardY.coerceIn(keyboardMarginPx, maxY)
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF12101E))
            .onSizeChanged {
                rootWidth = it.width
                rootHeight = it.height
            }
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(
            modifier = Modifier.weight(.05f).fillMaxHeight().background(Color(0xFF1D1930)).padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (playback == null) {
                ScratchRailButton("⌂", "خانه", onClick = onHome)
            } else {
                val closeLabel = when (playback.locale) { "ar" -> "إغلاق"; "en" -> "Close"; else -> "بستن" }
                val refreshLabel = when (playback.locale) { "ar" -> "تحديث"; "en" -> "Refresh"; else -> "رفرش" }
                ScratchRailButton("×", closeLabel, onClick = activity::closePackagedPlayback)
                Spacer(Modifier.height(7.dp))
                ScratchRailButton("↻", refreshLabel, onClick = activity::refreshPackagedPlayback)
            }
            Spacer(Modifier.height(7.dp))
            ScratchRailButton("⌨", "صفحه‌کلید", active = showFloatingKeyboard,
                onClick = { showGamepad = false; showFloatingKeyboard = !showFloatingKeyboard })
            Spacer(Modifier.height(7.dp))
            ScratchRailButton("🎮", "گیم‌پد", active = showGamepad,
                onClick = { showFloatingKeyboard = false; showGamepad = !showGamepad })
            ir.behnamapps.fascratch.inappbilling.presentation.ScratchQuickLessonsShortcut(activity, onQuickLessonsVisibility)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(8.dp).clip(CircleShape).background(ScratchOrange))
            Spacer(Modifier.height(8.dp))
            Text("FA", color = Color.White.copy(.48f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
        }

            if (playback == null) {
                WindowsScrollBar(
                    modifier = Modifier.weight(.05f).fillMaxHeight()
                        .semantics { contentDescription = "پیمایش ویرایشگر" },
                    scrollFraction = scrollFraction,
                    onScroll = { fraction ->
                        scrollFraction = fraction
                        webView?.let { view ->
                            val maxScroll = (view.contentHeight * view.scale - view.height).toInt().coerceAtLeast(0)
                            view.scrollTo(0, (maxScroll * fraction).roundToInt())
                        }
                    }
                )
            }

            Box(Modifier.weight(if (playback == null) .90f else .95f).fillMaxHeight()) {
                if (!playbackOnly) ScratchWebView(
                    activity = activity,
                    engine = engine,
                    visible = playback == null,
                    onProgress = { progress = it },
                    onLoadingChanged = { isLoading = it },
                    onWebViewReady = { webView = it },
                    onScrollFractionChanged = { scrollFraction = it }
                )
                if (!playbackOnly && playback == null) LoadingVisibility(isLoading = isLoading, progress = progress)
                if (playback != null) PackagedPlaybackView(activity, playback)
            }
        }

        if (showFloatingKeyboard || (showGamepad && !showGamepadSettings)) {
            val panelModifier = Modifier
                .offset { IntOffset(keyboardX.roundToInt(), keyboardY.roundToInt()) }
                .onSizeChanged { keyboardWidth = it.width; keyboardHeight = it.height }
            val dragPanel: (Float, Float) -> Unit = { deltaX, deltaY ->
                val maxX = (rootWidth - keyboardWidth - keyboardMarginPx).coerceAtLeast(keyboardMarginPx)
                val maxY = (rootHeight - keyboardHeight - keyboardMarginPx).coerceAtLeast(keyboardMarginPx)
                keyboardX = (keyboardX + deltaX).coerceIn(keyboardMarginPx, maxX)
                keyboardY = (keyboardY + deltaY).coerceIn(keyboardMarginPx, maxY)
            }
            if (showGamepad) FloatingScratchGamepad(
                modifier = panelModifier, onDrag = dragPanel,
                onClose = { showGamepad = false }, onKeyChange = stickKeyChange
            ) else FloatingScratchKeyboard(
                modifier = Modifier
                    .offset { IntOffset(keyboardX.roundToInt(), keyboardY.roundToInt()) }
                    .onSizeChanged {
                        keyboardWidth = it.width
                        keyboardHeight = it.height
                    },
                onDrag = { deltaX, deltaY ->
                    val maxX = (rootWidth - keyboardWidth - keyboardMarginPx)
                        .coerceAtLeast(keyboardMarginPx)
                    val maxY = (rootHeight - keyboardHeight - keyboardMarginPx)
                        .coerceAtLeast(keyboardMarginPx)
                    keyboardX = (keyboardX + deltaX).coerceIn(keyboardMarginPx, maxX)
                    keyboardY = (keyboardY + deltaY).coerceIn(keyboardMarginPx, maxY)
                },
                onClose = { showFloatingKeyboard = false },
                onKeyChange = activity::dispatchScratchKey
            )
        }

        if (showGamepad && !showGamepadSettings) {
            FloatingGamepadActions(layout = gamepadLayout, onSettings = { showGamepadSettings = true }, onClose = { showGamepad = false }, onKeyChange = actionKeyChange)
        }
        if (showGamepad && showGamepadSettings) {
            GamepadSettingsDialog(gamepadLayout, onDismiss = { showGamepadSettings = false }, onSave = {
                gamepadLayout = it
                gamepadPreferences.edit().putString("layout", GamepadLayout.encode(it)).apply()
                showGamepadSettings = false
            })
        }

        activity.scratchTextInputSession?.let { session ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(.72f)
                    .widthIn(max = 720.dp)
                    .imePadding()
                    .padding(bottom = 8.dp)
            ) {
                ScratchTextInputBar(
                    session = session,
                    onValueChange = { activity.updateScratchTextInput(session.id, it) },
                    onDone = activity::finishScratchTextInput
                )
            }
        }
    }
}

@Composable
private fun ScratchRailButton(icon: String, label: String, active: Boolean = false, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth(.72f).height(36.dp).clip(RoundedCornerShape(12.dp))
            .background(if (active) ScratchPurple.copy(.72f) else Color.White.copy(.10f))
            .semantics { contentDescription = label }.clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(icon, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun PackagedPlaybackView(activity: MainActivity, playback: PackagedPlayback) {
    val player = remember(playback.file, playback.qualityMission) { PackagedHtmlPlayer(activity, playback.file) {
        if (playback.qualityMission) ir.behnamapps.fascratch.inappbilling.learning.LearningRepository.get(activity).recordEditorMission("quality_run")
    } }
    DisposableEffect(player) {
        activity.attachPackagedPlayer(player)
        onDispose { activity.detachPackagedPlayer(player) }
    }
    AndroidView(factory = { player.frame }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun FloatingScratchKeyboard(
    modifier: Modifier,
    onDrag: (Float, Float) -> Unit,
    onClose: () -> Unit,
    onKeyChange: (Int, Boolean) -> Unit
) {
    Surface(
        modifier = modifier.width(134.dp),
        color = Color(0xEE211B35),
        shape = RoundedCornerShape(11.dp),
        shadowElevation = 10.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(25.dp)
                    .background(Color(0xFF332A4E))
                    .pointerInput(onDrag) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.x, dragAmount.y)
                        }
                    }
                    .padding(start = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("⠿", color = Color.White.copy(.62f), fontSize = 15.sp)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(25.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Center
                ) {
                    Text("×", color = Color.White.copy(.82f), fontSize = 17.sp)
                }
            }

            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        ScratchKeyButton(
                            label = "↑",
                            keyCode = KeyEvent.KEYCODE_DPAD_UP,
                            onKeyChange = onKeyChange
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ScratchKeyButton(
                            label = "←",
                            keyCode = KeyEvent.KEYCODE_DPAD_LEFT,
                            onKeyChange = onKeyChange
                        )
                        ScratchKeyButton(
                            label = "↓",
                            keyCode = KeyEvent.KEYCODE_DPAD_DOWN,
                            onKeyChange = onKeyChange
                        )
                        ScratchKeyButton(
                            label = "→",
                            keyCode = KeyEvent.KEYCODE_DPAD_RIGHT,
                            onKeyChange = onKeyChange
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    ScratchKeyButton(
                        label = "SPACE",
                        keyCode = KeyEvent.KEYCODE_SPACE,
                        onKeyChange = onKeyChange,
                        modifier = Modifier.fillMaxWidth().height(31.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun ScratchKeyButton(
    label: String,
    keyCode: Int,
    onKeyChange: (Int, Boolean) -> Unit,
    modifier: Modifier = Modifier.size(38.dp),
    glass: Boolean = false
) {
    var pressed by remember { mutableStateOf(false) }
    val buttonScale by animateFloatAsState(
        targetValue = if (pressed) .91f else 1f,
        animationSpec = tween(durationMillis = if (pressed) 70 else 110),
        label = "scratch-key-scale"
    )
    val buttonColor by animateColorAsState(
        targetValue = if (pressed) ScratchBlue else if (glass) Color(0x553E3458) else Color(0xFFF4F1FA),
        animationSpec = tween(durationMillis = 90),
        label = "scratch-key-color"
    )
    Box(
        modifier = modifier
            .scale(buttonScale)
            .clip(RoundedCornerShape(8.dp))
            .background(buttonColor)
            .border(1.dp, Color.Black.copy(.16f), RoundedCornerShape(8.dp))
            .pointerInput(keyCode, onKeyChange) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onKeyChange(keyCode, true)
                        try {
                            coroutineScope {
                                val repeatJob = launch {
                                    delay(ViewConfiguration.getLongPressTimeout().toLong())
                                    while (isActive) {
                                        onKeyChange(keyCode, true)
                                        delay(50L)
                                    }
                                }
                                try {
                                    tryAwaitRelease()
                                } finally {
                                    repeatJob.cancel()
                                }
                            }
                        } finally {
                            onKeyChange(keyCode, false)
                            pressed = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        if (label in listOf("↑", "↓", "←", "→")) GamepadKeyLabel(label, if (pressed || glass) Color.White else Color(0xFF211B35))
        else Text(
            text = label,
            color = if (pressed || glass) Color.White else Color(0xFF211B35),
            fontSize = if (label == "SPACE") 10.sp else 21.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun ScratchTextInputBar(
    session: ScratchTextInputSession,
    onValueChange: (TextFieldValue) -> Unit,
    onDone: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var value by remember(session.id) {
        mutableStateOf(
            TextFieldValue(
                text = session.text,
                selection = TextRange(session.text.length)
            )
        )
    }

    LaunchedEffect(session.id) {
        focusRequester.requestFocus()
        onValueChange(value)
        keyboardController?.show()
    }
    LaunchedEffect(session.text, session.selectionStart, session.selectionEnd) {
        if (session.text != value.text) {
            value = TextFieldValue(
                text = session.text,
                selection = TextRange(session.selectionStart, session.selectionEnd)
            )
        }
    }
    BackHandler {
        keyboardController?.hide()
        onDone()
    }

    Surface(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        color = Color(0xFFF8F7FC),
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = value,
                onValueChange = {
                    value = it
                    onValueChange(it)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .focusRequester(focusRequester)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFF201936),
                    fontSize = 16.sp,
                    textAlign = TextAlign.Start
                ),
                singleLine = !session.multiline,
                maxLines = if (session.multiline) 2 else 1,
                keyboardOptions = KeyboardOptions(
                    imeAction = if (session.multiline) ImeAction.Default else ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboardController?.hide()
                        onDone()
                    }
                ),
                cursorBrush = SolidColor(ScratchPurple),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.text.isEmpty()) {
                            Text("متن را وارد کنید", color = Color(0xFF777184), fontSize = 14.sp)
                        }
                        innerTextField()
                    }
                }
            )
            TextButton(
                onClick = {
                    value = TextFieldValue(text = "", selection = TextRange.Zero)
                    onValueChange(value)
                    focusRequester.requestFocus()
                    keyboardController?.show()
                },
                enabled = value.text.isNotEmpty(),
                modifier = Modifier.height(48.dp)
            ) {
                Text("پاک کردن")
            }
            TextButton(
                onClick = {
                    keyboardController?.hide()
                    onDone()
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text("تأیید", color = ScratchPurple, fontWeight = FontWeight.Bold)
            }
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
    engine: EditorEngine,
    visible: Boolean = true,
    onProgress: (Int) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onWebViewReady: (WebView?) -> Unit,
    onScrollFractionChanged: (Float) -> Unit
) {
    val context = LocalContext.current
    val webView = remember(engine) {
        val editorAssets = EditorAssetsConfig.load(context.assets)
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", EditorAssetsPathHandler(context, editorAssets))
            .addPathHandler("/imports/", WebViewAssetLoader.InternalStoragePathHandler(context,
                File(context.cacheDir, "phone_imports").apply { mkdirs() }))
            .addPathHandler("/downloaded-sprites/", ir.behnamapps.fascratch.editor.DownloadedSpritesPathHandler(context))
            .addPathHandler("/downloaded-backdrops/", ir.behnamapps.fascratch.editor.DownloadedMediaPathHandler(context, "backdrops"))
            .addPathHandler("/downloaded-sounds/", ir.behnamapps.fascratch.editor.DownloadedMediaPathHandler(context, "sounds"))
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
                    view?.let {
                        activity.installScratchTextInputSupport(it)
                        activity.triggerQueuedProjectLoad(it)
                    }
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
            loadUrl(editorAssets.entryUrl(engine))
        }
    }

    AndroidView(
        factory = { webView.also(onWebViewReady) },
        modifier = Modifier.fillMaxSize(),
        onRelease = { released ->
            onWebViewReady(null)
            activity.detachScratchWebView(released)
            released.stopLoading()
            released.onPause()
            (released.parent as? android.view.ViewGroup)?.removeView(released)
            released.webChromeClient = null
            released.webViewClient = WebViewClient()
            released.setOnScrollChangeListener(null)
            released.removeAllViews()
            released.destroy()
        },
        update = { it.visibility = if (visible) View.VISIBLE else View.INVISIBLE }
    )
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
