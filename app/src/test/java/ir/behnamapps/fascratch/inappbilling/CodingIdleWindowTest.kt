package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.CodingIdleWindow
import org.junit.Assert.*
import org.junit.Test

class CodingIdleWindowTest {
    @Test fun stopsExactlyAfterThreeMinutes() {
        val idle = CodingIdleWindow(1_000L)
        assertTrue(idle.active(180_999L))
        assertEquals(1L, idle.remainingMs(180_999L))
        assertFalse(idle.active(181_000L))
        assertFalse(idle.active(900_000L))
    }

    @Test fun touchingAfterIdleStartsANewWindowWithoutCreditingTheIdleGap() {
        val idle = CodingIdleWindow(0L)
        assertFalse(idle.active(600_000L))
        idle.interact(600_000L)
        assertTrue(idle.active(600_000L))
        assertEquals(180_000L, idle.remainingMs(600_000L))
        assertFalse(idle.active(780_000L))
    }

    @Test fun ongoingDragKeepsTheWindowActiveWithoutRestartingWork() {
        val idle = CodingIdleWindow(0L)
        for (now in 1_000L..600_000L step 1_000L) {
            idle.interact(now)
            assertTrue(idle.active(now))
        }
        assertTrue(idle.active(779_999L))
        assertFalse(idle.active(780_000L))
    }

    @Test fun touchBeforeDeadlineMovesTheDeadline() {
        val idle = CodingIdleWindow(0L)
        idle.interact(120_000L)
        assertEquals(120_000L, idle.remainingMs(180_000L))
        assertFalse(idle.active(300_000L))
    }

    @Test fun staleTimestampsDoNotMoveTheLastTouchBackwards() {
        val idle = CodingIdleWindow(100_000L)
        idle.interact(50_000L)
        assertEquals(180_000L, idle.remainingMs(100_000L))
        assertFalse(idle.active(280_000L))
    }
}
