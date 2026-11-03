package ir.behnamapps.fascratch.editor

import android.content.res.AssetManager
import org.json.JSONObject

enum class EditorEngine(val id: String, val entry: String) {
    SCRATCH("scratch", "build/index.html"),
    TURBO("turbo", "turbobuild/index.html")
}

class EditorAssetsConfig(entries: Map<EditorEngine, String>, aliases: Map<String, String>) {
    private val entries = entries.toMap()
    val sharedPaths: Map<String, String> = aliases.toMap()
    val availableEngines = EditorEngine.entries.filter { it in entries }
    val defaultEngine = availableEngines.firstOrNull() ?: error("Android editor manifest contains no editor")

    init {
        require(entries.all { (engine, entry) -> entry == engine.entry }) { "Invalid Android editor entry URL" }
        require(aliases.all { (original, shared) ->
            availableEngines.any { original.startsWith(it.entry.substringBefore('/') + "/") } &&
                shared.startsWith("shared/media/") && listOf(original, shared).all { path ->
                    !path.startsWith('/') && '\\' !in path && path.split('/').none { it == ".." || it == "." }
                }
        }) { "Invalid Android shared asset mapping" }
    }

    fun entryUrl(engine: EditorEngine = defaultEngine): String {
        require(engine in entries) { "Editor '${engine.id}' is not included in this Android build" }
        return "https://appassets.androidplatform.net/assets/${entries.getValue(engine)}"
    }

    companion object {
        fun load(assets: AssetManager): EditorAssetsConfig {
            val json = assets.open("editor-assets.json").bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
            require(json.getInt("version") == 1) { "Unsupported Android editor asset manifest version" }
            val engines = json.getJSONObject("engines")
            val entries = engines.keys().asSequence().associate { id ->
                val engine = EditorEngine.entries.firstOrNull { it.id == id }
                    ?: error("Unknown editor '$id' in Android editor manifest")
                engine to engines.getString(id)
            }
            val shared = json.getJSONObject("shared")
            return EditorAssetsConfig(entries, shared.keys().asSequence().associateWith { shared.getString(it) })
        }
    }
}
