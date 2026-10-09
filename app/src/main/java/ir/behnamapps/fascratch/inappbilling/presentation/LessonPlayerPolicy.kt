package ir.behnamapps.fascratch.inappbilling.presentation

import ir.behnamapps.fascratch.inappbilling.domain.Lesson

internal object LessonPlayerPolicy {
    val speeds = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)
    // Physical screen sides, deliberately independent of RTL text direction.
    fun doubleTapDelta(x: Float, width: Int) = if (x >= width / 2f) 5_000 else -5_000
    fun seek(position: Int, delta: Int, duration: Int) =
        (position.toLong() + delta).coerceIn(0L, duration.coerceAtLeast(0).toLong()).toInt()
    fun next(lessons: List<Lesson>, currentId: String): Lesson? {
        val index = lessons.indexOfFirst { it.id == currentId }
        return if (index < 0) null else lessons.getOrNull(index + 1)
    }
}
