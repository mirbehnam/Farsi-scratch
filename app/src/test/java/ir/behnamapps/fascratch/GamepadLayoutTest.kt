package ir.behnamapps.fascratch

import org.junit.Assert.*
import org.junit.Test

class GamepadLayoutTest {
    @Test fun overlappingArrowHoldsDoNotReleaseEachOther() {
        val events = mutableListOf<Pair<Int, Boolean>>()
        val dispatch = GamepadKeyDispatch { code, down -> events.add(code to down) }
        dispatch.change(0, 19, true)
        dispatch.change(1, 19, true)
        dispatch.change(1, 19, false)
        assertTrue(events.all { it.second })
        dispatch.change(0, 19, false)
        assertEquals(19 to false, events.last())
    }
    @Test fun defaultsAndPersistence() {
        assertTrue(GamepadLayout.valid(GamepadLayout.defaults))
        assertEquals(GamepadLayout.defaults, GamepadLayout.decode(GamepadLayout.encode(GamepadLayout.defaults)))
        assertEquals(GamepadLayout.defaults, GamepadLayout.decode("ESC:0"))
        assertEquals(listOf(PadKey("A", 3)), GamepadLayout.decode("A:0"))
    }
    @Test fun enforcesLimitSupportedKeysAndSpaceWidth() {
        var items = emptyList<PadKey>()
        for (key in 'A'..'K') items = GamepadLayout.add(items, key.toString())
        assertEquals(10, items.size)
        assertEquals(items, GamepadLayout.add(items, "ESC"))
        assertFalse(GamepadLayout.valid(listOf(PadKey("SPACE", 1))))
        assertFalse(GamepadLayout.valid(listOf(PadKey("SPACE", 0), PadKey("A", 2))))
        assertFalse(GamepadLayout.valid(listOf(PadKey("A", 0), PadKey("A", 1))))
    }
    @Test fun movesAndSwapsWithoutWideningPanel() {
        val moved = GamepadLayout.move(GamepadLayout.defaults, "W", 3)
        assertEquals(3, moved.first { it.key == "W" }.cell)
        val swapped = GamepadLayout.move(moved, "A", 10)
        assertEquals(9, swapped.first { it.key == "S" }.cell)
        val spaceDrop = GamepadLayout.move(moved, "SPACE", 9)
        assertTrue(GamepadLayout.valid(spaceDrop))
        assertEquals(9, spaceDrop.first { it.key == "SPACE" }.cell)
        assertEquals(15, GamepadLayout.move(moved, "SPACE", 15).first { it.key == "SPACE" }.cell)
    }
    @Test fun dropsIntoOccupiedCellsAndSixthRow() {
        val dropped = GamepadLayout.drop(GamepadLayout.defaults, "Z", 9)
        assertEquals(9, dropped.first { it.key == "Z" }.cell)
        assertTrue(GamepadLayout.valid(dropped))
        assertTrue(dropped.any { it.key == "A" })
        val lastCell = GamepadLayout.drop(dropped, "UP", 17)
        assertEquals(17, lastCell.first { it.key == "UP" }.cell)
        assertEquals(lastCell, GamepadLayout.decode(GamepadLayout.encode(lastCell)))
    }
}
