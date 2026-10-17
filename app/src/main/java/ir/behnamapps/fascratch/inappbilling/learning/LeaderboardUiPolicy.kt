package ir.behnamapps.fascratch.inappbilling.learning

internal fun nearbyScrollIndex(topCount: Int, nearbyCount: Int, selfIndex: Int): Int =
    if (nearbyCount <= 0) 0 else topCount.coerceIn(0, 50) + 2 + (selfIndex.coerceIn(0, nearbyCount - 1) - 2).coerceAtLeast(0)

internal fun membershipDuration(days: Long): String {
    val value = days.coerceAtLeast(0)
    return when {
        value < 1 -> "از امروز همراه اسکرچ"
        value < 7 -> "$value روز همراه اسکرچ"
        value < 30 -> "${value / 7} هفته همراه اسکرچ"
        value < 365 -> "${value / 30} ماه همراه اسکرچ"
        value % 365 / 30 == 0L -> "${value / 365} سال همراه اسکرچ"
        else -> "${value / 365} سال و ${value % 365 / 30} ماه همراه اسکرچ"
    }
}
