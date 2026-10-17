package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.learning.CoinChime
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class CoinChimeTest {
    @Test fun coinSoundIsShortAudibleAndDoesNotClip() {
        val samples = CoinChime.samples()
        assertEquals((22050 * .45).toInt(), samples.size)
        assertEquals(0, samples.first().toInt())
        assertTrue(samples.count { abs(it.toInt()) > 1000 } > 1000)
        assertTrue(samples.maxOf { abs(it.toInt()) } < 30000)
        assertTrue(samples.takeLast(100).maxOf { abs(it.toInt()) } < 100)
    }
}
