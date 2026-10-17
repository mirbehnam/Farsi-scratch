package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.R
import ir.behnamapps.fascratch.inappbilling.learning.AvatarArtwork
import org.junit.Assert.*
import org.junit.Test

class AvatarArtworkTest {
    @Test fun tenDistinctBundledAvatarsAreMappedWithoutReplacingTheDefault() {
        val ids = listOf("junior", "explorer", "builder", "debugger", "designer", "roboticist", "architect", "wizard", "senior", "full_stack")
        assertEquals(10, ids.map(AvatarArtwork::resource).distinct().size)
        ids.forEach {
            assertTrue(AvatarArtwork.known(it))
            assertNotEquals(R.drawable.learner_level_badge, AvatarArtwork.resource(it))
            assertTrue(AvatarArtwork.resource(it) != 0)
        }
    }

    @Test fun MissingOrFutureAvatarUsesSafeDefault() {
        listOf("default", "", "null", "future_badge", "../junior").forEach {
            assertFalse(AvatarArtwork.known(it))
            assertEquals(R.drawable.learner_level_badge, AvatarArtwork.resource(it))
        }
    }
}
