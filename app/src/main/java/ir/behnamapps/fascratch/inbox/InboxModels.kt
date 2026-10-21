package ir.behnamapps.fascratch.inbox

import org.json.JSONObject
import java.net.URI
import java.util.UUID

internal data class InboxImage(val url: String, val hash: String, val bytes: Int, val width: Int, val height: Int)
internal data class InboxButton(val label: String, val url: String)
internal data class InboxMessage(val id: String, val revision: Int, val notificationVersion: Int, val title: String, val body: String,
    val kind: String, val pinned: Boolean, val publishedMs: Long, val expiresMs: Long?, val dateLabel: String,
    val buttons: List<InboxButton>, val image: InboxImage?, val scope: String) {
    val readKey get() = "$scope:$id"
}
internal data class InboxFeed(val json: JSONObject, val messages: List<InboxMessage>, val etag: String?)
internal data class InboxState(val messages: List<InboxMessage> = emptyList(), val read: Map<String, Int> = emptyMap(),
    val initialized: Boolean = false, val refreshing: Boolean = false, val failed: Boolean = false, val sessionKey: String? = null) {
    val unread get() = messages.count { InboxPolicy.unread(it.notificationVersion, read[it.readKey]) }
}

internal object InboxPolicy {
    const val MAX_FEED_BYTES = 4 * 1024 * 1024
    const val MAX_IMAGE_BYTES = 1024 * 1024
    fun unread(notification: Int, read: Int?): Boolean = notification > (read ?: 0)
    fun refreshDue(now: Long, last: Long, cooldown: Long): Boolean = now >= cooldown && (last == 0L || now - last >= 30_000)
    fun visibleMessages(public: List<InboxMessage>, personal: List<InboxMessage>, currentKey: String?, cachedKey: String?, now: Long): List<InboxMessage> =
        (public + if (currentKey != null && currentKey == cachedKey) personal else emptyList())
            .filter { it.expiresMs == null || it.expiresMs > now }
            .sortedWith(compareByDescending<InboxMessage> { it.pinned }.thenByDescending { it.publishedMs }.thenBy { it.id })
    fun badge(count: Int): String = when { count <= 0 -> ""; count > 99 -> "۹۹+"; else -> count.toString().map { if (it in '0'..'9') "۰۱۲۳۴۵۶۷۸۹"[it - '0'] else it }.joinToString("") }
    fun uuid(value: String): Boolean = runCatching { UUID.fromString(value).toString() == value.lowercase() && value.length == 36 }.getOrDefault(false)
    fun hash(value: String): Boolean = value.matches(Regex("[a-f0-9]{64}"))
    fun safeLink(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme == "https" && !uri.host.isNullOrBlank() && '.' in uri.host && uri.rawUserInfo == null && uri.port in setOf(-1, 443) &&
            !uri.host.startsWith('[') && !uri.host.matches(Regex("[0-9.]+")) && !uri.host.endsWith(".local") &&
            value.length <= 2000 && !Regex("[\\x00-\\x20\\x7f<>]|%(?:0[0-9a-f]|1[0-9a-f]|7f)", RegexOption.IGNORE_CASE).containsMatchIn(value)
    }.getOrDefault(false)

    fun mediaUrl(value: String, scope: String, base: String, id: String, hash: String): Boolean = runCatching {
        if (!safeLink(value)) return false
        val uri = URI(value)
        val prefix = URI(base)
        // Private credentials are never sent outside this exact first-party media route.
        scope == "public" || (uri.host == prefix.host && uri.port == prefix.port && uri.path == prefix.path.trimEnd('/') + "/inbox/personal/$id/image/$hash")
    }.getOrDefault(false)

    fun parse(bytes: ByteArray, etag: String?, scope: String, owner: String?, flavor: String, version: Int, base: String): InboxFeed {
        require(bytes.size <= MAX_FEED_BYTES)
        val data = JSONObject(String(bytes, Charsets.UTF_8)).getJSONObject("data")
        require(data.getInt("schema") == 1 && data.getBoolean("complete") && data.getString("scope") == scope)
        require(data.getString("flavor") == flavor && data.getInt("version_code") == version)
        require(if (scope == "personal") data.getString("account_uuid") == owner else data.isNull("account_uuid"))
        val array = data.getJSONArray("messages")
        require(array.length() <= 100)
        val seen = HashSet<String>()
        fun text(json: JSONObject, field: String, max: Int): String = json.getString(field).also { require(it.codePointCount(0, it.length) <= max) }
        val messages = List(array.length()) { index ->
            val row = array.getJSONObject(index)
            val id = row.getString("id"); require(uuid(id) && seen.add(id))
            val revision = row.getInt("revision"); val notification = row.getInt("notification_version")
            require(revision > 0 && notification > 0)
            val actions = row.getJSONArray("buttons"); require(actions.length() <= 2)
            val buttons = List(actions.length()) { i -> actions.getJSONObject(i).let { action ->
                InboxButton(text(action, "label", 40), action.getString("url").also { require(safeLink(it)) })
            } }
            val image = row.optJSONObject("image")?.let {
                val digest = it.getString("sha256"); val count = it.getInt("bytes"); val width = it.getInt("width"); val height = it.getInt("height")
                require(hash(digest) && count in 1..MAX_IMAGE_BYTES && width in 1..1600 && height in 1..1600 && it.getString("mime") == "image/webp")
                val url = it.getString("url"); require(mediaUrl(url, scope, base, id, digest))
                InboxImage(url, digest, count, width, height)
            }
            val published = row.getLong("published_ms"); val expiry = if (row.isNull("expires_ms")) null else row.getLong("expires_ms")
            require(published > 0 && (expiry == null || expiry > published))
            InboxMessage(id, revision, notification, text(row,"title",100), text(row,"body",4000),
                row.getString("kind").takeIf { it in setOf("info","celebration","lesson","warning") } ?: "info", row.getBoolean("pinned"),
                published, expiry, text(row,"date_label",80), buttons, image, scope)
        }
        return InboxFeed(JSONObject().put("data",data), messages, etag?.take(200))
    }
}
