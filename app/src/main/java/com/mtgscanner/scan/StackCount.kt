package com.mtgscanner.scan

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Cards counted in a photo of a stack's edge: how many, and how evenly the edges were spaced (0 to 1). */
data class StackCountResult(val count: Int, val sureness: Float) {
    val sure: Boolean get() = sureness >= 0.5f
}

/**
 * Counts cards from a photo of the side of a stack (bulk commons, a pile of one card): each card's edge is a thin
 * line, so brightness across the stack rises and falls once per card. Tries the lines both ways (a stack lying
 * flat or standing up), finds the stack as the part with that ripple, and counts the ripples. Experimental. Pure.
 */
object StackCount {
    /** Thinnest card edge counted, in pixels. A card is about 0.3 mm thick, so the stack must fill the photo. */
    const val MIN_PERIOD = 3

    fun count(luma: IntArray, w: Int, h: Int): StackCountResult? {
        if (luma.size != w * h || w < 32 || h < 32) return null
        val across = profile(luma, w, h, rows = true)
        val along = profile(luma, w, h, rows = false)
        return listOfNotNull(countProfile(across), countProfile(along)).maxByOrNull { it.sureness }
    }

    /**
     * Brightness along the stack: with [rows], one value per row (edges lying flat, as horizontal lines), from the
     * middle half of the columns; the middle is used by a median, so a label or finger at one side counts less.
     */
    fun profile(luma: IntArray, w: Int, h: Int, rows: Boolean): DoubleArray {
        val len = if (rows) h else w
        val cross = if (rows) w else h
        val from = cross / 4
        val to = cross * 3 / 4
        val buf = IntArray(to - from)
        return DoubleArray(len) { i ->
            for (k in from until to) buf[k - from] = if (rows) luma[i * w + k] else luma[k * w + i]
            // The average of the middle half of the values (a trimmed mean): steady, but still smooth.
            buf.sort()
            val q = buf.size / 4
            var s = 0.0
            for (k in q until buf.size - q) s += buf[k]
            s / (buf.size - 2 * q).coerceAtLeast(1)
        }
    }

    /** Counts the ripples in [p]; null when there's no steady ripple. */
    fun countProfile(p: DoubleArray): StackCountResult? {
        val n = p.size
        if (n < 32) return null
        // Ripples only: brightness minus its own moving average.
        val win = 9
        val d = DoubleArray(n) { i ->
            val lo = maxOf(0, i - win)
            val hi = minOf(n - 1, i + win)
            var s = 0.0
            for (k in lo..hi) s += p[k]
            p[i] - s / (hi - lo + 1)
        }
        // Where the stack is: the longest run where the ripple is strong.
        val energy = DoubleArray(n) { i ->
            val lo = maxOf(0, i - win)
            val hi = minOf(n - 1, i + win)
            var s = 0.0
            for (k in lo..hi) s += d[k] * d[k]
            sqrt(s / (hi - lo + 1))
        }
        val peak = energy.max()
        if (peak < 1.5) return null
        var bestA = 0
        var bestB = -1
        var a = -1
        for (i in 0..n) {
            val on = i < n && energy[i] >= peak * 0.3
            if (on && a < 0) a = i
            if (!on && a >= 0) {
                if (i - 1 - a > bestB - bestA) { bestA = a; bestB = i - 1 }
                a = -1
            }
        }
        val len = bestB - bestA + 1
        if (len < MIN_PERIOD * 4) return null
        // The spacing of the edges: the first strong peak of the ripple's autocorrelation.
        val maxLag = len / 3
        if (maxLag <= MIN_PERIOD) return null
        val ac = DoubleArray(maxLag + 1)
        var zero = 0.0
        for (k in bestA..bestB) zero += d[k] * d[k]
        if (zero <= 0.0) return null
        for (lag in 1..maxLag) {
            var s = 0.0
            for (k in bestA..bestB - lag) s += d[k] * d[k + lag]
            ac[lag] = s / zero
        }
        var lag = -1
        for (l in MIN_PERIOD until maxLag) {
            if (ac[l] > ac[l - 1] && ac[l] >= ac[l + 1] && ac[l] > 0.15) { lag = l; break }
        }
        if (lag < 0) return null
        // A finer spacing from a peak further out (10 edges along, say), when the ripple keeps that steady.
        var period = lag.toDouble()
        for (m in 10 downTo 2) {
            val target = lag * m
            if (target + 2 > maxLag) continue
            val l = (target - 2..target + 2).maxBy { ac[it] }
            if (ac[l] > 0.1) { period = l.toDouble() / m; break }
        }
        // Find the edges: the darkest point in each spacing-wide step along the stack.
        val mins = ArrayList<Int>()
        var i = bestA
        val minGap = (period * 0.6).roundToInt().coerceAtLeast(2)
        var last = -minGap
        while (i <= bestB) {
            val isMin = (maxOf(bestA, i - minGap / 2)..minOf(bestB, i + minGap / 2)).all { d[it] >= d[i] }
            if (isMin && d[i] < 0 && i - last >= minGap) { mins += i; last = i }
            i++
        }
        // The run reaches a little past the stack (the ripple is smoothed): drop end points off the spacing.
        fun steady(gap: Int) = abs(gap - period) <= period * 0.3
        var first = 0
        var end = mins.size - 1
        while (end - first >= 2 && !steady(mins[first + 1] - mins[first])) first++
        while (end - first >= 2 && !steady(mins[end] - mins[end - 1])) end--
        val count = end - first + 1
        if (count < 2) return null
        val byLength = (mins[end] - mins[first]) / period + 1
        // Two counts that agree, and a clear ripple, make it sure.
        val agree = 1f - (abs(count - byLength) / maxOf(count.toDouble(), byLength)).toFloat()
        val sureness = (agree * ac[lag].toFloat().coerceIn(0f, 1f) * 2f).coerceIn(0f, 1f)
        return StackCountResult(((count + byLength) / 2).roundToInt(), sureness)
    }
}
