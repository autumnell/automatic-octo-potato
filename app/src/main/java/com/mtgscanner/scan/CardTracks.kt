package com.mtgscanner.scan

/**
 * Follows cards from one frame (or photo) to the next, so a card already worked out isn't worked out again (a
 * SORT-style tracker, as in jslok's card scanner): each card's outline is matched to the track whose predicted place
 * overlaps it most, the prediction being where it was plus how it last moved. A track nothing matches for
 * [maxMissed] updates in a row is dropped. Each track can carry a [T], such as what the card was read as.
 * Pure Kotlin for tests.
 */
class CardTracks<T>(private val minOverlap: Float = 0.5f, private val maxMissed: Int = 3) {
    private class Track<T>(val id: Int, var box: FloatArray, var dx: Float = 0f, var dy: Float = 0f, var missed: Int = 0, var value: T? = null)

    private val tracks = ArrayList<Track<T>>()
    private var nextId = 1

    val size: Int get() = tracks.size

    /**
     * Matches [quads] (corner x, y pairs) to the tracks, best overlaps first, and starts a track for each that
     * matches none. Returns each quad's track id, in order.
     */
    fun update(quads: List<FloatArray>): IntArray {
        val boxes = quads.map { boxOf(it) }
        val pairs = ArrayList<Triple<Int, Int, Float>>()
        for ((qi, b) in boxes.withIndex()) for ((ti, t) in tracks.withIndex()) {
            val o = overlap(b, predicted(t))
            if (o >= minOverlap) pairs += Triple(qi, ti, o)
        }
        pairs.sortByDescending { it.third }
        val ids = IntArray(quads.size)
        val usedTrack = BooleanArray(tracks.size)
        for ((qi, ti, _) in pairs) {
            if (ids[qi] != 0 || usedTrack[ti]) continue
            usedTrack[ti] = true
            val t = tracks[ti]
            val b = boxes[qi]
            t.dx = cx(b) - cx(t.box)
            t.dy = cy(b) - cy(t.box)
            t.box = b
            t.missed = 0
            ids[qi] = t.id
        }
        // Tracks with no card this time.
        for ((ti, t) in tracks.withIndex()) if (!usedTrack[ti]) t.missed++
        tracks.removeAll { it.missed > maxMissed }
        for (qi in quads.indices) if (ids[qi] == 0) {
            val t = Track<T>(nextId++, boxes[qi])
            tracks += t
            ids[qi] = t.id
        }
        return ids
    }

    fun valueOf(id: Int): T? = tracks.firstOrNull { it.id == id }?.value
    fun set(id: Int, value: T?) { tracks.firstOrNull { it.id == id }?.value = value }
    fun clear() = tracks.clear()

    private fun predicted(t: Track<T>): FloatArray = floatArrayOf(t.box[0] + t.dx, t.box[1] + t.dy, t.box[2] + t.dx, t.box[3] + t.dy)

    companion object {
        /** Left, top, right, bottom of a quad. */
        fun boxOf(q: FloatArray): FloatArray {
            var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
            for (i in 0 until q.size / 2) {
                l = minOf(l, q[i * 2]); r = maxOf(r, q[i * 2])
                t = minOf(t, q[i * 2 + 1]); b = maxOf(b, q[i * 2 + 1])
            }
            return floatArrayOf(l, t, r, b)
        }

        /** Intersection over union of two boxes, 0 to 1. */
        fun overlap(a: FloatArray, b: FloatArray): Float {
            val w = minOf(a[2], b[2]) - maxOf(a[0], b[0])
            val h = minOf(a[3], b[3]) - maxOf(a[1], b[1])
            if (w <= 0 || h <= 0) return 0f
            val inter = w * h
            val union = (a[2] - a[0]) * (a[3] - a[1]) + (b[2] - b[0]) * (b[3] - b[1]) - inter
            return if (union <= 0) 0f else inter / union
        }

        private fun cx(b: FloatArray) = (b[0] + b[2]) / 2
        private fun cy(b: FloatArray) = (b[1] + b[3]) / 2
    }
}
