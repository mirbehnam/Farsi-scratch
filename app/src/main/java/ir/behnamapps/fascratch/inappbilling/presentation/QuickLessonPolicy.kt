package ir.behnamapps.fascratch.inappbilling.presentation

/** Small value object only: no player, Surface, bitmap, Context or Activity can survive minimize. */
internal data class LessonPlaybackCheckpoint(val positionMs: Int = 0, val speed: Float = 1f, val autoPlay: Boolean = true)

internal object QuickLessonPolicy {
    fun shortcutVisible(accountEnabled: Boolean, recoveryRequired: Boolean, purchasedCourseIds: Set<String>) =
        accountEnabled && !recoveryRequired && purchasedCourseIds.isNotEmpty()

    fun checkpoint(position: Int, speed: Float, playing: Boolean) = LessonPlaybackCheckpoint(
        position.coerceAtLeast(0), speed.takeIf { it in LessonPlayerPolicy.speeds } ?: 1f, playing)
}
