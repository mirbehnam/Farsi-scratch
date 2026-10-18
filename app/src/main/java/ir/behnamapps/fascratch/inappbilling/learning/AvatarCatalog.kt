package ir.behnamapps.fascratch.inappbilling.learning

import ir.behnamapps.fascratch.R
import org.json.JSONObject

internal data class AvatarItem(val id: String, val title: String, val price: Int, val description: String, val owned: Boolean, val equipped: Boolean,
    val imageUrl: String? = null, val imageHash: String? = null, val forSale: Boolean = true)
internal data class AvatarCatalog(val uuid: String, val available: Boolean, val items: List<AvatarItem>) {
    /** Free built-in appearance belongs in the same grid; never send it through the buy flow. */
    fun displayItems(selectedAvatarId: String): List<AvatarItem> =
        listOf(AvatarItem("default", "گربهٔ پیش‌فرض", 0, "", owned = true,
            equipped = selectedAvatarId == "default", forSale = false)) +
            items.filterNot { it.id == "default" }.map { it.copy(equipped = it.id == selectedAvatarId) }

    companion object {
        fun parse(json: JSONObject): AvatarCatalog {
            val rows = json.optJSONArray("items")
            return AvatarCatalog(json.getString("uuid"), json.optBoolean("available"),
                (0 until (rows?.length() ?: 0).coerceAtMost(300)).mapNotNull { i ->
                    val row = rows?.optJSONObject(i) ?: return@mapNotNull null
                    val id = row.optString("id")
                    val price = row.optInt("price_coins", -1)
                    val url = row.optString("image_url").takeIf { it != "null" && it.isNotBlank() }
                    val hash = row.optString("image_sha256").takeIf { AvatarImages.validHash(it) }
                    if (!id.matches(Regex("[a-z0-9_]{1,40}")) || price !in 1..1_000_000 || (!AvatarArtwork.known(id) && (url == null || hash == null))) return@mapNotNull null
                    AvatarItem(id, row.optString("title").take(80), price, row.optString("description").take(300), row.optBoolean("owned"), row.optBoolean("equipped"), url, hash, row.optBoolean("is_for_sale", true))
                }.distinctBy { it.id })
        }
    }
}

internal object AvatarArtwork {
    private val images = mapOf(
        "junior" to R.drawable.learner_badge_junior,
        "explorer" to R.drawable.learner_badge_explorer,
        "builder" to R.drawable.learner_badge_builder,
        "debugger" to R.drawable.learner_badge_debugger,
        "designer" to R.drawable.learner_badge_designer,
        "roboticist" to R.drawable.learner_badge_roboticist,
        "architect" to R.drawable.learner_badge_architect,
        "wizard" to R.drawable.learner_badge_wizard,
        "senior" to R.drawable.learner_badge_senior,
        "full_stack" to R.drawable.learner_badge_full_stack,
    )
    fun known(id: String) = images.containsKey(id)
    fun resource(id: String) = images[id] ?: R.drawable.learner_level_badge
}
