package ir.behnamapps.fascratch.inappbilling.learning

import org.junit.Assert.*
import org.junit.Test

class HomeRankingPolicyTest {
    @Test fun defaultChanceSelectsOneThirdOfUniformDraws() {
        assertEquals(1000, (0 until 3000).count { HomeRankingPolicy.show(true, true, 100.0 / 3.0, (it + .5) / 3000.0) })
    }
    @Test fun missingCacheAndDisabledPolicyAlwaysKeepNormalHome() {
        assertFalse(HomeRankingPolicy.show(false, true, 100.0, 0.0))
        assertFalse(HomeRankingPolicy.show(true, false, 100.0, 0.0))
    }
    @Test fun adminProbabilityBoundariesAndFractionalValues() {
        assertFalse(HomeRankingPolicy.show(true, true, 0.0, 0.0))
        assertTrue(HomeRankingPolicy.show(true, true, 100.0, .999999))
        assertTrue(HomeRankingPolicy.show(true, true, 25.5, .254))
        assertFalse(HomeRankingPolicy.show(true, true, 25.5, .255))
        assertFalse(HomeRankingPolicy.show(true, true, Double.NaN, .1))
    }
}
