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
    /** Loose cards on the table: when this returns true, the next steady frame goes to [onSpread], upright. */
    private val wantsSpread: () -> Boolean = { false },
    private val onSpread: (Bitmap) -> Unit = {},
    /** The set lock (a set code), if on: a collector number alone then names the card, so the name isn't needed. */
    private val setLock: () -> String? = { null },
    /** The centering check is on: each read card's border widths are measured ([CenteringCheck]). */
    private val wantsCentering: () -> Boolean = { false },
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    init {
        // The reader loads its model on first use, which made the first card after opening Scan slow. Reading a
        // small made-up picture now does that while the camera starts.
        runCatching {
            val bmp = android.graphics.Bitmap.createBitmap(240, 60, android.graphics.Bitmap.Config.ARGB_8888)
            android.graphics.Canvas(bmp).apply {
                drawColor(android.graphics.Color.WHITE)
                drawText("Llanowar Elves", 8f, 40f, android.graphics.Paint().apply { textSize = 28f; color = android.graphics.Color.BLACK })
            }
            recognizer.process(com.google.mlkit.vision.common.InputImage.fromBitmap(bmp, 0)).addOnCompleteListener { bmp.recycle() }
        }
    }

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
    // One steady frame is enough: a blurry read is still caught by the strip sharpness check.
    private val gate = CardGate(stillFrames = 1)
    private var lastGate = 0L
    /** When the wait for a steady binder page started; 0 when not waiting. */
    private var pageWaitSince = 0L

    /** When the camera was last asked to focus on the card in view; 0 when it should be (a new card, or blur). */
    @Volatile private var focusedAt = 0L
    /** Something moved (or the guide emptied) since the last read: a new card may be there. */
    @Volatile private var movedSinceRead = true
    /** When something last moved in the guide, to read a card held still for long even less often. */
    private var lastMovedAt = 0L
    /** A coarse picture of the card at the last read ([CardLook]): when it changes, a new card is there. */
    private var readLook: IntArray? = null
    /** Card checks in a row whose picture differs from [readLook]. */
    private var lookChanged = 0
    /** Blurry frames in a row of the card in view: two ask the camera to focus again. */
    private var blurRun = 0

    /** Blurry frames of the card in view are skipped rather than misread. Analysis coroutine only. */
    private val sharp = StripQuality.Tracker()

    /** When something steady but with no card edges came into the guide (0: not now), and when that was last said. */
    @Volatile private var noShapeSince = 0L
    @Volatile private var noShapeTold = 0L
    private var lastUnsureCheck = 0L

    /** No card edges in this frame: after a while with none, tells the scanner, about once a second. */
    private fun noShape(now: Long) {
        if (noShapeSince == 0L) { noShapeSince = now; return }
        if (now - noShapeSince < NO_EDGES_MS || now - noShapeTold < 1000) return
        noShapeTold = now
        scope.launch { runCatching { onGuess(CardTextGuess(emptyList(), null, null, 0, noEdges = true)) } }
    }

    /** Reused for every frame, so a 3.7 MB bitmap isn't made four times a second. */
    private var frameBitmap: Bitmap? = null

    /**
     * Where to read in the frame (sensor pixels), and whether that is a found card or just the guide.
     * [quad] is the card's corners (sensor pixels, in the frame's own order) when an outline was found.
     */
    private class ReadArea(val box: OcrBox, val isCard: Boolean, val outline: OcrBox? = null, val quad: FloatArray? = null, val look: IntArray? = null)

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

    /**
     * The card in view followed from frame to frame. Each track keeps the card fingerprints worked out for it (with
     * the hash of the picture they came from), so a card held still or moved around isn't fingerprinted again.
     */
    private val tracks = CardTracks<List<Pair<Long, FloatArray>>>(minOverlap = 0.6f, maxMissed = 6)
    /** When the corner model last looked for a card the outline finder missed. */
    private var lastPose = 0L

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
        val spread = !page && wantsSpread()
        var area: ReadArea? = null
        // A held card that hasn't moved is checked less often too.
        val still = !movedSinceRead && holding()
        if (page || spread) {
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
            if (now - lastGate < (if (still) HOLD_GATE_MS else GATE_INTERVAL_MS)) {
                image.close()
                return
            }
            lastGate = now
            // Steady but no card outline seen: only OpenCV's closer look can still find a card, so without it
            // nothing is read. Text is only read from something card-shaped. OpenCV's look is a full picture
            // conversion, so it's done at most every [UNSURE_CHECK_MS], not on every read.
            val checked = runCatching { checkCard(image, rotation) }.getOrNull()
            val unsure = checked?.first?.state == GateState.UNSURE
            if (unsure) noShape(now) else if (checked?.first?.state != GateState.MOVING && checked?.first?.state != GateState.SETTLING) noShapeSince = 0L
            val skipUnsure = unsure && (!CvCards.ready || now - lastUnsureCheck < UNSURE_CHECK_MS)
            if (unsure && !skipUnsure) lastUnsureCheck = now
            val result = checked?.let { r -> if (skipUnsure) GateResult(GateState.SETTLING) to null else r }
            if (result?.first?.state == GateState.EMPTY) {
                focusedAt = 0L
                sharp.reset()
            }
            if (result == null || !result.first.runOcr) { movedSinceRead = true; lastMovedAt = now }
            // The card in view looks different from when it was last read (another card slid on top, say): read now.
            val look = result?.second?.look
            val before = readLook
            if (look != null && before != null && CardLook.differs(before, look)) {
                lookChanged++
                if (lookChanged >= 2) { movedSinceRead = true; lastMovedAt = now; lookChanged = 0 }
            } else lookChanged = 0
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
                    scope.launch { runCatching { onGuess(CardTextGuess(emptyList(), null, null, 0, guideEmpty = true)) } }
                    return
                }
                image.close()
                return
            }
            area = result?.second
        }
        // A handled card held still is read now and then, to notice when it changes; anything moving is read at once.
        // Still for a few seconds (set down, or between piles), it's read even less.
        val interval = when {
            page || spread || movedSinceRead || !holding() -> minIntervalMs
            now - lastMovedAt > LONG_STILL_MS -> LONG_HOLD_INTERVAL_MS
            else -> HOLD_INTERVAL_MS
        }
        if (now - lastRun < interval) {
            image.close()
            return
        }
        busy.set(true)
        lastRun = now
        if (movedSinceRead) lastMovedAt = now
        movedSinceRead = false
        readLook = area?.look
        lookChanged = 0
        // Only frames that passed the card check are turned into a picture; the camera hands over YUV. With OpenCV,
        // the frame goes straight into it and a Bitmap is made only if a read needs one.
        val direct: CvCards.Frame? = if (page || spread) null else directFrame(image)
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
                if (spread) onSpread(upright(picture(), rotation))
                else if (page) onPage(readPage(picture(), rotation))
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
                    // No outline found: the corner model may still see the card (borderless, a mat of its colour, glare).
                    if (a.quad == null) poseQuad(picture(), guide)?.let { q ->
                        val bounds = CvCards.boundsOf(q)
                        a = ReadArea(FrameGeometry.grow(bounds, 0.15, frameW, frameH), isCard = true, outline = bounds, quad = q)
                    }
                    // Nothing card-shaped (63:88, upright or sideways) in the guide: no text is read from it.
                    var quad = a.quad ?: run { noShape(SystemClock.elapsedRealtime()); tracks.update(emptyList()); return@launch }
                    noShapeSince = 0L
                    val track = tracks.update(listOf(quad))[0]
                    val draw = CardParts.drawer({ picture() }, cv)
                    val cjk = CjkNames.scriptFor(scanLang())?.takeIf { ForeignReading.ready }
                    // A found card: read just its name bar and footer, straightened and sharp.
                    val first = runCatching { readStrips(quad, rotation, draw, cjk, track) }.getOrNull()
                    // Nothing read: the outline may be a frame inside the card (showcase and borderless frames draw
                    // card-shaped panels), cutting off the name. The corner model sees the whole card: read that.
                    val outer = if (found != null && first?.blurry != true && first?.guess?.let(::hasText) != true) {
                        poseQuad(picture(), guide)?.takeIf { CardOutline.encloses(it, quad) }
                    } else null
                    val wider = outer?.let { o -> runCatching { readStrips(o, rotation, draw, cjk, track) }.getOrNull() }
                    if (wider != null && outer != null) quad = outer
                    val strips = wider ?: first
                    // Out of focus: reading it would only give a misread. The next frame will do.
                    if (strips?.blurry == true) return@launch
                    val stripGuess = strips?.guess?.let(::locked)
                    // A found card is read by its name bar and footer only, not its rules text. The whole card is
                    // read only when that gave nothing (the outline was a little off); the parser still takes
                    // names from the top and the footer from the bottom of what it reads.
                    val stripsRead = stripGuess?.let(::hasText) == true
                    var guess = if (stripsRead) stripGuess!! else {
                        val b = a.box
                        val bitmap = picture()
                        val c = if (b.width >= 32 && b.height >= 32 && (b.width < bitmap.width || b.height < bitmap.height)) {
                            Bitmap.createBitmap(bitmap, b.left, b.top, b.width, b.height)
                        } else bitmap
                        if (c !== bitmap) crop = c
                        // A big frame is read at about the size the reader worked with before, to keep it quick.
                        val readable = shrunk(c)
                        if (readable !== c) small = readable
                        readBestOrientation(readable, rotation, if (a.isCard) 1.0 else GUIDE_IN_CROP, cjk)
                    }
                    if (strips != null) guess = guess.copy(glare = strips.glare)
                    // The straightened card's art, and its set symbol, tell printings (or a textless card) apart.
                    guess = runCatching { withArt(quad, rotation, strips?.turn, guess, draw, track) }.getOrDefault(guess)
                    if (strips?.turn != null && wantsCentering()) {
                        runCatching { centeringOf(quad, strips.turn, draw) }.getOrNull()?.let { guess = guess.copy(centering = it) }
                    }
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

    /** A copy of the frame turned upright, for reading loose cards (the frame bitmap itself may be reused). */
    private fun upright(b: Bitmap, rotation: Int): Bitmap {
        if (rotation % 360 == 0) return b.copy(Bitmap.Config.ARGB_8888, false)
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    /**
     * With the set lock on, a footer that gave a collector number but no set code is that set's card: the lock
     * stands in for the set code. A poorly read name is dropped then, so the number decides (a misread number
     * still has to agree on two frames before it's looked up).
     */
    private fun locked(g: CardTextGuess): CardTextGuess {
        val lock = setLock() ?: return g
        if (g.setCode != null || g.collectorNumber == null) return g
        val nameOk = g.nameCandidates.firstOrNull()?.let { CardTextParser.readableScore(it) >= 4 } == true
        return g.copy(setCode = lock.uppercase(), nameCandidates = if (nameOk) g.nameCandidates else emptyList())
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
        val look = CardLook.of(luma, gw, card.left, card.top, card.right, card.bottom, gh)
        return res to ReadArea(FrameGeometry.grow(box, 0.15, image.width, image.height), isCard = true, outline = box, quad = quad, look = look)
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
    private suspend fun readStrips(quad: FloatArray, rotation: Int, draw: Draw, cjk: CjkScript?, track: Int? = null): StripRead {
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
            var fusedName: Bitmap? = null
            var fusedFooter: Bitmap? = null
            try {
                if (i == 0) {
                    glare = glareIn(name, CardWarp.NAME, CardWarp.NAME_GLARE) ||
                        (footer != null && glareIn(footer, CardWarp.FOOTER, CardWarp.FOOTER_GLARE))
                    // The footer's small print shows blur first; without one, the name bar.
                    val s = runCatching { CardParts.sharpness(footer ?: name) }.getOrNull()
                    val blurry = s != null && sharp.blurry(s)
                    if (blurry) { blurRun++; if (blurRun >= 2) { blurRun = 0; focusedAt = 0L } } else blurRun = 0
                    if (blurry) return StripRead(CardTextGuess(emptyList(), null, null, 0), null, glare, blurry = true)
                    // The strips of this card's last frames, kept to combine when glare covers part of them.
                    if (track != null) {
                        val key = (track.toLong() shl 2) or (norm(turn) / 90).toLong()
                        fusedName = runCatching { fusedStrip(nameStack, key, name, glare) }.getOrNull()
                        fusedFooter = footer?.let { f -> runCatching { fusedStrip(footerStack, key, f, glare) }.getOrNull() }
                    }
                }
                // Glare that moved between frames is gone from the combined strips. Otherwise uneven light (glare on
                // part of a strip, or dim) is evened out; low-contrast strips (foils, dark frames) are read with
                // their contrast stretched.
                nameRead = fusedName?.let { f -> runCatching { CardParts.stretched(f) }.getOrNull() ?: f }
                    ?: runCatching { CardParts.evened(name, glare) ?: CardParts.stretched(name) }.getOrNull()
                footerRead = fusedFooter?.let { f -> runCatching { CardParts.stretched(f) }.getOrNull() ?: f }
                    ?: footer?.let { f -> runCatching { CardParts.evened(f, glare) ?: CardParts.stretched(f) }.getOrNull() }
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
                // With the set lock on, the number alone names the card.
                val footerOk = g.collectorNumber != null && (g.setCode != null || setLock() != null)
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
                fusedName?.recycle()
                fusedFooter?.recycle()
            }
        }
        // No name in Latin letters either way up: maybe it's printed in Japanese, Chinese or Korean.
        if (cjk == null && ForeignReading.ready) {
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

    /** The name bar and footer of the card in view over its last frames, combined when glare shows ([GlareStack]). */
    private val nameStack = GlareStack()
    private val footerStack = GlareStack()

    /**
     * Adds [strip] to [stack] (drawn at the size of the strips already there) under [key] (card and way up). With
     * [glare] and two frames or more, returns the combined strip as a picture to read; otherwise null.
     */
    private fun fusedStrip(stack: GlareStack, key: Long, strip: Bitmap, glare: Boolean): Bitmap? {
        val sized = if (stack.size > 0 && (strip.width != stack.width || strip.height != stack.height)) {
            Bitmap.createScaledBitmap(strip, stack.width, stack.height, true)
        } else strip
        try {
            stack.add(key, CardWarp.luma(sized), sized.width, sized.height)
        } finally {
            if (sized !== strip) sized.recycle()
        }
        if (!glare) return null
        val f = stack.fused() ?: return null
        val px = IntArray(f.size) { val v = f[it]; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        return Bitmap.createBitmap(px, stack.width, stack.height, Bitmap.Config.ARGB_8888)
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
    private fun withArt(quad: FloatArray, rotation: Int, stripTurn: Int?, g: CardTextGuess, draw: Draw, track: Int? = null): CardTextGuess {
        // A full footer names the printing. A number with no set code (2003-2019 cards) still needs the art. Its
        // look is still taken, to notice a card that doesn't look like the printing it names (a proxy, a misprint).
        if (g.collectorNumber != null && g.setCode != null) {
            val looked = stripTurn?.let { t -> runCatching { lookOf(quad, t, draw) }.getOrNull() }?.let { g.copy(frameLook = it) } ?: g
            // With no name read, the picture checks the number: a misread digit names another card of the set.
            if (g.nameCandidates.isNotEmpty() || g.printedName != null || stripTurn == null) return looked
            return looked.copy(cardPrint = runCatching { printOf(quad, stripTurn, draw, track) }.getOrNull())
        }
        if (g.nameCandidates.isEmpty() && g.printedName == null) {
            val prints = CardPrints.ready
            val turns = CardQuad.uprightTurns(quad, rotation + preferredTurn)
            // Kept in case the card is saved for later, to show it next to the guesses.
            turns.firstOrNull()?.let { t -> CardParts.whole(quad, t, draw)?.let { runCatching { ScanPhotos.keep(it) }; it.recycle() } }
            val arts = if (wantsArt()) turns.mapNotNull { artOf(quad, it, draw) } else emptyList()
            val fingerprints = if (prints) turns.mapNotNull { printOf(quad, it, draw, track) } else emptyList()
            if (arts.isEmpty() && fingerprints.isEmpty()) return g
            return g.copy(
                artOnly = arts.map { it.hash }, artOnlyP = arts.map { it.phash ?: 0L },
                artOnlyEmbeddings = arts.mapNotNull { it.embedding }, artOnlyPrints = fingerprints,
            )
        }
        // Which way up the card is: as the strips were read, or as the whole-card read settled, if the outline agrees.
        val turn = stripTurn ?: norm(rotation + preferredTurn).takeIf { CardQuad.uprightTurns(quad, it).first() == it } ?: return g
        val art = artOf(quad, turn, draw)
        val symbol = symbolOf(quad, turn, draw)
        return g.copy(
            artHash = art?.hash ?: g.artHash, artPHash = art?.phash, artEmbedding = art?.embedding, symbol = symbol,
            cardPrint = printOf(quad, turn, draw, track, offer = true),
            frameLook = lookOf(quad, turn, draw) ?: g.frameLook,
        )
    }

    /** The card's border widths ([CenteringCheck]), drawn upright by [turn] with some of the table around it. */
    private fun centeringOf(quad: FloatArray, turn: Int, draw: Draw): Centering? {
        val m = CenteringCheck.MARGIN
        val card = draw(CardQuad.ordered(quad, turn), floatArrayOf(-m, -m, 1 + m, 1 + m), CenteringCheck.W, CenteringCheck.H) ?: return null
        return try {
            CenteringCheck.measure(CardWarp.luma(card), card.width, card.height)
        } finally {
            card.recycle()
        }
    }

    /** The straightened card's [FrameLook], upright by [turn]. */
    private fun lookOf(quad: FloatArray, turn: Int, draw: Draw): FrameLook? {
        val card = CardParts.whole(quad, turn, draw) ?: return null
        return try {
            ScanPhotos.keep(card)
            val px = IntArray(card.width * card.height)
            card.getPixels(px, 0, card.width, 0, 0, card.width, card.height)
            FrameLook.of(px, card.width, card.height)
        } catch (e: Exception) {
            null
        } finally {
            card.recycle()
        }
    }

    /** The art window of the straightened card: its hashes, and its embedding when the art model is ready. */
    private fun artOf(quad: FloatArray, turn: Int, draw: Draw): ArtRead? = CardParts.art(quad, turn, draw, ::embedCached)

    /**
     * The card's fingerprint by the card picture model, upright by [turn]; reused while the same card stays on
     * the same [track]. With [offer], the straightened card is kept as a possible test photo ([TestPhotos]).
     */
    private fun printOf(quad: FloatArray, turn: Int, draw: Draw, track: Int?, offer: Boolean = false): FloatArray? {
        if (!CardPrints.ready) return null
        val card = CardParts.whole(quad, turn, draw) ?: return null
        try {
            val key = ArtHashing.hash(card, 0, 0, card.width, card.height) ?: 0L
            val known = track?.let { tracks.valueOf(it) }.orEmpty()
            known.firstOrNull { ImageHash.distance(it.first, key) <= SAME_CARD_BITS }?.let { return it.second }
            val print = CardPrints.fingerprint(card) ?: return null
            track?.let { tracks.set(it, (listOf(key to print) + known).take(2)) }
            if (offer) TestPhotos.offer(card)
            return print
        } finally {
            card.recycle()
        }
    }

    /**
     * The card's corners by the corner model, in the [guide] part of [frame], when the outline finder found none;
     * at most every [POSE_MS], as it takes a while.
     */
    private fun poseQuad(frame: Bitmap, guide: OcrBox): FloatArray? {
        if (!CardPrints.ready) return null
        val now = SystemClock.elapsedRealtime()
        if (now - lastPose < POSE_MS) return null
        lastPose = now
        val l = guide.left.coerceIn(0, frame.width - 1)
        val t = guide.top.coerceIn(0, frame.height - 1)
        val w = guide.width.coerceAtMost(frame.width - l)
        val h = guide.height.coerceAtMost(frame.height - t)
        if (w < 64 || h < 64) return null
        val crop = if (l == 0 && t == 0 && w == frame.width && h == frame.height) frame else Bitmap.createBitmap(frame, l, t, w, h)
        try {
            val q = CardPrints.corners(crop) ?: return null
            // A card filling too little of the guide is too small to read.
            if (CardOutline.area(q) < w * h * 0.12f) return null
            return FloatArray(8) { if (it % 2 == 0) q[it] + l else q[it] + t }
        } finally {
            if (crop !== frame) crop.recycle()
        }
    }

    /** A name, a printed name or a collector number was read. */
    private fun hasText(g: CardTextGuess): Boolean = g.nameCandidates.isNotEmpty() || g.printedName != null || g.collectorNumber != null

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
        // The CJK models come from Google Play services; until one has downloaded, its reader fails: skip it for now.
        val (size, all) = try {
            readLines(bitmap, rotation, reader(script))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return null
        }
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
        /** Held still this long, a handled card is read only this often. */
        const val LONG_STILL_MS = 4_000L
        const val LONG_HOLD_INTERVAL_MS = 1_500L
        /** How often the card check runs while a handled card is held still. */
        const val HOLD_GATE_MS = 200L
        /** "Capture page" reads no sooner than this after the tap, and waits at most this long for a steady frame. */
        const val PAGE_MIN_WAIT_MS = 300L
        const val PAGE_MAX_WAIT_MS = 2_500L
        /** Grid size for the card check, along the longer side. */
        const val GRID = 160
        /** A little more than the guide's share of the guide crop ([FrameGeometry.guideCrop] adds 20%). */
        const val GUIDE_IN_CROP = 0.9
        /** Something in the guide with no card edges this long gets a hint. */
        const val NO_EDGES_MS = 2500L
        /** How often a steady frame with no clear outline gets OpenCV's closer look for a card. */
        const val UNSURE_CHECK_MS = 500L
        /** Longest side, in pixels, of a whole-card read. */
        const val MAX_READ = 1024
        /** Art hashes this close are the same art held still: its embedding is reused. */
        const val SAME_ART_BITS = 4
        /** Bits a whole card's hash may change by and still be the same card held in place. */
        const val SAME_CARD_BITS = 6
        /** How often the corner model may look when the outline finder sees no card. */
        const val POSE_MS = 700L
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
