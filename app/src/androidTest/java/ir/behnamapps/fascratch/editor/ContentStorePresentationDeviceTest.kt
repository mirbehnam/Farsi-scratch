package ir.behnamapps.fascratch.inappbilling.learning

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.behnamapps.fascratch.MainActivity
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ContentStorePresentationDeviceTest {
    @Test fun names_follow_language_and_missing_names_use_original_filename() {
        val names = JSONObject().put("fa", "گربه").put("en", "Cat").put("ar", "قطة")
        assertEquals("گربه", contentDisplayName(names, "my-cat.sprite3", "fa"))
        assertEquals("Cat", contentDisplayName(names, "my-cat.sprite3", "en"))
        assertEquals("قطة", contentDisplayName(names, "my-cat.sprite3", "ar"))
        assertEquals("Cat", contentDisplayName(names, "my-cat.sprite3", "de"))
        assertEquals("my-cat", contentDisplayName(JSONObject().put("en", JSONObject.NULL), "my-cat.sprite3", "fa"))
        assertEquals("my-cat", contentDisplayName(null, "my-cat.sprite3", "ar"))
    }
    @Test fun gif_changes_frames_and_stops_when_hidden_and_released() {
        val hex = "47494638396101000100800000000000ffffff21ff0b4e45545343415045322e30030100000021f904000a0000002c000000000100010000020244010021f904000a0000002c00000000010001000002024c01003b"
        val gif = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val animation = decodeStoreAnimation(gif); assertNotNull(animation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var view: StoreAnimationView
            scenario.onActivity { activity -> view = StoreAnimationView(activity); view.show(animation); activity.setContentView(view) }
            val colors = mutableSetOf<Int>()
            repeat(12) {
                Thread.sleep(43)
                scenario.onActivity {
                    assertTrue("GIF must be running while visible", view.animationRunning)
                    val bitmap = Bitmap.createBitmap(maxOf(1, view.width), maxOf(1, view.height), Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(bitmap)); colors += bitmap.getPixel(bitmap.width / 2, bitmap.height / 2); bitmap.recycle()
                }
            }
            assertTrue("The real native view must display multiple GIF frames", colors.size > 1)
            scenario.onActivity { view.visibility = View.GONE; assertFalse(view.animationRunning); view.release(); assertFalse(view.animationRunning) }
        }
    }
}