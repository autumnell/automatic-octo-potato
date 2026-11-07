package com.mtgscanner.scan

/**
 * One strip of a card (name bar or footer), straightened, from the last few frames of the same card. Glare moves
 * when the card or phone tilts a little, so for each pixel the darker readings are kept and the bright ones dropped:
 * the combined strip loses the glare, and averaging what's left evens out noise in the small print.
 */
class GlareStack(private val keep: Int = 4) {
    private var key: Long? = null
    private var w = 0
    private var h = 0
    private val frames = ArrayDeque<IntArray>()

    val size: Int get() = frames.size
    val width: Int get() = w
    val height: Int get() = h

    /** Whether the frames held are of [key]: a new card starts over at its own strip size. */
    fun holds(key: Long): Boolean = this.key == key && frames.isNotEmpty()

    fun reset() {
        frames.clear()
        key = null
        w = 0
        h = 0
    }

    /**
     * Adds one frame's strip, brightness 0..255, [sw] x [sh]. [key] names the card and way up: a new key starts
     * over. Strips are expected at the size of the first ([width] x [height]); another size starts over too.
     */
    fun add(key: Long, luma: IntArray, sw: Int, sh: Int) {
        if (key != this.key || (frames.isNotEmpty() && (sw != w || sh != h))) reset()
        if (luma.size != sw * sh || sw < 8 || sh < 4) return
        this.key = key
        w = sw
        h = sh
        frames.addLast(luma)
        while (frames.size > keep) frames.removeFirst()
    }

    /** The strips combined, each lined up with the newest first; null with fewer than two. */
    fun fused(): IntArray? {
        if (frames.size < 2) return null
        val newest = frames.last()
        val aligned = frames.map { f ->
            if (f === newest) f else {
                val (dx, dy) = GlareFusion.shift(newest, f, w, h)
                GlareFusion.shifted(f, w, h, dx, dy)
            }
        }
        return GlareFusion.fuse(aligned)
    }
}

/** Pure parts of [GlareStack], for tests. */
object GlareFusion {
    /** Largest shift tried each way, in pixels, to line two strips up. */
    const val MAX_SHIFT = 6

    /**
     * The shift (dx, dy) that best lines [b] up with [a] (both [w] x [h]): the one with the least difference on a
     * sparse grid. Bright pixels (glare) are left out of the comparison.
     */
    fun shift(a: IntArray, b: IntArray, w: Int, h: Int, max: Int = MAX_SHIFT): Pair<Int, Int> {
        val step = maxOf(1, minOf(w, h) / 24)
        var best = 0 to 0
        var bestCost = Double.MAX_VALUE
        for (dy in -max..max) for (dx in -max..max) {
            var cost = 0.0
            var n = 0
            var y = max
            while (y < h - max) {
                var x = max
                while (x < w - max) {
                    val va = a[y * w + x]
                    val vb = b[(y - dy) * w + (x - dx)]
                    if (va < GLARE && vb < GLARE) {
                        cost += kotlin.math.abs(va - vb)
                        n++
                    }
                    x += step
                }
                y += step
            }
            if (n == 0) continue
            val c = cost / n + (kotlin.math.abs(dx) + kotlin.math.abs(dy)) * 0.01
            if (c < bestCost) { bestCost = c; best = dx to dy }
        }
        return best
    }

    /** [src] moved by (dx, dy), edges repeated. */
    fun shifted(src: IntArray, w: Int, h: Int, dx: Int, dy: Int): IntArray {
        if (dx == 0 && dy == 0) return src
        return IntArray(w * h) { i ->
            val x = (i % w - dx).coerceIn(0, w - 1)
            val y = (i / w - dy).coerceIn(0, h - 1)
            src[y * w + x]
        }
    }

    /** For each pixel, the average of the darker half of the readings (at least one): glare drops out. */
    fun fuse(frames: List<IntArray>): IntArray {
        val n = frames.size
        val size = frames[0].size
        val take = maxOf(1, n / 2)
        val vals = IntArray(n)
        return IntArray(size) { i ->
            for (k in 0 until n) vals[k] = frames[k][i]
            vals.sort()
            var s = 0
            for (k in 0 until take) s += vals[k]
            s / take
        }
    }

    /** Brightness this high is taken for glare. */
    private const val GLARE = 235
}
