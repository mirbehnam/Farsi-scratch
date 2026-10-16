package ir.behnamapps.fascratch.inappbilling.learning

import org.json.JSONObject

data class MissionItem(val id: String, val title: String, val metric: String, val target: Long, val progress: Long,
    val reward: Int, val period: String, val status: String) {
    val fraction: Float get() = if (target <= 0) 0f else (progress.toDouble() / target).coerceIn(0.0, 1.0).toFloat()
    val ready: Boolean get() = status == "ready"
}

data class MissionBoard(val uuid: String, val available: Boolean, val items: List<MissionItem>) {
    val ordered: List<MissionItem> get() = items.sortedBy { when (it.status) { "ready" -> 0; "active" -> 1; else -> 2 } }
    companion object {
        fun parse(json: JSONObject): MissionBoard {
            val rows = json.optJSONArray("items")
            return MissionBoard(json.getString("uuid"), json.optBoolean("available"), rows?.let {
                (0 until it.length()).mapNotNull { index -> runCatching {
                    val row = it.getJSONObject(index)
                    val id = row.getString("id")
                    require(id in setOf("first_code", "coding_30", "watch_10", "watch_60", "daily", "details"))
                    MissionItem(id, row.getString("title"), row.getString("metric"), row.getLong("target").coerceAtLeast(1),
                        row.getLong("progress").coerceAtLeast(0), row.getInt("reward_xp").coerceAtLeast(0), row.getString("period"), row.getString("status"))
                }.getOrNull() }
            } ?: emptyList())
        }
    }
}
