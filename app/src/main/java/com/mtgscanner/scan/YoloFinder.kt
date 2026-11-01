package com.mtgscanner.scan

import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Reading open-card-collector's YOLO11n OBB card finder (matteot11/collector-mtg-detector-yolo11n-obb), turned into
 * ONNX by the Models workflow. Pure Kotlin.
 *
 * In: the picture scaled to fit [SIZE] x [SIZE] at the top left, the rest grey (letterboxed, as it was taught),
 * colours 0 to 1. Out: for each of its guesses (anchors), the box's centre, width and height in input pixels, a
 * score for each kind of card, and the box's angle in radians, as rows of one value per guess.
 */
object YoloFinder {
    const val SIZE = 640
    /** A guess must score this much (0 to 1) to count. */
    const val MIN_SCORE = 0.4f
    /** The grey of the letterbox's empty part. */
    const val PAD = 114

    val shape = longArrayOf(1, 3, SIZE.toLong(), SIZE.toLong())

    /** How much a [w] x [h] picture is scaled to fit. */
    fun scale(w: Int, h: Int): Float = SIZE.toFloat() / maxOf(w, h)

    /**
     * The best guess's corners in the original [w] x [h] picture, in [CardOutline.order], when it scores at least
     * [minScore] and is card-shaped. [out] is the model's one output; [anchors] its guesses (8400 at 640).
     */
    fun corners(out: FloatArray, w: Int, h: Int, anchors: Int = ANCHORS, minScore: Float = MIN_SCORE): FloatArray? {
        if (anchors <= 0 || out.size % anchors != 0) return null
        val rows = out.size / anchors
        // Centre, size, at least one kind, angle.
        if (rows < 6) return null
        var best = -1
        var bestScore = minScore
        for (a in 0 until anchors) {
            var s = 0f
            for (r in 4 until rows - 1) s = maxOf(s, out[r * anchors + a])
            if (s >= bestScore) { bestScore = s; best = a }
        }
        if (best < 0) return null
        fun v(r: Int) = out[r * anchors + best]
        val q = box(v(0), v(1), v(2), v(3), v(rows - 1), 1f / scale(w, h))
        val ordered = CardOutline.order(q)
        if (!CardOutline.isConvex(ordered) || !CardOutline.cardLike(ordered)) return null
        return FloatArray(8) { k -> ordered[k].coerceIn(0f, (if (k % 2 == 0) w else h).toFloat()) }
    }

    /** A turned box's four corners (x, y pairs), centre ([cx], [cy]), [bw] x [bh], turned [angle], times [k]. */
    fun box(cx: Float, cy: Float, bw: Float, bh: Float, angle: Float, k: Float = 1f): FloatArray {
        val c = cos(angle)
        val s = sin(angle)
        val ax = bw / 2 * c
        val ay = bw / 2 * s
        val bx = -bh / 2 * s
        val by = bh / 2 * c
        return floatArrayOf(
            (cx + ax + bx) * k, (cy + ay + by) * k,
            (cx + ax - bx) * k, (cy + ay - by) * k,
            (cx - ax - bx) * k, (cy - ay - by) * k,
            (cx - ax + bx) * k, (cy - ay + by) * k,
        )
    }

    /** Guesses at 640: one per cell of the 80, 40 and 20 wide grids. */
    val ANCHORS = (SIZE / 8) * (SIZE / 8) + (SIZE / 16) * (SIZE / 16) + (SIZE / 32) * (SIZE / 32)

    /** Where the picture sits in the letterbox: its scaled width and height. */
    fun fitted(w: Int, h: Int): Pair<Int, Int> {
        val s = scale(w, h)
        return min(SIZE, Math.round(w * s)) to min(SIZE, Math.round(h * s))
    }
}
