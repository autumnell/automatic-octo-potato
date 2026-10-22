package com.mtgscanner.ui.collection

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
 * Printable labels on A4: one big label per box (its name on a strip of label tape, what goes in it, and a QR
 * code that opens the box in the app), then its dividers, front to back: each a card-wide strip with a tab
 * sticking up, the letter or name on the tab, tabs stepping across like a real set. Grey lines mark where to cut.
 */
object BoxLabels {
    private const val W = 595
    private const val H = 842
    private const val MARGIN = 28f
    private const val BOX_H = 92f

    /** A card's width, 63 mm, so a divider strip is as wide as the cards behind it. */
    private const val CARD_W = 178.6f
    /** The tab above the strip. */
    private const val TAB_UP = 20f
    /** The top of the divider strip under the tab. */
    private const val STRIP_H = 16f
    private const val DIV_H = TAB_UP + STRIP_H
    /** Between rows of dividers; side by side they share a cut line, so three fit across. */
    private const val DIV_GAP = 6f
    private const val COL_GAP = 1f

    private val condensedBold: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

    /** Writes the PDF; returns how many labels it has. [cards] are every card, for the dividers in use. */
    fun write(boxes: List<Box>, cards: List<CollectionCard>, out: OutputStream): Int {
        val byBox = cards.groupBy { it.boxId }
        val doc = PdfDocument()
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 21f; typeface = condensedBold; color = Color.rgb(30, 28, 25); letterSpacing = 0.04f }
        val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 10f; color = Color.DKGRAY }
        val cut = cutPaint()
        val tape = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(241, 236, 223); style = Paint.Style.FILL }
        val tapeEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(190, 182, 164); style = Paint.Style.STROKE; strokeWidth = 0.7f }
        val div = DividerPaints()
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

        val dark = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        val qrSize = BOX_H - 12f
        val cols = ((width + COL_GAP) / (CARD_W + COL_GAP)).toInt().coerceAtLeast(1)
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
            // The name on a strip of label tape: off-white, a thin darker edge, condensed bold caps.
            val label = fit(b.name.uppercase(), textW - 20f, name)
            val tapeW = (name.measureText(label) + 20f).coerceAtMost(textW)
            val tapeTop = y + 14f
            c.drawRect(MARGIN + 10f, tapeTop, MARGIN + 10f + tapeW, tapeTop + 32f, tape)
            c.drawRect(MARGIN + 10f, tapeTop, MARGIN + 10f + tapeW, tapeTop + 32f, tapeEdge)
            c.drawText(label, MARGIN + 20f, tapeTop + 23f, name)
            val layout = BoxLayout.of(b.divider)
            val inside = listOfNotNull(
                BoxRules.fromJson(b.rules).describe(),
                layout.label().takeIf { layout.hasDividers || layout.byNumber },
            ).joinToString(" · ")
            c.drawText(fit(inside, textW, small), MARGIN + 12f, y + 66f, small)
            y += BOX_H + 8f
            count++

            val tabs = Sorting.tabs(layout, byBox[b.id].orEmpty().map { it.name to it.facts() })
            var i = 0
            tabs.chunked(cols).forEach { row ->
                room(DIV_H)
                val tc = page!!.canvas
                row.forEachIndexed { col, text ->
                    drawDivider(tc, MARGIN + col * (CARD_W + COL_GAP), y, text, i++, div)
                    count++
                }
                y += DIV_H + DIV_GAP
            }
            y += 12f
        }
        if (page == null) room(BOX_H)
        page?.let { doc.finishPage(it) }
        try { doc.writeTo(out) } finally { doc.close() }
        return count
    }

    /** One page of dividers (a new set's, say), the same shape and size as the box dividers. Returns how many. */
    fun writeTabs(labels: List<String>, out: OutputStream): Int {
        val doc = PdfDocument()
        val div = DividerPaints()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, 1).create())
        val width = W - 2 * MARGIN
        val cols = ((width + COL_GAP) / (CARD_W + COL_GAP)).toInt().coerceAtLeast(1)
        val rows = ((H - 2 * MARGIN + DIV_GAP) / (DIV_H + DIV_GAP)).toInt()
        val shown = labels.take(cols * rows)
        shown.forEachIndexed { i, label ->
            val x = MARGIN + (i % cols) * (CARD_W + COL_GAP)
            val y = MARGIN + (i / cols) * (DIV_H + DIV_GAP)
            drawDivider(page.canvas, x, y, label, i, div)
        }
        doc.finishPage(page)
        try { doc.writeTo(out) } finally { doc.close() }
        return shown.size
    }

    private class DividerPaints {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 12f; typeface = condensedBold; color = Color.BLACK }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(238, 238, 238); style = Paint.Style.FILL }
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.GRAY; style = Paint.Style.STROKE; strokeWidth = 0.6f }
    }

    private fun cutPaint() = Paint().apply { color = Color.LTGRAY; style = Paint.Style.STROKE; strokeWidth = 0.6f }

    /** One divider's top: a card-wide strip with a round-cornered tab sticking up, the label on the tab. */
    private fun drawDivider(c: Canvas, x: Float, y: Float, label: String, index: Int, p: DividerPaints) {
        // The tab fits its label, within reason.
        val tabW = (p.text.measureText(label) + 16f).coerceIn(56f, CARD_W * 0.6f)
        val tabX = x + DividerTabMath.tabLeft(index, CARD_W, tabW)
        val r = 4f
        val stripTop = y + TAB_UP
        val shape = Path().apply {
            moveTo(x, y + DIV_H)
            lineTo(x, stripTop)
            lineTo(tabX, stripTop)
            lineTo(tabX, y + r)
            arcTo(RectF(tabX, y, tabX + 2 * r, y + 2 * r), 180f, 90f)
            lineTo(tabX + tabW - r, y)
            arcTo(RectF(tabX + tabW - 2 * r, y, tabX + tabW, y + 2 * r), 270f, 90f)
            lineTo(tabX + tabW, stripTop)
            lineTo(x + CARD_W, stripTop)
            lineTo(x + CARD_W, y + DIV_H)
            close()
        }
        c.drawPath(shape, p.fill)
        c.drawPath(shape, p.outline)
        val t = fit(label, tabW - 8f, p.text)
        c.drawText(t, tabX + (tabW - p.text.measureText(t)) / 2, y + 14.5f, p.text)
    }

    private fun fit(s: String, max: Float, p: Paint): String {
        if (p.measureText(s) <= max) return s
        var t = s
        while (t.isNotEmpty() && p.measureText("$t…") > max) t = t.dropLast(1)
        return "$t…"
    }
}
