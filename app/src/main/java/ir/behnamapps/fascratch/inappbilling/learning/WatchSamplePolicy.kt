package ir.behnamapps.fascratch.inappbilling.learning

internal object WatchSamplePolicy {
    fun advancing(previousPlaying: Boolean, playing: Boolean, elapsedMs: Long, positionDelta: Long): Boolean =
        previousPlaying && playing && elapsedMs in 1..2000 && positionDelta in 1..(elapsedMs * 4 + 1500)
}
