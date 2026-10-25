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

    @Test fun hallVisitorMissionIsAcceptedAndAppearsWithIndependentRewards() {
        assertTrue("Hall visits must not be discarded by the API parser", "showcase_10" in MissionBoard.supportedMissionIds)
        val visit = MissionItem("showcase_10", "گردشگر تالارها", "showcase_visits", 10, 4, 30, "once", "active", rewardCoins = 150)
        val entry = MissionBoard("account", true, listOf(task, visit)).gridEntries.last() as MissionGridEntry.Task
        assertEquals("showcase_10", entry.key)
        assertEquals(150, entry.mission.rewardCoins)
        assertEquals(30, entry.mission.reward)
        assertEquals(.4f, entry.mission.fraction)
    }

    @Test fun tourMissionIsAcceptedAndShowsCoinAndXpRewardsSeparately() {
        assertTrue("tour_complete" in MissionBoard.supportedMissionIds)
        val tour = MissionItem("tour_complete", "راهنمای اسکرچ را کشف کن", "tour_videos", 3, 3, 20, "once", "ready", rewardCoins = 100)
        val entry = MissionBoard("account", true, listOf(tour)).gridEntries.single() as MissionGridEntry.Task
        assertEquals(100, entry.mission.rewardCoins)
        assertEquals(20, entry.mission.reward)
        assertTrue(entry.mission.ready)
    }
}
