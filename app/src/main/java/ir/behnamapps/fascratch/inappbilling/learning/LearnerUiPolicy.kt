package ir.behnamapps.fascratch.inappbilling.learning

internal fun learningDuration(milliseconds: Long): String {
    val minutes = milliseconds.coerceAtLeast(0) / 60000
    return if (minutes < 60) "$minutes دقیقه" else "${minutes / 60} ساعت و ${minutes % 60} دقیقه"
}
