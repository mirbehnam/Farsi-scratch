package ir.behnamapps.fascratch.editor

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.provider.MediaStore
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
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PackagedExportDeviceTest {
    @SuppressLint("SetJavaScriptEnabled")
    @Test fun currentProjectPackagesAndSavesHtmlOffline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val config = EditorAssetsConfig.load(context.assets)
        val filename = "PackagerDeviceTest-${System.currentTimeMillis()}"
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var web: WebView
            scenario.onActivity { activity ->
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val loader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", EditorAssetsPathHandler(activity, config)).build()
                web = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                    activity.attachScratchWebView(this)
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            if (request.url.host == "appassets.androidplatform.net") return loader.shouldInterceptRequest(request.url)
                            return WebResourceResponse("text/plain", "UTF-8", 404, "Offline test", emptyMap(),
                                ByteArrayInputStream(ByteArray(0)))
                        }
                    }
                    activity.setContentView(this)
                    loadUrl(config.entryUrl(EditorEngine.TURBO))
                }
            }
            fun evaluate(script: String): String {
                val result = AtomicReference<String?>()
                instrumentation.runOnMainSync { web.evaluateJavascript(script) { result.set(it) } }
                val end = SystemClock.elapsedRealtime() + 10000
                while (result.get() == null && SystemClock.elapsedRealtime() < end) Thread.sleep(30)
                return result.get() ?: error("WebView did not respond")
            }
            fun await(script: String) {
                val end = SystemClock.elapsedRealtime() + 120000
                while (SystemClock.elapsedRealtime() < end) {
                    if (evaluate(script) == "true") return
                    Thread.sleep(150)
                }
                error("Packager timed out: $script; ${evaluate("document.body.innerText.slice(-500)")}")
            }
            await("Boolean(window.vm && vm.runtime.targets.length>1 && window.ReduxStore)")
            assertEquals("true", evaluate("typeof AndroidPackagedExport.postMessage==='function'"))
            evaluate("ReduxStore.dispatch({type:'scratch-gui/locales/SELECT_LOCALE',locale:'en'});ReduxStore.dispatch({type:'projectTitle/SET_PROJECT_TITLE',title:'$filename'});true")
            evaluate("Array.from(document.querySelectorAll('span')).find(e=>e.textContent==='File').click();true")
            await("Array.from(document.querySelectorAll('span')).some(e=>e.textContent==='Package project')")
            evaluate("Array.from(document.querySelectorAll('span')).find(e=>e.textContent==='Package project').click();true")
            val frame = "document.querySelector('iframe').contentDocument"
            await("Boolean(document.querySelector('iframe') && $frame && Array.from($frame.querySelectorAll('button')).some(e=>e.textContent.trim()==='Package'))")
            assertEquals("true", evaluate("!$frame.body.innerText.includes('Select Project') && !$frame.body.innerText.includes('TurboWarp Packager')"))
            assertEquals("true", evaluate("document.querySelector('iframe').getBoundingClientRect().height>innerHeight*0.7"))
            assertEquals("false", evaluate("$frame.querySelector('details').open"))
            evaluate("window.__packagingStarted=performance.now();Array.from($frame.querySelectorAll('button')).find(e=>e.textContent.trim()==='Package').click();true")
            await("Array.from(document.querySelectorAll('[role=dialog] button')).some(e=>e.textContent.trim()==='Save')")
            assertEquals("true", evaluate("performance.now()-window.__packagingStarted>=4950"))
            evaluate("Array.from(document.querySelectorAll('[role=dialog] button')).find(e=>e.textContent.trim()==='Save').click();true")
            await("Array.from(document.querySelectorAll('[role=status]')).some(e=>e.textContent.includes('Saved to Downloads'))")
            val resolver = context.contentResolver
            val uri = resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Downloads._ID), "${MediaStore.Downloads.DISPLAY_NAME} = ?",
                arrayOf("$filename.html"), null)!!.use { cursor ->
                assertTrue("Export not present in Downloads", cursor.moveToFirst())
                android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))
            }
            try {
                val html = resolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
                assertTrue(html.length > 1000000)
                assertTrue(html.contains("new Scaffolding.Scaffolding()"))
            } finally { resolver.delete(uri, null, null) }
            val editor = web
            val playerMonitor = instrumentation.addMonitor(PackagedPlayerActivity::class.java.name, null, false)
            var playback: Activity? = null
            try {
                evaluate("Array.from(document.querySelectorAll('[role=dialog] button')).find(e=>e.textContent.trim()==='Run directly').click();true")
                playback = requireNotNull(playerMonitor.waitForActivityWithTimeout(15000)) { "Direct player did not open" }
                fun descendants(view: android.view.View): List<android.view.View> = listOf(view) +
                    if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
                    else emptyList()
                lateinit var refresh: android.widget.Button
                lateinit var close: android.widget.Button
                instrumentation.runOnMainSync {
                    val views = descendants(playback!!.window.decorView)
                    web = views.filterIsInstance<WebView>().single()
                    refresh = views.filterIsInstance<android.widget.Button>().single { it.text == "Refresh" }
                    close = views.filterIsInstance<android.widget.Button>().single { it.text == "Close" }
                }
                await("Boolean(window.vm && vm.runtime.targets.length>1)")
                assertEquals("true", evaluate("location.origin==='https://player.farsiscratch.invalid'"))
                assertEquals("true", evaluate("typeof AndroidProjectSaver==='undefined' && typeof AndroidScratchTextInput==='undefined' && typeof AndroidPackagedExport==='undefined'"))
                evaluate("window.__directPlayerTest=1;true")
                instrumentation.runOnMainSync { refresh.performClick() }
                await("Boolean(window.vm && vm.runtime.targets.length>1 && window.__directPlayerTest===undefined)")
                instrumentation.runOnMainSync { close.performClick() }
                web = editor
                await("Array.from(document.querySelectorAll('[role=dialog] button')).some(e=>e.textContent.trim()==='Run directly'&&!e.disabled)")
            } finally {
                web = editor
                instrumentation.runOnMainSync { playback?.finish() }
                instrumentation.removeMonitor(playerMonitor)
            }
            // Intercept the chooser so no file is sent to a real sharing destination.
            val chooser = instrumentation.addMonitor(IntentFilter(Intent.ACTION_CHOOSER),
                Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
            try {
                evaluate("Array.from(document.querySelectorAll('[role=dialog] button')).find(e=>e.textContent.trim()==='Share').click();true")
                await("Array.from(document.querySelectorAll('[role=status]')).some(e=>e.textContent.includes('sharing window was opened'))")
                assertEquals("Share should launch the native chooser", 1, chooser.hits)
            } finally { instrumentation.removeMonitor(chooser) }
            // Subframes never receive export authority, even when hosted on the same origin.
            evaluate("""
                window.__subframeRejected=false;
                const f=document.querySelector('iframe').contentWindow;
                f.AndroidPackagedExport.onmessage=e=>{window.__subframeRejected=JSON.parse(e.data).ok===false;};
                f.AndroidPackagedExport.postMessage(JSON.stringify({id:'untrusted',op:'begin',name:'blocked.html',mime:'text/html',bytes:1}));true
            """.trimIndent())
            await("window.__subframeRejected===true")
            instrumentation.runOnMainSync { web.stopLoading(); web.destroy() }
        }
    }
}
