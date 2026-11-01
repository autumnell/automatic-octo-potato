package com.mtgscanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Real scans kept for measuring the scanner. When turned on (Settings › Data, under knowing cards by picture),
 * each card added by scanning keeps its straightened picture, named by the Scryfall id it was found to be. They can
 * be checked on the phone (how many the picture alone gets right) or saved as a zip, to add to the build server's
 * accuracy test (app/src/test/resources/scan-photos).
 */
object TestPhotos {
    private const val PREFS = "scan"
    private const val KEY_ON = "keepTestPhotos"
    private const val KEEP = 300
    /** A picture older than this when a card is added belongs to another card. */
    private const val FRESH_MS = 4000L

    @Volatile private var enabled = false
    private var last: Pair<Long, Bitmap>? = null

    fun init(ctx: Context) { enabled = isOn(ctx) }
    fun isOn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ON, false)

    fun setOn(ctx: Context, on: Boolean) {
        prefs(ctx).edit().putBoolean(KEY_ON, on).apply()
        enabled = on
        if (!on) synchronized(this) { last?.second?.recycle(); last = null }
    }

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "test-photos")

    /** The straightened card the scanner just fingerprinted; kept until a card is added or the next one comes. */
    fun offer(card: Bitmap) {
        if (!enabled) return
        val copy = runCatching { card.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return
        synchronized(this) {
            last?.second?.recycle()
            last = SystemClock.elapsedRealtime() to copy
        }
    }

    /** A card was added as [id]: saves the picture it was read from, if it's fresh. */
    fun saveFor(ctx: Context, id: String) {
        if (!enabled) return
        val (at, bmp) = synchronized(this) { last.also { last = null } } ?: return
        try {
            if (SystemClock.elapsedRealtime() - at > FRESH_MS) return
            val d = dir(ctx).apply { mkdirs() }
            File(d, "${id}_${System.currentTimeMillis()}.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            d.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }
        } catch (_: Exception) {
        } finally {
            bmp.recycle()
        }
    }

    fun count(ctx: Context): Int = dir(ctx).listFiles()?.size ?: 0

    fun clear(ctx: Context) { dir(ctx).deleteRecursively() }

    /** Every kept photo in one zip. */
    fun zipTo(ctx: Context, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            dir(ctx).listFiles()?.sortedBy { it.name }?.forEach { f ->
                zip.putNextEntry(ZipEntry("scan-photos/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /** How the picture alone did on the kept photos. */
    data class Result(val right: Int, val wrong: Int, val unsure: Int) {
        val total: Int get() = right + wrong + unsure
    }

    /** Looks up every kept photo by its picture alone (as when no text can be read). Slow: call off the main thread. */
    fun check(ctx: Context): Result? {
        val g = CardPrints.gallery ?: return null
        var right = 0
        var wrong = 0
        var unsure = 0
        for (f in dir(ctx).listFiles().orEmpty()) {
            val id = f.name.substringBefore('_')
            val bmp = BitmapFactory.decodeFile(f.path) ?: continue
            val print = try { CardPrints.fingerprint(bmp) } finally { bmp.recycle() } ?: continue
            val t = CardPrints.thresholds
            val hit = g.match(print, minStandOut = t.standOut, minLead = t.lead)
            val row = g.rowOf(id)
            when {
                hit == null -> unsure++
                // Another printing of the same card still finds the card; the text or art then picks the printing.
                hit.id == id || (row >= 0 && hit.name == g.nameAt(row)) -> right++
                else -> wrong++
            }
        }
        return Result(right, wrong, unsure)
    }

    /**
     * Each kept photo looked up with no thresholds: how its best card stood out and led, and whether it was right.
     * Null without the gallery. Slow: call off the main thread.
     */
    fun samples(ctx: Context): List<TuneSample>? {
        val g = CardPrints.gallery ?: return null
        val out = ArrayList<TuneSample>()
        for (f in dir(ctx).listFiles().orEmpty()) {
            val id = f.name.substringBefore('_')
            val bmp = BitmapFactory.decodeFile(f.path) ?: continue
            val print = try { CardPrints.fingerprint(bmp) } finally { bmp.recycle() } ?: continue
            val hit = g.match(print, minScore = CardGallery.MIN_SCORE, minStandOut = 0f, minLead = -1f) ?: continue
            val row = g.rowOf(id)
            val right = hit.id == id || (row >= 0 && hit.name == g.nameAt(row))
            out += TuneSample(hit.standOut, hit.score - hit.runnerUp, right)
        }
        return out
    }

    /** Fits the picture match's thresholds to the kept photos ([ThresholdTuner]) and uses them. Null with too few. */
    fun tune(ctx: Context): TuneResult? {
        val r = ThresholdTuner.tune(samples(ctx) ?: return null) ?: return null
        CardPrints.setThresholds(ctx, r.thresholds)
        return r
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
