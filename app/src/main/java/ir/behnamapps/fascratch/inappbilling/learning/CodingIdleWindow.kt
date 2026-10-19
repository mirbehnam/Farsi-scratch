package ir.behnamapps.fascratch.inappbilling.learning

/** Monotonic time only: wall-clock changes must never extend credited coding time. */
internal class CodingIdleWindow(startMs: Long, private val timeoutMs: Long = TIMEOUT_MS) {
    init { require(timeoutMs > 0) }
    private var lastInteractionMs = startMs

    fun interact(nowMs: Long) { lastInteractionMs = maxOf(lastInteractionMs, nowMs) }

    fun remainingMs(nowMs: Long): Long =
        (timeoutMs - (nowMs - lastInteractionMs).coerceAtLeast(0)).coerceIn(0, timeoutMs)

    fun active(nowMs: Long): Boolean = remainingMs(nowMs) > 0

    companion object { const val TIMEOUT_MS = 3 * 60 * 1000L }
}
