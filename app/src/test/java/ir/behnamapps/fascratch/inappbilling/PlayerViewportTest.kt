package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.presentation.PlayerViewport
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerViewportTest {
    @Test fun wideVideoFitsWithoutStretching() {
        val fit = PlayerViewport.fit(1000f, 500f, 1920f, 1080f)
        assertEquals(500f, fit.height, .01f)
        assertEquals(16f / 9f, fit.width / fit.height, .01f)
    }
    @Test fun portraitVideoKeepsItsAspectInLandscape() {
        val fit = PlayerViewport.fit(1000f, 500f, 1080f, 1920f)
        assertEquals(281.25f, fit.width, .01f)
        assertEquals(500f, fit.height, .01f)
    }
    @Test fun zoomIsBoundedAndRejectsNonFiniteValues() {
        assertEquals(1f, PlayerViewport.zoom(.2f), 0f)
        assertEquals(4f, PlayerViewport.zoom(9f), 0f)
        assertEquals(1f, PlayerViewport.zoom(Float.NaN), 0f)
    }
    @Test fun panningCannotLoseVideoOffscreen() {
        assertEquals(0f, PlayerViewport.pan(500f, 800f, 1000f, 1f), 0f)
        assertEquals(300f, PlayerViewport.pan(999f, 800f, 1000f, 2f), 0f)
        assertEquals(-300f, PlayerViewport.pan(-999f, 800f, 1000f, 2f), 0f)
        assertEquals(0f, PlayerViewport.pan(Float.NaN, 800f, 1000f, 2f), 0f)
    }
    @Test fun unknownVideoSizeDoesNotDivideByZero() {
        assertEquals(PlayerViewport.Fit(0f, 0f), PlayerViewport.fit(1000f, 500f, 0f, 0f))
    }
}
