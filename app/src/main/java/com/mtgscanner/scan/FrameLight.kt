package com.mtgscanner.scan

/**
 * How bright one camera frame is, measured on the middle of the picture where the card sits.
 * [luma] is the average brightness 0..255. [glare] is the share of near-white pixels, and
 * [sparkle] the share of bright, strongly colored ones (the rainbow sheen of a foil).
 */
data class FrameLight(val luma: Int, val glare: Float, val sparkle: Float)

/** Pure helpers for low light and the foil hint. */
object LightCheck {
    const val DARK = 45
    const val BRIGHT = 200

    /** Measures RGBA_8888 pixels: [pixel] returns 0xRRGGBB for (x, y). Samples a grid over the middle 60%. */
    fun measure(width: Int, height: Int, pixel: (Int, Int) -> Int, grid: Int = 32): FrameLight {
        var sum = 0L
        var glare = 0
        var sparkle = 0
        var n = 0
        val x0 = (width * 0.2).toInt()
        val y0 = (height * 0.2).toInt()
        val w = (width * 0.6).toInt().coerceAtLeast(1)
        val h = (height * 0.6).toInt().coerceAtLeast(1)
        for (gy in 0 until grid) for (gx in 0 until grid) {
            val x = x0 + gx * w / grid
            val y = y0 + gy * h / grid
            val p = pixel(x, y)
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val l = (r * 299 + g * 587 + b * 114) / 1000
            sum += l
            val hi = maxOf(r, g, b)
            val lo = minOf(r, g, b)
            if (lo >= 235) glare++
            else if (hi >= 190 && hi - lo >= 90) sparkle++
            n++
        }
        if (n == 0) return FrameLight(0, 0f, 0f)
        return FrameLight((sum / n).toInt(), glare.toFloat() / n, sparkle.toFloat() / n)
    }

    /**
     * Whether the frames look like a foil: highlights and colored sheen that jump around as the
     * card or phone moves. Plain cards in sleeves glare too, but steadily. Only a hint; never certain.
     */
    fun looksFoil(frames: List<FrameLight>): Boolean {
        if (frames.size < 5) return false
        val glare = frames.map { it.glare.toDouble() }
        val sparkle = frames.map { it.sparkle.toDouble() }
        val swing = (glare.max() - glare.min()) + (sparkle.max() - sparkle.min())
        return swing >= 0.05 && sparkle.max() >= 0.02 && stdev(sparkle) + stdev(glare) >= 0.015
    }

    private fun stdev(v: List<Double>): Double {
        val m = v.average()
        return kotlin.math.sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }
}
