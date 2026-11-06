package ir.behnamapps.fascratch.editor

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ir.behnamapps.fascratch.MainActivity
import ir.behnamapps.fascratch.ScratchScreen
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DownloadedSpritesDeviceTest {
    @Test fun downloadedArchiveIsListedAndAddedInBothEditors() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        for (engine in EditorEngine.entries) ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var owner: MainActivity
            lateinit var fixture: File
            lateinit var namesFile: File
            var priorNames: ByteArray? = null
            val name = "DownloadedFixture-${System.nanoTime()}"
            val displayName = "اسپرایت آزمایشی $name"
            scenario.onActivity { activity ->
                owner = activity
                activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val entries = mutableMapOf<String, ByteArray>()
                ZipInputStream(activity.assets.open("turbobuild/packager/example.sb3")).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) { if (!entry.isDirectory) entries[entry.name] = zip.readBytes(); entry = zip.nextEntry }
                }
                val sprite = JSONObject(String(entries.remove("project.json")!!, Charsets.UTF_8)).getJSONArray("targets").getJSONObject(1).put("name", name)
                val handler = DownloadedSpritesPathHandler(activity)
                fixture = File(handler.directory, "$name.sprite3")
                ZipOutputStream(fixture.outputStream()).use { zip ->
                    zip.putNextEntry(ZipEntry("sprite.json")); zip.write(sprite.toString().toByteArray()); zip.closeEntry()
                    entries.forEach { (path, bytes) -> zip.putNextEntry(ZipEntry(path)); zip.write(bytes); zip.closeEntry() }
                }
                namesFile = File(handler.directory, "names.json")
                priorNames = if (namesFile.exists()) namesFile.readBytes() else null
                val names = priorNames?.let { JSONObject(it.toString(Charsets.UTF_8)) } ?: JSONObject()
                names.put(fixture.name, JSONObject().put("en", name).put("fa", displayName).put("ar", displayName))
                namesFile.writeText(names.toString())
                val catalog = JSONArray(handler.handle("catalog.json").data.bufferedReader().readText())
                assertTrue((0 until catalog.length()).any { catalog.getJSONObject(it).getString("name") == name })
                assertArrayEquals(fixture.readBytes(), handler.handle("sprite/${fixture.name}").data.readBytes())
                assertEquals(404, handler.handle("sprite/../outside.sprite3").statusCode)
                assertTrue(handler.handle("preview/${fixture.name}").data.readBytes().isNotEmpty())
                activity.setContent { ScratchScreen(activity, engine, {}, {}) }
            }
            try {
                fun descendants(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { descendants(v.getChildAt(it)) } else emptyList()
                fun evaluate(script: String): String? {
                    val result = AtomicReference<String?>()
                    instrumentation.runOnMainSync { descendants(owner.window.decorView).filterIsInstance<WebView>().firstOrNull()?.evaluateJavascript(script) { result.set(it) } }
                    val end = SystemClock.elapsedRealtime() + 3000
                    while (result.get() == null && SystemClock.elapsedRealtime() < end) Thread.sleep(30)
                    return result.get()
                }
                fun await(script: String) {
                    val end = SystemClock.elapsedRealtime() + 90000
                    while (SystemClock.elapsedRealtime() < end) { if (evaluate(script) == "true") return; Thread.sleep(100) }
                    error("Downloaded sprite test failed in $engine: $script; DOM=" + evaluate("JSON.stringify({origin:location.origin,bridge:typeof AndroidProjectSaver,probe:window.__downloadedProbe,head:document.body.innerText.slice(0,1400),tail:document.body.innerText.slice(-500)})"))
                }
                await("""(()=>{for(const b of document.querySelectorAll('button')){let f=b[Object.keys(b).find(k=>k.startsWith('__reactFiber$'))];while(f){if(f.memoizedProps?.vm)window.vm=f.memoizedProps.vm;if(f.memoizedProps?.store?.getState)window.ReduxStore=f.memoizedProps.store;f=f.return;}}return Boolean(window.vm&&window.ReduxStore&&typeof AndroidScratchLoadProjectUrl==='function');})()""")
                evaluate("window.__downloadedProbe='pending';fetch('https://appassets.androidplatform.net/downloaded-sprites/catalog.json').then(r=>r.text()).then(t=>window.__downloadedProbe=t).catch(e=>window.__downloadedProbe=String(e));true")
                await("window.__downloadedProbe!=='pending'")
                evaluate("ReduxStore.dispatch({type:'scratch-gui/modals/OPEN_MODAL',modal:'spriteLibrary'});true")
                await("document.body.innerText.includes('دانلودی‌ها')&&document.body.innerText.includes('$displayName')")
                evaluate("Array.from(document.querySelectorAll('button,[role=button],span,div')).find(e=>e.textContent==='دانلودی‌ها').click();true")
                await("document.body.innerText.includes('$displayName')")
                evaluate("Array.from(document.images).filter(i=>i.src.includes('/downloaded-sprites/preview/')).forEach(i=>{i.loading='eager';i.scrollIntoView()});true")
                await("Array.from(document.images).some(i=>i.src.includes('/downloaded-sprites/preview/')&&i.naturalWidth>0)")
                evaluate("Array.from(document.querySelectorAll('button,[role=button]')).find(e=>e.textContent==='$displayName').click();true")
                await("vm.runtime.targets.some(t=>t.getName()==='$displayName')")
            } finally { fixture.delete(); if (priorNames == null) namesFile.delete() else namesFile.writeBytes(priorNames!!) }
        }
    }
}
