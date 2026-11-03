package ir.behnamapps.fascratch.editor

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Locale

class EditorAssetsPathHandler(context: Context, private val config: EditorAssetsConfig) : WebViewAssetLoader.PathHandler {
    private val assets = context.assets
    private val ordinaryAssets = WebViewAssetLoader.AssetsPathHandler(context)

    override fun handle(path: String): WebResourceResponse? {
        val shared = config.sharedPaths[path] ?: return ordinaryAssets.handle(path)
        return try {
            WebResourceResponse(mediaMimeType(path), null, 200, "OK", emptyMap(),
                assets.open(shared, AssetManager.ACCESS_STREAMING))
        } catch (error: IOException) {
            Log.e("EditorAssets", "Cannot load shared media: original=$path target=$shared", error)
            // Never fall through to the network for a missing mapped local file.
            WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
    }

    private fun mediaMimeType(path: String): String = when (path.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "bmp" -> "image/bmp"
        "ico" -> "image/x-icon"
        "avif" -> "image/avif"
        "wav" -> "audio/wav"
        "mp3" -> "audio/mpeg"
        "ogg" -> "audio/ogg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "flac" -> "audio/flac"
        else -> "application/octet-stream"
    }
}
