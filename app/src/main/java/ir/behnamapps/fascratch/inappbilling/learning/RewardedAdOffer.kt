package ir.behnamapps.fascratch.inappbilling.learning

import org.json.JSONObject

data class RewardedAdOffer(val appId: String, val placementId: String, val rewardCoins: Int, val rewardXp: Int,
    val remaining: Int, val limit: Int, val windowMinutes: Int, val retryAtMs: Long?, val serverTimeMs: Long) {
    companion object {
        fun parse(json: JSONObject?): RewardedAdOffer? = runCatching {
            if (json == null || !json.optBoolean("enabled")) return null
            val app = json.getString("app_id"); val placement = json.getString("placement_id")
            java.util.UUID.fromString(app); java.util.UUID.fromString(placement)
            RewardedAdOffer(app, placement, json.getInt("reward_coins").coerceIn(0, 1_000_100_000),
                json.getInt("reward_xp").coerceIn(0, 1_000_100_000), json.getInt("remaining").coerceIn(0, 20),
                json.getInt("limit").coerceIn(1, 20), json.getInt("window_minutes").coerceIn(1, 10080),
                json.optLong("retry_at_ms").takeIf { it > 0 }, json.getLong("server_time_ms"))
        }.getOrNull()
    }
}

/** Server-relative duration; never trust the phone's wall clock to unlock a reward. */
internal fun rewardedAdWaitMs(offer: RewardedAdOffer): Long =
    if (offer.remaining > 0) 0 else offer.retryAtMs?.let { (it - offer.serverTimeMs).coerceIn(0, 604_800_000) } ?: 0

internal fun rewardedAdWaitText(ms: Long): String {
    if (ms <= 0) return "در حال بررسی نوبت بعد…"
    val minutes = (ms / 60_000 + if (ms % 60_000 > 0) 1 else 0)
    val hours = minutes / 60
    return "نوبت بعد: " + when {
        hours == 0L -> "$minutes دقیقه دیگر"
        minutes % 60 == 0L -> "$hours ساعت دیگر"
        else -> "$hours ساعت و ${minutes % 60} دقیقه دیگر"
    }
}

internal data class RewardedAdTicket(val uuid: String, val placement: String, val coins: Int, val xp: Int, val expiresAtMs: Long) {
    companion object {
        fun parse(json: JSONObject) = RewardedAdTicket(json.getString("uuid"), json.getString("placement_id"),
            json.getInt("reward_coins"), json.getInt("reward_xp"), json.getLong("expires_at_ms"))
    }
}
