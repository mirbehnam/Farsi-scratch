package ir.behnamapps.fascratch.inappbilling

import ir.behnamapps.fascratch.inappbilling.presentation.PlayerViewport
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPinchGeometryTest {
    @Test fun parallelFingerMovementPansWithoutChangingZoom() {
        assertEquals(40f, PlayerViewport.anchoredPan(10f, 200f, 230f, 500f, 1f), .001f)
        assertEquals(-20f, PlayerViewport.anchoredPan(10f, 200f, 170f, 500f, 1f), .001f)
    }
    @Test fun zoomAndPanKeepTheContentUnderTheFingerCentroid() {
        assertEquals(75f, PlayerViewport.anchoredPan(10f, 400f, 410f, 500f, 1.5f), .001f)
        // Translating before scaling around the current focal point is equivalent.
        assertEquals(75f, PlayerViewport.anchoredPan(20f, 410f, 410f, 500f, 1.5f), .001f)
        assertEquals(0f, PlayerViewport.pan(500f, 500f, 1000f, 1f), .001f)
        assertEquals(250f, PlayerViewport.pan(500f, 1000f, 1000f, 1.5f), .001f)
    }
}
