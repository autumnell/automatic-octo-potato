package com.mtgscanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import java.io.File

/**
 * Small photos of cards the scanner wasn't sure of: set aside to check later, or saved because they couldn't be
 * read. The scanner hands over each straightened card it looks at ([keep]); when one is set aside or saved, the
 * latest is written to filesDir/scan-photos under a name ([save]), to show next to the guesses later.
 */
object ScanPhotos {
    private const val KEEP = 200
    /** A picture older than this belongs to an earlier card. */
    private const val FRESH_MS = 5000L
    private const val W = 200
    private const val H = 280

    private var last: Pair<Long, Bitmap>? = null

    /** The straightened card the scanner just looked at, kept small until the next one. */
    fun keep(card: Bitmap) {
        val small = runCatching { Bitmap.createScaledBitmap(card, W, H, true) }.getOrNull() ?: return
        val copy = if (small === card) runCatching { card.copy(Bitmap.Config.ARGB_8888, false) }.getOrNull() ?: return else small
        synchronized(this) {
            last?.second?.recycle()
            last = SystemClock.elapsedRealtime() to copy
        }
    }

    /** Saves the latest card under [name] when it's fresh; returns the name, or null when there was none. */
    fun save(ctx: Context, name: String): String? = grab()?.let { write(ctx, name, it) }

    /**
     * A copy of the latest card when it's fresh, for [write]. Cheap (a 200×280 copy), so it can be taken on the main
     * thread the moment a card is set aside, and written out later on another thread.
     */
    fun grab(): Bitmap? {
        val (at, bmp) = synchronized(this) { last?.let { it.first to it.second.copy(Bitmap.Config.ARGB_8888, false) } } ?: return null
        if (SystemClock.elapsedRealtime() - at > FRESH_MS) { bmp.recycle(); return null }
        return bmp
    }

    /** Writes [bmp] (from [grab], recycled here) as [name] and trims the folder to the newest [KEEP]. File work. */
    fun write(ctx: Context, name: String, bmp: Bitmap): String? = try {
        val d = dir(ctx).apply { mkdirs() }
        File(d, "$name.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        d.listFiles()?.sortedByDescending { it.lastModified() }?.drop(KEEP)?.forEach { it.delete() }
        name
    } catch (_: Exception) {
        null
    } finally {
        bmp.recycle()
    }

    /** The photo saved as [name], or null when there's none. */
    fun file(ctx: Context, name: String?): File? = name?.let { File(dir(ctx), "$it.jpg").takeIf { f -> f.exists() } }

    fun rename(ctx: Context, from: String, to: String) {
        runCatching { File(dir(ctx), "$from.jpg").renameTo(File(dir(ctx), "$to.jpg")) }
    }

    fun delete(ctx: Context, name: String?) { name?.let { runCatching { File(dir(ctx), "$it.jpg").delete() } } }

    /** The name for an unknown scan's photo. */
    fun pendingName(id: Long) = "pending-$id"

    private fun dir(ctx: Context) = File(ctx.applicationContext.filesDir, "scan-photos")
}
