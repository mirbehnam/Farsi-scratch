package ir.behnamapps.fascratch.editor

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import ir.behnamapps.fascratch.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class QueuedProjectDeviceTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test fun savedProjectQueuedBeforeTurboStartsLoadsOnceWithoutTheInvalidProjectDialog() = verify(EditorEngine.TURBO, false)
    @Test fun downloadedProjectOpensInTurboWarp() = verify(EditorEngine.TURBO, true)
    @Test fun downloadedProjectOpensInScratch() = verify(EditorEngine.SCRATCH, true)
    @SuppressLint("SetJavaScriptEnabled")
    private fun verify(engine: EditorEngine, downloaded: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val filename = "QueuedLoadTest-${System.currentTimeMillis()}"
        var fixture: File? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var web: WebView
            try {
                scenario.onActivity { activity ->
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val incoming = File(if(downloaded) activity.cacheDir else File(activity.filesDir,"scratch_projects"), "$filename.sb3").apply {
                        activity.assets.open("turbobuild/packager/example.sb3").use { input ->
                            outputStream().use { input.copyTo(it) }
                        }
                    }
                    fixture = if(downloaded) activity.projectRepository.importStoreProject(incoming,filename,"${java.util.UUID.randomUUID()}:${java.util.UUID.randomUUID()}").also { incoming.delete() } else incoming
                    val saved = activity.projectRepository.listProjects().single { it.name == filename }
                    activity.queueProjectFromHome(saved)
                    val config = EditorAssetsConfig.load(activity.assets)
                    val loader = WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", EditorAssetsPathHandler(activity, config))
                        .addPathHandler("/downloaded-sprites/", DownloadedSpritesPathHandler(activity))
                        .addPathHandler("/downloaded-backdrops/", DownloadedMediaPathHandler(activity,"backdrops"))
                        .addPathHandler("/downloaded-sounds/", DownloadedMediaPathHandler(activity,"sounds"))
                        .addPathHandler("/projects/") { activity.projectRepository.openProjectForWeb(it) }.build()
                    web = WebView(activity).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                        activity.attachScratchWebView(this)
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                if (request.url.host == "appassets.androidplatform.net") return loader.shouldInterceptRequest(request.url)
                                return WebResourceResponse("text/plain", "UTF-8", 404, "Offline test", emptyMap(),
                                    ByteArrayInputStream(ByteArray(0)))
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                activity.triggerQueuedProjectLoad(view)
                            }
                        }
                        activity.setContentView(this)
                        loadUrl(config.entryUrl(engine))
                    }
                }
                fun evaluate(script: String): String {
                    val result = AtomicReference<String?>()
                    instrumentation.runOnMainSync { web.evaluateJavascript(script) { result.set(it) } }
                    val end = SystemClock.elapsedRealtime() + 10000
                    while (result.get() == null && SystemClock.elapsedRealtime() < end) Thread.sleep(30)
                    return result.get() ?: error("Queued project WebView did not respond")
                }
                val end = SystemClock.elapsedRealtime() + 60000
                var loaded = false
                while (SystemClock.elapsedRealtime() < end) {
                    loaded = evaluate("""
                        (()=>{if(!window.ReduxStore)for(const b of document.querySelectorAll('button')){let f=b[Object.keys(b).find(k=>k.startsWith('__reactFiber$'))];while(f){if(f.memoizedProps?.store?.getState)window.ReduxStore=f.memoizedProps.store;f=f.return;}}
                        return Boolean(window.ReduxStore && ReduxStore.getState().scratchGui.projectTitle==='$filename' && ReduxStore.getState().scratchGui.projectState.loadingState==='SHOWING_WITHOUT_ID');})()
                    """.trimIndent()) == "true"
                    if (loaded) break
                    Thread.sleep(100)
                }
                assertTrue("Cold saved-project load failed: ${evaluate("document.body.innerText.slice(-900)")}", loaded)
                assertEquals("true", evaluate("!(ReduxStore.getState().scratchGui.tw && ReduxStore.getState().scratchGui.tw.projectError) && !document.body.innerText.includes('Could not load project')"))
                Thread.sleep(300)
                assertEquals("\"$filename\"", evaluate("ReduxStore.getState().scratchGui.projectTitle"))
                instrumentation.runOnMainSync { web.stopLoading(); (web.parent as? android.view.ViewGroup)?.removeView(web); web.destroy() }
            } finally { fixture?.delete() }
        }
    }
}
