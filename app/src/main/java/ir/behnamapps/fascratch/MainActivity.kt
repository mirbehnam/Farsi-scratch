package ir.behnamapps.fascratch

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.webkit.WebViewAssetLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null

    // File chooser launcher for HTML5 input file (Upload image/audio/sprite in Scratch)
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data: Intent? = result.data
            val results: Array<Uri>? = when {
                data?.data != null -> arrayOf(data.data!!)
                data?.clipData != null -> {
                    val count = data.clipData!!.itemCount
                    Array(count) { i -> data.clipData!!.getItemAt(i).uri }
                }
                else -> null
            }
            fileChooserCallback?.onReceiveValue(results)
        } else {
            fileChooserCallback?.onReceiveValue(null)
        }
        fileChooserCallback = null
    }

    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen orientation to Landscape only
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE

        // Hide ActionBar if available
        supportActionBar?.hide()

        // Hide Status bar and Navigation bar for Fullscreen mode
        makeFullScreen()

        setContentView(R.layout.activity_main)

        // Close button action: show exit confirmation dialog
        findViewById<View>(R.id.btnClose).setOnClickListener {
            showExitConfirmationDialog()
        }

        webView = findViewById(R.id.webView)

        // Windows-style ScrollBar Track and Thumb Handle
        val scrollBarTrack = findViewById<View>(R.id.scrollBarTrack)
        val scrollThumb = findViewById<View>(R.id.scrollThumb)

        scrollBarTrack.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val trackHeight = scrollBarTrack.height.toFloat()
                val thumbHeight = scrollThumb.height.toFloat()
                val maxThumbY = (trackHeight - thumbHeight).coerceAtLeast(1f)

                // Calculate new Y for thumb handle
                var newThumbY = event.y - (thumbHeight / 2)
                newThumbY = newThumbY.coerceIn(0f, maxThumbY)
                scrollThumb.translationY = newThumbY

                // Scroll WebView accordingly
                val scrollRatio = newThumbY / maxThumbY
                val totalWebContentHeight = (webView.contentHeight * webView.scale).toInt()
                val maxWebScroll = (totalWebContentHeight - webView.height).coerceAtLeast(0)

                webView.scrollTo(0, (scrollRatio * maxWebScroll).toInt())
                true
            } else {
                false
            }
        }

        // Set up AndroidX WebViewAssetLoader for "Go Live / HTTP Server" behavior
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        with(webView.settings) {
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

            // Set Desktop Mode User-Agent
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false

            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                request?.grant(request.resources)
            }

            // Handle file picker / gallery uploads from Scratch (<input type="file">)
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                fileChooserCallback?.onReceiveValue(null)
                fileChooserCallback = filePathCallback

                val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                }

                try {
                    fileChooserLauncher.launch(intent)
                } catch (e: Exception) {
                    fileChooserCallback?.onReceiveValue(null)
                    fileChooserCallback = null
                    return false
                }
                return true
            }
        }

        // Load via virtual domain HTTPS scheme (Go Live style)
        webView.loadUrl("https://appassets.androidplatform.net/assets/build/index.html")
    }

    private fun showExitConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("خروج از برنامه")
            .setMessage("آیا مطمئن هستید که می‌خواهید از برنامه خارج شوید؟")
            .setPositiveButton("بله، خروج") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .setNegativeButton("انصراف") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun makeFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            makeFullScreen()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        showExitConfirmationDialog()
    }
}
