package ir.behnamapps.fascratch.editor

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import java.io.File

/** A separate player WebView never inherits the editor's JavaScript interfaces. */
class PackagedPlayerActivity : ComponentActivity() {
    private lateinit var web: WebView
    private lateinit var player: FrameLayout
    private lateinit var file: File
    private var fullView: View? = null
    private var fullCallback: WebChromeClient.CustomViewCallback? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        file = File(requireNotNull(intent.getStringExtra("file"))).canonicalFile
        val root = File(cacheDir, "packaged_html").canonicalFile
        require(file.parentFile?.parentFile == root && file.extension == "html" && file.isFile) {
            "Packaged player requires a completed HTML export in its private cache"
        }
        val locale = intent.getStringExtra("locale")
        val rtl = locale == "fa" || locale == "ar"
        val dark = intent.getBooleanExtra("dark", false)
        val foreground = if (dark) Color.WHITE else Color.rgb(45, 50, 60)
        val surface = if (dark) Color.rgb(40, 43, 49) else Color.rgb(233, 241, 252)
        val close = when (locale) { "fa" -> "بستن"; "ar" -> "إغلاق"; else -> "Close" }
        val refresh = when (locale) { "fa" -> "رفرش"; "ar" -> "تحديث"; else -> "Refresh" }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
            setBackgroundColor(Color.BLACK)
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setBackgroundColor(surface)
        }
        toolbar.addView(TextView(this).apply {
            text = file.name
            setTextColor(foreground)
            textSize = 15f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        fun button(label: String, icon: Int, action: () -> Unit): Button = Button(this).apply {
            text = label
            contentDescription = label
            isAllCaps = false
            textSize = 14f
            setTextColor(foreground)
            minHeight = dp(40)
            minimumHeight = dp(40)
            minimumWidth = dp(88)
            compoundDrawablePadding = dp(6)
            val drawable = getDrawable(icon)!!.mutate().apply {
                setTint(foreground)
                setBounds(0, 0, dp(20), dp(20))
            }
            setCompoundDrawablesRelative(drawable, null, null, null)
            backgroundTintList = android.content.res.ColorStateList.valueOf(surface)
            setOnClickListener { action() }
        }
        toolbar.addView(button(refresh, android.R.drawable.ic_popup_sync) { exitFullView(); web.reload() })
        toolbar.addView(button(close, android.R.drawable.ic_menu_close_clear_cancel) { finish() })
        layout.addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        player = FrameLayout(this)
        layout.addView(player, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val loader = WebViewAssetLoader.Builder().setDomain("player.farsiscratch.invalid").addPathHandler("/player/",
            WebViewAssetLoader.InternalStoragePathHandler(this, file.parentFile!!)).build()
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(Color.BLACK)
            // No project/text/export bridge, file chooser or popup handler is installed here.
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                    loader.shouldInterceptRequest(request.url)
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    if (fullView != null) { callback.onCustomViewHidden(); return }
                    fullView = view
                    fullCallback = callback
                    player.removeAllViews()
                    player.addView(view, FrameLayout.LayoutParams(-1, -1))
                }
                override fun onHideCustomView() { exitFullView() }
            }
        }
        player.addView(web, FrameLayout.LayoutParams(-1, -1))
        setContentView(layout)
        web.loadUrl("https://player.farsiscratch.invalid/player/${android.net.Uri.encode(file.name)}")
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun exitFullView() {
        if (fullView == null) return
        player.removeAllViews()
        fullView = null
        fullCallback?.onCustomViewHidden()
        fullCallback = null
        player.addView(web, FrameLayout.LayoutParams(-1, -1))
    }

    override fun onPause() {
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            player.removeAllViews()
            web.stopLoading()
            web.destroy()
        }
        if (isFinishing && ::file.isInitialized) file.parentFile?.deleteRecursively()
        super.onDestroy()
    }
}
