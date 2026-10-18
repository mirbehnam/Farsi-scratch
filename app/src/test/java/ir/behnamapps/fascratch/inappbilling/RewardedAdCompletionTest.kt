package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.RewardedAdCompletion
import org.junit.Assert.*
import org.junit.Test

class RewardedAdCompletionTest {
    @Test fun onlyShownAndSdkRewardedAdsQualify() {
        assertTrue(RewardedAdCompletion.earned(true, true))
        assertFalse(RewardedAdCompletion.earned(true, false))
        assertFalse(RewardedAdCompletion.earned(false, true))
        assertFalse(RewardedAdCompletion.earned(false, false))
    }
}
