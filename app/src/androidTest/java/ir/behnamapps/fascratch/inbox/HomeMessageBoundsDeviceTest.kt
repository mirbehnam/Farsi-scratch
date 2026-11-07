package ir.behnamapps.fascratch.inbox

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class HomeMessageBoundsDeviceTest {
    @Test fun longMessageKeepsHeadingAndConfirmationInsideTheWindow() = verify(1f)
    @Test fun largeFontsKeepConfirmationReachableWithoutScrollingTheWholeDialog() = verify(2f)

    private fun verify(fontScale: Float) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val confirmed = AtomicBoolean(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    val density = LocalDensity.current.density
                    CompositionLocalProvider(LocalDensity provides Density(density,fontScale)) {
                        HomeMessagePanel(InboxMessage("bounds-test",1,1,"پیام بلند آزمایشی برای بررسی محدودهٔ صفحه",
                            ("این یک پیام بلند برای بررسی اسکرول است.\n").repeat(100),"info",false,1,null,"۱۴۰۵/۰۸/۱۷",
                            emptyList(),null,"personal"),null,null) { confirmed.set(true) }
                    }
                }
            }
            fun node(label: String): AccessibilityNodeInfo? {
                val root = instrumentation.uiAutomation.rootInActiveWindow ?: return null
                fun find(current: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                    if (current.text?.toString()?.contains(label) == true) return current
                    for (i in 0 until current.childCount) current.getChild(i)?.let { find(it)?.let { found -> return found } }
                    return null
                }
                return find(root)
            }
            var confirm: AccessibilityNodeInfo? = null
            val deadline = SystemClock.uptimeMillis()+15_000
            while (confirm == null && SystemClock.uptimeMillis()<deadline) {
                SystemClock.sleep(150); confirm = node("تأیید، متوجه شدم")
            }
            assertNotNull("Confirmation must be visible without scrolling",confirm)
            val window = Rect(); instrumentation.uiAutomation.rootInActiveWindow.getBoundsInScreen(window)
            val button = Rect(); confirm!!.getBoundsInScreen(button)
            assertFalse(button.isEmpty)
            assertTrue("Button $button must fit window $window",window.contains(button))
            val heading = Rect(); node("پیام شخصی برای تو")!!.getBoundsInScreen(heading)
            assertTrue("Heading $heading must fit window $window",window.contains(heading))
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(screenshot)
            val screen = Rect(0,0,screenshot.width,screenshot.height)
            assertTrue("Button $button must fit physical screen $screen",screen.contains(button))
            assertTrue("Heading $heading must fit physical screen $screen",screen.contains(heading))
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"home-message-bounds-$fontScale.png")
                .outputStream().use { screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            screenshot.recycle()
            var clickable: AccessibilityNodeInfo? = confirm
            while (clickable != null && !clickable.isClickable) clickable = clickable.parent
            assertTrue(clickable?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
            instrumentation.waitForIdleSync()
            assertTrue("Confirmation callback must run",confirmed.get())
        }
    }
}
