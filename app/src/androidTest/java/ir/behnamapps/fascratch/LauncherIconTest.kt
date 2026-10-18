package ir.behnamapps.fascratch

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LauncherIconTest {
    @Test fun launcherKeepsTransparentCornersAndVisibleArtworkAtDifferentSizes() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val info = context.applicationInfo
        assertEquals(R.drawable.app_launcher_transparent, info.icon)
        for (size in listOf(48, 96, 192)) {
            val icon = context.getDrawable(info.icon)!!.mutate()
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            icon.setBounds(0, 0, size, size)
            icon.draw(Canvas(bitmap))
            for ((x, y) in listOf(0 to 0, size - 1 to 0, 0 to size - 1, size - 1 to size - 1)) {
                assertEquals("Transparent corner at $size px ($x,$y)", 0, Color.alpha(bitmap.getPixel(x, y)))
            }
            val occupied = (0 until size).filter { x ->
                (0 until size).any { y -> Color.alpha(bitmap.getPixel(x, y)) > 200 }
            }
            assertTrue("Artwork must not be tiny at $size px", occupied.size >= size * .7f)
            assertTrue("Artwork must remain visible", Color.alpha(bitmap.getPixel(size / 2, size / 2)) > 200)
            bitmap.recycle()
        }
    }
}
