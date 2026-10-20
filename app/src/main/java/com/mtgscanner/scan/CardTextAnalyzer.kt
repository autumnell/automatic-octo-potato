package com.mtgscanner.scan

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PixelFormat
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads text from camera frames and works out which way up the card is.
 *
 * The camera's own rotation only tells us how the phone is held, not how the card lies on the
 * table, so each frame is read at the orientation that worked last time first. If that doesn't
 * give a confident read, the other three quarter turns are tried and the most readable one wins.
 */
class CardTextAnalyzer(
    private val onGuess: (CardTextGuess) -> Unit,
    private val isPaused: () -> Boolean = { false },
    private val minIntervalMs: Long = 250,
    /** Binder page mode: when this returns true, the next frame is read as a 3x3 page. */
    private val wantsPage: () -> Boolean = { false },
    private val onPage: (List<CardTextGuess>) -> Unit = {},
    /** Brightness of the middle of the picture, a few times a second, even while paused (low light, foil hint). */
    private val onLight: (FrameLight) -> Unit = {},
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicBoolean(false)
    private var lastRun = 0L

    /** Extra quarter turns (0, 90, 180, 270) that last produced a good read. */
    @Volatile private var preferredTurn = 0

    private var lastLight = 0L

    /** Samples the RGBA frame directly, without making a bitmap. */
    private fun measureLight(image: ImageProxy) {
        runCatching {
            val plane = image.planes[0]
            val buf = plane.buffer
            val row = plane.rowStride
            val px = plane.pixelStride
            val light = LightCheck.measure(image.width, image.height, { x, y ->
                val i = y * row + x * px
                ((buf.get(i).toInt() and 0xFF) shl 16) or ((buf.get(i + 1).toInt() and 0xFF) shl 8) or (buf.get(i + 2).toInt() and 0xFF)
            })
            onLight(light)
        }
    }

    /** Card check before any text reading: skips frames with no card, or while the card moves. */
    private val gate = CardGate()
    private var lastGate = 0L

    /** Reused for every frame, so a 3.7 MB bitmap isn't made four times a second. */
    private var frameBitmap: Bitmap? = null

    /** Where to read in the frame (sensor pixels), and whether that is a found card or just the guide. */
    private class ReadArea(val box: OcrBox, val isCard: Boolean)

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastLight >= 200) {
            lastLight = now
            measureLight(image)
        }
        if (isPaused()) gate.reset()
        if (isPaused() || busy.get()) {
            image.close()
            return
        }
        val rotation = image.imageInfo.rotationDegrees
        val page = wantsPage()
        var area: ReadArea? = null
        if (!page) {
            // The card check is cheap; text reading only runs on a steady frame with something in the guide.
            if (now - lastGate < GATE_INTERVAL_MS) {
                image.close()
                return
            }
            lastGate = now
            val result = runCatching { checkCard(image, rotation) }.getOrNull()
            if (result != null && !result.first.runOcr) {
                if (result.first.state == GateState.EMPTY && now - lastRun >= minIntervalMs) {
                    // Nothing in the guide: tell the scanner, so it knows the card has left.
                    lastRun = now
                    image.close()
                    scope.launch { runCatching { onGuess(CardTextGuess(emptyList(), null, null, 0)) } }
                    return
                }
                image.close()
                return
            }
            area = result?.second
        }
        if (now - lastRun < minIntervalMs) {
            image.close()
            return
        }
        busy.set(true)
        lastRun = now
        val bitmap: Bitmap = try {
            frameToBitmap(image)
        } catch (t: Throwable) {
            busy.set(false)
            image.close()
            return
        }
        image.close()
        val reused = bitmap === frameBitmap

        scope.launch {
            var crop: Bitmap? = null
            try {
                if (page) onPage(readPage(bitmap, rotation))
                else {
                    val a = area ?: ReadArea(guideArea(bitmap.width, bitmap.height, rotation), isCard = false)
                    val b = a.box
                    val c = if (b.width >= 32 && b.height >= 32 && (b.width < bitmap.width || b.height < bitmap.height)) {
                        Bitmap.createBitmap(bitmap, b.left, b.top, b.width, b.height)
                    } else bitmap
                    if (c !== bitmap) crop = c
                    // Cropped to a found card, everything read is on it; cropped to the guide, keep to the guide.
                    onGuess(readBestOrientation(c, rotation, if (a.isCard) 1.0 else GUIDE_IN_CROP))
                }
            } catch (_: Throwable) {
                // Skip this frame; the next one will try again.
            } finally {
                crop?.recycle()
                if (!reused) bitmap.recycle()
                busy.set(false)
            }
        }
    }

    /** RGBA frames are copied into one reused bitmap; anything else falls back to a new one. */
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

    /** The guide area in sensor pixels. */
    private fun guideArea(sensorW: Int, sensorH: Int, rotation: Int): OcrBox {
        val sideways = rotation == 90 || rotation == 270
        val uw = if (sideways) sensorH else sensorW
        val uh = if (sideways) sensorW else sensorH
        return FrameGeometry.toSensor(FrameGeometry.guideCrop(uw, uh), rotation, sensorW, sensorH)
    }

    /**
     * Samples the guide area of the RGBA frame into a small grayscale grid (no bitmap) and runs the
     * card check. Returns what it decided and, when a card was found, the area to read.
     */
    private fun checkCard(image: ImageProxy, rotation: Int): Pair<GateResult, ReadArea?> {
        val plane = image.planes[0]
        val buf = plane.buffer
        val row = plane.rowStride
        val px = plane.pixelStride
        val region = guideArea(image.width, image.height, rotation)
        val step = maxOf(1, maxOf(region.width, region.height) / GRID)
        val gw = region.width / step
        val gh = region.height / step
        val luma = IntArray(gw * gh)
        fun at(x: Int, y: Int): Int {
            val i = y * row + x * px
            val r = buf.get(i).toInt() and 0xFF
            val g = buf.get(i + 1).toInt() and 0xFF
            val b = buf.get(i + 2).toInt() and 0xFF
            return (r * 77 + g * 150 + b * 29) shr 8
        }
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
        // Grown well past the outline found, in case it was an inner frame line (art, text box) rather than the card edge.
        return res to ReadArea(FrameGeometry.grow(box, 0.15, image.width, image.height), isCard = true)
    }

    private suspend fun readBestOrientation(bitmap: Bitmap, cameraRotation: Int, frame: Double): CardTextGuess {
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
        val (turn, guess) = best!!
        if (guess.score > 0) preferredTurn = turn
        // Without a readable footer, the artwork is what tells printings apart.
        val art = guess.artBox
        if (art != null && guess.collectorNumber == null) {
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

    private fun isConfident(g: CardTextGuess): Boolean =
        (g.setCode != null && g.collectorNumber != null) ||
            (g.nameCandidates.isNotEmpty() && g.score >= 25)

    private suspend fun read(bitmap: Bitmap, rotation: Int, frame: Double): CardTextGuess {
        val (size, lines) = readLines(bitmap, rotation)
        // Only read text inside the frame, so cards around the edges don't get mixed in.
        return CardTextParser.parse(CardTextParser.insideFrame(lines, size.first, size.second, frame))
    }

    /** OCR lines at [rotation], with the upright image's width and height. */
    private suspend fun readLines(bitmap: Bitmap, rotation: Int): Pair<Pair<Int, Int>, List<OcrLine>> {
        val text: Text = recognizer.process(InputImage.fromBitmap(bitmap, rotation)).await()
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
    }

    private companion object {
        /** How often the card check runs. Two steady checks in a row start a read. */
        const val GATE_INTERVAL_MS = 100L
        /** Grid size for the card check, along the longer side. */
        const val GRID = 160
        /** A little more than the guide's share of the guide crop ([FrameGeometry.guideCrop] adds 20%). */
        const val GUIDE_IN_CROP = 0.9
    }
}
