package com.mtgscanner.scan

/**
 * Camera YUV to RGB for single pixels: BT.601, limited range, the same conversion CameraX uses (through libyuv)
 * for its RGBA output and for ImageProxy.toBitmap(). Lets the card check and the light check read a few pixels
 * of a YUV frame without converting all of it. Pure Kotlin for tests.
 */
object Yuv {
    /** Brightness 0..255 from the Y sample alone. */
    fun luma(y: Int): Int = clamp(((y - 16) * 298 + 128) shr 8)

    /** 0xRRGGBB from one Y, U (Cb) and V (Cr) sample, each 0..255. */
    fun rgb(y: Int, u: Int, v: Int): Int {
        val c = (y - 16) * 298
        val d = u - 128
        val e = v - 128
        val r = clamp((c + 409 * e + 128) shr 8)
        val g = clamp((c - 100 * d - 208 * e + 128) shr 8)
        val b = clamp((c + 516 * d + 128) shr 8)
        return (r shl 16) or (g shl 8) or b
    }

    private fun clamp(v: Int): Int = if (v < 0) 0 else if (v > 255) 255 else v
}
