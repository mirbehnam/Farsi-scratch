package ir.behnamapps.fascratch.tour

import org.json.JSONObject
import java.net.URI

internal data class TourMedia(val url: String, val hash: String, val bytes: Long)
internal data class TourVideo(val id: String, val title: String, val description: String, val durationMs: Int,
    val video: TourMedia, val cover: TourMedia?, val json: String) {
    val key get() = "$id-${video.hash}"
}
internal data class TourState(val initialized: Boolean = false, val items: List<TourVideo> = emptyList(),
    val completed: Set<String> = emptySet(), val downloading: String? = null, val progress: Float = 0f, val coverRevision: Int = 0,
    val hasManifest: Boolean = false, val refreshing: Boolean = false, val catalogFailed: Boolean = false) {
    val allWatched: Boolean get() = items.isNotEmpty() && items.all { it.key in completed }
    fun completedVersions(): List<Pair<String, String>> = items.filter { it.key in completed }.map { it.id to it.video.hash }
}

internal object TourModels {
    const val MAX_CACHE = 512L * 1024 * 1024
    fun mediaUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null
    }.getOrDefault(false)
    fun parse(json: JSONObject): List<TourVideo> {
        require(json.getInt("schema") == 1)
        val rows = json.getJSONArray("items"); require(rows.length() <= 100)
        val seen = hashSetOf<String>()
        return List(rows.length()) { i ->
            val row = rows.getJSONObject(i); val id = row.getString("id")
            require(id.matches(Regex("[a-f0-9-]{36}")) && seen.add(id))
            fun media(obj: JSONObject, max: Long): TourMedia {
                val url = obj.getString("url"); val hash = obj.getString("sha256"); val size = obj.getLong("bytes")
                require(mediaUrl(url) && hash.matches(Regex("[a-f0-9]{64}")) && size in 1..max)
                return TourMedia(url, hash, size)
            }
            val video = media(row.getJSONObject("video"), MAX_CACHE)
            require(row.getString("version") == video.hash)
            val duration = row.getInt("duration_ms"); require(duration in 1..14_400_000)
            TourVideo(id, row.getString("title").take(120), row.optString("description").takeUnless { it == "null" }.orEmpty().take(1000),
                duration, video, row.optJSONObject("cover")?.let { media(it, 10L * 1024 * 1024) }, row.toString())
        }
    }
}
