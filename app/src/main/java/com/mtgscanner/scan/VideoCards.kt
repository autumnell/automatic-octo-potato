package com.mtgscanner.scan

import kotlinx.coroutines.ensureActive
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build

/**
 * Reads the cards in a recorded video (a pack opened card by card, a binder flipped through): takes still frames
 * through it, skips blurred ones, reads each frame like a photo ([PhotoCards]), and counts each card once while it
 * stays in view ([VideoCount]).
 */
object VideoCards {
    /** Longest video read, in milliseconds. */
    const val MAX_MS = 5 * 60_000L
    /** Most frames read from one video. */
    private const val MAX_FRAMES = 240
    /** At least this far apart, in milliseconds. */
    private const val MIN_STEP_MS = 350L
    /** Frames are read at most this big on their long side. */
    private const val MAX_PX = 1600
    /** Bits two picture keys may differ by and still be the same card (of 64). */
    private const val PRINT_KEY_BITS = 6

    /**
     * Every card read from the video at [uri], each card once (twice when two copies showed together). [onProgress]
     * gets 0 to 1. Null when the video can't be opened.
     */
    suspend fun read(
        ctx: Context, uri: Uri, embed: (Bitmap) -> ArtVector?, wantsArt: Boolean,
        onProgress: (Float) -> Unit = {},
    ): List<PhotoRead>? {
        val mmr = MediaMetadataRetriever()
        try {
            runCatching { mmr.setDataSource(ctx, uri) }.getOrElse { return null }
            val duration = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: return null
            val length = duration.coerceAtMost(MAX_MS)
            val step = maxOf(MIN_STEP_MS, length / MAX_FRAMES)
            val tracks = CardTracks<Pair<Long, CardTextGuess>>(maxMissed = 3)
            val count = VideoCount<PhotoRead>()
            // Picture keys already seen in this video: a near one (a few values flipped by noise) is the same card.
            val prints = ArrayList<Long>()
            // The footer is read on some frames only: a footer and a name read together are one card from then on.
            val names = VideoKeys()
            val all = ArrayList<PhotoRead>()
            var sharpest = 0.0
            var t = 0L
            while (t <= length) {
                // Closing the sheet or starting another video cancels this one: stop at the next frame.
                kotlin.coroutines.coroutineContext.ensureActive()
                onProgress(t.toFloat() / length.coerceAtLeast(1))
                val frame = frameAt(mmr, t)
                if (frame != null) {
                    try {
                        val sharp = Sharpness.of(frame)
                        sharpest = maxOf(sharpest * 0.95, sharp)
                        // A frame much blurrier than recent ones is the card moving: the next still one reads better.
                        if (sharp >= sharpest * 0.45) {
                            val reads = PhotoCards.read(frame, embed, wantsArt, tracks)
                            all += reads
                            count.frame(reads.map { r -> keyOf(r, prints, names) to r })
                        } else count.frame(emptyList(), skipped = true)
                    } finally {
                        frame.recycle()
                    }
                }
                t += step
            }
            onProgress(1f)
            val kept = count.result()
            // Pictures of the reads not kept (the same card in later frames) aren't needed.
            val keep = kept.mapNotNull { it.thumb }.toHashSet()
            all.forEach { r -> r.thumb?.takeIf { it !in keep }?.recycle() }
            return kept
        } finally {
            runCatching { mmr.release() }
        }
    }

    /** What makes two reads the same card: the name and printing read, else the card's picture fingerprint. */
    private fun keyOf(r: PhotoRead, prints: MutableList<Long>, names: VideoKeys): String? {
        val g = r.guess
        val footer = if (g.setCode != null && g.collectorNumber != null) g.key else null
        names.of(g.nameKey, footer)?.let { return it }
        val p = g.artOnlyPrints.firstOrNull() ?: return g.artOnly.firstOrNull()?.let { "art:$it" }
        // A coarse picture key: the signs of the first 64 values of the fingerprint. Values near zero flip from
        // frame to frame, so a key within a few bits of one already seen is taken as that card.
        var bits = 0L
        for (i in 0 until minOf(64, p.size)) if (p[i] > 0) bits = bits or (1L shl i)
        val known = prints.firstOrNull { java.lang.Long.bitCount(it xor bits) <= PRINT_KEY_BITS }
        if (known == null) prints += bits
        return "print:${known ?: bits}"
    }

    private fun frameAt(mmr: MediaMetadataRetriever, ms: Long): Bitmap? = runCatching {
        val us = ms * 1000
        val raw = if (Build.VERSION.SDK_INT >= 27) {
            mmr.getScaledFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST, MAX_PX, MAX_PX)
        } else mmr.getFrameAtTime(us, MediaMetadataRetriever.OPTION_CLOSEST)
        raw?.let { b ->
            val argb = if (b.config == Bitmap.Config.ARGB_8888) b else b.copy(Bitmap.Config.ARGB_8888, false).also { b.recycle() }
            val long = maxOf(argb.width, argb.height)
            if (long <= MAX_PX) argb
            else Bitmap.createScaledBitmap(argb, argb.width * MAX_PX / long, argb.height * MAX_PX / long, true).also { if (it !== argb) argb.recycle() }
        }
    }.getOrNull()
}

/**
 * One key per card through a video, though a frame may read its name, its footer ("MOM#123") or both. A frame
 * with both links them, so frames with only one of the two still count as the same card. Another footer with the
 * same name is another printing (Forests from two sets): it gets its own key, and the name follows the newest.
 * Pure, for tests.
 */
class VideoKeys {
    /** Name or footer read, to the card key it belongs to. */
    private val alias = HashMap<String, String>()
    /** Card key to the footer linked to it. */
    private val footerOf = HashMap<String, String>()

    /** The card's key for a frame that read [name] and [footer] (either may be null); null when neither was read. */
    fun of(name: String?, footer: String?): String? {
        if (footer == null) {
            if (name == null) return null
            return alias.getOrPut(name) { name }
        }
        alias[footer]?.let { k -> if (name != null) alias[name] = k; return k }
        val byName = name?.let { alias[it] }
        val key = when {
            byName != null && footerOf[byName] == null -> byName
            byName == null && name != null -> name
            else -> footer
        }
        footerOf[key] = footer
        alias[footer] = key
        if (name != null) alias[name] = key
        return key
    }
}

/** How sharp a frame is: the spread of its brightness changes between neighbours, on a small grey copy. */
object Sharpness {
    fun of(frame: Bitmap): Double {
        val w = 160
        val h = (frame.height * w / frame.width.coerceAtLeast(1)).coerceIn(16, 400)
        val small = Bitmap.createScaledBitmap(frame, w, h, true)
        val px = IntArray(w * h)
        small.getPixels(px, 0, w, 0, 0, w, h)
        if (small !== frame) small.recycle()
        return laplacianVariance(IntArray(px.size) { ImageHash.luma(px[it]) }, w, h)
    }

    /** Variance of the Laplacian of a [w] x [h] grey picture: high for sharp edges, low for blur. */
    fun laplacianVariance(grey: IntArray, w: Int, h: Int): Double {
        if (w < 3 || h < 3) return 0.0
        var sum = 0.0
        var sq = 0.0
        var n = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val l = (grey[i - 1] + grey[i + 1] + grey[i - w] + grey[i + w] - 4 * grey[i]).toDouble()
            sum += l; sq += l * l; n++
        }
        val mean = sum / n
        return sq / n - mean * mean
    }
}

/**
 * Counts cards through a video, frame by frame. A card seen again within [gap] frames is the same card still in
 * view; when it's gone longer, seeing it again is another copy. Two copies in one frame count as two.
 */
class VideoCount<T>(private val gap: Int = 3) {
    private class Run<T>(var last: Int, var most: Int, val items: MutableList<T>)

    private var index = 0
    private val runs = LinkedHashMap<String, MutableList<Run<T>>>()
    private val order = ArrayList<Pair<String, Run<T>>>()

    /** The reads in one frame, each with its card key (null: nothing read, left out). [skipped]: a blurred frame. */
    fun frame(reads: List<Pair<String?, T>>, skipped: Boolean = false) {
        if (!skipped) {
            for ((key, group) in reads.filter { it.first != null }.groupBy { it.first!! }) {
                val list = runs.getOrPut(key) { mutableListOf() }
                val open = list.lastOrNull()?.takeIf { index - it.last <= gap }
                if (open != null) {
                    open.last = index
                    if (group.size > open.most) {
                        open.items += group.drop(open.most).map { it.second }
                        open.most = group.size
                    }
                } else {
                    val run = Run(index, group.size, group.map { it.second }.toMutableList())
                    list += run
                    order += key to run
                }
            }
        }
        index++
    }

    /** One item per copy seen, in the order the cards first showed. */
    fun result(): List<T> = order.flatMap { it.second.items }
}
