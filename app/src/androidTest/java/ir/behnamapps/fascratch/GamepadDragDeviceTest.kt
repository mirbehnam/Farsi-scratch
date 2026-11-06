package ir.behnamapps.fascratch

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.ArrayDeque

@RunWith(AndroidJUnit4::class)
class GamepadDragDeviceTest {
    @Test fun rtlDragFollowsFingerAndDropsIntoActualCell() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val ui = instrumentation.uiAutomation
        ui.serviceInfo = ui.serviceInfo.apply { flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS }
        var saved: List<PadKey>? = null
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity {
                activity = it
                it.setContent { GamepadSettingsDialog(listOf(PadKey("A", 0), PadKey("B", 4)), {}, { saved = it }) }
            }
            fun find(label: String): AccessibilityNodeInfo? {
                if (Build.VERSION.SDK_INT >= 33) ui.clearCache()
                val queue = ArrayDeque<AccessibilityNodeInfo>()
                ui.windows.mapNotNull { it.root }.forEach(queue::add)
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    if (node.contentDescription?.toString() == label || node.text?.toString() == label) return node
                    for (i in 0 until node.childCount) node.getChild(i)?.let(queue::add)
                }
                return null
            }
            fun bounds(label: String): Rect {
                val deadline = SystemClock.elapsedRealtime() + 7000
                while (SystemClock.elapsedRealtime() < deadline) {
                    find(label)?.let { return Rect().apply { it.getBoundsInScreen(this) } }
                    Thread.sleep(50)
                }
                error("Missing $label")
            }
            fun screenshot(name: String) {
                ui.takeScreenshot()?.let { bitmap ->
                    File(activity.cacheDir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                    bitmap.recycle()
                }
            }
            val source = bounds("خانهٔ 0")
            val target = bounds("خانهٔ 4")
            val downTime = SystemClock.uptimeMillis()
            fun event(action: Int, x: Float, y: Float) {
                MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, y, 0).also {
                    it.source = InputDevice.SOURCE_TOUCHSCREEN
                    assertTrue(ui.injectInputEvent(it, true)); it.recycle()
                }
            }
            event(MotionEvent.ACTION_DOWN, source.exactCenterX(), source.exactCenterY())
            try {
                Thread.sleep(750)
                for (step in 1..12) {
                    val fraction = step / 12f
                    event(MotionEvent.ACTION_MOVE, source.exactCenterX() + (target.exactCenterX() - source.exactCenterX()) * fraction,
                        source.exactCenterY() + (target.exactCenterY() - source.exactCenterY()) * fraction)
                    Thread.sleep(20)
                }
                screenshot("gamepad-drag.png")
                val ghost = bounds("کلید در حال حرکت")
                assertEquals("Lifted key must track physical X even in RTL", target.exactCenterX(), ghost.exactCenterX(), 12f)
            } finally { event(MotionEvent.ACTION_UP, target.exactCenterX(), target.exactCenterY()) }
            Thread.sleep(150)
            assertNull(find("کلید در حال حرکت"))
            val save = bounds("ذخیره")
            event(MotionEvent.ACTION_DOWN, save.exactCenterX(), save.exactCenterY())
            event(MotionEvent.ACTION_UP, save.exactCenterX(), save.exactCenterY())
            instrumentation.waitForIdleSync()
            assertEquals(4, saved!!.first { it.key == "A" }.cell)
            assertEquals(0, saved!!.first { it.key == "B" }.cell)
        }
    }
}
