package com.mtgscanner.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.unitPrice
import java.io.File
import java.io.OutputStream
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

/**
 * A PDF listing every card worth at least a chosen amount per copy, with name, set, condition,
 * quantity, price, a photo of your own copy when there is one, and totals. Proxies are left out.
 */
object InsuranceReport {
    private const val W = 595 // A4 in points
    private const val H = 842
    private const val MARGIN = 36f
    private const val ROW = 22f
    private const val PHOTO_ROW = 74f

    /** The cards that go in the report, most valuable first. */
    fun select(cards: List<CollectionCard>, minValue: Double): List<CollectionCard> =
        cards.filter { !it.proxy && (it.unitPrice() ?: 0.0) >= minValue }
            .sortedByDescending { (it.unitPrice() ?: 0.0) * it.quantity }

    /** Writes the PDF to [out]. Returns how many entries it lists. */
    fun write(cards: List<CollectionCard>, minValue: Double, out: OutputStream, filesDir: File? = null): Int {
        val rows = select(cards, minValue)
        val money = NumberFormat.getCurrencyInstance(Locale.US)
        val doc = PdfDocument()
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9.5f; color = Color.BLACK }
        val bold = Paint(text).apply { typeface = Typeface.DEFAULT_BOLD }
        val title = Paint(bold).apply { textSize = 18f }
        val muted = Paint(text).apply { color = Color.DKGRAY }
        val line = Paint().apply { color = Color.LTGRAY; strokeWidth = 0.5f }

        // Columns: name, set, condition, qty, each, total
        val xName = MARGIN
        val xSet = 268f
        val xCond = 360f
        val xQty = 410f
        val xEach = 490f
        val xTotal = W - MARGIN

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun header() {
            val c = page!!.canvas
            bold.textAlign = Paint.Align.LEFT
            c.drawText("Name", xName, y, bold)
            c.drawText("Set", xSet, y, bold)
            c.drawText("Cond.", xCond, y, bold)
            bold.textAlign = Paint.Align.RIGHT
            c.drawText("Qty", xQty + 16f, y, bold)
            c.drawText("Each", xEach, y, bold)
            c.drawText("Total", xTotal, y, bold)
            bold.textAlign = Paint.Align.LEFT
            y += 6f
            c.drawLine(MARGIN, y, W - MARGIN, y, line)
            y += ROW - 6f
        }

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = MARGIN + 12f
            if (pageNo == 1) {
                page!!.canvas.drawText("Collection insurance report", MARGIN, y + 6f, title)
                y += 24f
                page!!.canvas.drawText(
                    "${LocalDate.now()} · cards worth ${money.format(minValue)} or more each",
                    MARGIN, y, muted,
                )
                y += 24f
            }
            header()
        }

        fun fit(s: String, maxWidth: Float, p: Paint): String {
            if (p.measureText(s) <= maxWidth) return s
            var t = s
            while (t.isNotEmpty() && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
            return "$t…"
        }

        newPage()
        for (c in rows) {
            val photo = c.photo.takeIf { it.isNotBlank() }?.let { loadThumb(it, filesDir) }
            val rowH = if (photo != null) PHOTO_ROW else ROW
            if (y + rowH > H - MARGIN - 40f) newPage()
            val canvas = page!!.canvas
            var nameX = xName
            if (photo != null) {
                val h = PHOTO_ROW - 10f
                val w = h * photo.width / photo.height.coerceAtLeast(1)
                val top = y - 12f
                canvas.drawBitmap(photo, null, Rect(xName.toInt(), top.toInt(), (xName + w).toInt(), (top + h).toInt()), null)
                nameX = xName + w + 6f
            }
            val each = c.unitPrice() ?: 0.0
            val extras = listOfNotNull(
                "Foil".takeIf { c.foil }, "Signed".takeIf { c.signed }, "Altered".takeIf { c.altered }, c.graded.takeIf { it.isNotBlank() },
            ).joinToString(", ")
            canvas.drawText(fit(c.name, xSet - nameX - 8f, text), nameX, y, text)
            if (extras.isNotEmpty()) canvas.drawText(fit(extras, xSet - nameX - 8f, muted), nameX, y + 11f, muted)
            canvas.drawText(fit("${c.setCode.uppercase()} #${c.collectorNumber}", xCond - xSet - 6f, text), xSet, y, text)
            canvas.drawText(c.condition, xCond, y, text)
            text.textAlign = Paint.Align.RIGHT
            canvas.drawText("${c.quantity}", xQty + 16f, y, text)
            canvas.drawText(money.format(each), xEach, y, text)
            canvas.drawText(money.format(each * c.quantity), xTotal, y, text)
            text.textAlign = Paint.Align.LEFT
            y += rowH
        }

        // Totals
        if (y + 50f > H - MARGIN) newPage()
        val canvas = page!!.canvas
        canvas.drawLine(MARGIN, y - 10f, W - MARGIN, y - 10f, line)
        y += 6f
        val copies = rows.sumOf { it.quantity }
        val total = rows.sumOf { (it.unitPrice() ?: 0.0) * it.quantity }
        canvas.drawText("${rows.size} entries · $copies cards", MARGIN, y, bold)
        bold.textAlign = Paint.Align.RIGHT
        canvas.drawText("Total ${money.format(total)}", xTotal, y, bold)
        bold.textAlign = Paint.Align.LEFT
        y += 16f
        canvas.drawText("Prices are market prices from Scryfall.", MARGIN, y, muted)
        doc.finishPage(page!!)
        doc.writeTo(out)
        doc.close()
        return rows.size
    }

    /** A small copy of the photo, so the PDF stays a sensible size. */
    private fun loadThumb(stored: String, filesDir: File?): Bitmap? = runCatching {
        val f = File(stored).let { if (!it.isAbsolute && filesDir != null) File(filesDir, stored) else it }
        if (!f.exists()) return null
        val path = f.path
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 300) sample *= 2
        val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        // Phone photos are often stored sideways with an EXIF note to turn them; BitmapFactory ignores it.
        val degrees = f.inputStream().use { com.mtgscanner.data.CardPhotos.exifRotation(it) }
        if (degrees == 0) bmp
        else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(degrees.toFloat()) }, true)
    }.getOrNull()
}
