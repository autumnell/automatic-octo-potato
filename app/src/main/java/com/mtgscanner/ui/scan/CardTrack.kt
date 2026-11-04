package com.mtgscanner.ui.scan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

/**
 * Where the scanner last saw a card, so the frame on screen can follow it. The analyzer reports the card's corners
 * in camera pixels; they're kept turned upright and as fractions of the frame, ready to place over the preview.
 */
object CardTrack {
    /** Corners as x,y pairs (0..1 of the upright frame), the upright frame's width/height, and when seen. */
    class Seen(val points: FloatArray, val aspect: Float, val at: Long)

    private val _seen = MutableStateFlow<Seen?>(null)
    val seen: StateFlow<Seen?> = _seen

    /** From the analyzer thread: a card's corners in the frame's own pixels ([w] by [h]), or null when none is seen. */
    fun report(quad: FloatArray?, w: Int, h: Int, rotation: Int) {
        if (quad == null || quad.size < 8 || w <= 0 || h <= 0) { _seen.value = null; return }
        val up = TrackMath.upright(quad, w, h, rotation)
        val (uw, uh) = if (rotation % 180 == 0) w to h else h to w
        _seen.value = Seen(up, uw.toFloat() / uh, android.os.SystemClock.elapsedRealtime())
    }

    fun clear() { _seen.value = null }
}

/** Pure geometry for [CardTrack] and the tracking frame. */
object TrackMath {
    /** Turns corners (frame pixels) by the camera's [rotation] so the frame is upright, as fractions 0..1. */
    fun upright(q: FloatArray, w: Int, h: Int, rotation: Int): FloatArray {
        val out = FloatArray(8)
        for (i in 0 until 4) {
            val x = q[2 * i]
            val y = q[2 * i + 1]
            val (ux, uy) = when (((rotation % 360) + 360) % 360) {
                90 -> (h - y) / h to x / w
                180 -> (w - x) / w to (h - y) / h
                270 -> y / h to (w - x) / w
                else -> x / w to y / h
            }
            out[2 * i] = ux
            out[2 * i + 1] = uy
        }
        return out
    }

    /**
     * Places upright fractions on a view of [viewW] by [viewH] that shows a frame of [aspect] (width/height) the way
     * the preview's "fill centre" does: scaled to cover the view, centred, the overflow cut off.
     */
    fun toView(points: FloatArray, aspect: Float, viewW: Float, viewH: Float): FloatArray {
        val frameH = 1000f
        val frameW = frameH * aspect
        val scale = max(viewW / frameW, viewH / frameH)
        val dx = (viewW - frameW * scale) / 2f
        val dy = (viewH - frameH * scale) / 2f
        return FloatArray(8) { i ->
            if (i % 2 == 0) points[i] * frameW * scale + dx else points[i] * frameH * scale + dy
        }
    }

    /**
     * The four corners going round clockwise on screen, starting from the top-left one, so one frame's corners line
     * up with the next frame's and the outline can glide between them.
     */
    fun clockwise(p: FloatArray): FloatArray {
        val cx = (p[0] + p[2] + p[4] + p[6]) / 4f
        val cy = (p[1] + p[3] + p[5] + p[7]) / 4f
        val idx = (0 until 4).sortedBy { atan2(p[2 * it + 1] - cy, p[2 * it] - cx) }
        val start = idx.indices.minBy { p[2 * idx[it]] + p[2 * idx[it] + 1] }
        return FloatArray(8) { k ->
            val i = idx[(start + k / 2) % 4]
            p[2 * i + (k % 2)]
        }
    }

    /** A card's upright size and tilt from its clockwise corners: width (short side), height and angle in degrees. */
    fun cardPose(p: FloatArray): Triple<Float, Float, Float> {
        val e1 = hypot(p[2] - p[0], p[3] - p[1])
        val e2 = hypot(p[4] - p[2], p[5] - p[3])
        var angle = if (e1 <= e2) Math.toDegrees(atan2((p[3] - p[1]).toDouble(), (p[2] - p[0]).toDouble())).toFloat()
        else Math.toDegrees(atan2((p[5] - p[3]).toDouble(), (p[4] - p[2]).toDouble())).toFloat()
        // A card upside down looks the same as one the right way up: take the smaller turn.
        while (angle > 90f) angle -= 180f
        while (angle <= -90f) angle += 180f
        return Triple(minOf(e1, e2), maxOf(e1, e2), angle)
    }
}
