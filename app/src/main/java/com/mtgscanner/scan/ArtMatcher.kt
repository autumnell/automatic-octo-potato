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

    /** The art model's embedding of the same art window as [hashCardImage]; with [only], null unless that's the chosen model. */
    fun embedCardImage(bitmap: Bitmap, embedder: ArtEmbedder, only: ArtModel? = null): ArtVector? {
        val l = (bitmap.width * 0.08).toInt()
        val t = (bitmap.height * 0.11).toInt()
        val w = (bitmap.width * 0.92).toInt() - l
        val h = (bitmap.height * 0.55).toInt() - t
        if (w < 16 || h < 16) return null
        val crop = Bitmap.createBitmap(bitmap, l, t, w, h)
        val argb = if (crop.config == Bitmap.Config.ARGB_8888) crop else crop.copy(Bitmap.Config.ARGB_8888, false)
        return try {
            embedder.embed(argb, only)
        } finally {
            if (argb !== crop) argb.recycle()
            if (crop !== bitmap) crop.recycle()
        }
    }
}

/**
 * Tells printings of the same card apart by their artwork, for cards without a readable
 * set code and collector number (older frames, foreign or foil cards).
 */
class ArtMatcher(
    private val client: OkHttpClient = OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build(),
    /** The art model; without it (or until its file is here) only hashes are compared. */
    private val embedder: ArtEmbedder? = null,
) {
    /** A printing's art: its hash, and its embedding when the model was ready. */
    private class Seen(val hash: Long, val embedding: ArtVector?)

    private val cache = object : LinkedHashMap<String, Seen>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Seen>?) = size > 600
    }
    private val gate = Semaphore(6)

    /**
     * The printing whose art clearly matches the camera's: by the art model when [embedding] is given and it's
     * sure, else by the [hash]. Printings sharing a picture don't compete; the first of them in [printings] wins.
     * An [embedding] from a model other than the chosen one is left out.
     */
    suspend fun bestPrinting(printings: List<ScryfallCard>, hash: Long?, embedding: ArtVector?): ScryfallCard? = coroutineScope {
        val model = embedder?.model
        val target = embedding?.takeIf { model != null && it.model == model.id && embedder?.ready == true }
        if (hash == null && target == null) return@coroutineScope null
        val wantModel = if (target != null) model else null
        val seen = printings.take(MAX_PRINTINGS).map { card ->
            async { seenFor(card, wantModel)?.let { card to it } }
        }.awaitAll().filterNotNull()
        if (target != null && wantModel != null) {
            val withEmb = seen.mapNotNull { (card, s) -> s.embedding?.takeIf { it.model == wantModel.id }?.let { card to it.values } }
            // Only when every printing could be embedded, so one missing picture can't win by default.
            if (withEmb.size == seen.size && withEmb.size >= 2) {
                ArtEmbedding.pickBest(
                    listOf(target.values), withEmb, minCos = wantModel.minCos, minLead = wantModel.minLead,
                    group = { it.illustrationId ?: it.id },
                )?.let { return@coroutineScope it }
            }
        }
        if (hash == null) return@coroutineScope null
        // Reprints often reuse the same art; any of them is a fine answer, so only compare distinct art.
        val distinct = seen.map { it.first to it.second.hash }.distinctBy { it.second }
        ImageHash.pickBest(hash, distinct)
    }

    /** [card]'s art; with [want], also its embedding by that model (a cached one from another model is redone). */
    private suspend fun seenFor(card: ScryfallCard, want: ArtModel?): Seen? {
        synchronized(cache) {
            cache[card.id]?.let { if (want == null || it.embedding?.model == want.id) return it }
        }
        val url = card.smallImageUrl ?: return null
        val seen = gate.withPermit {
            withContext(Dispatchers.IO) {
                runCatching {
                    client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        val bytes = resp.body?.bytes() ?: return@use null
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use null
                        try {
                            val hash = ArtHashing.hashCardImage(bmp) ?: return@use null
                            Seen(hash, if (want != null) embedder?.let { ArtHashing.embedCardImage(bmp, it, want) } else null)
                        } finally {
                            bmp.recycle()
                        }
                    }
                }.getOrNull()
            }
        } ?: return null
        synchronized(cache) { cache[card.id] = seen }
        return seen
    }

    companion object {
        const val MAX_PRINTINGS = 40
    }
}
