package ir.behnamapps.fascratch.editor

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.FileProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.ArrayDeque

@RunWith(AndroidJUnit4::class)
class HomePhoneFlowDeviceTest {
    @Test fun importedProjectOpensOnlyAfterUserSelectsTurbo() = runFlow(EditorEngine.TURBO)
    @Test fun importedProjectOpensOnlyAfterUserSelectsScratch() = runFlow(EditorEngine.SCRATCH)
    private fun runFlow(engine: EditorEngine) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var fixture: File
            lateinit var owner: MainActivity
            scenario.onActivity { activity ->
                owner = activity
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                fixture = File(activity.filesDir, "scratch_projects/HP-${System.currentTimeMillis() % 1000000}.sb3")
                fixture.parentFile!!.mkdirs()
                activity.assets.open("turbobuild/packager/example.sb3").use { input -> fixture.outputStream().use { input.copyTo(it) } }
            }
            val uri = FileProvider.getUriForFile(owner, "${owner.packageName}.fileprovider", fixture)
            val monitor = instrumentation.addMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*")
            }, Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
            fun find(label: String): AccessibilityNodeInfo? {
                if (Build.VERSION.SDK_INT >= 33) instrumentation.uiAutomation.clearCache()
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                instrumentation.uiAutomation.windows.mapNotNull { it.root }.forEach(queue::add)
                while (queue.isNotEmpty()) {
                    val n = queue.removeFirst()
                    if (n.text?.toString()?.contains(label) == true || n.contentDescription?.toString()?.contains(label) == true) return n
                    if (n.className?.toString() == "android.webkit.WebView") continue
                    for (i in 0 until n.childCount) n.getChild(i)?.let(queue::add)
                }
                return null
            }
            fun waitFor(label: String): AccessibilityNodeInfo {
                val end = SystemClock.elapsedRealtime() + 20000
                while (SystemClock.elapsedRealtime() < end) { find(label)?.let { return it }; Thread.sleep(100) }
                error("Control not found: $label")
            }
            fun tap(label: String) {
                val rect = Rect(); waitFor(label).getBoundsInScreen(rect); val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
                    event.source = InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(instrumentation.uiAutomation.injectInputEvent(event, true)); event.recycle()
                    if (action == MotionEvent.ACTION_DOWN) Thread.sleep(100)
                }
                instrumentation.waitForIdleSync()
            }
            try {
                instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
                    flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
                }
                tap("پروژه‌های من")
                tap("بارگذاری از حافظه گوشی")
                val copyDeadline = SystemClock.elapsedRealtime() + 15000
                var imported = emptyList<ir.behnamapps.fascratch.SavedScratchProject>()
                while (imported.isEmpty() && SystemClock.elapsedRealtime() < copyDeadline) {
                    imported = owner.projectRepository.listProjects().filter { it.projectFile != fixture && it.name.startsWith(fixture.nameWithoutExtension) }
                    Thread.sleep(100)
                }
                assertNull("Import must not open engine selection automatically", find("انتخاب نسخهٔ اسکرچ"))
                assertTrue("Imported file must be persisted", imported.isNotEmpty())
                Thread.sleep(400)
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    File(owner.cacheDir, "phone-import-list.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
                }
                tap(imported.last().name)
                waitFor("انتخاب نسخهٔ اسکرچ")
                assertNotNull(find("نسخه اصلی اسکرچ")); assertNotNull(find("نسخه پرسرعت اسکرچ"))
                assertEquals(1, monitor.hits)
                repeat(2) { round ->
                tap(if (engine == EditorEngine.TURBO) "نسخه پرسرعت اسکرچ" else "نسخه اصلی اسکرچ")
                fun descendants(view: android.view.View): List<android.view.View> = listOf(view) +
                    if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
                val deadline = SystemClock.elapsedRealtime() + 90000
                var loaded = false
                var lastResult: String? = null
                while (!loaded && SystemClock.elapsedRealtime() < deadline) {
                    val response = java.util.concurrent.atomic.AtomicReference<String?>()
                    instrumentation.runOnMainSync {
                        descendants(owner.window.decorView).filterIsInstance<android.webkit.WebView>().firstOrNull()?.evaluateJavascript("""
                            (()=>{for(const b of document.querySelectorAll('button')) { let f=b[Object.keys(b).find(k=>k.startsWith('__reactFiber$'))];while(f){if(f.memoizedProps?.store?.getState)window.ReduxStore=f.memoizedProps.store;f=f.return;}}
                            return JSON.stringify({title:window.ReduxStore?.getState().scratchGui.projectTitle,state:window.ReduxStore?.getState().scratchGui.projectState.loadingState});})()
                        """.trimIndent()) { response.set(it) }
                    }
                    val end = SystemClock.elapsedRealtime() + 3000
                    while (response.get() == null && SystemClock.elapsedRealtime() < end) Thread.sleep(50)
                    lastResult = response.get()
                    loaded = lastResult?.contains(imported.last().name) == true && lastResult?.contains("SHOWING_WITHOUT_ID") == true
                    Thread.sleep(100)
                }
                assertTrue("Imported project did not load in $engine: $lastResult", loaded)
                if (round == 0) {
                    tap("خانه")
                    tap("بله، خروج")
                    waitFor("پروژه‌های من")
                    val endRelease = SystemClock.elapsedRealtime() + 5000
                    var remaining = true
                    while (remaining && SystemClock.elapsedRealtime() < endRelease) {
                        instrumentation.runOnMainSync { remaining = descendants(owner.window.decorView).any { it is android.webkit.WebView } }
                        Thread.sleep(50)
                    }
                    assertFalse("Editor WebView remains after Home", remaining)
                    tap("پروژه‌های من")
                    tap(imported.last().name)
                    waitFor("انتخاب نسخهٔ اسکرچ")
                }
                }

            } finally {
                instrumentation.removeMonitor(monitor)
                owner.projectRepository.listProjects().filter { it.name.startsWith(fixture.nameWithoutExtension) && it.projectFile != fixture }.forEach { it.projectFile.delete() }
                fixture.delete()
            }
        }
    }
}
