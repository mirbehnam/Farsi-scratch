package ir.behnamapps.fascratch.inappbilling
import ir.behnamapps.fascratch.inappbilling.domain.DeferredStop
import org.junit.Assert.*
import org.junit.Test
class DeferredStopTest {
    @Test fun received_stop_waits_for_next_launch_even_when_home_reopens() {
        val launch=DeferredStop<String>(); launch.start(null); launch.received("update"); launch.start("update"); assertNull(launch.active)
        val next=DeferredStop<String>(); next.start("update"); assertEquals("update",next.active)
    }
    @Test fun rotation_does_not_drop_cached_lock_and_valid_removal_unlocks() {
        val launch=DeferredStop<String>(); launch.start("update"); launch.start(null); assertEquals("update",launch.active)
        launch.received("maintenance"); assertEquals("maintenance",launch.active)
        launch.received(null); assertNull(launch.active)
        launch.received("new-stop"); assertNull(launch.active)
    }
}
