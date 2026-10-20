package ir.behnamapps.fascratch.inappbilling.learning

import org.json.JSONObject

internal data class RankedLearner(val uuid: String, val name: String, val rank: Int, val level: Int, val xp: Double,
    val avatarId: String, val imageUrl: String?, val imageHash: String?) {
    companion object {
        fun parse(json: JSONObject) = RankedLearner(json.getString("uuid"), json.getString("display_name").take(100), json.getInt("rank").coerceAtLeast(1),
            json.getInt("level").coerceAtLeast(0), json.optDouble("xp", 0.0).takeIf { it.isFinite() && it >= 0 } ?: 0.0,
            json.optString("avatar_id", "default"), json.optString("image_url").takeUnless { it.isBlank() || it == "null" },
            json.optString("image_sha256").takeIf { AvatarImages.validHash(it) })
    }
}
internal data class LeaderboardBoard(val viewer: String, val viewerRank: Int?, val total: Int, val top: List<RankedLearner>, val nearby: List<RankedLearner>) {
    companion object {
        fun parse(json: JSONObject): LeaderboardBoard {
            fun list(key: String, max: Int): List<RankedLearner> = json.optJSONArray(key)?.let { a ->
                (0 until a.length().coerceAtMost(max)).map { RankedLearner.parse(a.getJSONObject(it)) }.distinctBy { it.uuid }
            }.orEmpty()
            return LeaderboardBoard(json.getString("viewer_uuid"), json.optInt("viewer_rank").takeIf { !json.isNull("viewer_rank") && it > 0 },
                json.optInt("total").coerceAtLeast(0), list("top", 50), list("nearby", 21))
        }
    }
}
internal data class ShowcaseAvatar(val id: String, val title: String, val equipped: Boolean, val url: String?, val hash: String?)
internal data class ShowcaseTrophy(val id: String, val title: String, val imageUrl: String? = null, val imageHash: String? = null)
internal data class ShowcaseStats(val watchMs: Long = 0, val codingMs: Long = 0, val completedLessons: Int = 0)
internal data class LearnerShowcase(val learner: RankedLearner, val memberDays: Long, val avatars: List<ShowcaseAvatar>, val trophies: List<ShowcaseTrophy>, val stats: ShowcaseStats? = null) {
    companion object {
        fun parse(json: JSONObject): LearnerShowcase {
            val images = json.optJSONArray("avatars")
            val trophies = json.optJSONArray("achievements")
            return LearnerShowcase(RankedLearner.parse(json.getJSONObject("learner")), json.optLong("membership_days").coerceAtLeast(0),
                (0 until (images?.length() ?: 0).coerceAtMost(300)).map { i -> images!!.getJSONObject(i).let {
                    ShowcaseAvatar(it.getString("id"), it.optString("title").take(80), it.optBoolean("equipped"),
                        it.optString("image_url").takeUnless { s -> s.isBlank() || s == "null" }, it.optString("image_sha256").takeIf(AvatarImages::validHash))
                } },
                (0 until (trophies?.length() ?: 0).coerceAtMost(100)).map { i -> trophies!!.getJSONObject(i).let {
                    ShowcaseTrophy(it.getString("id"), it.getString("title").take(100),
                        it.optString("image_url").takeUnless { value -> value.isBlank() || value == "null" },
                        it.optString("image_sha256").takeIf(AvatarImages::validHash))
                } },
                json.optJSONObject("stats")?.let { ShowcaseStats(it.optLong("watch_ms").coerceAtLeast(0), it.optLong("coding_ms").coerceAtLeast(0), it.optInt("completed_lessons").coerceAtLeast(0)) })
        }
    }
}
