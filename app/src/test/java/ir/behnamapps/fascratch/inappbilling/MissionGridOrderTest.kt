package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.*
import org.junit.Assert.*
import org.junit.Test

class MissionGridOrderTest {
    private val task = MissionItem("daily", "روزانه", "visit", 1, 1, 10, "today", "ready")
    private val offer = RewardedAdOffer("app", "placement", 10, 10, 2, 2, 120, null, 0)

    @Test fun adOccupiesFirstCellAndSharesRowWithFirstMission() {
        val entries = MissionBoard("account", true, listOf(task), offer).gridEntries
        assertEquals(listOf("rewarded_ad", "daily"), entries.map { it.key })
        assertEquals(1, entries.chunked(2).size)
        assertTrue(entries.first() is MissionGridEntry.Ad)
    }

    @Test fun disabledAdsLeaveNoBlankCellOrDedicatedRow() {
        assertEquals(listOf("daily"), MissionBoard("account", true, listOf(task)).gridEntries.map { it.key })
    }
}
