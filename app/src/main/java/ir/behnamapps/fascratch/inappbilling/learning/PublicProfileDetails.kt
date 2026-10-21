package ir.behnamapps.fascratch.inappbilling.learning

import org.json.JSONObject
import java.net.URI

data class PublicProfileDetails(
    val available: Boolean = false, val version: Int = 0, val canEditBio: Boolean = false, val canEditSocials: Boolean = false,
    val status: String = "empty", val reason: String? = null, val bio: String = "", val links: Map<String, String> = emptyMap(),
) {
    companion object {
        fun parse(json: JSONObject?): PublicProfileDetails {
            if (json == null) return PublicProfileDetails()
            val draft = json.optJSONObject("draft")
            return PublicProfileDetails(json.optBoolean("available"), json.optInt("version").coerceAtLeast(0),
                json.optBoolean("can_edit_bio"), json.optBoolean("can_edit_socials"),
                json.optString("status").takeIf { it in setOf("empty", "pending", "approved", "rejected") } ?: "empty",
                json.optString("reason").takeUnless { it.isBlank() || it == "null" }?.take(300),
                PublicProfilePolicy.text(draft?.optString("bio")), PublicProfilePolicy.links(draft?.optJSONObject("social_links")))
        }
    }
}

object PublicProfilePolicy {
    val networks = linkedMapOf("telegram" to "تلگرام", "rubika" to "روبیکا", "bale" to "بله", "instagram" to "اینستاگرام")
    private val hosts = mapOf("telegram" to "t.me", "rubika" to "rubika.ir", "bale" to "ble.ir", "instagram" to "www.instagram.com")
    private val reserved = setOf("joinchat", "share", "login", "accounts", "explore", "reel", "reels", "stories", "direct", "p", "c", "s")
    fun length(value: String): Int = value.codePointCount(0, value.length)
    fun text(value: String?): String {
        if (value.isNullOrBlank() || value == "null") return ""
        return if (length(value) <= 200) value else value.substring(0, value.offsetByCodePoints(0, 200))
    }
    fun safeUrl(network: String, value: String): String? {
        val host = hosts[network] ?: return null
        return try {
            val uri = URI(value)
            val username = uri.rawPath?.removePrefix("/") ?: return null
            val pattern = if (network == "instagram") Regex("[A-Za-z0-9_][A-Za-z0-9_.]{0,29}") else Regex("[A-Za-z][A-Za-z0-9_]{2,31}")
            if (uri.scheme != "https" || uri.host?.lowercase() !in setOf(host, host.removePrefix("www.")) || uri.rawUserInfo != null || uri.port != -1 ||
                uri.rawQuery != null || uri.rawFragment != null || !pattern.matches(username) || username.lowercase() in reserved) null
            else "https://$host/$username"
        } catch (_: Exception) { null }
    }
    fun links(json: JSONObject?): Map<String, String> = networks.keys.mapNotNull { key ->
        safeUrl(key, json?.optString(key).orEmpty())?.let { key to it }
    }.toMap()
}
