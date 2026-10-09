package ir.behnamapps.fascratch.inappbilling.learning

import org.junit.Assert.*
import org.junit.Test

class WatchEventMergePolicyTest {
    @Test fun adjacentCheckpointsMerge() {
        assertTrue(WatchEventMergePolicy.canMerge(0, 5000, 5000, 10000, 5000, 5000, 5000, 5000))
    }
    @Test fun pauseSeekAndOversizedWindowsStaySeparate() {
        assertFalse(WatchEventMergePolicy.canMerge(0, 5000, 6000, 11000, 5000, 5000, 5000, 5000))
        assertFalse(WatchEventMergePolicy.canMerge(0, 5000, 5000, 10000, 5000, 5000, 5000, 15000))
        assertFalse(WatchEventMergePolicy.canMerge(0, 60000, 60000, 65000, 60000, 5000, 60000, 60000))
    }
}
