package com.mtgscanner.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.mtgscanner.data.sharedHttpClient
import okhttp3.Request
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Proxies of the cards a deck is missing, for playtesting: nine to a page at real card size (63 × 88 mm),
 * with thin cut lines. A card whose picture can't be had prints as a box with its name.
 */
object ProxySheet {
    /** Card size in points (1/72 inch). */
    const val CARD_W = 63f / 25.4f * 72f
    const val CARD_H = 88f / 25.4f * 72f

    /** Letter in the US and Canada, A4 elsewhere. */
    fun pageSize(locale: Locale = Locale.getDefault()): Pair<Int, Int> =
        if (locale.country in setOf("US", "CA")) 612 to 792 else 595 to 842

    /** Where card [i] of a page sits: 3 × 3, centered. */
    fun slot(i: Int, pageW: Int, pageH: Int): RectF {
        val left = (pageW - 3 * CARD_W) / 2f
        val top = (pageH - 3 * CARD_H) / 2f
        val x = left + (i % 3) * CARD_W
        val y = top + (i / 3) * CARD_H
        return RectF(x, y, x + CARD_W, y + CARD_H)
    }

    /** One entry per copy: [names] repeated by [copies]. */
    fun copies(lines: List<Pair<String, Int>>): List<String> = lines.flatMap { (n, q) -> List(q.coerceAtLeast(0)) { n } }

    fun pages(copies: Int): Int = (copies + 8) / 9

    private val client by lazy { sharedHttpClient.newBuilder().callTimeout(20, TimeUnit.SECONDS).build() }

    /** A card picture at Scryfall's large size (about 270 dpi at card size). Null on failure. */
    fun fetch(url: String): Bitmap? = runCatching {
        val big = url.replace("/small/", "/large/").replace("/normal/", "/large/")
        client.newCall(Request.Builder().url(big).header("User-Agent", UserAgent.value).build()).execute().use { r ->
            if (!r.isSuccessful) return null
            val bytes = r.body?.bytes() ?: return null
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }.getOrNull()

    /**
     * Writes the sheet. [cards] is one (name, picture URL) per copy; each page fetches its pictures once per URL
     * and lets them go when it's done, so a long list doesn't hold every picture at once.
     * Returns the number of copies printed.
     */
    fun write(cards: List<Pair<String, String?>>, out: OutputStream, locale: Locale = Locale.getDefault()): Int {
        val (w, h) = pageSize(locale)
        val doc = PdfDocument()
        val cut = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.4f; style = Paint.Style.STROKE }
        val box = Paint().apply { color = Color.DKGRAY; strokeWidth = 1f; style = Paint.Style.STROKE }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; color = Color.BLACK }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        try {
            cards.chunked(9).forEachIndexed { p, chunk ->
                val page = doc.startPage(PdfDocument.PageInfo.Builder(w, h, p + 1).create())
                val c = page.canvas
                val pictures = HashMap<String, Bitmap?>()
                chunk.forEachIndexed { i, (name, url) ->
                    val r = slot(i, w, h)
                    val bmp = url?.let { pictures.getOrPut(it) { fetch(it) } }
                    if (bmp != null) {
                        c.drawBitmap(bmp, null, r, paint)
                    } else {
                        c.drawRect(r, box)
                        c.drawText(fit(name, r.width() - 16f, label), r.left + 8f, r.top + 20f, label)
                    }
                    c.drawRect(r, cut)
                }
                doc.finishPage(page)
                pictures.values.forEach { it?.recycle() }
            }
            doc.writeTo(out)
        } finally {
            doc.close()
        }
        return cards.size
    }

    private fun fit(s: String, maxWidth: Float, p: Paint): String {
        if (p.measureText(s) <= maxWidth) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }
}
