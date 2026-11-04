package ir.behnamapps.fascratch

import org.junit.Assert.*
import org.junit.Test

class GamepadDirectionTest {
    @Test fun allEightDirectionsRemainSelectedOutsideThePanel() {
        GamepadDirection.vectors.forEachIndexed { index, (x, y) ->
            assertEquals(index, GamepadDirection.sector(x * 50f, y * 50f, 10f))
            assertEquals(index, GamepadDirection.sector(x * 5000f, y * 5000f, 10f))
        }
    }
    @Test fun centerAndInvalidCoordinatesReleaseKeys() {
        assertNull(GamepadDirection.sector(0f, 0f, 10f))
        assertNull(GamepadDirection.sector(6f, 6f, 10f))
        assertNull(GamepadDirection.sector(Float.NaN, 50f, 10f))
    }
    @Test fun sectorsSnapToNearestDirection() {
        assertEquals(0, GamepadDirection.sector(100f, 40f, 10f))
        assertEquals(1, GamepadDirection.sector(100f, 43f, 10f))
        assertEquals(7, GamepadDirection.sector(100f, -43f, 10f))
        assertEquals(5, GamepadDirection.sector(-100f, -100f, 10f))
    }
}
