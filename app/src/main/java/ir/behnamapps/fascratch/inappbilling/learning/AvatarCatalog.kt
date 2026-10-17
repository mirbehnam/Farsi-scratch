package ir.behnamapps.fascratch.inappbilling.learning

import ir.behnamapps.fascratch.R
import org.json.JSONObject

internal data class AvatarItem(val id: String, val title: String, val price: Int, val description: String, val owned: Boolean, val equipped: Boolean)
internal data class AvatarCatalog(val uuid: String, val available: Boolean, val items: List<AvatarItem>) {
    companion object {
        fun parse(json: JSONObject): AvatarCatalog {
            val rows = json.optJSONArray("items")
            return AvatarCatalog(json.getString("uuid"), json.optBoolean("available"),
                (0 until (rows?.length() ?: 0).coerceAtMost(50)).mapNotNull { i ->
                    val row = rows?.optJSONObject(i) ?: return@mapNotNull null
                    val id = row.optString("id")
                    val price = row.optInt("price_coins", -1)
                    if (!AvatarArtwork.known(id) || price !in 1..1_000_000) return@mapNotNull null
                    AvatarItem(id, row.optString("title").take(80), price, row.optString("description").take(300), row.optBoolean("owned"), row.optBoolean("equipped"))
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
