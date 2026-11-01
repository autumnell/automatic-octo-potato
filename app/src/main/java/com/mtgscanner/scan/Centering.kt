package com.mtgscanner.scan

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * How evenly a card's border is printed: each side's border width, in pixels of the straightened picture it was
 * measured on. Graders write it as the share of the wider side: 55/45 left to right, 60/40 top to bottom.
 */
data class Centering(val left: Float, val right: Float, val top: Float, val bottom: Float) {
    /** The left border's share of left plus right, in percent. */
    val leftShare: Int get() = share(left, right)
    val topShare: Int get() = share(top, bottom)

    /** "55/45 · 60/40": left/right, then top/bottom, the wider side first. */
    val label: String get() = "${pair(leftShare)} · ${pair(topShare)}"

    private fun pair(p: Int): String = maxOf(p, 100 - p).let { "$it/${100 - it}" }
    private fun share(a: Float, b: Float): Int = if (a + b <= 0f) 50 else (a * 100f / (a + b)).roundToInt()

    companion object {
        /** The middle reading of each side over several frames; null for none. */
        fun median(readings: List<Centering>): Centering? {
            if (readings.isEmpty()) return null
            fun mid(f: (Centering) -> Float) = readings.map(f).sorted()[readings.size / 2]
            return Centering(mid { it.left }, mid { it.right }, mid { it.top }, mid { it.bottom })
        }
    }
}

/**
 * Measures [Centering] on a card drawn straight with a margin of the table around it ([MARGIN] of the card's size
 * on each side). Along many lines across each side, it finds the card's edge (near where the outline put it) and,
 * further in, the line where the border meets the frame; each side's border is the middle of those widths. Pure.
 */
object CenteringCheck {
    /** The margin drawn around the card, as a share of its width (left, right) or height (top, bottom). */
    const val MARGIN = 0.04f
    /** Size the card is drawn at to measure, margin included. */
    const val W = 540
    const val H = 754

    /** A border narrower or wider than these shares of the card's width isn't a border. */
    private const val MIN_BORDER = 0.012f
    private const val MAX_BORDER = 0.11f
    /** How far from where the outline put it the card's edge is looked for, as a share of the card's width. */
    private const val EDGE_SLACK = 0.025f
    /** Weakest brightness step counted as the border's inner line. */
    private const val MIN_STEP = 14
    /** Lines measured per side; fewer than half of them agreeing gives no reading. */
    private const val LINES = 41

    fun measure(luma: IntArray, w: Int, h: Int, margin: Float = MARGIN): Centering? {
        if (luma.size != w * h || w < 100 || h < 100) return null
        val cardW = w / (1 + 2 * margin)
        val cardH = h / (1 + 2 * margin)
        val left = side(luma, w, h, horizontal = true, fromStart = true, edge = margin * cardW, card = cardW, cross = h)
        val right = side(luma, w, h, horizontal = true, fromStart = false, edge = margin * cardW, card = cardW, cross = h)
        val top = side(luma, w, h, horizontal = false, fromStart = true, edge = margin * cardH, card = cardW, cross = w)
        val bottom = side(luma, w, h, horizontal = false, fromStart = false, edge = margin * cardH, card = cardW, cross = w)
        if (left == null || right == null || top == null || bottom == null) return null
        return Centering(left, right, top, bottom)
    }

    /**
     * One side's border width: along [LINES] lines across the middle of that side (rows for left and right,
     * columns for top and bottom), from the outside in.
     */
    private fun side(
        luma: IntArray, w: Int, h: Int, horizontal: Boolean, fromStart: Boolean, edge: Float, card: Float, cross: Int,
    ): Float? {
        val len = if (horizontal) w else h
        val depth = (edge + card * (EDGE_SLACK + MAX_BORDER) + 2).toInt().coerceAtMost(len / 2)
        // Brightness at step i inward from the outside along line c.
        fun at(c: Int, i: Int): Int {
            val p = if (fromStart) i else len - 1 - i
            return if (horizontal) luma[c * w + p] else luma[p * w + c]
        }
        val widths = ArrayList<Float>()
        for (k in 0 until LINES) {
            // The middle 60% of the side, away from corners (and the name and type bars' ends).
            val c = (cross * (0.2f + 0.6f * k / (LINES - 1))).toInt().coerceIn(1, cross - 2)
            // A three-line average along the line, to steady it.
            val prof = IntArray(depth) { i ->
                val a = at(c, i)
                val b = at((c - 1).coerceAtLeast(0), i)
                val d = at((c + 1).coerceAtMost(cross - 1), i)
                (a + b + d) / 3
            }
            fun step(i: Int) = if (i <= 0 || i >= depth - 1) 0 else abs(prof[i + 1] - prof[i - 1])
            // The card's edge: the biggest step near where the outline put it, else where the outline put it.
            val lo = (edge - card * EDGE_SLACK).toInt().coerceAtLeast(1)
            val hi = (edge + card * EDGE_SLACK).toInt().coerceAtMost(depth - 2)
            var outer = edge.roundToInt()
            var outerStep = MIN_STEP - 1
            for (i in lo..hi) if (step(i) > outerStep) { outerStep = step(i); outer = i }
            // The border's inner line: the biggest step further in.
            val from = outer + (card * MIN_BORDER).toInt().coerceAtLeast(2)
            val to = (outer + card * MAX_BORDER).toInt().coerceAtMost(depth - 2)
            var inner = -1
            var innerStep = MIN_STEP - 1
            for (i in from..to) if (step(i) > innerStep) { innerStep = step(i); inner = i }
            if (inner > 0) widths += (inner - outer).toFloat()
        }
        if (widths.size < LINES / 2) return null
        widths.sort()
        val mid = widths[widths.size / 2]
        // The lines should mostly agree; a side whose lines scatter (art reaching the edge) gives no reading.
        val near = widths.count { abs(it - mid) <= card * 0.006f + 1 }
        return if (near * 2 >= widths.size) mid else null
    }
}
