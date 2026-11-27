package com.mtgscanner.scan

/**
 * CollectorVision's fastweb corner detector (HanClinto/ccgdetector-fastweb-single, AGPL-3.0), the card finder Mault
 * uses (github.com/maultxyz/mault, GPL-3.0; run here the way its packages/shared/src/vision.ts runs it). The whole
 * picture is squashed to [SIZE] x [SIZE] with ImageNet colour scaling; the model gives the four corners as fractions
 * of the picture and a sharpness score (older exports: a card-present score instead).
 *
 * On the model comparison (Checks › model-compare, 2026-11-27) it put every corner within 3% of the card's height on
 * 29 of 30 photos (YOLO 11, the pose model 7), with a mean error of 1.1% (YOLO 4.0%), in about a fifth of YOLO's time.
 */
object FastwebFinder {
    const val SIZE = 384
    val shape = longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())

    /** Below this sharpness there is no clear card. */
    const val MIN_SHARPNESS = 0.02f
    /** With only a card-present score, its probability must reach this. */
    const val MIN_PRESENCE = 0.5f
    /** A quad smaller than this share of the picture is noise. */
    private const val MIN_AREA = 0.01f
    /** Corners closer than this (squared, in picture fractions) are the same corner. */
    private const val MIN_CORNER_GAP_SQ = 0.0004f

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    /** The model's input for a [SIZE] x [SIZE] picture (ARGB pixels). */
    fun input(px: IntArray): FloatArray {
        val n = SIZE * SIZE
        require(px.size == n)
        val out = FloatArray(3 * n)
        for (i in 0 until n) {
            val p = px[i]
            out[i] = (((p shr 16) and 0xFF) / 255f - MEAN[0]) / STD[0]
            out[n + i] = (((p shr 8) and 0xFF) / 255f - MEAN[1]) / STD[1]
            out[2 * n + i] = ((p and 0xFF) / 255f - MEAN[2]) / STD[2]
        }
        return out
    }

    /**
     * The card's corners in a [w] x [h] picture, in [CardOutline.order], from the model's outputs by name; null when
     * no card is clear or the corners don't make a usable, convex quad.
     */
    fun corners(out: Map<String, FloatArray>, w: Int, h: Int): FloatArray? {
        val c = out["corners"] ?: return null
        if (c.size < 8) return null
        val sharpness = out["sharpness"]?.firstOrNull()
        val presence = out["presence"]?.firstOrNull()
        when {
            sharpness != null -> if (sharpness < MIN_SHARPNESS) return null
            presence != null -> if (1f / (1f + kotlin.math.exp(-presence)) < MIN_PRESENCE) return null
        }
        val unit = FloatArray(8) { c[it].coerceIn(0f, 1f) }
        val around = CardOutline.order(unit)
        if (CardOutline.area(around) < MIN_AREA || !CardOutline.isConvex(around)) return null
        for (i in 0 until 4) for (j in i + 1 until 4) {
            val dx = unit[i * 2] - unit[j * 2]
            val dy = unit[i * 2 + 1] - unit[j * 2 + 1]
            if (dx * dx + dy * dy < MIN_CORNER_GAP_SQ) return null
        }
        return CardOutline.order(FloatArray(8) { unit[it] * (if (it % 2 == 0) w else h) })
    }
}
