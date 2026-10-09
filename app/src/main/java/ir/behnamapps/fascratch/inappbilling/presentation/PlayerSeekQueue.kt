package ir.behnamapps.fascratch.inappbilling.presentation

/** Keep the latest requested destination while one asynchronous decoder seek is in flight. */
internal class PlayerSeekQueue {
    var target: Int? = null
        private set
    private var inFlight: Int? = null
    val active: Boolean get() = inFlight != null

    fun request(position: Int): Int? {
        target = position
        if (inFlight != null) return null
        inFlight = position
        return position
    }

    fun complete(): Int? {
        if (target == inFlight) { reset(); return null }
        inFlight = target
        return target
    }

    fun reset() { target = null; inFlight = null }
}
