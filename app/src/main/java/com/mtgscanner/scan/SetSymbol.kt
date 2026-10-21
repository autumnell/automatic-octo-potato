package com.mtgscanner.scan

import kotlin.math.abs
import kotlin.math.sqrt

/** The shape of a set symbol: [SetSymbol.SIZE] x [SetSymbol.SIZE] edge strengths, zero mean and unit length. */
class SymbolPrint(val v: FloatArray) {
    /** Kept in 256 signed bytes: plenty for comparing shapes. */
    fun toBytes(): ByteArray {
        val max = v.maxOf { abs(it) }.takeIf { it > 0f } ?: 1f
        return ByteArray(v.size) { (v[it] / max * 127f).toInt().coerceIn(-127, 127).toByte() }
    }

    companion object {
        fun fromBytes(b: ByteArray): SymbolPrint? {
            if (b.size != SetSymbol.SIZE * SetSymbol.SIZE) return null
            return SetSymbol.normalize(FloatArray(b.size) { b[it].toFloat() })
        }
    }
}

/**
 * Tells set symbols apart by shape only (edges, not color, so rarity doesn't matter). The same steps run on the
 * symbol cropped from the camera's straightened card and on Scryfall's set icon drawn black on white, so the two
 * can be compared. Pure Kotlin for tests.
 */
object SetSymbol {
    const val SIZE = 16

    /** Least likeness (-1..1) for a match. */
    const val MIN_SCORE = 0.5f
    /** How far ahead of the next set's symbol the best one must be. */
    const val MIN_LEAD = 0.08f

    /**
     * The shape of the symbol in a [w] x [h] grayscale picture. Long straight lines crossing most of the picture
     * (the type line's frame) are left out, and the largest group of edges is taken as the symbol. Null when
     * nothing symbol-like is there.
     */
    fun fingerprint(luma: IntArray, w: Int, h: Int): SymbolPrint? {
        if (w < 8 || h < 8 || luma.size < w * h) return null
        val g = FloatArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            g[i] = (abs(luma[i + 1] - luma[i - 1]) + abs(luma[i + w] - luma[i - w])).toFloat()
        }
        val sorted = g.sortedArray()
        val p98 = sorted[(sorted.size * 0.98).toInt().coerceAtMost(sorted.size - 1)]
        val t = maxOf(24f, p98 * 0.3f)
        val mask = BooleanArray(w * h) { g[it] >= t }
        // Frame lines run across most of the crop; a symbol doesn't.
        for (y in 0 until h) {
            var n = 0
            for (x in 0 until w) if (mask[y * w + x]) n++
            if (n * 10 >= w * 8) for (x in 0 until w) mask[y * w + x] = false
        }
        for (x in 0 until w) {
            var n = 0
            for (y in 0 until h) if (mask[y * w + x]) n++
            if (n * 10 >= h * 8) for (y in 0 until h) mask[y * w + x] = false
        }
        val box = symbolBox(mask, w, h) ?: return null
        val (l, tp, r, b) = box
        val bw = r - l + 1
        val bh = b - tp + 1
        if (bw < 5 || bh < 5) return null
        // Into a square around the symbol, keeping its proportions.
        val side = maxOf(bw, bh).toFloat()
        val cx = (l + r) / 2f
        val cy = (tp + b) / 2f
        // Edges as on/off, so a faint outline counts as much as a sharp one: only the shape is compared.
        // Thickened in proportion to the symbol's size, so a printed outline's double edge and an icon's
        // single one look alike.
        val grow = maxOf(1, (side / 24f).toInt())
        val edges = FloatArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (!mask[y * w + x]) continue
            for (dy in -grow..grow) for (dx in -grow..grow) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) edges[ny * w + nx] = 1f
            }
        }
        val out = FloatArray(SIZE * SIZE)
        val cell = side / SIZE
        for (oy in 0 until SIZE) for (ox in 0 until SIZE) {
            var s = 0f
            for (sy in 0 until 3) for (sx in 0 until 3) {
                val x = cx - side / 2 + (ox + (sx + 0.5f) / 3f) * cell
                val y = cy - side / 2 + (oy + (sy + 0.5f) / 3f) * cell
                s += sample(edges, w, h, x, y)
            }
            out[oy * SIZE + ox] = s / 9f
        }
        return normalize(blur(out))
    }

    /** Bounding box (left, top, right, bottom) of the largest group of edge pixels, with any group touching it. */
    private fun symbolBox(mask: BooleanArray, w: Int, h: Int): IntArray? {
        val label = IntArray(w * h) { -1 }
        val boxes = ArrayList<IntArray>() // l, t, r, b, count
        val stack = IntArray(w * h)
        for (start in 0 until w * h) {
            if (!mask[start] || label[start] >= 0) continue
            val id = boxes.size
            val bx = intArrayOf(w, h, -1, -1, 0)
            var sp = 0
            stack[sp++] = start
            label[start] = id
            while (sp > 0) {
                val i = stack[--sp]
                val x = i % w
                val y = i / w
                if (x < bx[0]) bx[0] = x
                if (y < bx[1]) bx[1] = y
                if (x > bx[2]) bx[2] = x
                if (y > bx[3]) bx[3] = y
                bx[4]++
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue
                    val j = ny * w + nx
                    if (mask[j] && label[j] < 0) { label[j] = id; stack[sp++] = j }
                }
            }
            boxes += bx
        }
        val main = boxes.maxByOrNull { it[4] } ?: return null
        if (main[4] < 12) return null
        val box = intArrayOf(main[0], main[1], main[2], main[3])
        // Parts of the symbol that don't touch (islands, dots) join when they overlap it, grown by 2 pixels.
        var grew = true
        val used = BooleanArray(boxes.size)
        used[boxes.indexOf(main)] = true
        while (grew) {
            grew = false
            for ((k, o) in boxes.withIndex()) {
                if (used[k] || o[4] < 3) continue
                if (o[0] <= box[2] + 2 && o[2] >= box[0] - 2 && o[1] <= box[3] + 2 && o[3] >= box[1] - 2) {
                    box[0] = minOf(box[0], o[0]); box[1] = minOf(box[1], o[1])
                    box[2] = maxOf(box[2], o[2]); box[3] = maxOf(box[3], o[3])
                    used[k] = true
                    grew = true
                }
            }
        }
        return box
    }

    private fun sample(g: FloatArray, w: Int, h: Int, x: Float, y: Float): Float {
        if (x < 0 || y < 0 || x > w - 1 || y > h - 1) return 0f
        val x0 = x.toInt().coerceAtMost(w - 2)
        val y0 = y.toInt().coerceAtMost(h - 2)
        val fx = x - x0
        val fy = y - y0
        val a = g[y0 * w + x0] * (1 - fx) + g[y0 * w + x0 + 1] * fx
        val b = g[(y0 + 1) * w + x0] * (1 - fx) + g[(y0 + 1) * w + x0 + 1] * fx
        return a * (1 - fy) + b * fy
    }

    /** A 3x3 box blur, so a symbol a pixel off still lines up. */
    private fun blur(v: FloatArray): FloatArray = FloatArray(v.size) { i ->
        val x = i % SIZE
        val y = i / SIZE
        var s = 0f
        var n = 0
        for (dy in -1..1) for (dx in -1..1) {
            val nx = x + dx
            val ny = y + dy
            if (nx in 0 until SIZE && ny in 0 until SIZE) { s += v[ny * SIZE + nx]; n++ }
        }
        s / n
    }

    /** Zero mean, unit length; null for a flat picture. */
    fun normalize(v: FloatArray): SymbolPrint? {
        val mean = v.average().toFloat()
        val c = FloatArray(v.size) { v[it] - mean }
        val norm = sqrt(c.sumOf { (it * it).toDouble() }).toFloat()
        if (norm < 1e-4f) return null
        return SymbolPrint(FloatArray(c.size) { c[it] / norm })
    }

    /** Likeness -1..1 of two symbols, allowing them to be a cell off in any direction. */
    fun similarity(a: SymbolPrint, b: SymbolPrint): Float {
        var best = -1f
        for (dy in -1..1) for (dx in -1..1) {
            var s = 0f
            for (y in 0 until SIZE) {
                val y2 = y + dy
                if (y2 !in 0 until SIZE) continue
                for (x in 0 until SIZE) {
                    val x2 = x + dx
                    if (x2 !in 0 until SIZE) continue
                    s += a.v[y * SIZE + x] * b.v[y2 * SIZE + x2]
                }
            }
            if (s > best) best = s
        }
        return best
    }

    /**
     * Which of [refs] the [target] symbol is, when that's clear: at least [minScore] alike and [minLead] ahead
     * of the next one. Null otherwise.
     */
    fun <K> pick(target: SymbolPrint, refs: List<Pair<K, SymbolPrint>>, minScore: Float = MIN_SCORE, minLead: Float = MIN_LEAD): K? {
        if (refs.isEmpty()) return null
        val scored = refs.map { it.first to similarity(target, it.second) }.sortedByDescending { it.second }
        val best = scored.first()
        if (best.second < minScore) return null
        val second = scored.getOrNull(1)?.second ?: return best.first
        return if (best.second - second >= minLead) best.first else null
    }
}
