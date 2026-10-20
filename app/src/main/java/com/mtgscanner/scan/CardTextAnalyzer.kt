package com.mtgscanner.scan

import android.graphics.Bitmap
import android.graphics.Matrix
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
) : ImageAnalysis.Analyzer {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val busy = AtomicBoolean(false)
    private var lastRun = 0L

    /** Extra quarter turns (0, 90, 180, 270) that last produced a good read. */
    @Volatile private var preferredTurn = 0

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (isPaused() || busy.get() || now - lastRun < minIntervalMs) {
            image.close()
            return
        }
        busy.set(true)
        lastRun = now
        val rotation = image.imageInfo.rotationDegrees
        val bitmap: Bitmap = try {
            image.toBitmap()
        } catch (t: Throwable) {
            busy.set(false)
            image.close()
            return
        }
        image.close()

        scope.launch {
            try {
                if (wantsPage()) onPage(readPage(bitmap, rotation))
                else onGuess(readBestOrientation(bitmap, rotation))
            } catch (_: Throwable) {
                // Skip this frame; the next one will try again.
            } finally {
                bitmap.recycle()
                busy.set(false)
            }
        }
    }

    private suspend fun readBestOrientation(bitmap: Bitmap, cameraRotation: Int): CardTextGuess {
        val order = listOf(preferredTurn) + listOf(0, 90, 180, 270).filter { it != preferredTurn }
        var best: Pair<Int, CardTextGuess>? = null
        for (turn in order) {
            val guess = read(bitmap, (cameraRotation + turn) % 360)
            if (best == null || guess.score > best.second.score) best = turn to guess
            // Good enough: a readable name, or the exact printing from the footer.
            if (isConfident(guess)) break
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
        var best: Pair<Int, List<OcrLine>>? = null
        var bestScore = -1
        for (turn in order) {
            val lines = readLines(bitmap, (cameraRotation + turn) % 360).second
            val score = lines.sumOf { CardTextParser.readableScore(it.text) }
            if (score > bestScore) { bestScore = score; best = turn to lines }
            // Plenty of readable text: this is the right way up.
            if (score >= 120) break
        }
        val (turn, lines) = best!!
        if (bestScore > 0) preferredTurn = turn
        return CardTextParser.parseGrid(lines)
    }

    private fun hashArt(bitmap: Bitmap, rotation: Int, box: OcrBox): Long? {
        val upright = if (rotation == 0) bitmap else {
            val m = Matrix().apply { postRotate(rotation.toFloat()) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
        }
        return try {
            ArtHashing.hash(upright, box.left, box.top, box.right, box.bottom)
        } finally {
            if (upright !== bitmap) upright.recycle()
        }
    }

    private fun isConfident(g: CardTextGuess): Boolean =
        (g.setCode != null && g.collectorNumber != null) ||
            (g.nameCandidates.isNotEmpty() && g.score >= 25)

    private suspend fun read(bitmap: Bitmap, rotation: Int): CardTextGuess {
        val (size, lines) = readLines(bitmap, rotation)
        // Only read text inside the frame, so cards around the edges don't get mixed in.
        return CardTextParser.parse(CardTextParser.insideFrame(lines, size.first, size.second))
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
}
