package ir.behnamapps.fascratch

internal data class PadKey(val key: String, val cell: Int)

/** Releasing a right-panel arrow must not release the same arrow still held by the stick. */
internal class GamepadKeyDispatch(private val send: (Int, Boolean) -> Unit) {
    private val held = mutableMapOf<Int, MutableSet<Int>>()
    fun change(source: Int, code: Int, pressed: Boolean) {
        val keys = held.getOrPut(source) { mutableSetOf() }
        if (pressed) { keys.add(code); send(code, true) }
        else {
            val removed = keys.remove(code)
            if (removed && held.values.none { code in it }) send(code, false)
        }
    }
}

internal object GamepadLayout {
    const val rows = 6
    const val columns = 3
    const val cellCount = rows * columns
    val allowed = ('A'..'Z').map { it.toString() } + ('0'..'9').map { it.toString() } + listOf("UP", "DOWN", "LEFT", "RIGHT", "SPACE")
    val defaults = listOf(PadKey("E", 3), PadKey("F", 5), PadKey("W", 7), PadKey("A", 9), PadKey("S", 10), PadKey("D", 11), PadKey("SPACE", 12))
    fun cells(item: PadKey): List<Int> = if (item.key == "SPACE") (item.cell..item.cell + 2).toList() else listOf(item.cell)
    fun valid(items: List<PadKey>): Boolean = items.size <= 10 && items.map { it.key }.distinct().size == items.size &&
        items.all { it.key in allowed && it.cell in 0 until cellCount && (it.key != "SPACE" || it.cell % 3 == 0) } &&
        items.flatMap(::cells).let { it.size == it.distinct().size && it.all { c -> c in 0 until cellCount } }
    fun add(items: List<PadKey>, key: String): List<PadKey> =
        (0 until cellCount).map { items + PadKey(key, it) }.firstOrNull(::valid) ?: items
    fun move(items: List<PadKey>, key: String, cell: Int): List<PadKey> {
        val source = items.firstOrNull { it.key == key } ?: return items
        val destination = if (key == "SPACE") cell / 3 * 3 else cell
        if (destination !in 0 until cellCount) return items
        val placed = source.copy(cell = destination)
        val displaced = items.filter { it.key != key && cells(it).any { c -> c in cells(placed) } }
        var result = items.filter { it.key != key && it !in displaced } + placed
        // Prefer swapping into the source's freed cells; wider Space may need another row.
        for (item in displaced.sortedByDescending { cells(it).size }) {
            val candidates = (listOf(source.cell) + (0 until cellCount)).distinct()
            val replacement = candidates.map { result + item.copy(cell = it) }.firstOrNull(::valid) ?: return items
            result = replacement
        }
        return result.takeIf(::valid) ?: items
    }
    fun drop(items: List<PadKey>, key: String, cell: Int): List<PadKey> {
        if (key !in allowed || cell !in 0 until cellCount) return items
        if (items.any { it.key == key }) return move(items, key, cell)
        if (items.size >= 10) return items
        val added = add(items, key)
        return if (added == items) items else move(added, key, cell)
    }
    fun encode(items: List<PadKey>) = "v2|" + items.joinToString(",") { "${it.key}:${it.cell}" }
    fun decode(value: String?): List<PadKey> = runCatching {
        if (value == null) return defaults
        val current = value.startsWith("v2|")
        val payload = value.removePrefix("v2|")
        val items = if (payload.isEmpty()) emptyList() else payload.split(',').map {
            val parts = it.split(':')
            PadKey(parts[0], parts[1].toInt() + if (current) 0 else 3)
        }
        items.takeIf(::valid) ?: defaults
    }.getOrDefault(defaults)
    fun label(key: String) = when (key) { "UP" -> "↑"; "DOWN" -> "↓"; "LEFT" -> "←"; "RIGHT" -> "→"; else -> key }
}
