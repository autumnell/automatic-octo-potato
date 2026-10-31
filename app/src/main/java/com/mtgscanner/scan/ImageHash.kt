package com.mtgscanner.scan

/**
 * Tiny "difference hash" for comparing card artwork. Two pictures of the same art give hashes
 * that differ in only a few bits, even with different lighting or resolution.
 */
object ImageHash {
    /**
     * [gray] is a 9x8 grayscale image (row by row, 72 values). Each bit says whether a pixel is
     * brighter than its right-hand neighbour.
     */
    fun dHash(gray: IntArray): Long {
        require(gray.size == 72) { "need 9x8 pixels" }
        var hash = 0L
        var bit = 0
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                if (gray[y * 9 + x] > gray[y * 9 + x + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun distance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /** Cosines for a 32-point DCT, row u, column x. */
    private val dct: Array<DoubleArray> by lazy {
        Array(8) { u -> DoubleArray(32) { x -> kotlin.math.cos((2 * x + 1) * u * Math.PI / 64) } }
    }

    /**
     * "Perceptual hash": [gray] is a 32x32 grayscale image (1024 values). Its lowest 8x8 frequencies (by a
     * discrete cosine transform) each give a bit: above their median or not. It sees the picture's broad
     * shapes, where [dHash] sees edges, so two pictures that fool one rarely fool both.
     */
    fun pHash(gray: IntArray): Long {
        require(gray.size == 1024) { "need 32x32 pixels" }
        val c = dct
        // Rows first (32 rows x 8 frequencies), then columns.
        val rows = Array(32) { y -> DoubleArray(8) { u -> var s = 0.0; for (x in 0 until 32) s += gray[y * 32 + x] * c[u][x]; s } }
        val f = DoubleArray(64)
        for (v in 0 until 8) for (u in 0 until 8) {
            var s = 0.0
            for (y in 0 until 32) s += rows[y][u] * c[v][y]
            f[v * 8 + u] = s
        }
        // The first one is the overall brightness: left out of the median.
        val median = f.copyOfRange(1, 64).sorted().let { (it[31] + it[32]) / 2 }
        var hash = 0L
        for (i in 0 until 64) if (f[i] > median) hash = hash or (1L shl i)
        return hash
    }

    /** Brightness 0..255 of an ARGB pixel. */
    fun luma(argb: Int): Int {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /**
     * Picks the candidate whose art best matches [target], if it's clearly the best:
     * close enough ([maxDistance]) and ahead of the runner-up by [minLead] bits.
     */
    /**
     * [pickBest] with both hashes: each candidate's distance is its [dHash] bits plus its [pHash] bits (out of
     * 128), when the candidate has a second hash (0 means none: its first hash counts twice).
     */
    fun <T> pickBestBoth(target: Long, targetP: Long?, candidates: List<Triple<T, Long, Long>>, maxDistance: Int = 26, minLead: Int = 5): T? {
        if (targetP == null) return pickBest(target, candidates.map { it.first to it.second })
        if (candidates.isEmpty()) return null
        val sorted = candidates.map { (c, d, p) ->
            val dd = distance(target, d)
            c to dd + (if (p == 0L) dd else distance(targetP, p))
        }.sortedBy { it.second }
        val best = sorted.first()
        if (best.second > maxDistance) return null
        val runnerUp = sorted.getOrNull(1)?.second ?: Int.MAX_VALUE
        return if (runnerUp - best.second >= minLead) best.first else null
    }

    fun <T> pickBest(target: Long, candidates: List<Pair<T, Long>>, maxDistance: Int = 14, minLead: Int = 3): T? {
        if (candidates.isEmpty()) return null
        val sorted = candidates.map { it.first to distance(target, it.second) }.sortedBy { it.second }
        val best = sorted.first()
        if (best.second > maxDistance) return null
        val runnerUp = sorted.getOrNull(1)?.second ?: Int.MAX_VALUE
        return if (runnerUp - best.second >= minLead) best.first else null
    }
}
