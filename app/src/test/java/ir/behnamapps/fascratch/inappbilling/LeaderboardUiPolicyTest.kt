package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.membershipDuration
import ir.behnamapps.fascratch.inappbilling.learning.nearbyScrollIndex
import org.junit.Assert.*
import org.junit.Test

class LeaderboardUiPolicyTest {
    @Test fun initialPositionKeepsSelfVisibleAndHandlesEmptyOrSmallLeaderboards() {
        assertEquals(60, nearbyScrollIndex(50, 21, 10))
        assertEquals(52, nearbyScrollIndex(50, 2, 1))
        assertEquals(0, nearbyScrollIndex(50, 0, -1))
        assertEquals(4, nearbyScrollIndex(2, 2, 0))
    }
    @Test fun membershipUsesReadableDaysWeeksMonthsAndYears() {
        assertEquals("از امروز همراه اسکرچ", membershipDuration(-1))
        assertEquals("3 روز همراه اسکرچ", membershipDuration(3))
        assertEquals("2 هفته همراه اسکرچ", membershipDuration(15))
        assertEquals("2 ماه همراه اسکرچ", membershipDuration(65))
        assertEquals("1 سال همراه اسکرچ", membershipDuration(365))
        assertEquals("1 سال و 2 ماه همراه اسکرچ", membershipDuration(430))
    }
}
