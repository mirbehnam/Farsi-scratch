package ir.behnamapps.fascratch.inappbilling.presentation

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import ir.behnamapps.fascratch.inappbilling.learning.LearnerDialogTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.MainActivity
import ir.behnamapps.fascratch.inappbilling.domain.Course
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class PurchaseRecoveryDialogDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun node(label: String): AccessibilityNodeInfo? {
        fun find(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (n.text?.toString()?.contains(label) == true) return n
            for (i in 0 until n.childCount) n.getChild(i)?.let { find(it)?.let { found -> return found } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::find)
    }
    private fun awaitNode(label: String): AccessibilityNodeInfo {
        val until = SystemClock.uptimeMillis() + 10000
        while (SystemClock.uptimeMillis() < until) { node(label)?.let { return it }; SystemClock.sleep(150) }
        error("Missing visible label: $label")
    }
    private fun click(label: String) {
        var n: AccessibilityNodeInfo? = awaitNode(label)
        while (n != null && !n.isClickable) n = n.parent
        assertTrue(n?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
        instrumentation.waitForIdleSync()
    }
    private fun capture(name: String) {
        SystemClock.sleep(350) // Capture after the dialog's entrance animation.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
    @Test fun freshRecoveryShowsProgressBeforeOfferingDelayedHelp() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { LearnerDialogTheme {
                PurchaseRecoveryDialog(PurchaseRecoveryState(Course("qa", "دورهٔ آزمایشی", "", "sku"), SystemClock.elapsedRealtime(), false), onRetry = {}, onClose = {})
            } } }
            awaitNode("در حال بازیابی خریدت")
            assertNull(node("تماس با ما"))
            capture("recovery-progress.png")
        }
    }
    @Test fun delayedRecoveryKeepsRetryAndContactVisibleAtNormalAndLargeFonts() {
        for (scale in listOf(1f, 2f)) {
            val retried = AtomicBoolean(false)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity -> activity.setContent { LearnerDialogTheme {
                    CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                        PurchaseRecoveryDialog(PurchaseRecoveryState(Course("qa", "دورهٔ آزمایشی اسکرچ فارسی", "", "sku"),
                            SystemClock.elapsedRealtime() - 20000, false), onRetry = { retried.set(true) }, onClose = {})
                    }
                } } }
                val retry = awaitNode("تلاش مجدد")
                val contact = awaitNode("تماس با ما")
                val window = Rect(); instrumentation.uiAutomation.rootInActiveWindow.getBoundsInScreen(window)
                for (button in listOf(retry, contact)) {
                    val bounds = Rect(); button.getBoundsInScreen(bounds)
                    assertFalse(bounds.isEmpty)
                    assertTrue("Button $bounds must fit $window", window.contains(bounds))
                }
                capture("recovery-delayed-$scale.png")
                click("تلاش مجدد")
                assertTrue(retried.get())
            }
        }
    }
    @Test fun contactShowsTheExactHandleAndOpensBaleWithoutSendingAMessage() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { LearnerDialogTheme {
                PurchaseRecoveryDialog(PurchaseRecoveryState(Course("qa", "دورهٔ آزمایشی", "", "sku"),
                    SystemClock.elapsedRealtime(), false, running = false, message = "پاسخ استور دریافت نشد."), onRetry = {}, onClose = {})
            } } }
            click("تماس با ما")
            awaitNode("@behnamtajadini")
            capture("recovery-contact.png")
            click("@behnamtajadini")
            val until = SystemClock.uptimeMillis() + 8000
            while (instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() != "ir.nasim" && SystemClock.uptimeMillis() < until) SystemClock.sleep(200)
            assertEquals("ir.nasim", instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString())
        }
    }
}
