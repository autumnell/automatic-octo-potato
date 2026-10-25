package com.mtgscanner.scan

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Picks the card among four-cornered outlines found in a picture (by OpenCV's contour search): the biggest one that
 * is convex and card-shaped, 63:88 either way round, allowing for the card being seen at an angle. Quads are
 * x, y pairs. Pure Kotlin for tests.
 */
object CardOutline {
    /** Short side over long side of a card. */
    private const val ASPECT = 63f / 88f

    /**
     * The four corners in [CardQuad]'s order: clockwise on screen (y grows downwards), starting with the corner
     * nearest the image's top-left.
     */
    fun order(q: FloatArray): FloatArray {
        require(q.size == 8)
        val cx = (q[0] + q[2] + q[4] + q[6]) / 4
        val cy = (q[1] + q[3] + q[5] + q[7]) / 4
        // Ascending angle with y downwards goes clockwise on screen.
        val idx = (0 until 4).sortedBy { atan2((q[it * 2 + 1] - cy).toDouble(), (q[it * 2] - cx).toDouble()) }
        val start = idx.indices.minBy { q[idx[it] * 2] + q[idx[it] * 2 + 1] }
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val from = idx[(start + i) % 4]
            out[i * 2] = q[from * 2]
            out[i * 2 + 1] = q[from * 2 + 1]
        }
        return out
    }

    /** Area by the shoelace formula, always positive. */
    fun area(q: FloatArray): Float {
        var s = 0f
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            s += q[i * 2] * q[j * 2 + 1] - q[j * 2] * q[i * 2 + 1]
        }
        return abs(s) / 2
    }

    /** Every turn along the outline goes the same way. */
    fun isConvex(q: FloatArray): Boolean {
        var sign = 0
        for (i in 0 until 4) {
            val a = i
            val b = (i + 1) % 4
            val c = (i + 2) % 4
            val cross = (q[b * 2] - q[a * 2]) * (q[c * 2 + 1] - q[b * 2 + 1]) - (q[b * 2 + 1] - q[a * 2 + 1]) * (q[c * 2] - q[b * 2])
            if (cross == 0f) return false
            val s = if (cross > 0) 1 else -1
            if (sign == 0) sign = s else if (s != sign) return false
        }
        return true
    }

    private fun side(q: FloatArray, a: Int, b: Int): Float {
        val dx = q[a * 2] - q[b * 2]
        val dy = q[a * 2 + 1] - q[b * 2 + 1]
        return sqrt(dx * dx + dy * dy)
    }

    /** The inside angle at corner [i], in degrees. */
    private fun angle(q: FloatArray, i: Int): Double {
        val p = (i + 3) % 4
        val n = (i + 1) % 4
        val ax = (q[p * 2] - q[i * 2]).toDouble()
        val ay = (q[p * 2 + 1] - q[i * 2 + 1]).toDouble()
        val bx = (q[n * 2] - q[i * 2]).toDouble()
        val by = (q[n * 2 + 1] - q[i * 2 + 1]).toDouble()
        val la = sqrt(ax * ax + ay * ay)
        val lb = sqrt(bx * bx + by * by)
        if (la == 0.0 || lb == 0.0) return 0.0
        return Math.toDegrees(acos(((ax * bx + ay * by) / (la * lb)).coerceIn(-1.0, 1.0)))
    }

    /**
     * Whether an ordered convex quad could be a card seen from the phone: opposite sides of similar length (a tilt
     * shortens the far side, up to [maxTilt] of the near one), no corner sharper than [minAngle] degrees, and
     * the average sides near 63:88 (within about 15%, either way round).
     */
    fun cardLike(q: FloatArray, maxTilt: Float = 0.62f, minAngle: Double = 55.0): Boolean {
        if (!isConvex(q)) return false
        val top = side(q, 0, 1)
        val right = side(q, 1, 2)
        val bottom = side(q, 2, 3)
        val left = side(q, 3, 0)
        if (minOf(top, bottom) < maxOf(top, bottom) * maxTilt) return false
        if (minOf(left, right) < maxOf(left, right) * maxTilt) return false
        for (i in 0 until 4) {
            val a = angle(q, i)
            if (a < minAngle || a > 180 - minAngle) return false
        }
        val w = (top + bottom) / 2
        val h = (left + right) / 2
        if (w <= 0f || h <= 0f) return false
        val ratio = minOf(w, h) / maxOf(w, h)
        return ratio in ASPECT * 0.85f..ASPECT * 1.18f
    }

    /**
     * The biggest card-shaped outline among [candidates] (each four x, y pairs in any order) covering at least
     * [minArea], ordered as [order] gives it. Null when none fits.
     */
    fun pick(candidates: List<FloatArray>, minArea: Float): FloatArray? =
        candidates.asSequence()
            .filter { it.size == 8 }
            .map { order(it) }
            .filter { area(it) >= minArea && cardLike(it) }
            .maxByOrNull { area(it) }
}
