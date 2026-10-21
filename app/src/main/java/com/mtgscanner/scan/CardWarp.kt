package com.mtgscanner.scan

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import kotlin.math.roundToInt

/** Straightens the card seen at an angle into an upright 63:88 picture, or just a part of it. */
object CardWarp {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    // Parts of the upright card, as fractions of its width and height (left, top, right, bottom).
    /** The name bar. */
    val NAME = floatArrayOf(0.03f, 0.035f, 0.97f, 0.125f)
    /** The footer: collector number, set and language on the left, the copyright year on the right on newer cards. */
    val FOOTER = floatArrayOf(0.0f, 0.915f, 1.0f, 0.99f)
    /** The art window, the same part [ArtHashing.hashCardImage] takes from Scryfall's pictures. */
    val ART = floatArrayOf(0.08f, 0.11f, 0.92f, 0.55f)
    /** The set symbol at the right end of the type line. */
    val SYMBOL = floatArrayOf(0.85f, 0.545f, 0.96f, 0.625f)
    /** Where glare spoils reading: the name, and the footer's text. */
    val NAME_GLARE = floatArrayOf(0.06f, 0.045f, 0.80f, 0.11f)
    val FOOTER_GLARE = floatArrayOf(0.04f, 0.93f, 0.96f, 0.975f)

    /** The card's height in [src] pixels, from its corners. */
    fun cardHeight(quad: FloatArray): Float {
        val (v, h) = CardQuad.sides(quad)
        return maxOf(v, h)
    }

    /**
     * Renders [part] of the card at [outW] x [outH]. [quad] is the card's corners in [src], in the card's own
     * order (see [CardQuad.ordered]). Samples [src] directly, so a part drawn larger than it appears keeps all
     * the detail the camera caught. Null when the corners don't make a usable shape.
     */
    fun render(src: Bitmap, quad: FloatArray, part: FloatArray, outW: Int, outH: Int): Bitmap? {
        if (outW < 4 || outH < 4) return null
        val (l, t, r, b) = part.toList()
        val pw = r - l
        val ph = b - t
        fun x(u: Float) = (u - l) / pw * outW
        fun y(v: Float) = (v - t) / ph * outH
        val dst = floatArrayOf(x(0f), y(0f), x(1f), y(0f), x(1f), y(1f), x(0f), y(1f))
        val m = Matrix()
        if (!m.setPolyToPoly(quad, 0, dst, 0, 4)) return null
        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, m, paint)
        return out
    }

    /**
     * Renders [part] at [scale] times the size it has in [src] (at least [minW] wide), the card being
     * [cardH] pixels tall there.
     */
    fun renderScaled(src: Bitmap, quad: FloatArray, part: FloatArray, cardH: Float, scale: Float, minW: Int = 0): Bitmap? {
        val cardW = cardH * (63f / 88f)
        var w = (cardW * (part[2] - part[0]) * scale)
        var h = (cardH * (part[3] - part[1]) * scale)
        if (w < minW && w > 0) { h *= minW / w; w = minW.toFloat() }
        // Keep it a sensible size for text reading.
        val cap = 2400f / maxOf(w, h)
        if (cap < 1f) { w *= cap; h *= cap }
        return render(src, quad, part, w.roundToInt(), h.roundToInt())
    }

    /** The pixels of [b], row by row. */
    fun pixels(b: Bitmap): IntArray = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

    /** Brightness 0..255 of each pixel of [b]. */
    fun luma(b: Bitmap): IntArray = pixels(b).let { px -> IntArray(px.size) { ImageHash.luma(px[it]) } }
}
