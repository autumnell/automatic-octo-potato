package com.mtgscanner.scan

import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.opencv.core.Mat
import java.util.concurrent.atomic.AtomicBoolean

/** Draws a part of the card straightened: (corners in the card's order, part, width, height) to a new bitmap. */
internal typealias Draw = (FloatArray, FloatArray, Int, Int) -> Bitmap?

/**
 * Reads text from camera frames and works out which way up the card is.
 *
 * The camera's own rotation only tells us how the phone is held, not how the card lies on the
 * table, so each frame is read at the orientation that worked last time first. If that doesn't
 * give a confident read, the other three quarter turns are tried and the most readable one wins.
 *
 * Once the cheap card check finds a steady frame, OpenCV (when it loads) finds the card's four corners and
 * straightens its parts; otherwise the hand-written [CardQuad] and [CardWarp] do. Names are read with the Latin
 * reader; a Japanese, Chinese or Korean reader reads the name bar only when the scan bar's language is one of
 * those (that reader then goes first) or when the Latin reader found no name there.
 */
class CardTextAnalyzer(
    private val onGuess: (CardTextGuess) -> Unit,
    private val isPaused: () -> Boolean = { false },
    private val minIntervalMs: Long = 150,
    /** Binder page mode: when this returns true, the next frame is read as a 3x3 page. */
    private val wantsPage: () -> Boolean = { false },
    private val onPage: (List<CardTextGuess>) -> Unit = {},
    /** Brightness of the middle of the picture, a few times a second, even while paused (low light, foil hint). */
    private val onLight: (FrameLight) -> Unit = {},
    /** True when art indexes are downloaded, so a card with no readable text is worth hashing. */
    private val wantsArt: () -> Boolean = { false },
    /** The art model: an embedding of a picture of artwork, or null when it isn't ready. */
    private val embed: (Bitmap) -> ArtVector? = { null },
    /** The language picked in the scan bar (a Scryfall code); a CJK one has its reader go first. */
    private val scanLang: () -> String? = { null },
    /**
     * A card was found in the guide: focus and meter there. The point (x, y) is in the frame's own pixels, which
     * are [w] by [h]. Called once per card, and again when its frames keep coming out blurry.
     */
    private val onFocus: (x: Float, y: Float, w: Int, h: Int) -> Unit = { _, _, _, _ -> },
    /** The card in view is already handled: read it less often until something moves (saves battery and heat). */
    private val holding: () -> Boolean = { false },
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** The CJK readers, each made the first time it's needed (each loads its own model). Analysis thread only. */
    private val cjkReaders = HashMap<CjkScript, TextRecognizer>()

    private fun reader(script: CjkScript): TextRecognizer = cjkReaders.getOrPut(script) {
        when (script) {
            CjkScript.JAPANESE -> TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
            CjkScript.CHINESE -> TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            CjkScript.KOREAN -> TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
        }
    }

    /**
     * The CJK reader to try next when the Latin one finds no name and no language is picked: the one that last
     * found a name, else they take turns, one per frame.
     */
    @Volatile private var nextCjk = CjkScript.JAPANESE

    /** Kept between frames for OpenCV, so a new frame-sized buffer isn't made each time. Analysis thread only. */
    private var cvMat: Mat? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicBoolean(false)
    private var lastRun = 0L

    /** Extra quarter turns (0, 90, 180, 270) that last produced a good read. */
    @Volatile private var preferredTurn = 0

    private var lastLight = 0L

    /** Samples the frame where it lies, without making a bitmap. */
    private fun measureLight(image: ImageProxy) {
        runCatching {
            val pixels = FramePixels(image)
            onLight(LightCheck.measure(image.width, image.height, pixels::rgb))
        }
    }

    /** Card check before any text reading: skips frames with no card, or while the card moves. */
    private val gate = CardGate()
    private var lastGate = 0L
    /** When the wait for a steady binder page started; 0 when not waiting. */
    private var pageWaitSince = 0L

    /** When the camera was last asked to focus on the card in view; 0 when it should be (a new card, or blur). */
    @Volatile private var focusedAt = 0L
    /** Something moved (or the guide emptied) since the last read: a new card may be there. */
    @Volatile private var movedSinceRead = true
    /** Blurry frames in a row of the card in view: two ask the camera to focus again. */
    private var blurRun = 0

    /** Blurry frames of the card in view are skipped rather than misread. Analysis coroutine only. */
    private val sharp = StripQuality.Tracker()

    /** The last whole-card read, by the name read on the name bar: a card held still is read whole only once. */
    @Volatile private var lastWhole: Pair<String, CardTextGuess>? = null

    /** Reused for every frame, so a 3.7 MB bitmap isn't made four times a second. */
    private var frameBitmap: Bitmap? = null

    /**
     * Where to read in the frame (sensor pixels), and whether that is a found card or just the guide.
     * [quad] is the card's corners (sensor pixels, in the frame's own order) when an outline was found.
     */
    private class ReadArea(val box: OcrBox, val isCard: Boolean, val outline: OcrBox? = null, val quad: FloatArray? = null)

    /** What reading the name bar and footer strips of the straightened card gave. */
    private class StripRead(
        val guess: CardTextGuess,
        /** The clockwise turn of the frame that stood the card upright, when the strips could be read. */
        val turn: Int?,
        val glare: Boolean,
        /** Clearly blurrier than recent frames of this card: not read. */
        val blurry: Boolean = false,
    )

    /** The last art embeddings worked out, by the art hash they came with: a card held still isn't embedded again. */
    private val embedCache = ArrayDeque<Pair<Long, ArtVector>>()

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLight >= 200) {
            lastLight = now
            measureLight(image)
        }
        if (isPaused()) {
            gate.reset()
            pageWaitSince = 0L
        }
        if (isPaused() || busy.get()) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val page = wantsPage()
        var area: ReadArea? = null
        if (page) {
            // "Capture page": wait for the shake of the tap to settle and a steady frame (at most a few seconds).
            if (now - lastGate < GATE_INTERVAL_MS) {
                image.close()
                return
            }
            lastGate = now
            if (pageWaitSince == 0L) pageWaitSince = now
            val state = runCatching { checkCard(image, rotation) }.getOrNull()?.first?.state
            val steady = state != GateState.MOVING && state != GateState.SETTLING
            val waited = now - pageWaitSince
            if (waited < PAGE_MIN_WAIT_MS || (!steady && waited < PAGE_MAX_WAIT_MS)) {
                image.close()
                return
            }
            pageWaitSince = 0L
        } else {
            // The card check is cheap; text reading only runs on a steady frame with something in the guide.
            if (now - lastGate < GATE_INTERVAL_MS) {
                image.close()
                return
            }
            lastGate = now
            val result = runCatching { checkCard(image, rotation) }.getOrNull()
            if (result?.first?.state == GateState.EMPTY) {
                focusedAt = 0L
                sharp.reset()
            }
            if (result == null || !result.first.runOcr) movedSinceRead = true
            // A card in the guide: have the camera focus on it, rather than wherever its own focus settles.
            result?.second?.outline?.let { o ->
                if (focusedAt == 0L) {
                    focusedAt = now
                    runCatching { onFocus((o.left + o.right) / 2f, (o.top + o.bottom) / 2f, image.width, image.height) }
                }
            }
            if (result != null && !result.first.runOcr) {
                if (result.first.state == GateState.EMPTY && now - lastRun >= minIntervalMs) {
                    // Nothing in the guide: tell the scanner, so it knows the card has left.
                    lastRun = now
                    image.close()
                    lastWhole = null
                    scope.launch { runCatching { onGuess(CardTextGuess(emptyList(), null, null, 0, guideEmpty = true)) } }
                    return
                }
                image.close()
                return
            }
            area = result?.second
        }
        // A handled card held still is read now and then, to notice when it changes; anything moving is read at once.
        val interval = if (!page && !movedSinceRead && holding()) HOLD_INTERVAL_MS else minIntervalMs
        if (now - lastRun < interval) {
            image.close()
            return
        }
        busy.set(true)
        lastRun = now
        movedSinceRead = false
        // Only frames that passed the card check are turned into a picture; the camera hands over YUV. With OpenCV,
        // the frame goes straight into it and a Bitmap is made only if a read needs one.
        val direct: CvCards.Frame? = if (page) null else directFrame(image)
        val given: Bitmap? = if (direct != null) null else try {
            frameToBitmap(image)
        } catch (t: Throwable) {
            busy.set(false)
            image.close()
            return
        }
        image.close()
        val reused = given != null && given === frameBitmap

        scope.launch {
            var crop: Bitmap? = null
            var small: Bitmap? = null
            var cv: CvCards.Frame? = direct
            var made: Bitmap? = null
            // The whole frame as a Bitmap: the one handed over, or made from OpenCV's copy when first needed.
            fun picture(): Bitmap = given ?: made ?: cv!!.toBitmap().also { made = it }
            val frameW = given?.width ?: direct!!.width
            val frameH = given?.height ?: direct!!.height
            try {
                if (page) onPage(readPage(picture(), rotation))
                else {
                    val guide = guideArea(frameW, frameH, rotation)
                    var a = area ?: ReadArea(guide, isCard = false)
                    // OpenCV finds the card's corners more exactly, and even where the card check saw no clear outline.
                    if (cv == null && given != null) cv = cvFrame(given)
                    val found = cv?.let { f -> CardParts.cvSafe { f.findQuad(guide) } }
                    if (found != null) {
                        val bounds = CvCards.boundsOf(found)
                        a = ReadArea(FrameGeometry.grow(bounds, 0.15, frameW, frameH), isCard = true, outline = bounds, quad = found)
                    }
                    val draw = CardParts.drawer({ picture() }, cv)
                    val quad = a.quad
                    val cjk = CjkNames.scriptFor(scanLang())
                    // A found card: read just its name bar and footer, straightened and sharp.
                    val strips = if (quad != null) runCatching { readStrips(quad, rotation, draw, cjk) }.getOrNull() else null
                    // Out of focus: reading it would only give a misread. The next frame will do.
                    if (strips?.blurry == true) return@launch
                    val stripGuess = strips?.guess
                    val stripName = stripGuess?.nameKey
                    val seenWhole = lastWhole?.takeIf { stripName != null && it.first == stripName }?.second
                    var guess = if (stripGuess != null && stripGuess.setCode != null && stripGuess.collectorNumber != null) {
                        stripGuess
                    } else if (stripGuess != null && seenWhole != null) {
                        // Same name as the card read whole a moment ago: its rules text and footer haven't changed.
                        CardTextParser.merge(stripGuess, seenWhole)
                    } else {
                        // No complete footer from the strips: read the whole card too, as before (its rules
                        // text also tells the language), and take the best of both.
                        val b = a.box
                        val bitmap = picture()
                        val c = if (b.width >= 32 && b.height >= 32 && (b.width < bitmap.width || b.height < bitmap.height)) {
                            Bitmap.createBitmap(bitmap, b.left, b.top, b.width, b.height)
                        } else bitmap
                        if (c !== bitmap) crop = c
                        // A big frame is read at about the size the reader worked with before, to keep it quick.
                        val readable = shrunk(c)
                        if (readable !== c) small = readable
                        // Cropped to a found card, everything read is on it; cropped to the guide, keep to the guide.
                        // A CJK name already read from the name bar needs no CJK read of the whole card.
                        val wholeCjk = cjk.takeIf { stripGuess?.printedName == null }
                        val full = readBestOrientation(readable, rotation, if (a.isCard) 1.0 else GUIDE_IN_CROP, wholeCjk)
                        lastWhole = stripName?.let { it to full }
                        if (stripGuess != null) CardTextParser.merge(stripGuess, full) else full
                    }
                    if (strips != null) guess = guess.copy(glare = strips.glare)
                    // The straightened card's art, and its set symbol, tell printings (or a textless card) apart.
                    if (quad != null) guess = runCatching { withArt(quad, rotation, strips?.turn, guess, draw) }.getOrDefault(guess)
                    onGuess(guess)
                }
            } catch (_: Throwable) {
                // Skip this frame; the next one will try again.
            } finally {
                cv?.let { runCatching { it.release() } }
                small?.recycle()
                crop?.recycle()
                made?.recycle()
                if (given != null && !reused) given.recycle()
                busy.set(false)
            }
        }
    }

    /** RGBA frames are copied into one reused bitmap; YUV (what the camera gives) is converted into a new one. */
    private fun frameToBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes.singleOrNull()
        if (plane != null && image.format == PixelFormat.RGBA_8888 && plane.pixelStride == 4 && plane.rowStride == image.width * 4) {
            val bmp = frameBitmap?.takeIf { it.width == image.width && it.height == image.height && !it.isRecycled }
                ?: Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888).also { frameBitmap = it }
            val buf = plane.buffer
            buf.rewind()
            bmp.copyPixelsFromBuffer(buf)
            buf.rewind()
            return bmp
        }
        return image.toBitmap()
    }

    /** The camera frame converted straight into OpenCV, or null when OpenCV isn't loaded or the layout is unusual. */
    private fun directFrame(image: ImageProxy): CvCards.Frame? {
        if (!CvCards.ready) return null
        return CardParts.cvSafe {
            val m = cvMat ?: Mat().also { cvMat = it }
            if (CvCards.yuvInto(image, m)) CvCards.Frame(null, m) else null
        }
    }

    /** The frame copied into OpenCV, or null when OpenCV isn't loaded or fails. */
    private fun cvFrame(bitmap: Bitmap): CvCards.Frame? {
        if (!CvCards.ready) return null
        return CardParts.cvSafe {
            val m = cvMat ?: Mat().also { cvMat = it }
            CvCards.Frame(bitmap, m)
        }
    }

    /** The guide area in sensor pixels. */
    private fun guideArea(sensorW: Int, sensorH: Int, rotation: Int): OcrBox {
        val sideways = rotation == 90 || rotation == 270
        val uw = if (sideways) sensorH else sensorW
        val uh = if (sideways) sensorW else sensorH
        return FrameGeometry.toSensor(FrameGeometry.guideCrop(uw, uh), rotation, sensorW, sensorH)
    }

    /**
     * Samples the guide area of the frame into a small grayscale grid (no bitmap; from the Y plane of a YUV
     * frame) and runs the card check. Returns what it decided and, when a card was found, the area to read.
     */
    private fun checkCard(image: ImageProxy, rotation: Int): Pair<GateResult, ReadArea?> {
        val pixels = FramePixels(image)
        val region = guideArea(image.width, image.height, rotation)
        val step = maxOf(1, maxOf(region.width, region.height) / GRID)
        val gw = region.width / step
        val gh = region.height / step
        val luma = IntArray(gw * gh)
        fun at(x: Int, y: Int): Int = pixels.luma(x, y)
        val maxX = image.width - 1
        val maxY = image.height - 1
        for (gy in 0 until gh) for (gx in 0 until gw) {
            val x = minOf(region.left + gx * step, maxX - 1)
            val y = minOf(region.top + gy * step, maxY - 1)
            // A 2x2 average evens out sensor noise.
            luma[gy * gw + gx] = (at(x, y) + at(x + 1, y) + at(x, y + 1) + at(x + 1, y + 1)) / 4
        }
        val res = gate.check(luma, gw, gh)
        val card = res.card ?: return res to null
        val box = OcrBox(region.left + card.left * step, region.top + card.top * step, region.left + card.right * step, region.top + card.bottom * step)
        // The outline's corners, traced along each edge (a card held at an angle isn't a neat rectangle).
        val corners = CardQuad.corners(luma, gw, gh, card) ?: CardQuad.ofBox(card)
        val quad = CardQuad.scaled(corners, step, region.left, region.top)
        // Grown well past the outline found, in case it was an inner frame line (art, text box) rather than the card edge.
        return res to ReadArea(FrameGeometry.grow(box, 0.15, image.width, image.height), isCard = true, outline = box, quad = quad)
    }

    /** [b] scaled down to [MAX_READ] pixels on its long side, or [b] itself when it's no bigger. */
    private fun shrunk(b: Bitmap): Bitmap {
        val long = maxOf(b.width, b.height)
        if (long <= MAX_READ) return b
        val s = MAX_READ.toFloat() / long
        return Bitmap.createScaledBitmap(b, (b.width * s).toInt().coerceAtLeast(1), (b.height * s).toInt().coerceAtLeast(1), true)
    }

    private fun norm(turn: Int) = ((turn % 360) + 360) % 360

    /**
     * Reads the name bar and the footer of the card straightened from [quad], each drawn from the full frame:
     * the footer at twice its size, so its small print is sharp. Tries the way up that worked last, then the other
     * the outline allows. Also checks the two strips for glare.
     *
     * With [cjk] (the scan bar's language is Japanese, Chinese or Korean) the name bar is read by that reader,
     * which reads Latin letters too. Otherwise, when the Latin reader finds no name and no complete footer, one
     * CJK reader reads the name bar (see [nextCjk]).
     */
    private suspend fun readStrips(quad: FloatArray, rotation: Int, draw: Draw, cjk: CjkScript?): StripRead {
        val cardH = CardWarp.cardHeight(quad)
        if (cardH < 120f) return StripRead(CardTextGuess(emptyList(), null, null, 0), null, false)
        var glare = false
        var year: Int? = null
        val turns = CardQuad.uprightTurns(quad, rotation + preferredTurn)
        // What each way up gave, for the CJK try after the loop.
        val tried = ArrayList<Pair<Int, CardTextGuess>>()
        for ((i, turn) in turns.withIndex()) {
            val q = CardQuad.ordered(quad, turn)
            val name = CardParts.name(q, cardH, draw) ?: continue
            val footer = CardParts.footer(q, cardH, draw)
            var nameRead: Bitmap? = null
            var footerRead: Bitmap? = null
            try {
                if (i == 0) {
                    glare = glareIn(name, CardWarp.NAME, CardWarp.NAME_GLARE) ||
                        (footer != null && glareIn(footer, CardWarp.FOOTER, CardWarp.FOOTER_GLARE))
                    // The footer's small print shows blur first; without one, the name bar.
                    val s = runCatching { CardParts.sharpness(footer ?: name) }.getOrNull()
                    val blurry = s != null && sharp.blurry(s)
                    if (blurry) { blurRun++; if (blurRun >= 2) { blurRun = 0; focusedAt = 0L } } else blurRun = 0
                    if (blurry) return StripRead(CardTextGuess(emptyList(), null, null, 0), null, glare, blurry = true)
                }
                // Low-contrast strips (foils, dark frames, dim light) are read with their contrast stretched.
                nameRead = runCatching { CardParts.stretched(name) }.getOrNull()
                footerRead = footer?.let { f -> runCatching { CardParts.stretched(f) }.getOrNull() }
                // Both strips in one read when the same reader reads both; a CJK name bar has its own reader.
                val (nameLines, footerLines) = if (footer != null && cjk == null) {
                    CardParts.stackedLines(nameRead ?: name, footerRead ?: footer, cardH, recognizer)
                } else {
                    stripLines(nameRead ?: name, CardWarp.NAME, cardH, if (cjk != null) reader(cjk) else recognizer) to
                        (if (footer != null) stripLines(footerRead ?: footer, CardWarp.FOOTER, cardH) else emptyList())
                }
                if (nameLines.isEmpty() && footerLines.isEmpty()) continue
                // Names only from the name bar: the footer's artist line is not a card name.
                val names = CardTextParser.parse(nameLines).nameCandidates
                var g = CardTextParser.parse(nameLines + footerLines, footerAnywhere = true).copy(nameCandidates = names)
                if (year == null) year = g.year
                val footerOk = g.setCode != null && g.collectorNumber != null
                val nameOk = names.firstOrNull()?.let { CardTextParser.readableScore(it) >= 4 } == true
                val printed = if (cjk != null && !nameOk) CjkNames.names(nameLines).firstOrNull() else null
                if (printed != null) g = withPrinted(g, printed, cjk!!, footerLines)
                if (footerOk || nameOk || printed != null) {
                    preferredTurn = norm(turn - rotation)
                    return StripRead(g, turn, glare)
                }
                tried += turn to g
            } finally {
                name.recycle()
                footer?.recycle()
                nameRead?.recycle()
                footerRead?.recycle()
            }
        }
        // No name in Latin letters either way up: maybe it's printed in Japanese, Chinese or Korean.
        if (cjk == null) {
            val script = nextCjk
            for ((turn, latin) in tried) {
                val q = CardQuad.ordered(quad, turn)
                val name = CardParts.name(q, cardH, draw) ?: continue
                val lines = try {
                    stripLines(name, CardWarp.NAME, cardH, reader(script))
                } finally {
                    name.recycle()
                }
                val printed = CjkNames.names(lines).firstOrNull() ?: continue
                preferredTurn = norm(turn - rotation)
                // The footer was read in Latin letters this way up; its language code still counts.
                val footerLang = latin.text.lineSequence().firstNotNullOfOrNull { CardTextParser.findLangCode(it) }
                    ?.let { LanguageDetector.fromFooter(it) }
                val lang = CjkNames.langOf(script, printed, footerLang, scanLang())
                return StripRead(latin.copy(nameCandidates = emptyList(), printedName = printed, lang = lang), turn, glare)
            }
            if (tried.isNotEmpty()) nextCjk = CjkNames.next(script)
        }
        return StripRead(CardTextGuess(emptyList(), null, null, 0, year = year), null, glare)
    }

    /** [g] with the CJK name [printed] read by [script], and the language that tells. */
    private fun withPrinted(g: CardTextGuess, printed: String, script: CjkScript, footerLines: List<OcrLine>): CardTextGuess {
        val footerLang = footerLines.firstNotNullOfOrNull { CardTextParser.findLangCode(it.text) }?.let { LanguageDetector.fromFooter(it) }
        return g.copy(nameCandidates = emptyList(), printedName = printed, lang = CjkNames.langOf(script, printed, footerLang, scanLang()))
    }

    /** OCR lines of a strip drawn from [part] of the card, placed on a card [cardH] pixels tall. */
    private suspend fun stripLines(strip: Bitmap, part: FloatArray, cardH: Float, reader: TextRecognizer = recognizer): List<OcrLine> =
        CardParts.stripLines(strip, part, cardH, reader)

    /** Glare over [sub] (fractions of the card) of a strip drawn from [part]. */
    private fun glareIn(strip: Bitmap, part: FloatArray, sub: FloatArray): Boolean {
        fun fx(u: Float) = ((u - part[0]) / (part[2] - part[0]) * strip.width).toInt().coerceIn(0, strip.width)
        fun fy(v: Float) = ((v - part[1]) / (part[3] - part[1]) * strip.height).toInt().coerceIn(0, strip.height)
        val l = fx(sub[0])
        val t = fy(sub[1])
        val w = fx(sub[2]) - l
        val h = fy(sub[3]) - t
        if (w < 12 || h < 3) return false
        val px = IntArray(w * h)
        strip.getPixels(px, 0, w, l, t, w, h)
        return LightCheck.glare(px, w, h)
    }

    /**
     * Adds what the straightened card's picture tells: with no text read, its art hashed (and embedded) both ways
     * up the outline allows, for the art indexes; with a name but no footer number, its art and set symbol, to
     * pick the printing. [stripTurn] is the way up the strips were read at.
     */
    private fun withArt(quad: FloatArray, rotation: Int, stripTurn: Int?, g: CardTextGuess, draw: Draw): CardTextGuess {
        // A full footer names the printing. A number with no set code (2003-2019 cards) still needs the art.
        if (g.collectorNumber != null && g.setCode != null) return g
        if (g.nameCandidates.isEmpty() && g.printedName == null) {
            if (!wantsArt()) return g
            val arts = CardQuad.uprightTurns(quad, rotation + preferredTurn).mapNotNull { artOf(quad, it, draw) }
            if (arts.isEmpty()) return g
            return g.copy(artOnly = arts.map { it.first }, artOnlyEmbeddings = arts.mapNotNull { it.second })
        }
        // Which way up the card is: as the strips were read, or as the whole-card read settled, if the outline agrees.
        val turn = stripTurn ?: norm(rotation + preferredTurn).takeIf { CardQuad.uprightTurns(quad, it).first() == it } ?: return g
        val art = artOf(quad, turn, draw)
        val symbol = symbolOf(quad, turn, draw)
        return g.copy(artHash = art?.first ?: g.artHash, artEmbedding = art?.second, symbol = symbol)
    }

    /** The art window of the straightened card: its hash, and its embedding when the art model is ready. */
    private fun artOf(quad: FloatArray, turn: Int, draw: Draw): Pair<Long, ArtVector?>? = CardParts.art(quad, turn, draw, ::embedCached)

    /** The set symbol's shape, from the right end of the type line. */
    private fun symbolOf(quad: FloatArray, turn: Int, draw: Draw): SymbolPrint? = CardParts.symbol(quad, turn, draw)

    /** An embedding of [art], reused while the same art (by its [hash]) stays in view. */
    private fun embedCached(hash: Long, art: Bitmap): ArtVector? {
        embedCache.firstOrNull { ImageHash.distance(it.first, hash) <= SAME_ART_BITS }?.let { return it.second }
        val e = embed(art) ?: return null
        embedCache.addFirst(hash to e)
        while (embedCache.size > 4) embedCache.removeLast()
        return e
    }

    /**
     * The whole card read at each quarter turn until one is confident. With [cjk] (a CJK language picked in the
     * scan bar) and no name or number read, that reader reads the top of the card at the best turn for a name.
     */
    private suspend fun readBestOrientation(bitmap: Bitmap, cameraRotation: Int, frame: Double, cjk: CjkScript? = null): CardTextGuess {
        val order = listOf(preferredTurn) + listOf(0, 90, 180, 270).filter { it != preferredTurn }
        var best: Pair<Int, CardTextGuess>? = null
        for (turn in order) {
            val guess = read(bitmap, (cameraRotation + turn) % 360, frame)
            if (best == null || guess.score > best.second.score) best = turn to guess
            // Good enough: a readable name, or the exact printing from the footer.
            if (isConfident(guess)) break
            // No text at all: other turns won't find any either.
            if (guess.text.isEmpty()) break
        }
        var (turn, guess) = best!!
        if (guess.score > 0) preferredTurn = turn
        if (cjk != null && guess.collectorNumber == null && guess.nameCandidates.none { CardTextParser.readableScore(it) >= 4 }) {
            readCjkName(bitmap, (cameraRotation + turn) % 360, frame, cjk)?.let { printed ->
                guess = guess.copy(
                    nameCandidates = emptyList(), printedName = printed,
                    lang = CjkNames.langOf(cjk, printed, guess.lang?.takeIf { it in CjkNames.LANGS && it != "zhs" }, scanLang()),
                )
            }
        }
        // Without a readable footer, the artwork is what tells printings apart.
        val art = guess.artBox
        if (art != null && (guess.collectorNumber == null || guess.setCode == null)) {
            val hash = runCatching { hashArt(bitmap, (cameraRotation + turn) % 360, art) }.getOrNull()
            if (hash != null) return guess.copy(artHash = hash)
        }
        return guess
    }

    /** Reads a whole binder page, picking the way up that gives the most readable text. */
    private suspend fun readPage(bitmap: Bitmap, cameraRotation: Int): List<CardTextGuess> {
        val order = listOf(preferredTurn) + listOf(0, 90, 180, 270).filter { it != preferredTurn }
        var best: Pair<Int, Pair<Pair<Int, Int>, List<OcrLine>>>? = null
        var bestScore = -1
        for (turn in order) {
            val read = readLines(bitmap, (cameraRotation + turn) % 360)
            val score = read.second.sumOf { CardTextParser.readableScore(it.text) }
            if (score > bestScore) { bestScore = score; best = turn to read }
            // Plenty of readable text: this is the right way up.
            if (score >= 120) break
        }
        val (turn, read) = best!!
        if (bestScore > 0) preferredTurn = turn
        val (size, lines) = read
        return CardTextParser.parseGrid(lines, frame = CardTextParser.pageFrame(size.first, size.second))
    }

    /** Crops the art first, then turns only that small piece upright. */
    private fun hashArt(bitmap: Bitmap, rotation: Int, box: OcrBox): Long? {
        val s = FrameGeometry.toSensor(box, rotation, bitmap.width, bitmap.height)
        val l = s.left.coerceIn(0, bitmap.width - 1)
        val t = s.top.coerceIn(0, bitmap.height - 1)
        val w = s.right.coerceAtMost(bitmap.width) - l
        val h = s.bottom.coerceAtMost(bitmap.height) - t
        if (w < 9 || h < 8) return null
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        val upright = Bitmap.createBitmap(bitmap, l, t, w, h, m, true)
        return try {
            ArtHashing.hash(upright, 0, 0, upright.width, upright.height)
        } finally {
            if (upright !== bitmap) upright.recycle()
        }
    }

    /** A CJK name from the top quarter of the card's text, read by [script]'s reader at [rotation]. */
    private suspend fun readCjkName(bitmap: Bitmap, rotation: Int, frame: Double, script: CjkScript): String? {
        val (size, all) = readLines(bitmap, rotation, reader(script))
        val lines = CardTextParser.insideFrame(all, size.first, size.second, frame)
        if (lines.isEmpty()) return null
        val top = lines.minOf { it.top }
        val span = (lines.maxOf { it.bottom } - top).coerceAtLeast(1)
        return CjkNames.names(lines.filter { it.top - top <= span * 0.25 }).firstOrNull()
    }

    private fun isConfident(g: CardTextGuess): Boolean =
        (g.setCode != null && g.collectorNumber != null) ||
            (g.nameCandidates.isNotEmpty() && g.score >= 25)

    private suspend fun read(bitmap: Bitmap, rotation: Int, frame: Double): CardTextGuess {
        val (size, lines) = readLines(bitmap, rotation)
        // Only read text inside the frame, so cards around the edges don't get mixed in.
        return CardTextParser.parse(CardTextParser.insideFrame(lines, size.first, size.second, frame))
    }

    /** OCR lines at [rotation], with the upright image's width and height. */
    private suspend fun readLines(bitmap: Bitmap, rotation: Int, reader: TextRecognizer = recognizer): Pair<Pair<Int, Int>, List<OcrLine>> {
        val text: Text = reader.process(InputImage.fromBitmap(bitmap, rotation)).await()
        val sideways = rotation == 90 || rotation == 270
        val w = if (sideways) bitmap.height else bitmap.width
        val h = if (sideways) bitmap.width else bitmap.height
        val lines = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left, box.top, box.right, box.bottom)
            }
        }
        return (w to h) to lines
    }

    fun close() {
        scope.cancel()
        recognizer.close()
        cjkReaders.values.forEach { runCatching { it.close() } }
        // The OpenCV buffer is left to the garbage collector: a frame still being read may be using it.
    }

    private companion object {
        /** How often the card check runs. Two steady checks in a row start a read. */
        const val GATE_INTERVAL_MS = 66L
        /** How often a handled card held still is read again. */
        const val HOLD_INTERVAL_MS = 700L
        /** "Capture page" reads no sooner than this after the tap, and waits at most this long for a steady frame. */
        const val PAGE_MIN_WAIT_MS = 300L
        const val PAGE_MAX_WAIT_MS = 2_500L
        /** Grid size for the card check, along the longer side. */
        const val GRID = 160
        /** A little more than the guide's share of the guide crop ([FrameGeometry.guideCrop] adds 20%). */
        const val GUIDE_IN_CROP = 0.9
        /** Longest side, in pixels, of a whole-card read. */
        const val MAX_READ = 1024
        /** Art hashes this close are the same art held still: its embedding is reused. */
        const val SAME_ART_BITS = 4
    }
}

/**
 * Reads single pixels of a camera frame in place: YUV_420_888 (what the camera gives) or RGBA_8888. The card
 * check and the light check sample only a sparse grid, so the frame is never converted as a whole for them.
 */
private class FramePixels(image: ImageProxy) {
    private val planes = image.planes
    private val yuv = image.format == ImageFormat.YUV_420_888 && planes.size >= 3
    private val buf = planes[0].buffer
    private val row = planes[0].rowStride
    private val px = planes[0].pixelStride
    private val uBuf = if (yuv) planes[1].buffer else buf
    private val uRow = if (yuv) planes[1].rowStride else 0
    private val uPx = if (yuv) planes[1].pixelStride else 0
    private val vBuf = if (yuv) planes[2].buffer else buf
    private val vRow = if (yuv) planes[2].rowStride else 0
    private val vPx = if (yuv) planes[2].pixelStride else 0

    /** Brightness 0..255 at (x, y). */
    fun luma(x: Int, y: Int): Int {
        val i = y * row + x * px
        if (yuv) return Yuv.luma(buf.get(i).toInt() and 0xFF)
        val r = buf.get(i).toInt() and 0xFF
        val g = buf.get(i + 1).toInt() and 0xFF
        val b = buf.get(i + 2).toInt() and 0xFF
        return (r * 77 + g * 150 + b * 29) shr 8
    }

    /** 0xRRGGBB at (x, y). U and V are at half resolution. */
    fun rgb(x: Int, y: Int): Int {
        val i = y * row + x * px
        if (yuv) {
            val cx = x shr 1
            val cy = y shr 1
            val u = uBuf.get(cy * uRow + cx * uPx).toInt() and 0xFF
            val v = vBuf.get(cy * vRow + cx * vPx).toInt() and 0xFF
            return Yuv.rgb(buf.get(i).toInt() and 0xFF, u, v)
        }
        return ((buf.get(i).toInt() and 0xFF) shl 16) or ((buf.get(i + 1).toInt() and 0xFF) shl 8) or (buf.get(i + 2).toInt() and 0xFF)
    }
}
