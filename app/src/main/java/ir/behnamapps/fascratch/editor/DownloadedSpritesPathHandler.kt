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
import java.util.zip.ZipFile

/** Local, read-only catalog shared by the two editor engines. */
class DownloadedSpritesPathHandler(context: Context) : WebViewAssetLoader.PathHandler {
    val directory = File(context.filesDir, "downloaded_sprites").apply { mkdirs() }.canonicalFile

    private fun sprite(name: String): File {
        val file = File(directory, name).canonicalFile
        require(file.parentFile == directory && file.isFile && file.extension.equals("sprite3", true)) { "Invalid downloaded sprite: $name" }
        return file
    }
    private fun readLimited(input: java.io.InputStream, limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit) { "Sprite entry too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
    private fun metadata(file: File): JSONObject = ZipFile(file).use { zip ->
        val entry = requireNotNull(zip.getEntry("sprite.json")) { "sprite.json missing" }
        val bytes = zip.getInputStream(entry).use { readLimited(it, 1024 * 1024) }
        require(bytes.size <= 1024 * 1024) { "Sprite metadata too large" }
        JSONObject(String(bytes, Charsets.UTF_8)).also { require(!it.optBoolean("isStage")) { "Stage is not a sprite" } }
    }
    private fun preview(file: File): Pair<String, ByteArray> {
        val costume = metadata(file).getJSONArray("costumes").getJSONObject(0)
        val name = costume.optString("md5ext").ifEmpty { costume.getString("assetId") + "." + costume.getString("dataFormat") }
        require('/' !in name && '\\' !in name) { "Invalid costume path" }
        val mime = when (name.substringAfterLast('.').lowercase()) {
            "svg" -> "image/svg+xml"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
            else -> error("Unsupported preview format")
        }
        val bytes = ZipFile(file).use { zip ->
            val entry = requireNotNull(zip.getEntry(name)) { "Preview missing" }
            zip.getInputStream(entry).use { readLimited(it, 16 * 1024 * 1024) }
        }
        require(bytes.size <= 16 * 1024 * 1024) { "Preview too large" }
        return mime to bytes
    }
    private fun catalog(): JSONArray {
        val list = JSONArray()
        val names = downloadedDisplayNames(directory)
        directory.listFiles().orEmpty().filter { it.isFile && it.extension.equals("sprite3", true) }.sortedBy { it.name }.forEach { candidate ->
            try {
                val file = sprite(candidate.name)
                val data = metadata(file)
                require(data.getJSONArray("costumes").length() > 0) { "No costumes" }
                val id = Uri.encode(file.name) + "?v=${file.lastModified()}-${file.length()}"
                list.put(JSONObject().put("name", data.optString("name").ifBlank { file.nameWithoutExtension })
                    .put("fileName", file.name).put("displayNames", names.optJSONObject(file.name))
                    .put("downloadedSpriteUrl", "https://appassets.androidplatform.net/downloaded-sprites/sprite/$id")
                    .put("rawURL", "https://appassets.androidplatform.net/downloaded-sprites/preview/$id")
                    .put("tags", JSONArray().put("downloaded")))
            } catch (error: Exception) { Log.w("DownloadedSprites", "Cannot index sprite: ${candidate.name}", error) }
        }
        return list
    }
    override fun handle(path: String): WebResourceResponse = try {
        when {
            path == "catalog.json" -> WebResourceResponse("application/json", "UTF-8", ByteArrayInputStream(catalog().toString().toByteArray(Charsets.UTF_8)))
            path.startsWith("sprite/") -> WebResourceResponse("application/x.scratch.sprite3", null, sprite(path.removePrefix("sprite/")).inputStream())
            path.startsWith("preview/") -> preview(sprite(path.removePrefix("preview/"))).let { (mime, bytes) -> WebResourceResponse(mime, null, ByteArrayInputStream(bytes)) }
            else -> error("Unknown downloaded sprite resource")
        }
    } catch (error: Exception) {
        Log.w("DownloadedSprites", "Cannot serve resource: $path", error)
        WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }
}
