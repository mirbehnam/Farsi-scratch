package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.WatchSamplePolicy
import org.junit.Assert.*
import org.junit.Test

class WatchSamplePolicyTest {
    @Test fun onlyAdvancingContinuousPlaybackCounts() {
        assertTrue(WatchSamplePolicy.advancing(true, true, 300, 300))
        assertTrue(WatchSamplePolicy.advancing(true, true, 300, 600))
        assertFalse(WatchSamplePolicy.advancing(false, true, 300, 300))
        assertFalse(WatchSamplePolicy.advancing(true, false, 300, 300))
        assertFalse(WatchSamplePolicy.advancing(true, true, 300, 0))
    }
    @Test fun seekSleepAndClockResetNeverCountAsWatchTime() {
        assertFalse(WatchSamplePolicy.advancing(true, true, 300, 10000))
        assertFalse(WatchSamplePolicy.advancing(true, true, 300, -10000))
        assertFalse(WatchSamplePolicy.advancing(true, true, 60000, 60000))
        assertFalse(WatchSamplePolicy.advancing(true, true, -1, 300))
    }
}
