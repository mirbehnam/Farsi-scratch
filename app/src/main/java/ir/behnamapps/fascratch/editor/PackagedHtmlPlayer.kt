package ir.behnamapps.fascratch.editor

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.Log
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.webkit.WebViewAssetLoader
import java.io.File

data class PackagedPlayback(val file: File, val locale: String, val qualityMission: Boolean = false)

/** Owns a bridge-free WebView and confines HTML fullscreen to its content frame. */
@SuppressLint("SetJavaScriptEnabled")
class PackagedHtmlPlayer(context: Context, exportedFile: File, private val onReady: () -> Unit = {}) {
    private val file = exportedFile.canonicalFile
    val frame = FrameLayout(context)
    val web: WebView
    private var fullView: View? = null
    private var fullCallback: WebChromeClient.CustomViewCallback? = null
    val inputView: View get() = fullView ?: web
    private var disposed = false

    init {
        val root = File(context.cacheDir, "packaged_html").canonicalFile
        require(file.parentFile?.parentFile == root && file.extension == "html" && file.isFile) {
            "HTML player requires a completed export in its private cache"
        }
        val loader = WebViewAssetLoader.Builder().setDomain("player.farsiscratch.invalid")
            .addPathHandler("/player/", WebViewAssetLoader.InternalStoragePathHandler(context, file.parentFile!!)).build()
        web = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(Color.BLACK)
            webViewClient = object : WebViewClient() {
                override fun onPageCommitVisible(view: WebView, url: String) {
                    if (!disposed && url == "https://player.farsiscratch.invalid/player/${android.net.Uri.encode(file.name)}") onReady()
                }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && request.url.toString() != "https://player.farsiscratch.invalid/player/${android.net.Uri.encode(file.name)}"
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullView != null) { callback.onCustomViewHidden(); return }
                    fullView = view
                    fullCallback = callback
                    web.visibility = View.INVISIBLE
                    frame.addView(view, FrameLayout.LayoutParams(-1, -1))
                }
                override fun onHideCustomView() { exitFullView() }
            }
        }
        frame.addView(web, FrameLayout.LayoutParams(-1, -1))
        web.loadUrl("https://player.farsiscratch.invalid/player/${android.net.Uri.encode(file.name)}")
    }

    fun refresh() { exitFullView(); web.reload() }

    private fun exitFullView() {
        if (fullView == null) return
        frame.removeView(fullView)
        fullView = null
        web.visibility = View.VISIBLE
        val callback = fullCallback
        fullCallback = null
        callback?.onCustomViewHidden()
        web.requestFocus()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        exitFullView()
        frame.removeAllViews()
        web.stopLoading()
        web.onPause()
        web.destroy()
        if (!file.parentFile!!.deleteRecursively()) Log.w("PackagedPlayer", "Cannot remove HTML player cache: ${file.name}")
    }
}
