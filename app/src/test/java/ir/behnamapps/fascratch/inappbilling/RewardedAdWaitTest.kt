package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.*
import org.junit.Assert.*
import org.junit.Test

class RewardedAdWaitTest {
    private fun offer(remaining: Int = 0, retry: Long? = 7_201_000) =
        RewardedAdOffer("app", "placement", 10, 10, remaining, 2, 120, retry, 1_000)

    @Test fun waitsUseServerTimeAndOnlyApplyWhenQuotaIsExhausted() {
        assertEquals(7_200_000L, rewardedAdWaitMs(offer()))
        assertEquals(0L, rewardedAdWaitMs(offer(1)))
        assertEquals(0L, rewardedAdWaitMs(offer(retry = 500)))
        assertEquals(0L, rewardedAdWaitMs(offer(retry = null)))
    }

    @Test fun readableWaitRoundsUpWithoutPrematureUnlock() {
        assertEquals("نوبت بعد: 2 ساعت دیگر", rewardedAdWaitText(7_200_000))
        assertEquals("نوبت بعد: 1 ساعت و 1 دقیقه دیگر", rewardedAdWaitText(3_600_001))
        assertEquals("نوبت بعد: 1 دقیقه دیگر", rewardedAdWaitText(1))
        assertEquals("در حال بررسی نوبت بعد…", rewardedAdWaitText(0))
    }
}
