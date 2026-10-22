package com.mtgscanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import org.opencv.core.Mat
import kotlin.math.roundToInt

/** One card found in a photo: what was read off it, and a small picture of it for the review list. */
class PhotoRead(val guess: CardTextGuess, val thumb: Bitmap?)

/**
 * Reads every card in a photo from the gallery (a binder page, cards on a table, a screenshot of a listing): the
 * photo is scaled down, each card found with OpenCV ([CvCards.Frame.findAll]) and straightened, then read the way
 * the camera reads one card: its name bar and footer, the whole card when those give nothing, and its art (and
 * set symbol) to tell printings apart or to find a card with no readable text.
 */
object PhotoCards {
    /** Longest side the photo is scaled to, so a 50 MP picture doesn't run out of memory. */
    const val MAX_PX = 2800

    /**
     * The picture at [uri], turned upright and at most [maxPx] on its longest side, as an ARGB_8888 bitmap.
     * Null when it can't be read.
     */
    fun decode(context: Context, uri: Uri, maxPx: Int = MAX_PX): Bitmap? = runCatching {
        val raw = if (Build.VERSION.SDK_INT >= 28) {
            val src = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                val w = info.size.width
                val h = info.size.height
                val s = maxPx.toFloat() / maxOf(w, h)
                if (s < 1f) decoder.setTargetSize((w * s).roundToInt().coerceAtLeast(1), (h * s).roundToInt().coerceAtLeast(1))
                // Pixels the app can read and draw from (not a hardware bitmap).
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else decodeOld(context, uri, maxPx)
        raw?.let { fit(it, maxPx) }
    }.getOrNull()

    /** Before Android 9: sampled down while decoding, then turned as the photo's EXIF says. */
    private fun decodeOld(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val long = maxOf(bounds.outWidth, bounds.outHeight)
        if (long <= 0) return null
        var sample = 1
        while (long / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val turn = runCatching {
            cr.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            }
        }.getOrNull() ?: 0
        if (turn == 0) return bmp
        val m = Matrix().apply { postRotate(turn.toFloat()) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true).also { if (it !== bmp) bmp.recycle() }
    }

    /** [b] at most [maxPx] on its long side, in ARGB_8888 (what OpenCV and the art hash read). */
    private fun fit(b: Bitmap, maxPx: Int): Bitmap {
        var out = b
        val long = maxOf(b.width, b.height)
        if (long > maxPx) {
            val s = maxPx.toFloat() / long
            out = Bitmap.createScaledBitmap(b, (b.width * s).roundToInt().coerceAtLeast(1), (b.height * s).roundToInt().coerceAtLeast(1), true)
            if (out !== b) b.recycle()
        }
        if (out.config != Bitmap.Config.ARGB_8888) {
            val copy = out.copy(Bitmap.Config.ARGB_8888, false)
            out.recycle()
            out = copy
        }
        return out
    }

    /**
     * Every card in [photo], top row first. [embed] is the art model (null until it's ready); [wantsArt] is true
     * when art indexes are downloaded, so a card with no readable text is worth hashing.
     */
    suspend fun read(photo: Bitmap, embed: (Bitmap) -> ArtVector?, wantsArt: Boolean): List<PhotoRead> {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val mat = Mat()
        var frame: CvCards.Frame? = null
        try {
            if (CvCards.ready) frame = cvSafe { CvCards.Frame(photo, mat) }
            val found = frame?.let { f -> cvSafe { f.findAll() } }.orEmpty()
            // No outline found (or no OpenCV): a picture of just one card is that card.
            val quads = found.ifEmpty { listOfNotNull(wholeIfCard(photo)) }
            val draw = drawer(photo, frame)
            val out = ArrayList<PhotoRead>()
            for (q in quads) out += runCatching { readCard(q, draw, recognizer, embed, wantsArt) }.getOrElse { PhotoRead(empty(), null) }
            return out
        } finally {
            frame?.let { runCatching { it.release() } }
            runCatching { mat.release() }
            runCatching { recognizer.close() }
        }
    }

    /** Draws card parts with OpenCV's warpPerspective when [cv] is there, else with [CardWarp] (a Canvas). */
    private fun drawer(src: Bitmap, cv: CvCards.Frame?): Draw {
        if (cv == null) return { q, p, w, h -> CardWarp.render(src, q, p, w, h) }
        return { q, p, w, h -> cvSafe { cv.render(q, p, w, h) } ?: CardWarp.render(src, q, p, w, h) }
    }

    private fun empty() = CardTextGuess(emptyList(), null, null, 0)

    /** The whole picture as one card, when it's card-shaped (a single card's picture). */
    private fun wholeIfCard(photo: Bitmap): FloatArray? {
        val q = CardQuad.ofBox(OcrBox(0, 0, photo.width, photo.height))
        return q.takeIf { CardOutline.cardLike(it) }
    }

    private inline fun <T> cvSafe(block: () -> T): T? = try {
        block()
    } catch (e: LinkageError) {
        CvCards.disable()
        null
    } catch (e: Exception) {
        null
    }

    /** Reads one card from its corners [quad] in the photo. */
    private suspend fun readCard(
        quad: FloatArray, draw: Draw, recognizer: TextRecognizer, embed: (Bitmap) -> ArtVector?, wantsArt: Boolean,
    ): PhotoRead {
        val cardH = CardWarp.cardHeight(quad)
        val nameScale = (NAME_TARGET_H / cardH).coerceIn(1f, 2f)
        val turns = CardQuad.uprightTurns(quad, 0)
        var guess: CardTextGuess? = null
        var turn: Int? = null
        // The name bar and footer, straightened and drawn larger: the way the camera reads a card it found.
        for (t in turns) {
            val q = CardQuad.ordered(quad, t)
            val name = CardWarp.renderScaled(q, CardWarp.NAME, cardH, nameScale, draw = draw) ?: continue
            val footer = CardWarp.renderScaled(q, CardWarp.FOOTER, cardH, FOOTER_SCALE, draw = draw)
            try {
                val nameLines = stripLines(name, CardWarp.NAME, cardH, recognizer)
                val footerLines = if (footer != null) stripLines(footer, CardWarp.FOOTER, cardH, recognizer) else emptyList()
                if (nameLines.isEmpty() && footerLines.isEmpty()) continue
                val names = CardTextParser.parse(nameLines).nameCandidates
                val g = CardTextParser.parse(nameLines + footerLines, footerAnywhere = true).copy(nameCandidates = names)
                val footerRead = g.setCode != null && g.collectorNumber != null
                val nameRead = names.firstOrNull()?.let { CardTextParser.readableScore(it) >= 4 } == true
                if (footerRead || nameRead) {
                    guess = g
                    turn = t
                    break
                }
            } finally {
                name.recycle()
                footer?.recycle()
            }
        }
        // Nothing from the strips (a small or odd card): read the whole card, both ways up, and keep the better.
        if (guess == null) {
            var best: Pair<Int, CardTextGuess>? = null
            for (t in turns) {
                val whole = wholeCard(CardQuad.ordered(quad, t), cardH, draw) ?: continue
                val g = try {
                    val (size, lines) = linesOf(whole, recognizer)
                    CardTextParser.parse(CardTextParser.insideFrame(lines, size.first, size.second, 0.98))
                } finally {
                    whole.recycle()
                }
                if (best == null || g.score > best.second.score) best = t to g
            }
            best?.takeIf { it.second.key != null }?.let { (t, g) -> guess = g; turn = t }
        }
        var g = guess ?: empty()
        val upright = turn
        // Without a full footer the art tells the printing, or finds a card with no readable text.
        if (g.setCode == null || g.collectorNumber == null) {
            if (g.key == null) {
                if (wantsArt) {
                    val arts = turns.mapNotNull { artOf(quad, it, draw, embed) }
                    if (arts.isNotEmpty()) g = g.copy(artOnly = arts.map { it.first }, artOnlyEmbeddings = arts.mapNotNull { it.second })
                }
            } else if (upright != null) {
                val art = artOf(quad, upright, draw, embed)
                g = g.copy(artHash = art?.first ?: g.artHash, artEmbedding = art?.second, symbol = symbolOf(quad, upright, draw))
            }
        }
        val thumb = runCatching { draw(CardQuad.ordered(quad, upright ?: turns.first()), WHOLE, THUMB_W, THUMB_H) }.getOrNull()
        return PhotoRead(g, thumb)
    }

    /** The whole card straightened, about [WHOLE_H] tall. */
    private fun wholeCard(q: FloatArray, cardH: Float, draw: Draw): Bitmap? {
        val s = (WHOLE_H / cardH).coerceIn(0.5f, 2f)
        return CardWarp.renderScaled(q, WHOLE, cardH, s, draw = draw)
    }

    /** OCR lines of [b] and its size. */
    private suspend fun linesOf(b: Bitmap, reader: TextRecognizer): Pair<Pair<Int, Int>, List<OcrLine>> {
        val text = reader.process(InputImage.fromBitmap(b, 0)).await()
        val lines = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left, box.top, box.right, box.bottom)
            }
        }
        return (b.width to b.height) to lines
    }

    /** OCR lines of a strip drawn from [part] of the card, placed on a card [cardH] pixels tall. */
    private suspend fun stripLines(strip: Bitmap, part: FloatArray, cardH: Float, reader: TextRecognizer): List<OcrLine> {
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

    /** The art window's hash, and its embedding when the art model is ready. */
    private fun artOf(quad: FloatArray, turn: Int, draw: Draw, embed: (Bitmap) -> ArtVector?): Pair<Long, ArtVector?>? {
        val art = draw(CardQuad.ordered(quad, turn), CardWarp.ART, ART_W, ART_H) ?: return null
        return try {
            val hash = ArtHashing.hash(art, 0, 0, art.width, art.height) ?: return null
            hash to runCatching { embed(art) }.getOrNull()
        } finally {
            art.recycle()
        }
    }

    /** The set symbol's shape, from the right end of the type line. */
    private fun symbolOf(quad: FloatArray, turn: Int, draw: Draw): SymbolPrint? {
        val b = draw(CardQuad.ordered(quad, turn), CardWarp.SYMBOL, SYMBOL_PX, SYMBOL_PX) ?: return null
        return try {
            SetSymbol.fingerprint(CardWarp.luma(b), b.width, b.height)
        } finally {
            b.recycle()
        }
    }

    /** The whole upright card. */
    private val WHOLE = floatArrayOf(0f, 0f, 1f, 1f)
    private const val NAME_TARGET_H = 700f
    private const val FOOTER_SCALE = 2f
    private const val WHOLE_H = 1100f
    private const val ART_W = 224
    private const val ART_H = 164
    private const val SYMBOL_PX = 88
    private const val THUMB_W = 126
    private const val THUMB_H = 176
}
