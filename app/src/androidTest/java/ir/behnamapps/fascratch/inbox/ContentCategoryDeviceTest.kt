package ir.behnamapps.fascratch.inbox

import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.MainActivity
import ir.behnamapps.fascratch.inappbilling.learning.ContentCategoryDialog
import ir.behnamapps.fascratch.inappbilling.learning.storeCategories
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

class ContentCategoryDeviceTest {
    @Test fun fourIllustratedCategoriesAreVisibleAndSelectDistinctTypes() = verify(1f)
    @Test fun largeFontsKeepTheCategoryChooserInsideTheScreen() = verify(2f)
    private fun verify(scale:Float){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val selected=AtomicReference<String>();val closed=AtomicBoolean(false)
        val previous=android.provider.Settings.System.getString(instrumentation.targetContext.contentResolver,"font_scale")
        fun fontSetting(command:String){android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).use{it.readBytes()};SystemClock.sleep(500)}
        try {
        // Dialog windows have their own density: change the real system setting, not just a parent composition local.
        fontSetting("settings put system font_scale $scale")
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            scenario.onActivity{activity->activity.setContent{
                assertEquals(scale,activity.resources.configuration.fontScale,0.01f)
                ContentCategoryDialog({closed.set(true)}){selected.set(it)}
            }}
            fun find(label:String):AccessibilityNodeInfo?{
                fun visit(node:AccessibilityNodeInfo):AccessibilityNodeInfo?{
                    if(node.text?.contains(label)==true)return node
                    for(i in 0 until node.childCount)node.getChild(i)?.let{visit(it)?.let{found->return found}}
                    return null
                }
                return instrumentation.uiAutomation.rootInActiveWindow?.let(::visit)
            }
            val deadline=SystemClock.uptimeMillis()+12_000
            while(find("دانلود اسپرایت")==null && SystemClock.uptimeMillis()<deadline)SystemClock.sleep(100)
            instrumentation.waitForIdleSync();SystemClock.sleep(500)
            val screenshot=instrumentation.uiAutomation.takeScreenshot()
            val bounds=Rect(0,0,screenshot.width,screenshot.height)
            java.io.File(instrumentation.targetContext.getExternalFilesDir(null),"content-categories-$scale.png").outputStream().use{
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
            }
            for(category in storeCategories){
                val node=find(category.title);assertNotNull(category.title,node)
                val rect=Rect();node!!.getBoundsInScreen(rect)
                assertFalse(rect.isEmpty);assertTrue("${category.kind} outside $bounds: $rect",bounds.contains(rect))
                var button:AccessibilityNodeInfo?=node
                while(button!=null&&!button.isClickable)button=button.parent
                assertTrue(button?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true)
                instrumentation.waitForIdleSync();assertEquals(category.kind,selected.get())
            }
            screenshot.recycle()
            var close=find("بستن")!!
            while(!close.isClickable)close=close.parent
            assertTrue(close.performAction(AccessibilityNodeInfo.ACTION_CLICK));instrumentation.waitForIdleSync();assertTrue(closed.get())
        }
        } finally {fontSetting(if(previous==null) "settings delete system font_scale" else "settings put system font_scale $previous")}
    }
}
