package ir.behnamapps.fascratch.editor

import android.annotation.SuppressLint
import android.os.SystemClock
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewAssetLoader
import ir.behnamapps.fascratch.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class EditorAssetsDeviceTest {
    @Test fun everyAliasServesTheExpectedBytesAndMissingMediaReturns404() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val config = EditorAssetsConfig.load(context.assets)
        val handler = EditorAssetsPathHandler(context, config)
        config.sharedPaths.forEach { (original, shared) ->
            val response = requireNotNull(handler.handle(original))
            assertEquals(original, 200, response.statusCode)
            val digest = response.data.use { input ->
                val hash = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(65536)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
                hash.digest().joinToString("") { "%02x".format(it) }
            }
            assertEquals(original, shared.substringAfterLast('/').substringBefore('.'), digest)
        }
        val missing = config.defaultEngine.entry.substringBefore('/') + "/missing.svg"
        val missingHandler = EditorAssetsPathHandler(context, EditorAssetsConfig(
            config.availableEngines.associateWith { it.entry }, mapOf(missing to "shared/media/nonexistent.svg")
        ))
        assertEquals(404, missingHandler.handle(missing)!!.statusCode)
        config.availableEngines.forEach { engine ->
            assertTrue(handler.handle(engine.entry)!!.data.use { it.read() } >= 0)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Test fun packagedEditorsLoadAddSpritesAndRoundTripProjectsOffline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val config = EditorAssetsConfig.load(context.assets)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (engine in config.availableEngines) {
                lateinit var web: WebView
                scenario.onActivity { activity ->
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    val loader = WebViewAssetLoader.Builder()
                        .addPathHandler("/assets/", EditorAssetsPathHandler(activity, config)).build()
                    web = WebView(activity).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36"
                        activity.attachScratchWebView(this)
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                                if (request.url.host == "appassets.androidplatform.net") {
                                    return loader.shouldInterceptRequest(request.url)
                                }
                                return WebResourceResponse("text/plain", "UTF-8", 404, "Offline test", emptyMap(),
                                    ByteArrayInputStream(ByteArray(0)))
                            }
                            override fun onPageFinished(view: WebView, url: String?) {
                                activity.installScratchTextInputSupport(view)
                            }
                        }
                        activity.setContentView(this)
                        loadUrl(config.entryUrl(engine) + "?lang=en&locale=en")
                    }
                }
                fun evaluate(script: String): String {
                    val result = AtomicReference<String?>()
                    instrumentation.runOnMainSync { web.evaluateJavascript(script) { result.set(it) } }
                    val deadline = SystemClock.elapsedRealtime() + 10_000
                    while (result.get() == null && SystemClock.elapsedRealtime() < deadline) Thread.sleep(30)
                    return result.get() ?: error("WebView JS did not respond: ${engine.id}")
                }
                fun await(script: String) {
                    val deadline = SystemClock.elapsedRealtime() + 90_000
                    while (SystemClock.elapsedRealtime() < deadline) {
                        if (evaluate(script) == "true") return
                        Thread.sleep(150)
                    }
                    error("Offline ${engine.id} timed out: $script; ${evaluate("document.body.innerText.slice(0,300)")}")
                }
                await("""
                    (()=>{
                    const pending=[],seen=new Set();
                    for(const el of document.querySelectorAll('*')){
                    const key=Object.keys(el).find(k=>/^__react(Fiber|InternalInstance|Container)/.test(k));
                    if(key)pending.push(el[key]);}
                    while(pending.length){const f=pending.pop();if(!f||seen.has(f))continue;seen.add(f);
                    const p=f.memoizedProps;if(p&&p.store&&p.store.getState)window.__editorStore=p.store;
                    if(p&&p.vm&&p.vm.runtime)window.__editorVm=p.vm;
                    pending.push(f.child,f.sibling,f.return,f.current);}
                    window.__editorStore=window.__editorStore||window.ReduxStore;
                    window.__editorVm=window.__editorVm||window.vm||
                    (window.__editorStore&&window.__editorStore.getState().scratchGui.vm);
                    return Boolean(window.__editorStore&&window.__editorVm&&window.__editorVm.runtime.targets.length>0);
                    })()
                """.trimIndent())
                evaluate("window.__editorStore.dispatch({type:'scratch-gui/locales/SELECT_LOCALE',locale:'en'});true")
                val count = evaluate("window.__editorVm.runtime.targets.length").toInt()
                evaluate("window.__editorStore.dispatch({type:'scratch-gui/modals/OPEN_MODAL',modal:'spriteLibrary'});true")
                await("Boolean(document.querySelector('[role=dialog] input'))")
                evaluate("""
                    (()=>{const input=document.querySelector('[role=dialog] input');
                    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(input,'Bear');
                    input.dispatchEvent(new Event('input',{bubbles:true}));return true;})()
                """.trimIndent())
                val cards = "document.querySelectorAll('[role=dialog] button,[role=dialog] [role=button]')"
                await("Array.from($cards).some(el=>el.textContent.trim()==='Bear')")
                evaluate("Array.from($cards).find(el=>el.textContent.trim()==='Bear').click();true")
                await("window.__editorVm.runtime.targets.length > $count")
                evaluate("""
                    window.__assetTest=null;
                    (async()=>{try{const blob=await window.__editorVm.saveProjectSb3();
                    await window.__editorVm.loadProject(await blob.arrayBuffer());
                    window.__assetTest={count:window.__editorVm.runtime.targets.length,ok:true};}
                    catch(e){window.__assetTest={error:String(e)};}})();true
                """.trimIndent())
                await("Boolean(window.__assetTest)")
                assertEquals("true", evaluate("window.__assetTest.ok===true && window.__editorVm.runtime.targets.length>$count"))
                instrumentation.runOnMainSync { web.stopLoading(); web.destroy() }
            }
        }
    }
}
