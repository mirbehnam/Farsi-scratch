package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.presentation.PlayerSeekQueue
import ir.behnamapps.fascratch.inappbilling.presentation.LessonPlayerPolicy
import org.junit.Assert.*
import org.junit.Test

class PlayerSeekQueueTest {
    @Test fun repeatedJumpsAccumulateBeforeDecoderCompletes() {
        val queue = PlayerSeekQueue()
        assertEquals(15000, queue.request(15000))
        assertNull(queue.request(LessonPlayerPolicy.seek(queue.target!!, 5000, 60000)))
        assertNull(queue.request(LessonPlayerPolicy.seek(queue.target!!, 5000, 60000)))
        assertEquals(25000, queue.target)
        assertEquals(25000, queue.complete())
        assertTrue(queue.active)
        assertNull(queue.complete())
        assertFalse(queue.active)
        assertNull(queue.target)
    }
    @Test fun reversingDirectionKeepsLatestTargetAndResetDropsOldRequests() {
        val queue = PlayerSeekQueue()
        queue.request(15000)
        queue.request(20000)
        queue.request(10000)
        assertEquals(10000, queue.complete())
        queue.reset()
        assertFalse(queue.active)
        assertNull(queue.target)
        assertNull(queue.complete())
        assertEquals(5000, queue.request(5000))
    }
}
