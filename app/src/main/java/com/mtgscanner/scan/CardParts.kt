package com.mtgscanner.scan

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognizer
import kotlinx.coroutines.tasks.await

/**
 * Reading the parts of a straightened card: name bar, footer, art window and set symbol. Shared by the live camera
 * ([CardTextAnalyzer]) and photos from the gallery ([PhotoCards]), so a fix to one reaches both.
 */
internal object CardParts {
    /** The name strip is drawn at least this tall per card height, so small cards still read. */
    const val NAME_TARGET_H = 700f
    /** The footer's small print, drawn at twice its size. */
    const val FOOTER_SCALE = 2f
    /** The art window, about the shape of the card's (0.84 x 0.44 of 63:88). */
    const val ART_W = 224
    const val ART_H = 164
    const val SYMBOL_PX = 88

    /** How much larger the name bar is drawn for a card [cardH] pixels tall. */
    fun nameScale(cardH: Float): Float = (NAME_TARGET_H / cardH).coerceIn(1f, 2f)

    /** The name bar of the card with corners [q] (in reading order), straightened and drawn larger. */
    fun name(q: FloatArray, cardH: Float, draw: Draw): Bitmap? =
        CardWarp.renderScaled(q, CardWarp.NAME, cardH, nameScale(cardH), draw = draw)

    /** The footer, straightened and drawn at [FOOTER_SCALE]. */
    fun footer(q: FloatArray, cardH: Float, draw: Draw): Bitmap? =
        CardWarp.renderScaled(q, CardWarp.FOOTER, cardH, FOOTER_SCALE, draw = draw)

    /** How sharp a strip is (see [StripQuality.sharpness]). */
    fun sharpness(strip: Bitmap): Double = StripQuality.sharpness(CardWarp.luma(strip), strip.width, strip.height)

    /**
     * A low-contrast strip redrawn in gray with its contrast stretched, which the text reader takes better (foils,
     * dark frames, dim light); null when the strip is fine as it is.
     */
    fun stretched(strip: Bitmap): Bitmap? {
        val luma = CardWarp.luma(strip)
        val (lo, hi) = StripQuality.levels(luma)
        if (!StripQuality.flat(lo, hi) || hi - lo < 12) return null
        val out = StripQuality.stretch(luma, lo, hi)
        val px = IntArray(out.size) { val v = out[it]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(px, strip.width, strip.height, Bitmap.Config.ARGB_8888)
    }

    /** Runs an OpenCV step; null when it fails. A missing native piece turns OpenCV off for good. */
    inline fun <T> cvSafe(block: () -> T): T? = try {
        block()
    } catch (e: LinkageError) {
        CvCards.disable()
        null
    } catch (e: Exception) {
        null
    }

    /** Draws card parts with OpenCV's warpPerspective when [cv] is there, else with [CardWarp] (a Canvas). */
    fun drawer(src: Bitmap, cv: CvCards.Frame?): Draw {
        if (cv == null) return { q, p, w, h -> CardWarp.render(src, q, p, w, h) }
        return { q, p, w, h -> cvSafe { cv.render(q, p, w, h) } ?: CardWarp.render(src, q, p, w, h) }
    }

    /** OCR lines of a strip drawn from [part] of the card, placed on a card [cardH] pixels tall. */
    suspend fun stripLines(strip: Bitmap, part: FloatArray, cardH: Float, reader: TextRecognizer): List<OcrLine> {
        val cardW = cardH * (63f / 88f)
        val sx = strip.width / ((part[2] - part[0]) * cardW)
        val sy = strip.height / ((part[3] - part[1]) * cardH)
        val ox = part[0] * cardW
        val oy = part[1] * cardH
        val text = reader.process(InputImage.fromBitmap(strip, 0)).await()
        return text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(
                    line.text,
                    (ox + box.left / sx).toInt(), (oy + box.top / sy).toInt(),
                    (ox + box.right / sx).toInt(), (oy + box.bottom / sy).toInt(),
                )
            }
        }
    }

    /**
     * The name bar and footer read in one pass: drawn one above the other on white, with a gap so their lines
     * can't run together, and the lines split back by where they sit. One read instead of two.
     */
    suspend fun stackedLines(
        name: Bitmap, footer: Bitmap, cardH: Float, reader: TextRecognizer,
    ): Pair<List<OcrLine>, List<OcrLine>> {
        val gap = maxOf(24, name.height / 2)
        val combo = Bitmap.createBitmap(maxOf(name.width, footer.width), name.height + gap + footer.height, Bitmap.Config.ARGB_8888)
        try {
            android.graphics.Canvas(combo).apply {
                drawColor(android.graphics.Color.WHITE)
                drawBitmap(name, 0f, 0f, null)
                drawBitmap(footer, 0f, (name.height + gap).toFloat(), null)
            }
            val text = reader.process(InputImage.fromBitmap(combo, 0)).await()
            val split = name.height + gap / 2
            val nameLines = ArrayList<OcrLine>()
            val footerLines = ArrayList<OcrLine>()
            for (block in text.textBlocks) for (line in block.lines) {
                val box = line.boundingBox ?: continue
                if (box.centerY() < split) {
                    nameLines += place(line.text, box.left, box.top, box.right, box.bottom, name, CardWarp.NAME, cardH)
                } else {
                    val dy = name.height + gap
                    footerLines += place(line.text, box.left, box.top - dy, box.right, box.bottom - dy, footer, CardWarp.FOOTER, cardH)
                }
            }
            return nameLines to footerLines
        } finally {
            combo.recycle()
        }
    }

    /** A line found at (l, t, r, b) in a [strip] drawn from [part], placed on a card [cardH] pixels tall. */
    private fun place(text: String, l: Int, t: Int, r: Int, b: Int, strip: Bitmap, part: FloatArray, cardH: Float): OcrLine {
        val cardW = cardH * (63f / 88f)
        val sx = strip.width / ((part[2] - part[0]) * cardW)
        val sy = strip.height / ((part[3] - part[1]) * cardH)
        val ox = part[0] * cardW
        val oy = part[1] * cardH
        return OcrLine(text, (ox + l / sx).toInt(), (oy + t / sy).toInt(), (ox + r / sx).toInt(), (oy + b / sy).toInt())
    }

    /**
     * The art window of the straightened card: its hash, and its embedding when [embed] gives one
     * (called with the hash and the art picture, which is recycled afterwards).
     */
    fun art(quad: FloatArray, turn: Int, draw: Draw, embed: (Long, Bitmap) -> ArtVector?): Pair<Long, ArtVector?>? {
        val art = draw(CardQuad.ordered(quad, turn), CardWarp.ART, ART_W, ART_H) ?: return null
        return try {
            val hash = ArtHashing.hash(art, 0, 0, art.width, art.height) ?: return null
            hash to embed(hash, art)
        } finally {
            art.recycle()
        }
    }

    /** The set symbol's shape, from the right end of the type line. */
    fun symbol(quad: FloatArray, turn: Int, draw: Draw): SymbolPrint? {
        val b = draw(CardQuad.ordered(quad, turn), CardWarp.SYMBOL, SYMBOL_PX, SYMBOL_PX) ?: return null
        return try {
            SetSymbol.fingerprint(CardWarp.luma(b), b.width, b.height)
        } finally {
            b.recycle()
        }
    }
}
