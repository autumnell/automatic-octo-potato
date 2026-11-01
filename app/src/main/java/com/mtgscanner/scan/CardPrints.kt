package com.mtgscanner.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.mtgscanner.data.ScryfallCard
import com.mtgscanner.data.sharedHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Knowing a card by its picture: a model trained on Magic cards turns the straightened card into a fingerprint,
 * which is looked up among the fingerprints of every printing (the gallery). A second model finds a card's corners
 * when the outline finder can't (borderless cards, a mat of the same colour, glare on an edge).
 *
 * Off until turned on in Settings › Data: then the two models and the gallery (about 75 MB) are downloaded once from
 * Hugging Face, and the gallery is packed into a smaller file. Printings newer than the gallery are added from
 * Scryfall's pictures on Wi-Fi, once a week.
 *
 * Models: dhvazquez/mtg_card_identification_embeddings and dhvazquez/mtg_card_pose_estimation (MIT).
 * Gallery: maxhille/mtg-gallery-creator (CC-BY 4.0), built from Scryfall's card images.
 */
object CardPrints {
    sealed interface State {
        data object Off : State
        /** Downloading [percent] of everything. */
        data class Downloading(val percent: Int) : State
        data object Preparing : State
        data object Ready : State
        data class Failed(val reason: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Off)
    val state: StateFlow<State> = _state
    val ready: Boolean get() = _state.value == State.Ready && gallery != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val client by lazy { sharedHttpClient.newBuilder().readTimeout(60, TimeUnit.SECONDS).build() }
    private lateinit var dir: File

    @Volatile var gallery: CardGallery? = null
        private set
    @Volatile private var cardNet: OnnxNet? = null
    @Volatile private var poseNet: OnnxNet? = null

    private const val PREFS = "scan"
    private const val KEY_ON = "cardPrints"
    private const val KEY_TOPPED = "cardPrintsTopUp"
    private const val KEY_STAND_OUT = "printsStandOut"
    private const val KEY_LEAD = "printsLead"

    /** How sure a picture match must be: the defaults, or fitted to your own scans ([TestPhotos.tune]). */
    @Volatile var thresholds = MatchThresholds()
        private set

    /** Keeps [t] (the defaults clear what was fitted). */
    fun setThresholds(ctx: Context, t: MatchThresholds) {
        thresholds = t
        val e = prefs(ctx).edit()
        if (t.isDefault) e.remove(KEY_STAND_OUT).remove(KEY_LEAD) else e.putFloat(KEY_STAND_OUT, t.standOut).putFloat(KEY_LEAD, t.lead)
        e.apply()
    }

    private class Download(val url: String, val file: String, val bytes: Long)

    private const val HF = "https://huggingface.co"
    private val CARD_MODEL = Download("$HF/dhvazquez/mtg_card_identification_embeddings/resolve/main/20260521-044732/onnx/mtg_embed_fp16.onnx", "card.onnx", 7_390_423)
    private val POSE_MODEL = Download("$HF/dhvazquez/mtg_card_pose_estimation/resolve/main/exports/mtg_4kp_s_r4b_fp16.onnx", "corners.onnx", 7_495_617)
    private val GALLERY = Download("$HF/maxhille/mtg-gallery-creator/resolve/main/gallery/gallery_f16.bin", "gallery_f16.bin", 54_091_264)
    private val IDS = Download("$HF/maxhille/mtg-gallery-creator/resolve/main/gallery/gallery_ids.json", "gallery_ids.json", 4_225_880)
    private val META = Download("$HF/maxhille/mtg-gallery-creator/resolve/main/gallery/meta.json", "meta.json", 7_253_252)
    private val ALL = listOf(CARD_MODEL, POSE_MODEL, IDS, META, GALLERY)
    /** Rounded, for the settings row. */
    const val DOWNLOAD_MB = 77

    private const val PACKED = "gallery.bin"
    private const val EXTRA = "gallery-new.bin"

    fun isOn(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_ON, false)

    /** At app start: when turned on, loads what's downloaded (and fetches anything missing). */
    fun init(ctx: Context) {
        dir = File(ctx.applicationContext.filesDir, "prints")
        val p = prefs(ctx)
        if (p.contains(KEY_STAND_OUT)) thresholds = MatchThresholds(p.getFloat(KEY_STAND_OUT, CardGallery.MIN_STAND_OUT), p.getFloat(KEY_LEAD, CardGallery.MIN_LEAD))
        if (isOn(ctx)) start(ctx.applicationContext)
    }

    fun turnOn(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ON, true).apply()
        start(ctx.applicationContext)
    }

    /** Off: the models are closed and every file deleted to free the space. */
    fun turnOff(ctx: Context) {
        prefs(ctx).edit().putBoolean(KEY_ON, false).remove(KEY_TOPPED).apply()
        _state.value = State.Off
        scope.launch {
            lock.withLock {
                close()
                runCatching { File(ctx.applicationContext.filesDir, "prints").deleteRecursively() }
            }
        }
    }

    private fun start(app: Context) {
        if (!::dir.isInitialized) dir = File(app.filesDir, "prints")
        val s = _state.value
        if (s is State.Downloading || s == State.Preparing || s == State.Ready) return
        scope.launch {
            lock.withLock {
                try {
                    prepare(app)
                    _state.value = if (isOn(app)) State.Ready else State.Off
                } catch (t: Throwable) {
                    close()
                    _state.value = if (isOn(app)) State.Failed(failReason(t)) else State.Off
                }
            }
        }
    }

    private fun failReason(t: Throwable): String = when (t) {
        is java.net.UnknownHostException, is java.net.ConnectException -> "No connection"
        is java.net.SocketTimeoutException -> "Connection timed out"
        is OutOfMemoryError -> "Not enough memory"
        else -> t.message?.takeIf { it.length < 60 } ?: "Download failed"
    }

    private suspend fun prepare(app: Context) {
        dir.mkdirs()
        val packed = File(dir, PACKED)
        val needed = ALL.filter { d -> !File(dir, d.file).exists() && !(packed.exists() && d in listOf(GALLERY, IDS, META)) }
        if (needed.isNotEmpty()) {
            val free = dir.usableSpace
            if (free in 1 until needed.sumOf { it.bytes } + 60L * 1024 * 1024) throw IOException("Not enough space")
            val total = needed.sumOf { it.bytes }
            var before = 0L
            _state.value = State.Downloading(0)
            for (d in needed) {
                if (!isOn(app)) return
                fetch(d) { done -> _state.value = State.Downloading(((before + done) * 100 / total).toInt().coerceIn(0, 100)) }
                before += d.bytes
            }
        }
        if (!packed.exists()) {
            _state.value = State.Preparing
            val part = File(dir, "$PACKED.part")
            part.outputStream().use { out ->
                File(dir, IDS.file).reader().use { ids ->
                    File(dir, META.file).reader().use { meta ->
                        File(dir, GALLERY.file).inputStream().use { halves -> CardGallery.Published.convert(ids, meta, halves, out) }
                    }
                }
            }
            if (!part.renameTo(packed)) throw IOException("Couldn't save the gallery")
            listOf(GALLERY, IDS, META).forEach { File(dir, it.file).delete() }
        }
        if (!isOn(app)) return
        _state.value = State.Preparing
        val base = packed.inputStream().use { CardGallery.Codec.read(it) }
        val extra = File(dir, EXTRA).takeIf { it.exists() }?.let { f -> runCatching { f.inputStream().use { CardGallery.Codec.read(it) } }.getOrNull() }
        gallery = CardGallery.merge(base, extra)
        cardNet = OnnxNet(File(dir, CARD_MODEL.file))
        poseNet = runCatching { OnnxNet(File(dir, POSE_MODEL.file)) }.getOrNull()
    }

    private fun fetch(d: Download, progress: (Long) -> Unit) {
        val file = File(dir, d.file)
        val part = File(dir, d.file + ".part")
        client.newCall(Request.Builder().url(d.url).build()).execute().use { resp ->
            val body = resp.body
            if (!resp.isSuccessful || body == null) throw IOException("Download failed (${resp.code})")
            part.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var shown = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - shown >= 512 * 1024) { shown = done; progress(done) }
                    }
                }
            }
        }
        // A cut-off download is smaller than the file.
        if (part.length() < d.bytes * 9 / 10) { part.delete(); throw IOException("Download cut off") }
        if (!part.renameTo(file)) throw IOException("Couldn't save ${d.file}")
    }

    private fun close() {
        cardNet?.close(); cardNet = null
        poseNet?.close(); poseNet = null
        gallery = null
    }

    /** The fingerprint of a straightened card picture ([CardPrintPrep.CARD_W] x [CardPrintPrep.CARD_H]); null when off. */
    fun fingerprint(card: Bitmap): FloatArray? {
        val net = cardNet ?: return null
        if (!ready) return null
        val px = pixelsAt(card, CardPrintPrep.CARD_W, CardPrintPrep.CARD_H)
        return runCatching {
            val out = net.run(CardPrintPrep.cardInput(px), CardPrintPrep.cardShape).values.firstOrNull() ?: return null
            CardGallery.unit(out)
        }.getOrNull()
    }

    /** The four corners of the card in [picture], by the corner model; null when off or no card is clear. */
    fun corners(picture: Bitmap): FloatArray? {
        val net = poseNet ?: return null
        if (!ready) return null
        val px = pixelsAt(picture, CardPrintPrep.POSE, CardPrintPrep.POSE)
        return runCatching {
            val out = net.run(CardPrintPrep.poseInput(px), CardPrintPrep.poseShape)
            CardPrintPrep.corners(out["scores"] ?: return null, out["keypoints"] ?: return null, picture.width, picture.height)
        }.getOrNull()
    }

    /** [b]'s pixels at [w] x [h] (stretched to fit, as the models were taught). */
    private fun pixelsAt(b: Bitmap, w: Int, h: Int): IntArray {
        val sized = if (b.width == w && b.height == h) b else Bitmap.createScaledBitmap(b, w, h, true)
        return try {
            IntArray(w * h).also { sized.getPixels(it, 0, w, 0, 0, w, h) }
        } finally {
            if (sized !== b) sized.recycle()
        }
    }

    /** The card matched by [print] (and [also], the same card read the other way up), or null when none clearly is. */
    fun match(print: FloatArray?, also: List<FloatArray> = emptyList(), onlySet: String? = null): GalleryHit? {
        val g = gallery ?: return null
        val t = thresholds
        return (listOfNotNull(print) + also).mapNotNull { g.match(it, onlySet, minStandOut = t.standOut, minLead = t.lead) }.maxByOrNull { it.standOut }
    }

    /** Of [printings], the one [print] shows, when clear. */
    fun pick(print: FloatArray?, printings: List<ScryfallCard>): ScryfallCard? {
        val g = gallery ?: return null
        print ?: return null
        val id = g.pick(print, printings.map { it.id }) ?: return null
        return printings.firstOrNull { it.id == id }
    }

    /**
     * Adds printings released in the last year that the gallery doesn't have (it was built once; new sets come
     * out every few weeks), from Scryfall's pictures, at most [max] a time. Once a week, on Wi-Fi.
     */
    suspend fun addNewPrintings(ctx: Context, recent: suspend (String) -> List<ScryfallCard>, onWifi: Boolean, max: Int = 600) {
        if (!ready || !onWifi) return
        val p = prefs(ctx)
        val today = LocalDate.now()
        val last = runCatching { LocalDate.parse(p.getString(KEY_TOPPED, "")) }.getOrNull()
        if (last != null && last.plusDays(7) > today) return
        val g = gallery ?: return
        val missing = recent(today.minusYears(1).toString()).filter { !g.has(it.id) && it.imageUrl != null }.take(max)
        p.edit().putString(KEY_TOPPED, today.toString()).apply()
        if (missing.isEmpty()) return
        val rows = ArrayList<CardGallery.Row>()
        for (c in missing) {
            if (!isOn(ctx)) return
            val bmp = runCatching {
                client.newCall(Request.Builder().url(c.imageUrl!!).build()).execute().use { r ->
                    r.body?.bytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }
                }
            }.getOrNull() ?: continue
            try {
                fingerprint(bmp)?.let { rows += CardGallery.Row(c.id, c.name, c.set, c.collectorNumber, it) }
            } finally {
                bmp.recycle()
            }
        }
        if (rows.isEmpty()) return
        lock.withLock {
            val file = File(dir, EXTRA)
            val old = file.takeIf { it.exists() }?.let { f -> runCatching { f.inputStream().use { CardGallery.Codec.read(it) } }.getOrNull() }
            val extra = old?.let { CardGallery.merge(it, CardGallery.of(rows)) } ?: CardGallery.of(rows)
            val part = File(dir, "$EXTRA.part")
            part.outputStream().use { CardGallery.Codec.write(extra, it) }
            part.renameTo(file)
            gallery?.let { gallery = CardGallery.merge(it, CardGallery.of(rows)) }
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
