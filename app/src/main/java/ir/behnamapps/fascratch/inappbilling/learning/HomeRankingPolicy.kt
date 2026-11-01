package ir.behnamapps.fascratch.inappbilling.learning

internal object HomeRankingPolicy {
    fun showViewer(registered: Boolean, viewer: String?, topIds: List<String>): Boolean =
        registered && !viewer.isNullOrBlank() && viewer !in topIds
    fun show(hasCache: Boolean, enabled: Boolean, probability: Double, draw: Double): Boolean =
        hasCache && enabled && draw >= 0.0 && draw < 1.0 && draw * 100 < probability.coerceIn(0.0, 100.0)
    const val MAX_PLAYERS = 50
    const val MIN_REFRESH_MS = 60_000L
}
