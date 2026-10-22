package com.mtgscanner.data

/**
 * Where the real boxes sit: a grid of [rows] shelves with [cols] spots each, top shelf first. [spots] maps a box id
 * to its spot (row * cols + col). Saved as text in prefs ("3,4;12:0,15:5"). Pure, for tests.
 */
data class ShelfLayout(val rows: Int = 3, val cols: Int = 4, val spots: Map<Long, Int> = emptyMap()) {
    val size: Int get() = rows * cols

    fun boxAt(spot: Int): Long? = spots.entries.firstOrNull { it.value == spot }?.key

    fun spotOf(boxId: Long): Int? = spots[boxId]

    /** Puts [boxId] at [spot]. A box already there swaps to [boxId]'s old spot, or comes off the shelf. */
    fun place(boxId: Long, spot: Int): ShelfLayout {
        if (spot !in 0 until size) return this
        val old = spots[boxId]
        val there = boxAt(spot)
        val out = spots.toMutableMap()
        if (there != null && there != boxId) {
            if (old != null) out[there] = old else out.remove(there)
        }
        out[boxId] = spot
        return copy(spots = out)
    }

    fun remove(boxId: Long): ShelfLayout = copy(spots = spots - boxId)

    /** A new grid size; boxes that no longer fit come off (their row and column are kept otherwise). */
    fun resize(newRows: Int, newCols: Int): ShelfLayout {
        val r = newRows.coerceIn(1, MAX)
        val c = newCols.coerceIn(1, MAX)
        val out = HashMap<Long, Int>()
        for ((id, s) in spots) {
            val row = s / cols
            val col = s % cols
            if (row < r && col < c) out[id] = row * c + col
        }
        return ShelfLayout(r, c, out)
    }

    /** Only boxes that still exist. */
    fun keepOnly(ids: Set<Long>): ShelfLayout = copy(spots = spots.filterKeys { it in ids })

    /** "Shelf 2 · spot 3", counted from the top left. */
    fun label(boxId: Long): String? = spots[boxId]?.let { "Shelf ${it / cols + 1} · spot ${it % cols + 1}" }

    fun encode(): String = "$rows,$cols;" + spots.entries.sortedBy { it.value }.joinToString(",") { "${it.key}:${it.value}" }

    companion object {
        const val MAX = 8

        fun decode(s: String?): ShelfLayout {
            if (s.isNullOrBlank()) return ShelfLayout()
            val head = s.substringBefore(';').split(',')
            val rows = head.getOrNull(0)?.toIntOrNull()?.coerceIn(1, MAX) ?: 3
            val cols = head.getOrNull(1)?.toIntOrNull()?.coerceIn(1, MAX) ?: 4
            var layout = ShelfLayout(rows, cols)
            s.substringAfter(';', "").split(',').forEach { pair ->
                val id = pair.substringBefore(':').toLongOrNull() ?: return@forEach
                val spot = pair.substringAfter(':', "").toIntOrNull() ?: return@forEach
                layout = layout.place(id, spot)
            }
            return layout
        }
    }
}
