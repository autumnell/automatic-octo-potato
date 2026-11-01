package com.mtgscanner.scan

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * What tells printings with the same art apart, from a straightened card: the footer strip (set code, number,
 * rarity), the set symbol, the colour of the frame and border, and the colours of the art. Printings that share
 * a picture differ in these. Pure: built from [CardPrintPrep.CARD_W] x [CardPrintPrep.CARD_H] ARGB pixels.
 */
class FrameLook(
    /** The footer strip in grey, [FOOT_W] x [FOOT_H], evened to mean 0 and length 1. */
    val footer: FloatArray,
    /** The set symbol in grey, [SYM] x [SYM], evened the same way. */
    val symbol: FloatArray,
    /** Mean colour (r, g, b) of the top, left, right and bottom border, then of the text box: 15 values. */
    val frame: FloatArray,
    /** Mean colour (r, g, b) of a 4 x 4 grid over the art: 48 values. */
    val art: FloatArray,
    /** How much the footer and the symbol area vary (standard deviation of grey, 0 to 255); little means blank. */
    val footerContrast: Float = 255f,
    val symbolContrast: Float = 255f,
) {
    /** How unlike [other] this is: 0 for the same picture, about 1 and more for a different printing. */
    fun distance(other: FrameLook): Float {
        val foot = patternDistance(footer, footerContrast, other.footer, other.footerContrast)
        val sym = patternDistance(symbol, symbolContrast, other.symbol, other.symbolContrast)
        // Exposure differs between a photo and Scryfall's scan: scale this one's colours to the other's brightness.
        val gain = (other.frame.sum() + other.art.sum()) / (frame.sum() + art.sum()).coerceAtLeast(1f)
        val g = gain.coerceIn(0.5f, 2f)
        val frameD = colorDistance(frame, other.frame, g)
        val artD = colorDistance(art, other.art, g)
        return foot * W_FOOT + sym * W_SYM + frameD * W_FRAME + artD * W_ART
    }

    companion object {
        const val FOOT_W = 48
        const val FOOT_H = 6
        const val SYM = 12

        private const val W_FOOT = 1.0f
        private const val W_SYM = 0.6f
        private const val W_FRAME = 1.2f
        private const val W_ART = 0.6f

        /** A pick must be this far ahead of the next printing. */
        const val MIN_LEAD = 0.12f
        /** And no further than this from the scan. */
        const val MAX_DISTANCE = 1.2f

        // Parts of the card, as fractions of its width and height (left, top, right, bottom).
        private val FOOTER = floatArrayOf(0.04f, 0.905f, 0.56f, 0.965f)
        private val SYMBOL = floatArrayOf(0.82f, 0.555f, 0.95f, 0.61f)
        private val ART = floatArrayOf(0.09f, 0.12f, 0.91f, 0.54f)
        private val BORDERS = arrayOf(
            floatArrayOf(0.10f, 0.005f, 0.90f, 0.03f),
            floatArrayOf(0.005f, 0.15f, 0.035f, 0.85f),
            floatArrayOf(0.965f, 0.15f, 0.995f, 0.85f),
            floatArrayOf(0.30f, 0.972f, 0.70f, 0.995f),
            floatArrayOf(0.12f, 0.64f, 0.88f, 0.86f),
        )

        /** The look of a straightened card, [w] x [h] ARGB pixels. */
        fun of(px: IntArray, w: Int, h: Int): FrameLook {
            val footerGrey = grey(px, w, h, FOOTER, FOOT_W, FOOT_H)
            val symbolGrey = grey(px, w, h, SYMBOL, SYM, SYM)
            val footerContrast = std(footerGrey)
            val symbolContrast = std(symbolGrey)
            val footer = evened(footerGrey)
            val symbol = evened(symbolGrey)
            val frame = FloatArray(15)
            BORDERS.forEachIndexed { i, r -> meanColor(px, w, h, r).copyInto(frame, i * 3) }
            val art = FloatArray(48)
            for (gy in 0 until 4) for (gx in 0 until 4) {
                val cw = (ART[2] - ART[0]) / 4
                val ch = (ART[3] - ART[1]) / 4
                val r = floatArrayOf(ART[0] + gx * cw, ART[1] + gy * ch, ART[0] + (gx + 1) * cw, ART[1] + (gy + 1) * ch)
                meanColor(px, w, h, r).copyInto(art, (gy * 4 + gx) * 3)
            }
            return FrameLook(footer, symbol, frame, art, footerContrast, symbolContrast)
        }

        /**
         * The printing in [candidates] whose look is clearly closest to [scan]: within [MAX_DISTANCE] and [MIN_LEAD]
         * ahead of the next one. Candidates with the same [key] (one picture shared by several printings) don't
         * compete. Null when none stands out.
         */
        fun <T> pick(scan: FrameLook, candidates: List<Pair<T, FrameLook>>, key: (T) -> Any = { it as Any }): T? {
            if (candidates.isEmpty()) return null
            val scored = candidates.map { (c, look) -> c to scan.distance(look) }.sortedBy { it.second }
            val best = scored.first()
            if (best.second > MAX_DISTANCE) return null
            val bestKey = key(best.first)
            val next = scored.firstOrNull { key(it.first) != bestKey } ?: return best.first
            return if (next.second - best.second >= MIN_LEAD) best.first else null
        }

        /** Below this contrast a part is taken as blank: its "pattern" would only be noise. */
        private const val BLANK = 6f

        /** 0 when both are blank, 1 when only one is, else 1 - the cosine of their patterns. */
        private fun patternDistance(a: FloatArray, ca: Float, b: FloatArray, cb: Float): Float {
            val blankA = ca < BLANK
            val blankB = cb < BLANK
            return when {
                blankA && blankB -> 0f
                blankA || blankB -> 1f
                else -> 1f - cos(a, b)
            }
        }

        private fun std(v: FloatArray): Float {
            val mean = v.average()
            return sqrt(v.sumOf { (it - mean) * (it - mean) } / v.size).toFloat()
        }

        /** Mean 0, length 1, so light and contrast don't count, only the pattern. */
        private fun evened(v: FloatArray): FloatArray {
            val mean = v.average().toFloat()
            var len = 0f
            for (i in v.indices) { v[i] -= mean; len += v[i] * v[i] }
            len = sqrt(len)
            if (len > 1e-6f) for (i in v.indices) v[i] /= len
            return v
        }

        private fun cos(a: FloatArray, b: FloatArray): Float {
            var s = 0f
            for (i in a.indices) s += a[i] * b[i]
            return s.coerceIn(-1f, 1f)
        }

        /**
         * Colours ([a] scaled by [gain]) compared by their chroma (each colour minus its own grey), so a darker or brighter photo of the
         * same frame still matches; then their brightness, counted less. 0 for the same, about 1 for black vs white.
         */
        private fun colorDistance(a: FloatArray, b: FloatArray, gain: Float): Float {
            var sum = 0f
            val n = a.size / 3
            for (i in 0 until n) {
                val ga = (a[i * 3] + a[i * 3 + 1] + a[i * 3 + 2]) / 3 * gain
                val gb = (b[i * 3] + b[i * 3 + 1] + b[i * 3 + 2]) / 3
                var chroma = 0f
                for (k in 0 until 3) chroma += abs((a[i * 3 + k] * gain - ga) - (b[i * 3 + k] - gb))
                sum += chroma / 255f + abs(ga - gb) / 255f * 0.5f
            }
            return sum / n
        }

        private fun grey(px: IntArray, w: Int, h: Int, r: FloatArray, ow: Int, oh: Int): FloatArray {
            val out = FloatArray(ow * oh)
            val x0 = r[0] * w; val y0 = r[1] * h
            val sw = (r[2] - r[0]) * w / ow; val sh = (r[3] - r[1]) * h / oh
            for (oy in 0 until oh) for (ox in 0 until ow) {
                // Mean over the patch each output pixel covers.
                val ax = (x0 + ox * sw).toInt().coerceIn(0, w - 1); val bx = (x0 + (ox + 1) * sw).toInt().coerceIn(ax + 1, w)
                val ay = (y0 + oy * sh).toInt().coerceIn(0, h - 1); val by = (y0 + (oy + 1) * sh).toInt().coerceIn(ay + 1, h)
                var s = 0; var n = 0
                for (y in ay until by) for (x in ax until bx) { s += ImageHash.luma(px[y * w + x]); n++ }
                out[oy * ow + ox] = s.toFloat() / n
            }
            return out
        }

        private fun meanColor(px: IntArray, w: Int, h: Int, r: FloatArray): FloatArray {
            val ax = (r[0] * w).toInt().coerceIn(0, w - 1); val bx = (r[2] * w).toInt().coerceIn(ax + 1, w)
            val ay = (r[1] * h).toInt().coerceIn(0, h - 1); val by = (r[3] * h).toInt().coerceIn(ay + 1, h)
            var rs = 0L; var gs = 0L; var bs = 0L; var n = 0
            for (y in ay until by) for (x in ax until bx) {
                val c = px[y * w + x]
                rs += (c shr 16) and 0xFF; gs += (c shr 8) and 0xFF; bs += c and 0xFF; n++
            }
            return floatArrayOf(rs.toFloat() / n, gs.toFloat() / n, bs.toFloat() / n)
        }
    }
}
