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
    fun <T> pickBest(target: Long, candidates: List<Pair<T, Long>>, maxDistance: Int = 14, minLead: Int = 3): T? {
        if (candidates.isEmpty()) return null
        val sorted = candidates.map { it.first to distance(target, it.second) }.sortedBy { it.second }
        val best = sorted.first()
        if (best.second > maxDistance) return null
        val runnerUp = sorted.getOrNull(1)?.second ?: Int.MAX_VALUE
        return if (runnerUp - best.second >= minLead) best.first else null
    }
}
