package com.mtgscanner.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mtgscanner.data.ScryfallCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Hashes the artwork in part of a bitmap. */
object ArtHashing {
    fun hash(bitmap: Bitmap, left: Int, top: Int, right: Int, bottom: Int): Long? {
        val l = left.coerceIn(0, bitmap.width - 1)
        val t = top.coerceIn(0, bitmap.height - 1)
        val w = (right.coerceAtMost(bitmap.width) - l)
        val h = (bottom.coerceAtMost(bitmap.height) - t)
        if (w < 9 || h < 8) return null
        val crop = Bitmap.createBitmap(bitmap, l, t, w, h)
        val small = Bitmap.createScaledBitmap(crop, 9, 8, true)
        val px = IntArray(72)
        small.getPixels(px, 0, 9, 0, 0, 9, 8)
        if (small !== crop) small.recycle()
        if (crop !== bitmap) crop.recycle()
        return ImageHash.dHash(IntArray(72) { ImageHash.luma(px[it]) })
    }

    /** The art window on a Scryfall card image, as fractions of its width and height. */
    fun hashCardImage(bitmap: Bitmap): Long? = hash(
        bitmap,
        (bitmap.width * 0.08).toInt(), (bitmap.height * 0.11).toInt(),
        (bitmap.width * 0.92).toInt(), (bitmap.height * 0.55).toInt(),
    )
}

/**
 * Tells printings of the same card apart by their artwork, for cards without a readable
 * set code and collector number (older frames, foreign or foil cards).
 */
class ArtMatcher(
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build(),
) {
    private val cache = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?) = size > 600
    }
    private val gate = Semaphore(6)

    /** The printing whose art clearly matches [target], or null when it's not clear. */
    suspend fun bestPrinting(printings: List<ScryfallCard>, target: Long): ScryfallCard? = coroutineScope {
        val hashed = printings.take(MAX_PRINTINGS).map { card ->
            async { hashFor(card)?.let { card to it } }
        }.awaitAll().filterNotNull()
        // Reprints often reuse the same art; any of them is a fine answer, so only compare distinct art.
        val distinct = hashed.distinctBy { it.second }
        ImageHash.pickBest(target, distinct)
    }

    private suspend fun hashFor(card: ScryfallCard): Long? {
        synchronized(cache) { cache[card.id]?.let { return it } }
        val url = card.smallImageUrl ?: return null
        val hash = gate.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        val bytes = resp.body?.bytes() ?: return@use null
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
                        ArtHashing.hashCardImage(bmp).also { bmp.recycle() }
                    }
                }.getOrNull()
            }
        } ?: return null
        synchronized(cache) { cache[card.id] = hash }
        return hash
    }

    companion object {
        const val MAX_PRINTINGS = 40
    }
}
