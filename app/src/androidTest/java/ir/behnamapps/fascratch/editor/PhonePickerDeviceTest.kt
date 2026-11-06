package ir.behnamapps.fascratch.editor

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PhonePickerDeviceTest {
    @Test fun homeOpensNativePickerAndCancelDoesNotOpenEditor() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val picker = instrumentation.addMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") },
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val done = CountDownLatch(1)
                scenario.onActivity { activity ->
                    activity.pickProjectFromPhone { project ->
                        assertNull(project)
                        done.countDown()
                    }
                }
                assertTrue("System picker must return cancellation", done.await(15, TimeUnit.SECONDS))
                assertEquals("Home must open the native picker directly", 1, picker.hits)
            }
        } finally { instrumentation.removeMonitor(picker) }
    }

    @Test fun selectedPhoneProjectLoadsInOriginalAndQualityPlaybackReleasesResources() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val picked = CountDownLatch(1)
        var fixture: java.io.File? = null
        var imported: java.io.File? = null
        lateinit var owner: MainActivity
        lateinit var web: android.webkit.WebView
        fun descendants(view: android.view.View): List<android.view.View> = listOf(view) +
            if (view is android.view.ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) }
            else emptyList()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                owner = activity
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                fixture = java.io.File(activity.filesDir, "scratch_projects/PhonePickerTest-${System.currentTimeMillis()}.sb3").apply {
                    activity.assets.open("turbobuild/packager/example.sb3").use { input -> outputStream().use { input.copyTo(it) } }
                }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(owner, "${owner.packageName}.fileprovider", fixture!!)
            val picker = instrumentation.addMonitor(IntentFilter(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); addDataType("*/*") },
                Instrumentation.ActivityResult(Activity.RESULT_OK, Intent().setData(uri)), true)
            try {
                scenario.onActivity { activity ->
                    activity.pickProjectFromPhone { project ->
                        assertNotNull(project)
                        imported = project!!.projectFile
                        activity.queueProjectFromHome(project)
                        activity.setContent {
                            ir.behnamapps.fascratch.ScratchScreen(activity, EditorEngine.SCRATCH, {}, {})
                        }
                        picked.countDown()
                    }
                }
                assertTrue(picked.await(20, TimeUnit.SECONDS))
                assertEquals(1, picker.hits)
                assertArrayEquals(fixture!!.readBytes(), imported!!.readBytes())
                assertTrue(imported!!.canonicalPath.startsWith(java.io.File(owner.filesDir, "scratch_projects").canonicalPath))
            } finally { instrumentation.removeMonitor(picker) }
            val deadline = android.os.SystemClock.elapsedRealtime() + 15000
            var editor: android.webkit.WebView? = null
            while (editor == null && android.os.SystemClock.elapsedRealtime() < deadline) {
                instrumentation.runOnMainSync { editor = descendants(owner.window.decorView).filterIsInstance<android.webkit.WebView>()
                    .firstOrNull { it.url.orEmpty().contains("/assets/build/") } }
                Thread.sleep(50)
            }
            web = requireNotNull(editor)
            val readyDeadline = android.os.SystemClock.elapsedRealtime() + 60000
            var progress = 0
            while (progress < 100 && android.os.SystemClock.elapsedRealtime() < readyDeadline) {
                instrumentation.runOnMainSync { progress = web.progress }
                Thread.sleep(100)
            }
            fun evaluate(script: String): String {
                val result = java.util.concurrent.atomic.AtomicReference<String?>()
                instrumentation.runOnMainSync { web.evaluateJavascript(script) { result.set(it) } }
                val end = android.os.SystemClock.elapsedRealtime() + 30000
                while (result.get() == null && android.os.SystemClock.elapsedRealtime() < end) Thread.sleep(30)
                return result.get() ?: error("Original editor did not respond")
            }
            fun await(script: String) {
                val end = android.os.SystemClock.elapsedRealtime() + 90000
                while (android.os.SystemClock.elapsedRealtime() < end) {
                    if (evaluate(script) == "true") return
                    Thread.sleep(100)
                }
                error("Original quality test timed out: $script; ${evaluate("document.body.innerText.slice(-500)")}")
            }
            await("""
                (()=>{for(const b of document.querySelectorAll('button')) {
                    const key=Object.keys(b).find(k=>k.startsWith('__reactFiber$'));let f=b[key];
                    while(f){if(f.memoizedProps?.vm)window.vm=f.memoizedProps.vm;
                        if(f.memoizedProps?.store?.getState)window.ReduxStore=f.memoizedProps.store;f=f.return;}
                }return Boolean(window.ReduxStore&&ReduxStore.getState().scratchGui.projectTitle===${org.json.JSONObject.quote(fixture!!.nameWithoutExtension)}
                    &&ReduxStore.getState().scratchGui.projectState.loadingState==='SHOWING_WITHOUT_ID');})()
            """.trimIndent())
            evaluate("ReduxStore.dispatch({type:'scratch-gui/locales/SELECT_LOCALE',locale:'fa'});true")
            await("Array.from(document.querySelectorAll('button')).some(e=>e.textContent.trim()==='اجرای با کیفیت'&&!e.disabled)")
            evaluate("Array.from(document.querySelectorAll('button')).find(e=>e.textContent.trim()==='اجرای با کیفیت').click();true")
            var player: android.webkit.WebView? = null
            val playbackDeadline = android.os.SystemClock.elapsedRealtime() + 90000
            while (player == null && android.os.SystemClock.elapsedRealtime() < playbackDeadline) {
                instrumentation.runOnMainSync { player = descendants(owner.window.decorView).filterIsInstance<android.webkit.WebView>()
                    .firstOrNull { it.url.orEmpty().startsWith("https://player.farsiscratch.invalid/") } }
                Thread.sleep(100)
            }
            val playerView = requireNotNull(player) { "Native quality player did not open: ${evaluate("document.body.innerText.slice(-500)")}" }
            val playbackFile = owner.packagedPlayback!!.file
            await("!document.querySelector('iframe[src*=quality-packager]')")
            instrumentation.runOnMainSync { assertEquals(android.view.View.INVISIBLE, editor!!.visibility) }
            web = playerView
            await("Boolean(window.vm&&vm.runtime.targets.length>1)")
            assertEquals("true", evaluate("typeof AndroidPackagedExport==='undefined'&&typeof AndroidProjectSaver==='undefined'"))
            instrumentation.runOnMainSync { owner.closePackagedPlayback() }
            Thread.sleep(400)
            instrumentation.waitForIdleSync()
            assertFalse("Playback HTML must be deleted on close", playbackFile.exists())
            instrumentation.runOnMainSync { assertNull(playerView.parent); assertEquals(android.view.View.VISIBLE, editor!!.visibility) }
        }
        fixture?.delete()
        assertTrue("Imported project must persist after Activity destruction", imported!!.exists())
        imported?.delete()
    }
}
