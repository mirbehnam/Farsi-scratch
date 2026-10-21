package ir.behnamapps.fascratch.inappbilling.learning

import org.json.JSONObject
import java.net.URI

data class PublicProfileDetails(
    val available: Boolean = false, val version: Int = 0, val canEditBio: Boolean = false, val canEditSocials: Boolean = false,
    val status: String = "empty", val reason: String? = null, val bio: String = "", val links: Map<String, String> = emptyMap(),
    val editCostCoins: Int? = null,
) {
    companion object {
        fun parse(json: JSONObject?): PublicProfileDetails {
            if (json == null) return PublicProfileDetails()
            val draft = json.optJSONObject("draft")
            return PublicProfileDetails(json.optBoolean("available"), json.optInt("version").coerceAtLeast(0),
                json.optBoolean("can_edit_bio"), json.optBoolean("can_edit_socials"),
                json.optString("status").takeIf { it in setOf("empty", "pending", "approved", "rejected") } ?: "empty",
                json.optString("reason").takeUnless { it.isBlank() || it == "null" }?.take(300),
                PublicProfilePolicy.text(draft?.optString("bio")), PublicProfilePolicy.links(draft?.optJSONObject("social_links")),
                json.optInt("edit_cost_coins").takeIf { !json.isNull("edit_cost_coins") && it in 1..100000 })
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

    fun normalizeInput(network: String, input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return ""
        val host = hosts[network] ?: return null
        val candidate = if ('/' in value || ':' in value) {
            if (value.startsWith("https://")) value.trimEnd('/') else "https://" + value.trimEnd('/')
        } else "https://$host/" + value.trimStart('@')
        return safeUrl(network, candidate)
    }

    fun editCost(previousBio: String, previousLinks: Map<String, String>, bio: String, links: Map<String, String>, price: Int): Int {
        val bioChanged = bio.trim().isNotEmpty() && bio.trim() != previousBio
        val linksChanged = links.any { (network, value) ->
            val normalized = normalizeInput(network, value)
            normalized != null && normalized.isNotEmpty() && normalized != previousLinks[network]
        }
        return (if (bioChanged) price else 0) + (if (linksChanged) price else 0)
    }
}
