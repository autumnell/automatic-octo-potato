package com.mtgscanner.scan

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The card's four corners, as x, y pairs in the order top-left, top-right, bottom-right, bottom-left of the
 * image they were found in (not yet of the card, which may lie any way up). Pure Kotlin for tests.
 */
object CardQuad {
    /** The corners of an axis-aligned [box]. */
    fun ofBox(box: OcrBox): FloatArray = floatArrayOf(
        box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.top.toFloat(),
        box.right.toFloat(), box.bottom.toFloat(), box.left.toFloat(), box.bottom.toFloat(),
    )

    private fun dist(q: FloatArray, a: Int, b: Int): Float {
        val dx = q[a * 2] - q[b * 2]
        val dy = q[a * 2 + 1] - q[b * 2 + 1]
        return sqrt(dx * dx + dy * dy)
    }

    /** Average length of the left and right sides, and of the top and bottom ones. */
    fun sides(q: FloatArray): Pair<Float, Float> =
        (dist(q, 0, 3) + dist(q, 1, 2)) / 2 to (dist(q, 0, 1) + dist(q, 3, 2)) / 2

    /**
     * The clockwise turns (0, 90, 180, 270) that would stand the card upright, most likely first: the card
     * is taller than wide, so only two fit its outline. [preferred] is the turn that worked last time.
     */
    fun uprightTurns(q: FloatArray, preferred: Int): List<Int> {
        val (vertical, horizontal) = sides(q)
        val tallInImage = vertical >= horizontal
        val p = ((preferred % 360) + 360) % 360
        val fits = (p % 180 == 0) == tallInImage
        val first = if (fits) p else (p + 90) % 360
        return listOf(first, (first + 180) % 360)
    }

    /**
     * The corners in the card's own order (its top-left first, then clockwise) once the image is turned
     * clockwise by [turn]. Turning by 90 brings the image's bottom-left corner to the top-left, and so on.
     */
    fun ordered(q: FloatArray, turn: Int): FloatArray {
        val k = (((turn % 360) + 360) % 360) / 90
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val from = (i - k + 4) % 4
            out[i * 2] = q[from * 2]
            out[i * 2 + 1] = q[from * 2 + 1]
        }
        return out
    }

    /** [q] (in a grid sampled every [step] pixels from ([left], [top])) in the full image's pixels. */
    fun scaled(q: FloatArray, step: Int, left: Int, top: Int): FloatArray =
        FloatArray(8) { i -> (if (i % 2 == 0) left else top) + q[i] * step }

    /**
     * Refines an axis-aligned card [box] found in a [w] x [h] grid into four corners: each side's edge is traced
     * row by row (or column by column) near the box and a straight line fitted to it, and the corners are where
     * the lines meet. A side that can't be traced keeps the box's straight line. Null when the result is not a
     * sensible four-sided shape near the box.
     */
    fun corners(luma: IntArray, w: Int, h: Int, box: OcrBox): FloatArray? {
        if (box.width < 12 || box.height < 12 || luma.size < w * h) return null
        var sum = 0L
        for (i in 0 until w * h) sum += luma[i]
        val limit = maxOf(10, (sum / (w * h)).toInt() * 15 / 100)
        val bandX = maxOf(3, box.width / 12)
        val bandY = maxOf(3, box.height / 12)

        // Lines as (a, b): x = a*y + b for the left and right sides, y = a*x + b for the top and bottom.
        fun vertical(x0: Int): Pair<Double, Double> {
            val pts = ArrayList<Pair<Double, Double>>()
            val skip = box.height / 8
            for (y in box.top + skip..box.bottom - skip) {
                if (y < 1 || y >= h - 1) continue
                var best = -1
                var bestG = limit
                // The step between x and x + 1, so the edge sits at x + 0.5.
                for (x in (x0 - bandX)..(x0 + bandX)) {
                    if (x < 0 || x >= w - 1) continue
                    val g = abs(luma[y * w + x + 1] - luma[y * w + x])
                    if (g > bestG) { bestG = g; best = x }
                }
                if (best >= 0) pts += y.toDouble() to best + 0.5
            }
            return fitLine(pts, box.height - 2 * skip) ?: (0.0 to x0.toDouble())
        }

        fun horizontal(y0: Int): Pair<Double, Double> {
            val pts = ArrayList<Pair<Double, Double>>()
            val skip = box.width / 8
            for (x in box.left + skip..box.right - skip) {
                if (x < 1 || x >= w - 1) continue
                var best = -1
                var bestG = limit
                for (y in (y0 - bandY)..(y0 + bandY)) {
                    if (y < 0 || y >= h - 1) continue
                    val g = abs(luma[(y + 1) * w + x] - luma[y * w + x])
                    if (g > bestG) { bestG = g; best = y }
                }
                if (best >= 0) pts += x.toDouble() to best + 0.5
            }
            return fitLine(pts, box.width - 2 * skip) ?: (0.0 to y0.toDouble())
        }

        val left = vertical(box.left)
        val right = vertical(box.right)
        val top = horizontal(box.top)
        val bottom = horizontal(box.bottom)
        val q = FloatArray(8)
        fun meet(v: Pair<Double, Double>, hz: Pair<Double, Double>, i: Int): Boolean {
            // x = a1*y + b1 and y = a2*x + b2
            val d = 1 - v.first * hz.first
            if (abs(d) < 1e-6) return false
            val x = (v.first * hz.second + v.second) / d
            val y = hz.first * x + hz.second
            q[i * 2] = x.toFloat()
            q[i * 2 + 1] = y.toFloat()
            return true
        }
        if (!meet(left, top, 0) || !meet(right, top, 1) || !meet(right, bottom, 2) || !meet(left, bottom, 3)) return null
        // Each corner stays near the box's corner.
        val square = ofBox(box)
        val tolX = box.width * 0.15f
        val tolY = box.height * 0.15f
        for (i in 0 until 4) {
            if (abs(q[i * 2] - square[i * 2]) > tolX || abs(q[i * 2 + 1] - square[i * 2 + 1]) > tolY) return null
        }
        return q
    }

    /**
     * Least-squares line through [pts] (t, v): v = a*t + b. Points far off the first fit are dropped and it is
     * fitted again. Null when fewer than 40% of the [span] samples were found.
     */
    internal fun fitLine(pts: List<Pair<Double, Double>>, span: Int): Pair<Double, Double>? {
        if (pts.size < 6 || pts.size * 10 < span * 4) return null
        fun fit(p: List<Pair<Double, Double>>): Pair<Double, Double>? {
            val n = p.size.toDouble()
            val st = p.sumOf { it.first }
            val sv = p.sumOf { it.second }
            val stt = p.sumOf { it.first * it.first }
            val stv = p.sumOf { it.first * it.second }
            val den = n * stt - st * st
            if (abs(den) < 1e-9) return null
            val a = (n * stv - st * sv) / den
            return a to (sv - a * st) / n
        }
        val first = fit(pts) ?: return null
        val kept = pts.filter { abs(first.first * it.first + first.second - it.second) <= 1.5 }
        if (kept.size < 6 || kept.size * 10 < span * 4) return null
        return fit(kept)
    }
}
