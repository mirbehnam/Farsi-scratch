package ir.behnamapps.fascratch.editor

import android.content.Context
import android.net.Uri
import android.util.Log
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

internal fun downloadedDisplayNames(directory: File): JSONObject {
    val manifest = File(directory, "names.json")
    if (!manifest.exists()) return JSONObject()
    return try {
        require(manifest.canonicalFile.parentFile == directory.canonicalFile && manifest.length() <= 1048576) { "Invalid downloaded names manifest" }
        JSONObject(manifest.readText(Charsets.UTF_8))
    } catch (error: Exception) {
        Log.w("DownloadedMedia", "Cannot read names manifest: $manifest", error)
        JSONObject()
    }
}

class DownloadedMediaPathHandler(context: Context, kind: String) : WebViewAssetLoader.PathHandler {
    private val directory = File(context.filesDir, "downloaded_$kind").apply { mkdirs() }.canonicalFile
    private val prefix = "https://appassets.androidplatform.net/downloaded-$kind/"
    private val types = if (kind == "backdrops") mapOf("svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg")
        else mapOf("wav" to "audio/wav", "mp3" to "audio/mpeg")
    private fun file(name: String): File = File(directory, name).canonicalFile.also {
        require(it.parentFile == directory && it.isFile && it.extension.lowercase() in types && it.length() <= 134217728) { "Invalid downloaded media: $name" }
    }
    override fun handle(path: String): WebResourceResponse = try {
        if (path == "catalog.json") {
            val catalog = JSONArray()
            val names = downloadedDisplayNames(directory)
            directory.listFiles().orEmpty().sortedBy { it.name }.forEach { candidate ->
                try {
                    val media = file(candidate.name)
                    val url = prefix + "file/" + Uri.encode(media.name) + "?v=${media.lastModified()}-${media.length()}"
                    catalog.put(JSONObject().put("name", media.nameWithoutExtension).put("downloadedMedia", true).put("fileName", media.name).put("displayNames", names.optJSONObject(media.name))
                        .put("downloadedSpriteUrl", url).put("rawURL", url).put("mime", types[media.extension.lowercase()])
                        .put("tags", JSONArray().put("downloaded")))
                } catch (error: Exception) { Log.w("DownloadedMedia", "Cannot index ${candidate.name}", error) }
            }
            WebResourceResponse("application/json", "UTF-8", ByteArrayInputStream(catalog.toString().toByteArray(Charsets.UTF_8)))
        } else {
            require(path.startsWith("file/")) { "Unknown downloaded media resource" }
            val media = file(path.removePrefix("file/"))
            WebResourceResponse(types.getValue(media.extension.lowercase()), null, media.inputStream())
        }
    } catch (error: Exception) {
        Log.w("DownloadedMedia", "Cannot serve $path", error)
        WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }
}
