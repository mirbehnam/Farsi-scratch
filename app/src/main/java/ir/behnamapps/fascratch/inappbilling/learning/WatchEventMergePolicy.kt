package ir.behnamapps.fascratch.inappbilling.learning

/** Only adjacent, never-submitted checkpoints may share one immutable server receipt. */
internal object WatchEventMergePolicy {
    fun canMerge(start: Long, end: Long, nextStart: Long, nextEnd: Long,
                 watch: Long, nextWatch: Long, to: Long, nextFrom: Long): Boolean =
        end == nextStart && to == nextFrom && nextEnd > end && start < end &&
            watch > 0 && nextWatch > 0 && watch + nextWatch <= 60_000 && nextEnd - start <= 65_000
}
