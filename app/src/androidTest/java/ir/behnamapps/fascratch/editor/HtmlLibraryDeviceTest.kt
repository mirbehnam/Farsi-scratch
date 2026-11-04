package ir.behnamapps.fascratch.editor

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.MainActivity
import ir.behnamapps.fascratch.SavedScratchProject
import ir.behnamapps.fascratch.ScratchScreen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HtmlLibraryDeviceTest {
    @Test fun libraryDefaultsToSb3ThenPlaysAndSharesSavedHtmlWithoutLoadingAnEditor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var saved: SavedHtmlProject? = null
        var source: File? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var owner: MainActivity
            scenario.onActivity { activity ->
                owner = activity
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                source = File(activity.cacheDir, "HtmlLibraryFixture-${System.currentTimeMillis()}.html").apply {
                    writeText("<!doctype html><html><body>Library playback<script>window.libraryFixture=true</script></body></html>")
                }
                saved = activity.htmlProjectRepository.save(source!!)
                activity.setContent {
                    ProjectLibraryDialog(
                        projects = listOf(SavedScratchProject("SB3 fixture", File(activity.cacheDir, "fixture.sb3"), null, 0)),
                        onLoad = {}, onLoadFromPhone = {}, onDismiss = {}, showManagementActions = true,
                        htmlProjects = listOf(saved!!),
                        onPlayHtml = { project -> activity.playSavedHtml(project) {
                            activity.setContent { ScratchScreen(activity, EditorAssetsConfig.load(activity.assets).defaultEngine, {}, {}, playbackOnly = true) }
                        } }, onShareHtml = activity::shareSavedHtml
                    )
                }
            }
            fun find(label: String): AccessibilityNodeInfo? {
                if (android.os.Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                val queue = java.util.ArrayDeque<AccessibilityNodeInfo>()
                instrumentation.uiAutomation.windows.mapNotNull { it.root }.forEach(queue::add)
                while (!queue.isEmpty()) {
                    val node = queue.removeFirst()
                    if (node.text?.toString()?.contains(label) == true || node.contentDescription?.toString() == label) return node
                    if (node.className?.toString() == "android.webkit.WebView") continue
                    for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                }
                return null
            }
            fun waitFor(label: String): AccessibilityNodeInfo {
                val end = SystemClock.elapsedRealtime() + 10000
                while (SystemClock.elapsedRealtime() < end) {
                    find(label)?.let { return it }
                    Thread.sleep(100)
                }
                error("HTML library control not found: $label")
            }
            fun tap(label: String) {
                val bounds = Rect()
                waitFor(label).getBoundsInScreen(bounds)
                val time = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action,
                        bounds.exactCenterX(), bounds.exactCenterY(), 0).apply { this.source = InputDevice.SOURCE_TOUCHSCREEN }
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true))
                    event.recycle()
                    if (action == MotionEvent.ACTION_DOWN) Thread.sleep(100)
                }
                instrumentation.waitForIdleSync()
            }
            instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            try {
                fun capture(name: String) {
                    instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                        File(instrumentation.targetContext.cacheDir, name).outputStream().use {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                        }
                        bitmap.recycle()
                    }
                }
                waitFor("SB3 fixture")
                capture("html-library-sb3-test.png")
                assertNull(find("▶ اجرا"))
                tap("پروژه‌های HTML")
                Thread.sleep(300)
                capture("html-library-after-tab.png")
                waitFor("HtmlLibraryFixture-")
                waitFor("▶ اجرا")
                capture("html-library-html-test.png")
                val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_CHOOSER),
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
                try { tap("↗ اشتراک‌گذاری"); assertEquals(1, monitor.hits) }
                finally { instrumentation.removeMonitor(monitor) }
                tap("▶ اجرا")
                fun descendants(view: View): List<View> = listOf(view) +
                    if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                var player: WebView? = null
                val end = SystemClock.elapsedRealtime() + 10000
                while (player == null && SystemClock.elapsedRealtime() < end) {
                    instrumentation.runOnMainSync {
                        val views = descendants(owner.window.decorView).filterIsInstance<WebView>()
                        assertFalse(views.any { it.url.orEmpty().contains("/assets/") })
                        player = views.firstOrNull { it.url.orEmpty().startsWith("https://player.farsiscratch.invalid/") }
                    }
                    Thread.sleep(100)
                }
                assertNotNull(player)
                val playbackFile = owner.packagedPlayback!!.file
                var loaded = false
                val loadDeadline = SystemClock.elapsedRealtime() + 10000
                while (!loaded && SystemClock.elapsedRealtime() < loadDeadline) {
                    instrumentation.runOnMainSync { loaded = player!!.progress == 100 }
                    Thread.sleep(50)
                }
                val result = AtomicReference<String?>()
                instrumentation.runOnMainSync { player!!.evaluateJavascript("Boolean(window.libraryFixture && typeof AndroidPackagedExport==='undefined' && typeof AndroidProjectSaver==='undefined')") { result.set(it) } }
                val ready = SystemClock.elapsedRealtime() + 10000
                while (result.get() == null && SystemClock.elapsedRealtime() < ready) Thread.sleep(50)
                assertEquals("true", result.get())
                tap("بستن")
                Thread.sleep(300)
                assertFalse(playbackFile.exists())
                assertTrue(saved!!.file.exists())
            } finally {
                instrumentation.runOnMainSync { owner.closePackagedPlayback() }
                saved?.file?.delete()
                source?.delete()
            }
        }
    }
}

