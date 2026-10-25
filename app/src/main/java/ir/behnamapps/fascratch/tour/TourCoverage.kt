package ir.behnamapps.fascratch.tour

/** Local playback coverage. Seeks never create watched time. No account or reward data. */
internal class TourCoverage(val durationMs: Int, initial: List<Pair<Int, Int>> = emptyList(), var ended: Boolean = false) {
    private val spans = mutableListOf<Pair<Int, Int>>()
    private var lastPosition: Int? = null
    private var lastTime = 0L
    init { require(durationMs in 1..14_400_000); initial.forEach { add(it.first, it.second) } }
    val complete: Boolean get() = ended && durationMs - spans.sumOf { it.second - it.first } <= minOf(1000, durationMs / 100)
    fun ranges(): List<Pair<Int, Int>> = spans.toList()
    fun discontinuity() { lastPosition = null }
    fun sample(position: Int, playing: Boolean, now: Long, finished: Boolean = false) {
        val p = position.coerceIn(0, durationMs)
        lastPosition?.let { previous ->
            val delta = p - previous
            val elapsed = now - lastTime
            if ((playing || finished) && elapsed in 0..2000 && delta in 0..(elapsed + 150).toInt()) add(previous, p)
        }
        if (finished && p >= durationMs - 1000) ended = true
        // A stationary paused sample is also a safe baseline for the next play tick.
        // Actual seeks explicitly reset this baseline through the player's callback.
        lastPosition = p.takeUnless { finished }
        lastTime = now
    }
    private fun add(start: Int, end: Int) {
        if (end <= start || start < 0 || end > durationMs) return
        var a = start; var b = end
        val iterator = spans.iterator()
        while (iterator.hasNext()) {
            val old = iterator.next()
            if (old.first <= b && old.second >= a) { a = minOf(a, old.first); b = maxOf(b, old.second); iterator.remove() }
        }
        spans.add(a to b); spans.sortBy { it.first }
    }
}
