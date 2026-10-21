package com.mtgscanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.imageembedder.ImageEmbedder
import com.mtgscanner.data.sharedHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit

/**
 * A small on-device image model (MobileNet V3 small, through MediaPipe's image embedder) that turns a picture of
 * artwork into 1024 numbers; the same art gives close numbers even under different light. The model (about 4 MB)
 * isn't in the app: it's downloaded into filesDir the first time art needs comparing. Until it's here, or if it
 * fails, [embed] returns null and the art hashes are used alone.
 */
class ArtEmbedder(private val context: Context) {
    private val file = File(context.filesDir, "models/$MODEL_NAME")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = sharedHttpClient.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()
    private val downloadLock = Mutex()
    private val lock = Any()
    private var embedder: ImageEmbedder? = null
    /** The model wouldn't load or run this session: stop trying. */
    @Volatile private var broken = false
    @Volatile private var lastTry = 0L

    /** The model file is here (it loads on first use). */
    val ready: Boolean get() = !broken && file.exists()

    /** Starts downloading the model in the background if it isn't here; at most once a minute. */
    fun ensure() {
        if (broken || file.exists()) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTry < RETRY_MS && lastTry != 0L) return
        lastTry = now
        scope.launch { runCatching { download() } }
    }

    /** Downloads the model if needed and says whether it's usable. */
    suspend fun awaitReady(): Boolean {
        if (broken) return false
        if (!file.exists()) runCatching { download() }
        return ready
    }

    private suspend fun download() {
        downloadLock.withLock {
            if (file.exists()) return
            withContext(Dispatchers.IO) {
                file.parentFile?.mkdirs()
                val part = File(file.parentFile, "$MODEL_NAME.part")
                client.newCall(Request.Builder().url(MODEL_URL).build()).execute().use { resp ->
                    val body = resp.body
                    if (resp.isSuccessful && body != null) part.outputStream().use { out -> body.byteStream().copyTo(out) }
                }
                // A cut-off download is much smaller than the model.
                if (part.exists() && part.length() > MIN_BYTES) {
                    if (!part.renameTo(file)) part.delete()
                } else {
                    part.delete()
                }
            }
        }
    }

    /**
     * The quantized embedding of [bitmap] (ARGB_8888), or null when the model isn't here yet (its download is
     * started) or fails. Runs on the caller's thread; one picture at a time.
     */
    fun embed(bitmap: Bitmap): ByteArray? {
        if (broken) return null
        if (!file.exists()) { ensure(); return null }
        return synchronized(lock) {
            val e = embedder ?: create() ?: return@synchronized null
            try {
                val result = e.embed(BitmapImageBuilder(bitmap).build())
                val values = result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
                values?.takeIf { it.isNotEmpty() }?.let { ArtEmbedding.quantize(it) }
            } catch (t: Throwable) {
                null
            }
        }
    }

    private fun create(): ImageEmbedder? = try {
        val buffer = FileInputStream(file).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()) }
        val options = ImageEmbedder.ImageEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetBuffer(buffer).build())
            .setRunningMode(RunningMode.IMAGE)
            .setL2Normalize(true)
            .setQuantize(false)
            .build()
        ImageEmbedder.createFromOptions(context, options).also { embedder = it }
    } catch (t: Throwable) {
        // A damaged file: delete it so the next session downloads it again, and use hashes for now.
        broken = true
        runCatching { file.delete() }
        null
    }

    fun close() {
        synchronized(lock) {
            runCatching { embedder?.close() }
            embedder = null
        }
    }

    companion object {
        private const val MODEL_NAME = "mobilenet_v3_small.tflite"
        /** Version 1 of the model, pinned so every index built with it stays comparable. */
        const val MODEL_URL = "https://storage.googleapis.com/mediapipe-models/image_embedder/mobilenet_v3_small/float32/1/mobilenet_v3_small.tflite"
        private const val MIN_BYTES = 1_000_000L
        private const val RETRY_MS = 60_000L
    }
}
