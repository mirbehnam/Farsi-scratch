package ir.behnamapps.fascratch.inappbilling.presentation

import android.annotation.SuppressLint
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.*
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import ir.behnamapps.fascratch.BuildConfig
import ir.behnamapps.fascratch.inappbilling.data.readBytesBounded
import ir.behnamapps.fascratch.inappbilling.domain.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** No JavascriptInterface, tokens, SDK hooks, cookies, file access or remote navigation. */
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
@Composable
internal fun RemoteCatalog(layout: CatalogLayout, state: TrainingState, onSelect: (Course) -> Unit,
    onFailure: () -> Unit, modifier: Modifier, native: @Composable () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val latestState by rememberUpdatedState(state)
    val select by rememberUpdatedState(onSelect)
    val fail by rememberUpdatedState(onFailure)
    var ready by remember(layout.sha256) { mutableStateOf(false) }
    val handler = remember { Handler(Looper.getMainLooper()) }
    var web by remember(layout.sha256) { mutableStateOf<WebView?>(null) }
    var pageLoaded by remember(layout.sha256) { mutableStateOf(false) }
    var lastHeartbeat by remember(layout.sha256) { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var stopped by remember(layout.sha256) { mutableStateOf(false) }
    var disposed by remember(layout.sha256) { mutableStateOf(false) }
    fun fallback() {
        if (!disposed && !stopped) {
            stopped = true; ready = false; fail()
        }
    }
    fun update(view: WebView) {
        if (stopped || disposed) return
        val payload = catalogPayload(latestState).toString()
            .replace("<", "\\u003c").replace("\u2028", "\\u2028").replace("\u2029", "\\u2029")
        runCatching {
            view.evaluateJavascript("try{window.__scratchUpdate($payload);true}catch(e){false}") { result ->
                if (result != "true") fallback()
            }
        }.onFailure { fallback() }
    }
    val watchdog = remember(layout.sha256) {
        object : Runnable {
            override fun run() {
                if (disposed || stopped || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
                if (SystemClock.elapsedRealtime() - lastHeartbeat > 1800) fallback()
                else handler.postDelayed(this, 250)
            }
        }
    }
    DisposableEffect(layout.sha256, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                handler.removeCallbacks(watchdog); web?.onPause()
            } else if (event == Lifecycle.Event.ON_START && !stopped) {
                lastHeartbeat = SystemClock.elapsedRealtime(); web?.onResume()
                handler.removeCallbacks(watchdog); handler.postDelayed(watchdog, 250)
            }
        }
        lifecycle.addObserver(observer)
        handler.postDelayed(watchdog, 250)
        onDispose {
            disposed = true
            lifecycle.removeObserver(observer); handler.removeCallbacks(watchdog)
            web?.let { view ->
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                // A renderer-crashed WebView is still removed/destroyed, never reused.
                runCatching { view.stopLoading() }
                runCatching { view.destroy() }
            }
            web = null
        }
    }
    val payload = catalogPayload(state).toString()
    LaunchedEffect(payload, pageLoaded) { if (pageLoaded) web?.let { update(it) } }
    Box(modifier) {
        AndroidView(modifier = Modifier.fillMaxSize().alpha(if (ready) 1f else 0f), factory = { ctx ->
            FrameLayout(ctx).also { host ->
                try {
                    if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                        handler.post { fallback() }
                    } else {
                        val view = WebView(context)
                        web = view
                        view.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        view.settings.apply {
                            javaScriptEnabled = true
                            allowFileAccess = false; allowContentAccess = false
                            @Suppress("DEPRECATION")
                            allowFileAccessFromFileURLs = false
                            @Suppress("DEPRECATION")
                            allowUniversalAccessFromFileURLs = false
                            domStorageEnabled = false; databaseEnabled = false
                            javaScriptCanOpenWindowsAutomatically = false; setSupportMultipleWindows(false)
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            if (Build.VERSION.SDK_INT >= 26) safeBrowsingEnabled = true
                        }
                        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false)
                        view.webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
                            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback) { callback.invoke(origin, false, false) }
                            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult): Boolean { result.cancel(); return true }
                            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult): Boolean { result.cancel(); return true }
                            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult): Boolean { result.cancel(); return true }
                        }
                        view.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                            @Deprecated("Legacy WebView callback")
                            override fun shouldOverrideUrlLoading(view: WebView, url: String) = true
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                                return catalogImage(request)
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                if (url?.trimEnd('/') != CatalogLayoutPolicy.ORIGIN) { fallback(); return }
                                pageLoaded = true; update(view)
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest, error: WebResourceError?) { if (request.isForMainFrame) fallback() }
                            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest, response: WebResourceResponse?) { if (request.isForMainFrame) fallback() }
                            override fun onReceivedSslError(view: WebView?, callback: SslErrorHandler, error: SslError?) { callback.cancel(); fallback() }
                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean { fallback(); return true }
                        }
                        WebViewCompat.addWebMessageListener(view, "ScratchNative", setOf(CatalogLayoutPolicy.ORIGIN)) { source, message, origin, mainFrame, _ ->
                            if (disposed || stopped || !mainFrame || origin.toString().trimEnd('/') != CatalogLayoutPolicy.ORIGIN) return@addWebMessageListener
                            val json = runCatching { message.data?.takeIf { it.length < 1024 }?.let(::JSONObject) }.getOrNull() ?: return@addWebMessageListener
                            when (json.optString("type")) {
                                "error" -> fallback()
                                "heartbeat" -> runCatching {
                                    source.evaluateJavascript("window.__scratchHealthy && window.__scratchHealthy() === true") { healthy ->
                                        if (!disposed && !stopped && healthy == "true") {
                                            lastHeartbeat = SystemClock.elapsedRealtime(); ready = true
                                        } else if (healthy != "true") fallback()
                                    }
                                }.onFailure { fallback() }.let { }
                                "view-course" -> CatalogLayoutPolicy.courseAction(json.optString("type"), json.optString("courseId"), mainFrame,
                                    origin.toString().trimEnd('/'), latestState.busy, latestState.courses)?.let(select)
                            }
                        }
                        host.addView(view, FrameLayout.LayoutParams(-1, -1))
                        view.loadDataWithBaseURL(CatalogLayoutPolicy.ORIGIN + "/", layout.document, "text/html", "UTF-8", null)
                    }
                } catch (_: Exception) { handler.post { fallback() } }
            }
        })
        // Never replace a working native screen with a blank WebView/loading spinner.
        if (!ready || stopped) native()
    }
}

private fun catalogPayload(state: TrainingState): JSONObject = JSONObject().put("busy", state.busy).put("courses", JSONArray().apply {
    state.courses.forEach { c -> put(JSONObject().put("id", c.id).put("title", c.title).put("description", c.description)
        .put("instructor", c.instructor).put("coverUrl", c.coverUrl ?: JSONObject.NULL)
        .put("priceToman", c.serverPriceToman?.takeIf { state.prices[c.id] != null } ?: JSONObject.NULL)
        .put("compareAtToman", c.compareAtToman ?: JSONObject.NULL).put("discountEndsAtMillis", c.discountEndsAtMillis ?: JSONObject.NULL)
        .put("purchased", c.id in state.purchasedIds)) }
})

private fun catalogImage(request: WebResourceRequest): WebResourceResponse {
    fun blocked() = WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(byteArrayOf()))
    val target = request.url.toString()
    val base = BuildConfig.COURSE_API_BASE.trimEnd('/')
    // Only the anonymous, public image route. No video, query, navigation or user cookies.
    if (request.method != "GET" || request.isForMainFrame || !CoursePolicy.sameOrigin(base, target)) return blocked()
    val uri = Uri.parse(target)
    val prefix = Uri.parse(base).path + "/media/"
    if (uri.query != null || uri.fragment != null || !uri.path.orEmpty().startsWith(prefix)) return blocked()
    if (runCatching { CoursePolicy.uuid(uri.path!!.removePrefix(prefix)) }.isFailure) return blocked()
    val connection = runCatching { URL(target).openConnection() as HttpsURLConnection }.getOrNull() ?: return blocked()
    return try {
        connection.connectTimeout = 1500; connection.readTimeout = 1500; connection.instanceFollowRedirects = false; connection.useCaches = false
        val mime = connection.contentType?.substringBefore(';')
        if (connection.responseCode != 200 || mime !in setOf("image/webp", "image/png", "image/jpeg", "image/gif")) blocked()
        else WebResourceResponse(mime, null, ByteArrayInputStream(connection.inputStream.use { it.readBytesBounded(3 * 1024 * 1024) }))
    } catch (_: Exception) { blocked() } finally { connection.disconnect() }
}
