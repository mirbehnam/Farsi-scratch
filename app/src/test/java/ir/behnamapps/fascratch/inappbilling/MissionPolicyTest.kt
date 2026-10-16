package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.MissionBoard
import ir.behnamapps.fascratch.inappbilling.learning.MissionItem
import org.junit.Assert.*
import org.junit.Test

class MissionPolicyTest {
    private fun item(status: String, value: Long, target: Long = 100) = MissionItem(status, status, "watch_ms", target, value, 50, "once", status)

    @Test fun progressIsAlwaysBoundedAndOnlyServerReadyCanClaim() {
        assertEquals(.5f, item("active", 50).fraction)
        assertEquals(0f, item("active", -10).fraction)
        assertEquals(1f, item("claimed", 1000).fraction)
        assertEquals(0f, item("active", 10, 0).fraction)
        assertFalse(item("active", 100).ready)
        assertTrue(item("ready", 100).ready)
        assertFalse(item("claimed", 100).ready)
    }

    @Test fun readyRewardsSortFirstThenActiveThenClaimed() {
        val board = MissionBoard("account-a", true, listOf(item("claimed",100),item("active",20),item("ready",100)))
        assertEquals(listOf("ready","active","claimed"),board.ordered.map { it.status })
    }
}
