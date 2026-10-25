package com.mtgscanner.scan

import kotlin.math.abs

/**
 * A coarse picture of the card in the guide: its brightness in an 8x8 grid, measured inside the card's outline
 * (so a nudge barely changes it) and taken relative to its own average (so the camera brightening doesn't
 * either). Two looks that differ mean another card is there, before any text is read.
 */
object CardLook {
    const val N = 8

    /** Average difference per cell above which it's another card. Only starts a read, so it leans sensitive. */
    const val LIMIT = 14

    /** The look of the card at [l, t, r, b) in a [w] by [h] grid of brightness, or null when it's too small. */
    fun of(luma: IntArray, w: Int, l: Int, t: Int, r: Int, b: Int, h: Int = luma.size / maxOf(1, w)): IntArray? {
        val left = l.coerceIn(0, w)
        val right = r.coerceIn(0, w)
        val top = t.coerceIn(0, h)
        val bottom = b.coerceIn(0, h)
        val cw = right - left
        val ch = bottom - top
        if (cw < N || ch < N) return null
        val out = IntArray(N * N)
        for (gy in 0 until N) for (gx in 0 until N) {
            val x0 = left + gx * cw / N
            val x1 = left + (gx + 1) * cw / N
            val y0 = top + gy * ch / N
            val y1 = top + (gy + 1) * ch / N
            var sum = 0
            var n = 0
            for (y in y0 until y1) for (x in x0 until x1) { sum += luma[y * w + x]; n++ }
            out[gy * N + gx] = if (n > 0) sum / n else 0
        }
        val mean = out.sum() / out.size
        for (i in out.indices) out[i] -= mean
        return out
    }

    /** Whether [a] and [b] look like different cards. */
    fun differs(a: IntArray, b: IntArray, limit: Int = LIMIT): Boolean {
        if (a.size != b.size || a.isEmpty()) return false
        var total = 0
        for (i in a.indices) total += abs(a[i] - b[i])
        return total / a.size > limit
    }
}
