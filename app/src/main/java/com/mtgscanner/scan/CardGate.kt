package com.mtgscanner.scan

import kotlin.math.abs

/** Boxes between the camera's sensor image and the upright image the user sees. Pure Kotlin for tests. */
object FrameGeometry {
    /** Card width / height. */
    const val CARD_ASPECT = 63.0 / 88.0

    /**
     * The area to read in an upright [width] x [height] camera image: the on-screen card guide
     * (centered, card-shaped, [guide] of the width) grown by [margin] so a card a little off-center (or a preview cropped differently) still fits.
     */
    fun guideCrop(width: Int, height: Int, guide: Double = 0.66, margin: Double = 1.2): OcrBox {
        val cw = minOf(width.toDouble(), width * guide * margin)
        val ch = minOf(height.toDouble(), cw / CARD_ASPECT)
        val l = ((width - cw) / 2).toInt()
        val t = ((height - ch) / 2).toInt()
        return OcrBox(l, t, width - l, height - t)
    }

    private fun norm(rotation: Int) = ((rotation % 360) + 360) % 360

    /**
     * Maps [box] in the upright image (a [sensorW] x [sensorH] image turned clockwise by [rotation])
     * back to sensor pixels.
     */
    fun toSensor(box: OcrBox, rotation: Int, sensorW: Int, sensorH: Int): OcrBox = when (norm(rotation)) {
        90 -> OcrBox(box.top, sensorH - box.right, box.bottom, sensorH - box.left)
        180 -> OcrBox(sensorW - box.right, sensorH - box.bottom, sensorW - box.left, sensorH - box.top)
        270 -> OcrBox(sensorW - box.bottom, box.left, sensorW - box.top, box.right)
        else -> box
    }

    /** The other way: a box in sensor pixels to the upright image. */
    fun toUpright(box: OcrBox, rotation: Int, sensorW: Int, sensorH: Int): OcrBox = when (norm(rotation)) {
        90 -> OcrBox(sensorH - box.bottom, box.left, sensorH - box.top, box.right)
        180 -> OcrBox(sensorW - box.right, sensorH - box.bottom, sensorW - box.left, sensorH - box.top)
        270 -> OcrBox(box.top, sensorW - box.right, box.bottom, sensorW - box.left)
        else -> box
    }

    /** [box] grown by [fraction] of its size on every side, kept inside [width] x [height]. */
    fun grow(box: OcrBox, fraction: Double, width: Int, height: Int): OcrBox {
        val dx = (box.width * fraction).toInt()
        val dy = (box.height * fraction).toInt()
        return OcrBox(
            (box.left - dx).coerceIn(0, width), (box.top - dy).coerceIn(0, height),
            (box.right + dx).coerceIn(0, width), (box.bottom + dy).coerceIn(0, height),
        )
    }
}

/** What the card check decided for one camera frame. */
enum class GateState {
    /** Nothing with edges in the guide: no card. */
    EMPTY,
    /** The picture changed a lot since the last frame: wait for it to settle. */
    MOVING,
    /** Steady for one frame only so far. */
    SETTLING,
    /** A card-shaped rectangle is in the guide and the picture is steady. */
    CARD,
    /** Steady, with something there, but no clear card outline (busy table, sleeve glare): read it anyway. */
    UNSURE,
}

/** [card] is in the grid's pixels when [state] is [GateState.CARD]. */
data class GateResult(val state: GateState, val card: OcrBox? = null) {
    /** Whether this frame is worth reading text from. */
    val runOcr: Boolean get() = state == GateState.CARD || state == GateState.UNSURE
}

/**
 * A cheap check, before any text reading, for whether a card is in the guide and held still.
 * Works on a small grayscale grid (about 160 pixels across) sampled from the camera frame.
 *
 * Only [GateState.EMPTY], [GateState.MOVING] and [GateState.SETTLING] skip text reading, so when
 * the outline can't be found the frame is still read ([GateState.UNSURE]).
 */
class CardGate(
    /** Steady frames in a row needed before reading. */
    private val stillFrames: Int = 2,
    /** Mean brightness change (0..255) between frames above which the picture is moving. */
    private val moveLimit: Int = 12,
) {
    private var prev: IntArray? = null
    private var still = 0

    fun reset() {
        prev = null
        still = 0
    }

    fun check(luma: IntArray, w: Int, h: Int): GateResult {
        require(luma.size >= w * h)
        val last = prev
        prev = luma.copyOf(w * h)
        if (last != null && last.size == w * h && CardFinder.meanDiff(last, luma, w * h) > moveLimit) {
            still = 0
            return GateResult(if (CardFinder.isEmpty(luma, w, h)) GateState.EMPTY else GateState.MOVING)
        }
        if (last == null || last.size != w * h) still = 0 else still++
        if (CardFinder.isEmpty(luma, w, h)) return GateResult(GateState.EMPTY)
        if (still < stillFrames - 1) return GateResult(GateState.SETTLING)
        val card = CardFinder.find(luma, w, h)
        return if (card != null) GateResult(GateState.CARD, card) else GateResult(GateState.UNSURE)
    }
}

/** The pure image checks behind [CardGate]. */
object CardFinder {

    fun meanDiff(a: IntArray, b: IntArray, n: Int): Int {
        if (n <= 0) return 0
        var sum = 0L
        for (i in 0 until n) sum += abs(a[i] - b[i])
        return (sum / n).toInt()
    }

    /** Edge strength that counts, scaled to the light so dim pictures still show a card. */
    private fun edgeLimit(luma: IntArray, n: Int): Int {
        var sum = 0L
        for (i in 0 until n) sum += luma[i]
        val mean = if (n == 0) 0 else (sum / n).toInt()
        return maxOf(10, mean * 15 / 100)
    }

    /** Hardly any edges: an empty table, a wall, darkness. Kept strict, so a faint card still counts as something. */
    fun isEmpty(luma: IntArray, w: Int, h: Int): Boolean {
        if (w < 3 || h < 3) return false
        val limit = edgeLimit(luma, w * h)
        var edges = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (abs(luma[i + 1] - luma[i - 1]) > limit || abs(luma[i + w] - luma[i - w]) > limit) edges++
        }
        return edges * 200 < (w - 2) * (h - 2) // under 0.5%
    }

    /**
     * Finds a card outline: two strong vertical edges and two strong horizontal ones, each running
     * along most of the side, making a card-shaped rectangle (either way round) that fills a fair
     * part of the grid. Returns null when there is no clear outline.
     */
    fun find(luma: IntArray, w: Int, h: Int): OcrBox? {
        if (w < 12 || h < 12) return null
        val limit = edgeLimit(luma, w * h)
        val vx = BooleanArray(w * h) // vertical edge (brightness changes left to right)
        val hy = BooleanArray(w * h)
        val col = IntArray(w)
        val row = IntArray(h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            if (abs(luma[i + 1] - luma[i - 1]) > limit) { vx[i] = true; col[x]++ }
            if (abs(luma[i + w] - luma[i - w]) > limit) { hy[i] = true; row[y]++ }
        }
        val left = argMax(col, 1, w / 2)
        val right = argMax(col, w / 2, w - 1)
        val top = argMax(row, 1, h / 2)
        val bottom = argMax(row, h / 2, h - 1)
        if (left < 0 || right < 0 || top < 0 || bottom < 0) return null
        // Each side must stand out from the columns or rows around it (not just evenly busy texture).
        val colMedian = col.sorted()[w / 2].coerceAtLeast(1)
        val rowMedian = row.sorted()[h / 2].coerceAtLeast(1)
        if (col[left] < colMedian * 3 / 2 || col[right] < colMedian * 3 / 2) return null
        if (row[top] < rowMedian * 3 / 2 || row[bottom] < rowMedian * 3 / 2) return null

        val bw = right - left
        val bh = bottom - top
        if (bw <= 0 || bh <= 0) return null
        // Fills a fair part of the guide.
        if (bw.toLong() * bh < w.toLong() * h / 5) return null
        // Card-shaped, upright or sideways.
        val ratio = bw.toDouble() / bh
        val a = FrameGeometry.CARD_ASPECT
        val upright = ratio in a * 0.8..a * 1.25
        val sideways = ratio in (1 / a) * 0.8..(1 / a) * 1.25
        if (!upright && !sideways) return null
        // Each side's edge runs along most of it.
        fun vCover(x: Int): Boolean {
            var n = 0
            for (y in top..bottom) {
                if ((x - 1..x + 1).any { it in 0 until w && vx[y * w + it] }) n++
            }
            return n * 10 >= (bh + 1) * 6
        }
        fun hCover(y: Int): Boolean {
            var n = 0
            for (x in left..right) {
                if ((y - 1..y + 1).any { it in 0 until h && hy[it * w + x] }) n++
            }
            return n * 10 >= (bw + 1) * 6
        }
        if (!vCover(left) || !vCover(right) || !hCover(top) || !hCover(bottom)) return null
        return OcrBox(left, top, right, bottom)
    }

    private fun argMax(v: IntArray, from: Int, until: Int): Int {
        var best = -1
        var bestV = 0
        for (i in from until until) if (v[i] > bestV) { bestV = v[i]; best = i }
        return best
    }
}
