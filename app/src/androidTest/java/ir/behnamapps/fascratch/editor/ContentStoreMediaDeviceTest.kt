package ir.behnamapps.fascratch.inappbilling.learning

import android.graphics.Bitmap
import android.media.AudioManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.MainActivity
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class ContentStoreMediaDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun find(label: String): AccessibilityNodeInfo? {
        fun visit(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
            if (node.text?.contains(label) == true) return node
            for (i in 0 until node.childCount) node.getChild(i)?.let { visit(it)?.let { found -> return found } }
            return null
        }
        return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
    }
    private fun waitFor(predicate: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        while (!predicate() && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(80)
        assertTrue("Expected device state did not arrive", predicate())
    }
    private fun click(label: String) {
        waitFor { find(label) != null }
        var node = find(label)!!
        while (!node.isClickable) node = node.parent ?: error("No clickable parent")
        assertTrue(node.performAction(AccessibilityNodeInfo.ACTION_CLICK))
    }
    @Test fun nativeSpriteAndBackdropPreviewDrawTheImageInsideTheDialog() {
        val bitmap = Bitmap.createBitmap(160, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(0xFFFF00FF.toInt()) }
        try {
            for (kind in listOf("sprites", "backdrops")) ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val loaded = AtomicBoolean(false)
                scenario.onActivity { activity -> activity.setContent { MaterialTheme {
                    StoreImagePreview(ContentPart("fixture-$kind", kind, "Preview fixture", "fixture"), ContentStoreRepository.get(activity),
                        load = { loaded.set(true); StorePicture(bitmap = bitmap) }, close = {})
                } } }
                waitFor { loaded.get() && find("Preview fixture") != null }
                instrumentation.waitForIdleSync(); SystemClock.sleep(250)
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                var visible = 0
                for (x in 0 until screenshot.width step 4) for (y in 0 until screenshot.height step 4) {
                    val color = screenshot.getPixel(x, y)
                    if (android.graphics.Color.red(color) > 220 && android.graphics.Color.blue(color) > 220 && android.graphics.Color.green(color) < 40) visible++
                }
                File(instrumentation.targetContext.getExternalFilesDir(null), "store-$kind-native-preview.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
                screenshot.recycle(); assertTrue("Native preview pixels must actually be visible", visible > 200)
            }
        } finally { bitmap.recycle() }
    }
    @Test fun soundPlaysInlineAndStopReleasesTheTemporaryClipWithoutADialog() {
        val selected = AtomicBoolean(false); lateinit var clip: File
        val audio = instrumentation.targetContext.getSystemService(AudioManager::class.java)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme {
                var playing by remember { mutableStateOf(false) }
                InlineSoundPreview(ContentPart("sound-fixture", "sounds", "Sound", "fixture"), playing, load = {
                    val pcm = ByteArray(16000 * 10)
                    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVEfmt ".toByteArray())
                        .putInt(16).putShort(1).putShort(1).putInt(8000).putInt(16000).putShort(2).putShort(16).put("data".toByteArray()).putInt(pcm.size).array()
                    File.createTempFile("store-test-clip-", ".wav", activity.cacheDir).also { clip = it; it.writeBytes(header + pcm) }
                }) { playing = it; selected.set(it) }
            } } }
            click("▶ پخش"); waitFor { selected.get() && audio.isMusicActive }
            assertNull("Inline playback must not open a preview dialog", find("بستن"))
            click("■ توقف"); waitFor { !selected.get() && !clip.exists() }
        }
    }
    @Test fun ownedDownloadIsGreenPulsesAndWaitsForATap() {
        val clicked = AtomicBoolean(false)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> activity.setContent { MaterialTheme { OwnedDownloadButton(true) { clicked.set(true) } } } }
            waitFor { find("دانلود رایگان") != null }
            val widths = mutableSetOf<Int>()
            repeat(10) {
                val node = find("دانلود رایگان")!!; val bounds = android.graphics.Rect(); node.getBoundsInScreen(bounds)
                widths += bounds.width(); SystemClock.sleep(120)
            }
            assertFalse("Download must wait for the user's explicit tap", clicked.get())
            assertTrue("The button's scale must pulse on the real device", widths.size > 1)
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            var green = 0
            for (x in 0 until screenshot.width step 4) for (y in 0 until screenshot.height step 4) {
                val c = screenshot.getPixel(x, y)
                if (android.graphics.Color.green(c) in 100..175 && android.graphics.Color.red(c) < 70 && android.graphics.Color.blue(c) < 120) green++
            }
            screenshot.recycle(); assertTrue("Download must use the green owned style", green > 20)
            click("دانلود رایگان"); waitFor { clicked.get() }
        }
    }
}
