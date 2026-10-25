package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.MissionItem
import ir.behnamapps.fascratch.inappbilling.learning.coinBalanceText
import org.junit.Assert.*
import org.junit.Test

class CoinAndCycleUiTest {
    @Test fun repeatableMissionCanHaveRewardReadyWhileNextCycleIsEmpty() {
        val item = MissionItem("coding_month", "ماه", "coding_days", 30, 0, 2000, "2026-10-17", "ready",
            rewardCoins = 2000, repeatable = true, completedCount = 2, pendingRewards = 1, achievementUnlocked = true, nextCycleTomorrow = true)
        assertTrue(item.ready)
        assertEquals(0f, item.fraction)
        assertTrue(item.dayCaption.contains("30"))
        assertTrue(item.dayCaption.contains("فردا"))
        assertEquals(2000, item.rewardCoins)
        assertTrue(item.achievementUnlocked)
    }

    @Test fun coinsUseCompactGroupedDigitsAndDoNotShowInvalidNumbers() {
        assertEquals("2,000", coinBalanceText(2000.0))
        assertEquals("0", coinBalanceText(.125))
        assertEquals("1,999", coinBalanceText(1999.999))
        assertEquals("0", coinBalanceText(-1.0))
        assertEquals("0", coinBalanceText(Double.NaN))
    }
}
