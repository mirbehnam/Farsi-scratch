package ir.behnamapps.fascratch.inappbilling.learning

/** Keep in sync with the web avatar preview and docs/AVATAR_BADGE_LAYOUT.md. */
internal object AvatarBadgeLayout {
    const val TEXT_TOP = .60f
    const val TEXT_HEIGHT = .30f
    const val TEXT_WIDTH = .80f
    const val LINE_HEIGHT = 1.15f
    const val SHADOW = .006f

    fun fontRatio(level: Int): Float = if (level < 1000) .12f else .10f

    // The image and text must share the same centered, fitted square even in a rectangular slot.
    fun side(width: Float, height: Float): Float = minOf(width, height).coerceAtLeast(0f)
}
