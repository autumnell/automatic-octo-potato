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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.util.concurrent.TimeUnit

/** The art model file being downloaded: [done] of [total] bytes. */
data class ModelDownload(val model: ArtModel, val done: Long, val total: Long)

/**
 * An on-device image model (MobileNet V3, through MediaPipe's image embedder) that turns a picture of artwork into
 * about a thousand numbers; the same art gives close numbers even under different light. Two models can be chosen
 * ([ArtModel]); the chosen one isn't in the app: it's downloaded into filesDir the first time art needs comparing
 * (or when it's picked), and the other one's file is deleted. Until it's here, or if it fails, [embed] returns null
 * and the art hashes are used alone.
 */
class ArtEmbedder(private val context: Context) {
    private val dir = File(context.filesDir, "models")
    private val prefs = context.getSharedPreferences("scan", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = sharedHttpClient.newBuilder().readTimeout(30, TimeUnit.SECONDS).build()
    private val downloadLock = Mutex()
    private val lock = Any()
    private var embedder: ImageEmbedder? = null
    /** The model wouldn't load or run this session: stop trying (until another model is picked). */
    @Volatile private var broken = false
    @Volatile private var lastTry = 0L

    private val _model = MutableStateFlow(ArtModel.byId(prefs.getString(PREF, null)) ?: ArtModel.STANDARD)
    /** The chosen model. */
    val chosen: StateFlow<ArtModel> = _model
    val model: ArtModel get() = _model.value

    private val _download = MutableStateFlow<ModelDownload?>(null)
    /** The model download under way, for a progress bar. */
    val download: StateFlow<ModelDownload?> = _download

    private val _failed = MutableStateFlow(false)
    /** The last model download didn't finish. */
    val failed: StateFlow<Boolean> = _failed

    /** Called (on a background thread) after the chosen model changes, so indexes built with the old one are rebuilt. */
    @Volatile var onModelChanged: (() -> Unit)? = null

    init {
        // A switch that was cut short may have left the other model's file behind.
        scope.launch { runCatching { deleteUnused() } }
    }

    private fun fileOf(m: ArtModel) = File(dir, m.fileName)

    /** The chosen model's file is here (it loads on first use). */
    val ready: Boolean get() = !broken && fileOf(model).exists()

    /** Whether [m]'s file is downloaded. */
    fun has(m: ArtModel): Boolean = fileOf(m).exists()

    /**
     * Picks the model to use. Its file is downloaded now (the progress shows in [download]) and the other model's
     * file deleted to free the space. Art indexes built with the other model are rebuilt in the background.
     */
    fun select(m: ArtModel) {
        if (m == model) { ensure(force = true); return }
        synchronized(lock) {
            runCatching { embedder?.close() }
            embedder = null
            _model.value = m
            broken = false
        }
        prefs.edit().putString(PREF, m.id).apply()
        _failed.value = false
        scope.launch {
            deleteUnused()
            runCatching { onModelChanged?.invoke() }
        }
        ensure(force = true)
    }

    /** Deletes the files of the models not chosen, and any cut-off downloads of them. */
    private fun deleteUnused() {
        val keep = model
        ArtModel.entries.filter { it != keep }.forEach { m ->
            runCatching { fileOf(m).delete() }
            runCatching { File(dir, m.fileName + ".part").delete() }
        }
    }

    /** Starts downloading the chosen model in the background if it isn't here; at most once a minute unless [force]. */
    fun ensure(force: Boolean = false) {
        if (broken || ready) return
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastTry < RETRY_MS && lastTry != 0L) return
        lastTry = now
        scope.launch { runCatching { download() } }
    }

    /** Downloads the chosen model if needed and says whether it's usable. */
    suspend fun awaitReady(): Boolean {
        if (broken) return false
        if (!fileOf(model).exists()) runCatching { download() }
        return ready
    }

    private suspend fun download() {
        downloadLock.withLock {
            val m = model
            val file = fileOf(m)
            if (file.exists()) return
            _failed.value = false
            _download.value = ModelDownload(m, 0, m.bytes)
            try {
                withContext(Dispatchers.IO) {
                    dir.mkdirs()
                    val part = File(dir, m.fileName + ".part")
                    client.newCall(Request.Builder().url(m.url).build()).execute().use { resp ->
                        val body = resp.body
                        if (!resp.isSuccessful || body == null) throw IOException("HTTP ${resp.code}")
                        val total = body.contentLength().takeIf { it > 0 } ?: m.bytes
                        part.outputStream().use { out ->
                            body.byteStream().use { input ->
                                val buf = ByteArray(64 * 1024)
                                var done = 0L
                                var shown = 0L
                                while (true) {
                                    // Another model was picked meanwhile: drop this one.
                                    if (model != m) throw IOException("Model changed")
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    done += n
                                    if (done - shown >= 256 * 1024) {
                                        shown = done
                                        _download.value = ModelDownload(m, done, total)
                                    }
                                }
                            }
                        }
                    }
                    // A cut-off download is smaller than the model.
                    if (part.length() >= m.bytes * 9 / 10 && model == m) {
                        if (!part.renameTo(file)) part.delete()
                    } else {
                        part.delete()
                    }
                }
            } catch (t: Throwable) {
                runCatching { File(dir, m.fileName + ".part").delete() }
                if (model == m) _failed.value = true
                throw t
            } finally {
                _download.value = null
            }
            if (!file.exists() && model == m) _failed.value = true
        }
    }

    /**
     * The quantized embedding of [bitmap] (ARGB_8888) by the chosen model, or null when the model isn't here yet
     * (its download is started) or fails. With [only], null unless that is still the chosen model. Runs on the
     * caller's thread; one picture at a time.
     */
    fun embed(bitmap: Bitmap, only: ArtModel? = null): ArtVector? {
        if (broken) return null
        val m = model
        if (only != null && only != m) return null
        if (!fileOf(m).exists()) { ensure(); return null }
        return synchronized(lock) {
            // The model changed while waiting for the lock.
            if (model != m) return@synchronized null
            val e = embedder ?: create(m) ?: return@synchronized null
            try {
                val result = e.embed(BitmapImageBuilder(bitmap).build())
                val values = result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
                values?.takeIf { it.isNotEmpty() }?.let { ArtVector(m.id, ArtEmbedding.quantize(it)) }
            } catch (t: Throwable) {
                null
            }
        }
    }

    /** Called holding [lock]. */
    private fun create(m: ArtModel): ImageEmbedder? = try {
        val buffer = FileInputStream(fileOf(m)).use { it.channel.map(FileChannel.MapMode.READ_ONLY, 0, it.channel.size()) }
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
        runCatching { fileOf(m).delete() }
        null
    }

    fun close() {
        synchronized(lock) {
            runCatching { embedder?.close() }
            embedder = null
        }
    }

    private companion object {
        const val PREF = "artModel"
        const val RETRY_MS = 60_000L
    }
}
