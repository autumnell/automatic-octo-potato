package com.mtgscanner.ui.collection

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.mtgscanner.data.BoxLayout
import com.mtgscanner.data.BoxLinks
import com.mtgscanner.data.BoxRules
import com.mtgscanner.data.Sorting
import com.mtgscanner.data.db.Box
import com.mtgscanner.data.db.CollectionCard
import com.mtgscanner.data.db.facts
import java.io.OutputStream

/**
 * Printable labels on A4: one big label per box (name, what goes in it, and a QR code that opens the box in the
 * app), then small tabs for its dividers, front to back. Light grey lines mark where to cut.
 */
object BoxLabels {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 28f
    private const val BOX_H = 92f
    private const val TAB_H = 34f
    private const val TAB_COLS = 4

    /** Writes the PDF; returns how many labels it has. [cards] are every card, for the dividers in use. */
    fun write(boxes: List<Box>, cards: List<CollectionCard>, out: OutputStream): Int {
        val byBox = cards.groupBy { it.boxId }
        val doc = PdfDocument()
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 22f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val tab = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val cut = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val width = W - 2 * MARGIN

        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = H.toFloat()
        var count = 0
        fun room(h: Float) {
            if (page != null && y + h <= H - MARGIN) return
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            y = MARGIN
        }
        fun fit(s: String, max: Float, p: Paint): String {
            if (p.measureText(s) <= max) return s
            var t = s
            while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
            return "$t…"
        }

        val dark = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val qrSize = BOX_H - 12f
        for (b in boxes) {
            room(BOX_H)
            val c = page!!.canvas
            c.drawRect(MARGIN, y, MARGIN + width, y + BOX_H, cut)
            val qr = com.mtgscanner.util.QrCodes.modules(BoxLinks.link(b.id))
            if (qr != null) {
                val left = MARGIN + width - qrSize - 6f
                val top = y + 6f
                val cell = qrSize / qr.size
                for (row in qr.indices) for (col in qr.indices) {
                    if (qr[row][col]) c.drawRect(left + col * cell, top + row * cell, left + (col + 1) * cell, top + (row + 1) * cell, dark)
                }
            }
            val textW = width - 24f - (if (qr != null) qrSize + 8f else 0f)
            c.drawText(fit(b.name, textW, name), MARGIN + 12f, y + 36f, name)
            val layout = BoxLayout.of(b.divider)
            val inside = listOfNotNull(
                BoxRules.fromJson(b.rules).describe(),
                layout.label().takeIf { layout.hasDividers || layout.byNumber },
            ).joinToString(" · ")
            c.drawText(fit(inside, textW, small), MARGIN + 12f, y + 58f, small)
            y += BOX_H + 6f
            count++

            val tabs = Sorting.tabs(layout, byBox[b.id].orEmpty().map { it.name to it.facts() })
            val tabW = width / TAB_COLS
            tabs.chunked(TAB_COLS).forEach { row ->
                room(TAB_H)
                val tc = page!!.canvas
                row.forEachIndexed { i, label ->
                    val x = MARGIN + i * tabW
                    tc.drawRect(x, y, x + tabW, y + TAB_H, cut)
                    tc.drawText(fit(label, tabW - 12f, tab), x + 6f, y + 22f, tab)
                    count++
                }
                y += TAB_H
            }
            y += 14f
        }
        if (page == null) room(BOX_H)
        page?.let { doc.finishPage(it) }
        try { doc.writeTo(out) } finally { doc.close() }
        return count
    }

    /** One page of divider tabs (a new set's, say), in the same size as the box dividers. Returns how many. */
    fun writeTabs(labels: List<String>, out: OutputStream): Int {
        val doc = PdfDocument()
        val tab = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 13f; typeface = Typeface.DEFAULT_BOLD; color = Color.BLACK }
        val cut = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.6f }
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val tabW = (W - 2 * MARGIN) / 2
        var y = MARGIN
        for (label in labels.take(40)) {
            page.canvas.drawRect(MARGIN, y, MARGIN + tabW, y + TAB_H, cut)
            var t = label
            while (t.isNotEmpty() && tab.measureText(t) > tabW - 12f) t = t.dropLast(1)
            page.canvas.drawText(t, MARGIN + 6f, y + 22f, tab)
            y += TAB_H
        }
        doc.finishPage(page)
        try { doc.writeTo(out) } finally { doc.close() }
        return labels.size.coerceAtMost(40)
    }
}
