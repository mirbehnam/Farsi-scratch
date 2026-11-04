package ir.behnamapps.fascratch

import kotlin.math.*

/** Screen coordinates: right is +x, down is +y. Distance outside the pad is unbounded. */
internal object GamepadDirection {
    val vectors = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)
    fun sector(x: Float, y: Float, deadZone: Float): Int? {
        if (!x.isFinite() || !y.isFinite() || hypot(x, y) <= deadZone) return null
        return ((floor(atan2(y.toDouble(), x.toDouble()) / (PI / 4) + .5).toInt() % 8) + 8) % 8
    }
}
