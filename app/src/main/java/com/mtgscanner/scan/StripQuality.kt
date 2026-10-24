package com.mtgscanner.scan

/**
 * Checks on a straightened name bar or footer before it is read: how sharp it is, and whether its contrast is
 * worth stretching. Works on brightness values (0..255), so it's pure and testable.
 */
object StripQuality {
    /**
     * Edge strength over contrast: the mean of the absolute Laplacian divided by the spread of brightness. Higher
     * is sharper. Dividing by the spread keeps a dark or dim strip from looking blurry just for being dark.
     */
    fun sharpness(luma: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3 || luma.size < w * h) return 0.0
        var sum = 0L
        var n = 0
        for (y in 1 until h - 1) {
            val row = y * w
            for (x in 1 until w - 1) {
                val i = row + x
                val lap = 4 * luma[i] - luma[i - 1] - luma[i + 1] - luma[i - w] - luma[i + w]
                sum += if (lap < 0) -lap else lap
                n++
            }
        }
        val (lo, hi) = levels(luma, 0.05, 0.95)
        return sum.toDouble() / n / (hi - lo + 8)
    }

    /** Brightness at the [low] and [high] fractions of the strip's pixels, darkest first. */
    fun levels(luma: IntArray, low: Double = 0.02, high: Double = 0.98): Pair<Int, Int> {
        if (luma.isEmpty()) return 0 to 255
        val hist = IntArray(256)
        for (v in luma) hist[v.coerceIn(0, 255)]++
        fun at(frac: Double): Int {
            val target = (luma.size * frac).toLong()
            var seen = 0L
            for (v in 0..255) {
                seen += hist[v]
                if (seen > target) return v
            }
            return 255
        }
        return at(low) to at(high)
    }

    /** Low contrast: worth stretching before reading (foils, dark frames, dim light). */
    fun flat(lo: Int, hi: Int): Boolean = hi - lo < FLAT_RANGE

    /** Brightness stretched so [lo] becomes black and [hi] white. */
    fun stretch(luma: IntArray, lo: Int, hi: Int): IntArray {
        val span = (hi - lo).coerceAtLeast(1)
        return IntArray(luma.size) { ((luma[it] - lo) * 255 / span).coerceIn(0, 255) }
    }

    /**
     * Tells blurry frames of the card in view from sharp ones by comparing each with the sharpest seen lately:
     * the camera refocusing, or a slight shake the card check missed. The best decays a little on each skip, so a
     * change of light can't keep every frame out.
     */
    class Tracker {
        private var best = 0.0

        /** True when [s] is clearly blurrier than recent frames of the same card: skip reading it. */
        fun blurry(s: Double): Boolean {
            if (s >= best) { best = s; return false }
            if (s < best * BLUR_SHARE) { best *= 0.85; return true }
            return false
        }

        fun reset() { best = 0.0 }
    }

    /** Brightness spread below which a strip is stretched. */
    const val FLAT_RANGE = 150
    /** Share of the sharpest recent frame below which a frame counts as blurry. */
    const val BLUR_SHARE = 0.5
}
