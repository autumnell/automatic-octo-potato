package com.mtgscanner.scan

import android.graphics.Bitmap
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
                val guess = readBestOrientation(bitmap, rotation)
                onGuess(guess)
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
        return guess
    }

    private fun isConfident(g: CardTextGuess): Boolean =
        (g.setCode != null && g.collectorNumber != null) ||
            (g.nameCandidates.isNotEmpty() && g.score >= 25)

    private suspend fun read(bitmap: Bitmap, rotation: Int): CardTextGuess {
        val text: Text = recognizer.process(InputImage.fromBitmap(bitmap, rotation)).await()
        val lines = text.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left, box.top, box.right, box.bottom)
            }
        }
        return CardTextParser.parse(lines)
    }

    fun close() {
        scope.cancel()
        recognizer.close()
    }
}
