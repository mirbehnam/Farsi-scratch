package ir.behnamapps.fascratch.editor

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import ir.behnamapps.fascratch.ScratchScreen
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
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
            lateinit var owner: MainActivity
            fun descendants(view: android.view.View): List<android.view.View> = listOf(view) +
                if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
                else emptyList()
            scenario.onActivity { activity ->
                owner = activity
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.setContent {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        ScratchScreen(activity, EditorEngine.TURBO, {}, {})
                    }
                }
            }
            val viewDeadline = SystemClock.elapsedRealtime() + 15000
            var editorView: WebView? = null
            while (editorView == null && SystemClock.elapsedRealtime() < viewDeadline) {
                instrumentation.runOnMainSync {
                    editorView = descendants(owner.window.decorView).filterIsInstance<WebView>()
                        .firstOrNull { it.url.orEmpty().contains("/assets/turbobuild/") }
                }
                if (editorView == null) Thread.sleep(80)
            }
            web = requireNotNull(editorView) { "Editor WebView did not mount" }
            instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    android.accessibilityservice.AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            }
            var railDescriptions = ""
            fun rail(label: String): android.view.accessibility.AccessibilityNodeInfo? {
                val queue = java.util.ArrayDeque<android.view.accessibility.AccessibilityNodeInfo>()
                instrumentation.uiAutomation.windows.mapNotNull { it.root }.forEach(queue::add)
                val descriptions = mutableListOf<String>()
                while (!queue.isEmpty()) {
                    val node = queue.removeFirst()
                    val description = node.contentDescription?.toString()
                    if (description != null) descriptions.add(description)
                    if (description?.contains(label) == true) return node
                    if (node.className?.toString() == "android.webkit.WebView") continue
                    for (index in 0 until node.childCount) node.getChild(index)?.let(queue::add)
                }
                railDescriptions = descriptions.joinToString(" | ")
                return null
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
            await("Array.from(document.querySelectorAll('[role=status]')).some(e=>e.textContent.includes('Saved in My Projects'))")
            val saved = owner.htmlProjectRepository.listProjects().single { it.file.name == "$filename.html" }
            try {
                val html = saved.file.readText()
                assertTrue(html.length > 1000000)
                assertTrue(html.contains("new Scaffolding.Scaffolding()"))
                assertTrue(saved.file.canonicalPath.startsWith(java.io.File(context.filesDir, "packaged_projects").canonicalPath))
            } finally { saved.file.delete() }
            val editor = web
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                java.io.File(context.cacheDir, "sidebar-before-player-test.png").outputStream().use {
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            assertNotNull("Home rail not found: $railDescriptions", rail("خانه"))
            assertNotNull(rail("پیمایش ویرایشگر"))
            evaluate("window.__editorSpaceEvents=0;addEventListener('keydown',e=>{if(e.keyCode===32)window.__editorSpaceEvents++});true")
            try {
                evaluate("Array.from(document.querySelectorAll('[role=dialog] button')).find(e=>e.textContent.trim()==='Run directly').click();true")
                val deadline = SystemClock.elapsedRealtime() + 15000
                var playerView: WebView? = null
                while (playerView == null && SystemClock.elapsedRealtime() < deadline) {
                    instrumentation.runOnMainSync {
                        playerView = descendants(owner.window.decorView).filterIsInstance<WebView>()
                            .firstOrNull { it.url.orEmpty().startsWith("https://player.farsiscratch.invalid/") }
                    }
                    if (playerView == null) Thread.sleep(80)
                }
                web = requireNotNull(playerView) { "Inline HTML player did not mount" }
                await("Boolean(window.vm && vm.runtime.targets.length>1)")
                assertEquals("true", evaluate("location.origin==='https://player.farsiscratch.invalid'"))
                assertEquals("true", evaluate("typeof AndroidProjectSaver==='undefined' && typeof AndroidScratchTextInput==='undefined' && typeof AndroidPackagedExport==='undefined'"))
                assertNull(rail("خانه"))
                assertNull(rail("پیمایش ویرایشگر"))
                assertNotNull(rail("Close"))
                assertNotNull(rail("Refresh"))
                assertNotNull(rail("صفحه‌کلید"))
                evaluate("window.__playerSpaceEvents=0;addEventListener('keydown',e=>{if(e.keyCode===32)window.__playerSpaceEvents++});window.__directPlayerTest=1;true")
                instrumentation.runOnMainSync {
                    owner.dispatchScratchKey(android.view.KeyEvent.KEYCODE_SPACE, true)
                    owner.dispatchScratchKey(android.view.KeyEvent.KEYCODE_SPACE, false)
                }
                await("window.__playerSpaceEvents===1")
                web = editor
                assertEquals("0", evaluate("window.__editorSpaceEvents"))
                web = playerView!!
                instrumentation.runOnMainSync {
                    web.webChromeClient!!.onShowCustomView(android.widget.FrameLayout(owner), object : android.webkit.WebChromeClient.CustomViewCallback {
                        override fun onCustomViewHidden() {}
                    })
                }
                assertNotNull(rail("Close"))
                assertNotNull(rail("Refresh"))
                assertNull(rail("پیمایش ویرایشگر"))
                instrumentation.runOnMainSync { owner.refreshPackagedPlayback() }
                await("Boolean(window.vm && vm.runtime.targets.length>1 && window.__directPlayerTest===undefined)")
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    java.io.File(context.cacheDir, "sidebar-player-test.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
                instrumentation.runOnMainSync { owner.closePackagedPlayback() }
                web = editor
                await("Array.from(document.querySelectorAll('[role=dialog] button')).some(e=>e.textContent.trim()==='Run directly'&&!e.disabled)")
                instrumentation.waitForIdleSync()
                assertNotNull("Home rail not found: $railDescriptions", rail("خانه"))
                assertNotNull(rail("پیمایش ویرایشگر"))
                assertNull(rail("Close"))
                assertNull(rail("Refresh"))
            } finally {
                web = editor
                instrumentation.runOnMainSync { owner.closePackagedPlayback() }
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
