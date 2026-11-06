package ir.behnamapps.fascratch.editor

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.MainActivity
import ir.behnamapps.fascratch.ScratchScreen
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class EditorReentryDeviceTest {
    @Test fun repeatedExitAndReentryReleasesThePreviousWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val selected = mutableStateOf<EditorEngine?>(null)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var owner: MainActivity
            scenario.onActivity { activity ->
                owner = activity
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                activity.setContent {
                    selected.value?.let { engine -> ScratchScreen(activity, engine, {}, { selected.value = null }) }
                        ?: Text("Home")
                }
            }
            fun descendants(view: View): List<View> = listOf(view) +
                if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
            val active = MainActivity::class.java.getDeclaredField("activeScratchWebView").apply { isAccessible = true }
            var previous: WebView? = null
            for (engine in listOf(EditorEngine.TURBO, EditorEngine.SCRATCH, EditorEngine.TURBO, EditorEngine.SCRATCH)) {
                instrumentation.runOnMainSync { selected.value = engine }
                val deadline = SystemClock.elapsedRealtime() + 90000
                var current: WebView? = null
                var ready = false
                while (!ready && SystemClock.elapsedRealtime() < deadline) {
                    val answer = AtomicReference<String?>()
                    instrumentation.runOnMainSync {
                        current = descendants(owner.window.decorView).filterIsInstance<WebView>().firstOrNull()
                        current?.evaluateJavascript("typeof window.AndroidScratchLoadProjectUrl === 'function' && document.querySelectorAll('canvas').length > 0") { answer.set(it) }
                    }
                    val callbackDeadline = SystemClock.elapsedRealtime() + 2000
                    while (answer.get() == null && SystemClock.elapsedRealtime() < callbackDeadline) Thread.sleep(50)
                    ready = answer.get() == "true"
                }
                assertTrue("Editor did not become ready after reentry: $engine", ready)
                assertNotSame(previous, current)
                instrumentation.runOnMainSync { assertSame(current, active.get(owner)); selected.value = null }
                val releaseDeadline = SystemClock.elapsedRealtime() + 5000
                var released = false
                while (!released && SystemClock.elapsedRealtime() < releaseDeadline) {
                    instrumentation.runOnMainSync { released = active.get(owner) == null && current!!.parent == null }
                    if (!released) Thread.sleep(50)
                }
                instrumentation.runOnMainSync {
                    assertNull("Activity retains editor after exit", active.get(owner))
                    assertNull("Old WebView remains attached", current!!.parent)
                    assertTrue("Editor views remain on Home", descendants(owner.window.decorView).none { it is WebView })
                }
                previous = current
            }
        }
    }
}
